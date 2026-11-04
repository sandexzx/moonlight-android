package com.limelight.ui;

import android.view.MotionEvent;
import android.view.View;

/**
 * Pinch zoom for the stream. It only transforms the view, so the stream itself, its latency and
 * the mapping of touches onto the picture are unaffected.
 *
 * Two fingers also scroll the PC, so a gesture counts as a pinch only once the fingers move apart
 * or together. Fingers moving in parallel are left alone.
 */
public class StreamZoom {
    private static final float MAX_SCALE = 4f;

    // Ending a pinch this close to the normal size means the normal size
    private static final float SNAP_SCALE = 1.05f;

    private static final float SPAN_SLOP_DP = 24f;
    private static final float SCROLL_SLOP_DP = 32f;

    private final View stream;
    private final View window;
    private final float spanSlop;
    private final float scrollSlop;

    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private int keyboardHeight;
    private float keyboardOffset;

    private boolean watching;
    private boolean pinching;

    private float startSpan;
    private float startFocusX;
    private float startFocusY;

    // Negative until the current pair of fingers has been measured
    private float lastSpan = -1f;
    private float lastFocusX;
    private float lastFocusY;

    public StreamZoom(View stream, View window) {
        this.stream = stream;
        this.window = window;

        float density = stream.getResources().getDisplayMetrics().density;
        spanSlop = SPAN_SLOP_DP * density;
        scrollSlop = SCROLL_SLOP_DP * density;

        stream.setPivotX(0f);
        stream.setPivotY(0f);

        // Rotation and the keyboard both move the picture and change the space around it
        stream.addOnLayoutChangeListener((view, left, top, right, bottom,
                                          oldLeft, oldTop, oldRight, oldBottom) -> apply());
    }

    /**
     * Takes every touch event before the views get it.
     *
     * @return true when the event belongs to a pinch and must not reach the stream
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                watching = false;
                pinching = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                watching = !pinching && event.getPointerCount() == 2;
                lastSpan = -1f;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                watching = false;
                lastSpan = -1f;
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    onTwoFingerMove(event);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean endedPinch = pinching;
                watching = false;
                pinching = false;
                if (endedPinch) {
                    settle();
                }
                return endedPinch;
        }

        return pinching;
    }

    private void onTwoFingerMove(MotionEvent event) {
        float x0 = event.getX(0);
        float y0 = event.getY(0);
        float x1 = event.getX(1);
        float y1 = event.getY(1);

        float span = (float)Math.hypot(x1 - x0, y1 - y0);
        float focusX = (x0 + x1) / 2f;
        float focusY = (y0 + y1) / 2f;

        if (lastSpan < 0f) {
            startSpan = span;
            startFocusX = focusX;
            startFocusY = focusY;
        }
        else if (watching) {
            if (Math.abs(span - startSpan) > spanSlop) {
                watching = false;
                pinching = true;
            }
            else if (Math.hypot(focusX - startFocusX, focusY - startFocusY) > scrollSlop) {
                watching = false;
            }
        }
        else if (pinching && lastSpan > 0f) {
            zoom(span / lastSpan, focusX, focusY);
        }

        lastSpan = span;
        lastFocusX = focusX;
        lastFocusY = focusY;
    }

    // The point of the picture under the fingers stays under them, so moving both pans too
    private void zoom(float ratio, float focusX, float focusY) {
        float newScale = Math.max(1f, Math.min(scale * ratio, MAX_SCALE));
        float applied = newScale / scale;

        float visualLeft = stream.getLeft() + offsetX;
        float visualTop = stream.getTop() + offsetY;
        offsetX = focusX - (lastFocusX - visualLeft) * applied - stream.getLeft();
        offsetY = focusY - (lastFocusY - visualTop) * applied - stream.getTop();

        scale = newScale;
        apply();
    }

    private void settle() {
        if (scale < SNAP_SCALE) {
            scale = 1f;
            apply();
        }
    }

    private void apply() {
        offsetX = clampOffset(stream.getLeft(), stream.getWidth(), window.getWidth(), offsetX);
        offsetY = clampOffset(stream.getTop(), stream.getHeight(), window.getHeight() - keyboardHeight, offsetY);

        stream.setScaleX(scale);
        stream.setScaleY(scale);
        stream.setTranslationX(offsetX);
        stream.setTranslationY(offsetY + keyboardOffset);
    }

    // A picture smaller than the visible space stays centered in it. A larger one covers it.
    private float clampOffset(int start, int size, int viewport, float offset) {
        if (scale == 1f) {
            return 0f;
        }

        float scaledSize = size * scale;
        float position = start + offset;
        if (scaledSize <= viewport) {
            position = (viewport - scaledSize) / 2f;
        }
        else {
            position = Math.max(viewport - scaledSize, Math.min(position, 0f));
        }
        return position - start;
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
    }

    /** Extra vertical shift while the picture travels along with the keyboard. */
    public void setKeyboardOffset(float offset) {
        keyboardOffset = offset;
        stream.setTranslationY(offsetY + keyboardOffset);
    }
}
