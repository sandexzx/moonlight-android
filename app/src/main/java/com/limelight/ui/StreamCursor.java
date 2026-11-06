package com.limelight.ui;

import android.view.View;

import com.limelight.nvstream.NvConnection;

/**
 * The PC's cursor in remote desktop mouse mode. The host never reports where its cursor is, so the
 * position here is only true because every move leaves from here as an absolute position.
 */
public class StreamCursor {
    private final NvConnection conn;
    private final View stream;
    private final StreamZoom zoom;
    private final short width;
    private final short height;

    // Shares of the picture. The cursor jumps here from wherever it really was on the first move.
    private double x = 0.5;
    private double y = 0.5;

    public StreamCursor(NvConnection conn, View stream, StreamZoom zoom, int width, int height) {
        this.conn = conn;
        this.stream = stream;
        this.zoom = zoom;
        this.width = (short)width;
        this.height = (short)height;
    }

    /** Moves the cursor by a distance on the phone's screen, so it keeps pace with the finger at any zoom. */
    public void moveOnScreen(int deltaX, int deltaY) {
        if (zoom.takeCursorPull()) {
            x = zoom.pullIntoViewX(x);
            y = zoom.pullIntoViewY(y);
        }

        x = clamp(x + deltaX / (stream.getWidth() * (double)stream.getScaleX()));
        y = clamp(y + deltaY / (stream.getHeight() * (double)stream.getScaleY()));

        conn.sendMousePosition((short)Math.round(x * (width - 1)), (short)Math.round(y * (height - 1)),
                width, height);
        zoom.followCursor(x, y);
    }

    private static double clamp(double share) {
        return Math.max(0.0, Math.min(share, 1.0));
    }
}
