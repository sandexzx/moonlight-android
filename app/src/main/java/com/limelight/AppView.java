package com.limelight;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.ui.apps.AppItem;
import com.limelight.ui.apps.AppListActions;
import com.limelight.ui.apps.AppListHost;
import com.limelight.ui.apps.AppListState;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;

public class AppView extends ComponentActivity implements AppListActions {
    public static final String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";
    public static final String NAME_EXTRA = "Name";
    public static final String UUID_EXTRA = "UUID";
    public static final String NEW_PAIR_EXTRA = "NewPair";
    public static final String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private String uuidString;
    private ShortcutHelper shortcutHelper;
    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private CachedAppAssetLoader assetLoader;
    private AppListState state;
    private String lastRawApplist;
    private int lastRunningAppId;
    private volatile boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean serviceBound;
    private final HashSet<Integer> hiddenAppIds = new HashSet<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                final ComputerDetails foundComputer = localBinder.getComputer(uuidString);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (foundComputer == null) {
                        finish();
                        return;
                    }
                    computer = foundComputer;
                    shortcutHelper.createAppViewShortcut(computer, true,
                            getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);
                    double divisor = Math.max(1.0, 300 / (72 *
                            (getResources().getDisplayMetrics().densityDpi / 160.0)));
                    assetLoader = new CachedAppAssetLoader(computer, divisor,
                            new NetworkAssetLoader(AppView.this, localBinder.getUniqueId()),
                            new MemoryAssetLoader(), new DiskAssetLoader(AppView.this));
                    managerBinder = localBinder;
                    // Read the cache off the main thread, before starting polling.
                    new Thread(() -> {
                        populateAppGridWithCache();
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) startComputerUpdates();
                        });
                    }).start();
                });
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inForeground = true;
        shortcutHelper = new ShortcutHelper(this);
        UiHelper.setLocale(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }
        UiHelper.notifyNewComposeRootView(this);
        boolean showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);
        SharedPreferences prefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String id : prefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(id));
        }
        String computerName = getIntent().getStringExtra(NAME_EXTRA);
        setTitle(computerName);
        state = new AppListState(computerName, showHiddenApps, hiddenAppIds);
        AppListHost.attach(this, state, this);
        serviceBound = bindService(new Intent(this, ComputerManagerService.class),
                serviceConnection, Service.BIND_AUTO_CREATE);
    }

    private void populateAppGridWithCache() {
        try {
            lastRawApplist = CacheHelper.readInputStreamToString(
                    CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(lastRawApplist)));
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: " + lastRawApplist);
                lastRawApplist = null;
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
        }
    }

    private void startComputerUpdates() {
        if (managerBinder == null || assetLoader == null || !inForeground) return;
        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(ComputerDetails details) {
                if (suspendGridUpdates || !details.uuid.equalsIgnoreCase(uuidString)) return;
                runOnUiThread(() -> {
                    if (!inForeground || isFinishing() || isDestroyed() || suspendGridUpdates) return;
                    if (details.state == ComputerDetails.State.OFFLINE) {
                        Toast.makeText(AppView.this, R.string.lost_connection, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    if (details.state == ComputerDetails.State.ONLINE &&
                            details.pairState != PairingManager.PairState.PAIRED) {
                        shortcutHelper.disableComputerShortcut(details, getString(R.string.scut_not_paired));
                        Toast.makeText(AppView.this, R.string.scut_not_paired, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    if (details.rawAppList != null && !details.rawAppList.equals(lastRawApplist)) {
                        try {
                            List<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(details.rawAppList));
                            lastRawApplist = details.rawAppList;
                            updateUiWithAppList(apps);
                        } catch (IOException | XmlPullParserException e) {
                            e.printStackTrace();
                        }
                    }
                    if (details.runningGameId != lastRunningAppId) {
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }
                });
            }
        });
        if (poller == null) poller = managerBinder.createAppListPoller(computer);
        poller.start();
        for (AppItem item : state.getApps()) {
            if (state.art(item.getId()) == null) loadArt(state.app(item.getId()));
        }
    }

    private void stopComputerUpdates() {
        if (poller != null) poller.stop();
        if (managerBinder != null) managerBinder.stopPolling();
        if (assetLoader != null) {
            assetLoader.cancelForegroundLoads();
            assetLoader.cancelBackgroundLoads();
            assetLoader.freeCacheMemory();
        }
    }

    private void updateUiWithServerinfo(ComputerDetails details) {
        runOnUiThread(() -> state.updateRunning(details.runningGameId));
    }

    private void updateUiWithAppList(List<NvApp> apps) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            HashSet<Integer> incoming = new HashSet<>();
            for (NvApp app : apps) {
                incoming.add(app.getAppId());
                if (state.app(app.getAppId()) == null) shortcutHelper.enableAppShortcut(computer, app);
            }
            for (AppItem old : state.getApps()) {
                if (!incoming.contains(old.getId())) {
                    shortcutHelper.disableAppShortcut(computer, state.app(old.getId()), "App removed from PC");
                }
            }
            state.updateApps(apps);
            if (inForeground) {
                for (NvApp app : apps) {
                    assetLoader.queueCacheLoad(app);
                    loadArt(app);
                }
            }
        });
    }

    private void loadArt(NvApp app) {
        assetLoader.loadArt(app, art -> {
            if (art != null) state.updateArt(app.getAppId(), art);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.showDecoderCrashDialog(this);
        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;
        stopComputerUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Dialog.closeDialogs();
        if (serviceBound) unbindService(serviceConnection);
    }

    @Override
    public void onBack() { finish(); }

    @Override
    public void onStartApp(int appId) {
        NvApp app = state.app(appId);
        if (app == null || managerBinder == null) return;
        ServerHelper.doStart(this, app, computer, managerBinder);
    }

    @Override
    public void onResumeApp(int appId) { onStartApp(appId); }

    @Override
    public void onQuitApp(int appId) {
        NvApp app = state.app(appId);
        if (app == null || managerBinder == null) return;
        state.confirm(getString(R.string.applist_menu_quit), getString(R.string.applist_quit_confirmation),
                getString(R.string.yes), getString(R.string.no), true, () -> {
                    if (managerBinder == null) return;
                    suspendGridUpdates = true;
                    ServerHelper.doQuit(this, computer, app, managerBinder, () -> {
                        suspendGridUpdates = false;
                        if (poller != null) poller.pollNow();
                    });
                });
    }

    @Override
    public void onQuitAndStartApp(int appId) {
        NvApp app = state.app(appId);
        if (app == null || managerBinder == null) return;
        state.confirm(getString(R.string.applist_menu_quit), getString(R.string.applist_quit_confirmation),
                getString(R.string.yes), getString(R.string.no), true, () -> {
                    if (managerBinder != null) ServerHelper.doStart(this, app, computer, managerBinder);
                });
    }

    @Override
    public void onToggleHiddenApp(int appId) {
        NvApp app = state.app(appId);
        if (app == null) return;
        boolean hidden = !hiddenAppIds.remove(appId);
        if (hidden) hiddenAppIds.add(appId);
        state.setHidden(appId, hidden);
        HashSet<String> ids = new HashSet<>();
        for (Integer id : hiddenAppIds) ids.add(id.toString());
        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE).edit()
                .putStringSet(uuidString, ids).apply();
    }

    @Override
    public void onShowAppDetails(int appId) {
        NvApp app = state.app(appId);
        if (app == null) return;
        state.showMessage(getString(R.string.title_details), app.toString(), getString(android.R.string.ok));
    }

    @Override
    public void onCreateAppShortcut(int appId) {
        NvApp app = state.app(appId);
        if (app == null || assetLoader == null) return;
        assetLoader.loadArt(app, art -> {
            if (art == null || !shortcutHelper.createPinnedGameShortcut(computer, app, art)) {
                state.toast(getString(R.string.unable_to_pin_shortcut));
            }
        });
    }
}
