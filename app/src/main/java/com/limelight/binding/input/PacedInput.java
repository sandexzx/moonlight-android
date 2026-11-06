package com.limelight.binding.input;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.KeyEvent;

import java.util.ArrayDeque;

/** Serializes composer input without flooding the native input queue. */
public final class PacedInput {
    public static final long TEXT_INTERVAL_MS = 80;

    public interface Callbacks {
        void sendText(String codePoint);
        void sendTextKey(int androidKeyCode);
        void onProgress(Float fraction);
    }

    private static final class Step {
        final Runnable action;
        final boolean text;

        Step(Runnable action, boolean text) {
            this.action = action;
            this.text = text;
        }
    }

    private final HandlerThread thread = new HandlerThread("PacedInput");
    private final Handler handler;
    private final Callbacks callbacks;
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private boolean stopped;
    private boolean pumpScheduled;
    private long queuedText;
    private long doneText;
    private final Runnable pump = this::pump;

    public PacedInput(Callbacks callbacks) {
        this.callbacks = callbacks;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public synchronized void post(Runnable keyStep) {
        if (stopped) return;
        queue.addLast(new Step(keyStep, false));
        schedule(0);
    }

    public synchronized void sendText(String text) {
        if (stopped) return;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            final Runnable action;
            if (codePoint == '\r' || codePoint == '\n') {
                if (codePoint == '\r' && offset < text.length() && text.charAt(offset) == '\n') {
                    offset++;
                }
                action = () -> callbacks.sendTextKey(KeyEvent.KEYCODE_ENTER);
            } else if (codePoint == '\t') {
                action = () -> callbacks.sendTextKey(KeyEvent.KEYCODE_TAB);
            } else {
                String character = new String(Character.toChars(codePoint));
                action = () -> callbacks.sendText(character);
            }
            queue.addLast(new Step(action, true));
            queuedText++;
        }
        if (queuedText != 0) callbacks.onProgress((float) doneText / queuedText);
        schedule(0);
    }

    public synchronized void cancelText() {
        if (stopped) return;
        queue.removeIf(step -> step.text);
        clearProgress();
        if (queue.isEmpty()) {
            handler.removeCallbacks(pump);
            pumpScheduled = false;
        }
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        queue.clear();
        handler.removeCallbacksAndMessages(null);
        pumpScheduled = false;
        clearProgress();
        thread.quit();
    }

    private void clearProgress() {
        queuedText = doneText = 0;
        callbacks.onProgress(null);
    }

    private void schedule(long delay) {
        if (!stopped && !pumpScheduled && !queue.isEmpty()) {
            pumpScheduled = true;
            handler.postDelayed(pump, delay);
        }
    }

    private synchronized void pump() {
        pumpScheduled = false;
        if (stopped || queue.isEmpty()) return;
        Step step = queue.removeFirst();
        step.action.run();
        if (step.text) {
            doneText++;
            if (doneText == queuedText) clearProgress();
            else callbacks.onProgress((float) doneText / queuedText);
        }
        schedule(step.text ? TEXT_INTERVAL_MS : 0);
    }
}
