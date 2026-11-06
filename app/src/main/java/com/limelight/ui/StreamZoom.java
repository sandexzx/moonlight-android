package com.limelight.ui;

import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

/**
 * Pinch zoom for the stream. It only transforms the view, so the stream itself and its latency
 * are unaffected.
 *
 * Two fingers also scroll the PC, so a gesture counts as a pinch only once the fingers move apart
 * or together. Fingers moving in parallel are left alone.
 *
 * A pinch scales around the PC's cursor, wherever the fingers are. When the host owns the cursor
 * and its position is unknown here, it scales around the fingers instead.
 *
 * While zoomed, the camera follows the cursor: it stays still while the cursor is in the middle of
 * the view and glides once the cursor comes near an edge.
 */
public class StreamZoom {
    private static final float MAX_SCALE = 4f;

    // Ending a pinch this close to the normal size means the normal size
    private static final float SNAP_SCALE = 1.05f;

    private static final float SPAN_SLOP_DP = 24f;
    private static final float SCROLL_SLOP_DP = 32f;

    // The share of the view on each side where the cursor starts pushing the camera
    private static final float FOLLOW_MARGIN = 0.18f;

    // How fast the camera catches up, per second. Higher is snappier, lower is floatier.
    private static final float FOLLOW_RATE = 16f;

    // A long frame must not read as a jump
    private static final long MAX_FRAME_NANOS = 50_000_000L;

    private static final float REST_DISTANCE = 0.5f;
    private static final float REST_SPEED = 8f;

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

    private final FollowSpring followX = new FollowSpring();
    private final FollowSpring followY = new FollowSpring();
    private final Choreographer.FrameCallback followFrame = this::onFollowFrame;
    private boolean following;
    private long lastFrameNanos;

    // Shares of the picture
    private boolean cursorKnown;
    private double cursorX;
    private double cursorY;

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
                                          oldLeft, oldTop, oldRight, oldBottom) -> {
            stopFollowing();
            apply();
        });
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
                stopFollowing();
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

    private void zoom(float ratio, float focusX, float focusY) {
        float newScale = Math.max(1f, Math.min(scale * ratio, MAX_SCALE));

        if (cursorKnown) {
            // The point of the picture under the cursor stays where it is on the screen
            offsetX += (float)cursorX * stream.getWidth() * (scale - newScale);
            offsetY += (float)cursorY * stream.getHeight() * (scale - newScale);
        }
        else {
            // The point of the picture under the fingers stays under them, so moving both pans too
            float applied = newScale / scale;
            float visualLeft = stream.getLeft() + offsetX;
            float visualTop = stream.getTop() + offsetY;
            offsetX = focusX - (lastFocusX - visualLeft) * applied - stream.getLeft();
            offsetY = focusY - (lastFocusY - visualTop) * applied - stream.getTop();
        }

        scale = newScale;
        apply();
    }

    private void settle() {
        if (scale < SNAP_SCALE) {
            scale = 1f;
            apply();
        }
    }

    /** Takes the cursor's new position, as shares of the picture. */
    public void followCursor(double cursorX, double cursorY) {
        cursorKnown = true;
        this.cursorX = cursorX;
        this.cursorY = cursorY;

        if (scale == 1f || pinching) {
            return;
        }

        if (!following) {
            followX.rest(offsetX);
            followY.rest(offsetY);
        }

        // The margins are measured from where the camera is heading, not where it is on this
        // frame. Otherwise a camera that is still catching up would keep pushing itself further.
        followX.target = followTarget(stream.getLeft(), stream.getWidth(), window.getWidth(),
                cursorX, followX.target);
        followY.target = followTarget(stream.getTop(), stream.getHeight(), visibleHeight(),
                cursorY, followY.target);

        if (!following && (followX.target != offsetX || followY.target != offsetY)) {
            following = true;
            lastFrameNanos = 0;
            Choreographer.getInstance().postFrameCallback(followFrame);
        }
    }

    private float followTarget(int start, int size, int viewport, double cursor, float target) {
        float margin = viewport * FOLLOW_MARGIN;
        float onScreen = start + target + (float)cursor * size * scale;

        if (onScreen < margin) {
            target += margin - onScreen;
        }
        else if (onScreen > viewport - margin) {
            target -= onScreen - (viewport - margin);
        }
        return clampOffset(start, size, viewport, target);
    }

    private void onFollowFrame(long frameNanos) {
        float seconds = lastFrameNanos == 0 ? 0f :
                Math.min(frameNanos - lastFrameNanos, MAX_FRAME_NANOS) / 1_000_000_000f;
        lastFrameNanos = frameNanos;

        offsetX = followX.step(offsetX, seconds);
        offsetY = followY.step(offsetY, seconds);

        if (followX.isAtRest(offsetX) && followY.isAtRest(offsetY)) {
            offsetX = followX.target;
            offsetY = followY.target;
            following = false;
        }
        else {
            Choreographer.getInstance().postFrameCallback(followFrame);
        }
        apply();
    }

    private void stopFollowing() {
        following = false;
        Choreographer.getInstance().removeFrameCallback(followFrame);
    }

    private int visibleHeight() {
        return window.getHeight() - keyboardHeight;
    }

    private void apply() {
        offsetX = clampOffset(stream.getLeft(), stream.getWidth(), window.getWidth(), offsetX);
        offsetY = clampOffset(stream.getTop(), stream.getHeight(), visibleHeight(), offsetY);

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

    /**
     * A critically damped spring: it eases in, and settles as fast as it can without overshooting.
     * This is the exact solution rather than a numeric step, so 60 Hz and 120 Hz feel the same.
     */
    private static final class FollowSpring {
        float target;
        private float velocity;

        void rest(float offset) {
            target = offset;
            velocity = 0f;
        }

        float step(float offset, float seconds) {
            float distance = offset - target;
            float drift = (velocity + FOLLOW_RATE * distance) * seconds;
            float decay = (float)Math.exp(-FOLLOW_RATE * seconds);

            velocity = (velocity - FOLLOW_RATE * drift) * decay;
            return target + (distance + drift) * decay;
        }

        boolean isAtRest(float offset) {
            return Math.abs(offset - target) < REST_DISTANCE && Math.abs(velocity) < REST_SPEED;
        }
    }
}
