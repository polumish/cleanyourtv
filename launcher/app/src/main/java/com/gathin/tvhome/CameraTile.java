package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The small live camera picture in Home's top right corner (config.json camera_rtsp_url). Muted; it plays only
 * while Home is on screen ({@link #start()} from onResume, {@link #stop()} from onPause) and is a focus target
 * that opens the full-screen view with sound on OK. Nothing is built unless {@link #configure} is called.
 */
public class CameraTile extends FrameLayout {
    private final SurfaceView surface;
    private final TextView status;
    private RtspPlayer player;

    public CameraTile(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClipChildren(true);
        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = new TextView(context);
        status.setTextColor(0xCCFFFFFF);
        status.setTextSize(14f);
        status.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        status.setText(R.string.camera_connecting);
        addView(status, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.TRANSPARENT);
        focused.setStroke(Math.round(3 * density), 0xF2FFFFFF);
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(Color.TRANSPARENT);
        resting.setStroke(Math.round(1 * density), 0x33FFFFFF);
        StateListDrawable frame = new StateListDrawable();
        frame.addState(new int[]{android.R.attr.state_focused}, focused);
        frame.addState(new int[]{}, resting);
        setForeground(frame);
    }

    /** Sets the stream and the spoken name; the picture is silent, whatever the stream carries. */
    void configure(CameraSettings settings) {
        setContentDescription(settings.label.isEmpty() ? getResources().getString(R.string.camera_default_label)
                : settings.label);
        player = new RtspPlayer(getContext(), surface, false, state -> {
            status.setVisibility(state == RtspPlayer.State.PLAYING ? GONE : VISIBLE);
            status.setText(state == RtspPlayer.State.RETRYING ? R.string.camera_no_signal : R.string.camera_connecting);
        });
        player.setUrl(settings.url);
    }

    void start() {
        if (player != null) player.start();
    }

    void stop() {
        if (player != null) player.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
