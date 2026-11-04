package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CachedAppAssetLoader {
    private final ThreadPoolExecutor cacheExecutor = executor(1, 100);
    private final ThreadPoolExecutor foregroundExecutor = executor(3, 40);
    private final ThreadPoolExecutor networkExecutor = executor(3, 40);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ComputerDetails computer;
    private final double scalingDivider;
    private final NetworkAssetLoader networkLoader;
    private final MemoryAssetLoader memoryLoader;
    private final DiskAssetLoader diskLoader;

    private static ThreadPoolExecutor executor(int threads, int pending) {
        return new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.DAYS,
                new LinkedBlockingQueue<Runnable>(pending), new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public CachedAppAssetLoader(ComputerDetails computer, double scalingDivider,
                                NetworkAssetLoader networkLoader, MemoryAssetLoader memoryLoader,
                                DiskAssetLoader diskLoader) {
        this.computer = computer;
        this.scalingDivider = scalingDivider;
        this.networkLoader = networkLoader;
        this.memoryLoader = memoryLoader;
        this.diskLoader = diskLoader;
    }

    public interface ArtCallback {
        /** Called on the main thread. Null means this app has no real art. */
        void onArt(Bitmap art);
    }

    public void loadArt(NvApp app, ArtCallback callback) {
        LoaderTuple tuple = new LoaderTuple(computer, app);
        ScaledBitmap cached = memoryLoader.loadBitmapFromCache(tuple);
        if (cached != null) {
            deliver(cached, callback);
            return;
        }
        foregroundExecutor.execute(() -> {
            ScaledBitmap bitmap = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
            if (bitmap != null) {
                memoryLoader.populateCache(tuple, bitmap);
                deliver(bitmap, callback);
            } else {
                // Network retries must not occupy a disk worker.
                networkExecutor.execute(() -> {
                    ScaledBitmap loaded = doNetworkAssetLoad(tuple, true);
                    if (loaded != null) memoryLoader.populateCache(tuple, loaded);
                    deliver(loaded, callback);
                });
            }
        });
    }

    private void deliver(ScaledBitmap bitmap, ArtCallback callback) {
        Bitmap art = isBitmapPlaceholder(bitmap) ? null : bitmap.bitmap;
        mainHandler.post(() -> callback.onArt(art));
    }

    public void cancelBackgroundLoads() {
        cacheExecutor.getQueue().clear();
    }

    public void cancelForegroundLoads() {
        foregroundExecutor.getQueue().clear();
        networkExecutor.getQueue().clear();
    }

    public void freeCacheMemory() {
        memoryLoader.clearCache();
    }

    private ScaledBitmap doNetworkAssetLoad(LoaderTuple tuple, boolean wantBitmap) {
        for (int i = 0; i < 3; i++) {
            InputStream in = networkLoader.getBitmapStream(tuple);
            if (in != null) {
                try {
                    diskLoader.populateCacheWithStream(tuple, in);
                } finally {
                    try {
                        in.close();
                    } catch (IOException ignored) {}
                }
                if (!wantBitmap) return null;
                ScaledBitmap bitmap = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
                if (bitmap != null) return bitmap;
            }
            try {
                Thread.sleep((int) (1000 + Math.random() * 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void queueCacheLoad(NvApp app) {
        LoaderTuple tuple = new LoaderTuple(computer, app);
        if (memoryLoader.loadBitmapFromCache(tuple) != null) return;
        cacheExecutor.execute(() -> {
            if (!diskLoader.checkCacheExists(tuple)) doNetworkAssetLoad(tuple, false);
        });
    }

    private boolean isBitmapPlaceholder(ScaledBitmap bitmap) {
        return bitmap == null ||
                (bitmap.originalWidth == 130 && bitmap.originalHeight == 180) || // GFE 2.0
                (bitmap.originalWidth == 628 && bitmap.originalHeight == 888); // GFE 3.0
    }

    public static class LoaderTuple {
        public final ComputerDetails computer;
        public final NvApp app;

        public LoaderTuple(ComputerDetails computer, NvApp app) {
            this.computer = computer;
            this.app = app;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LoaderTuple)) return false;
            LoaderTuple other = (LoaderTuple) o;
            return computer.uuid.equals(other.computer.uuid) && app.getAppId() == other.app.getAppId();
        }

        @Override
        public String toString() {
            return "(" + computer.uuid + ", " + app.getAppId() + ")";
        }
    }
}
