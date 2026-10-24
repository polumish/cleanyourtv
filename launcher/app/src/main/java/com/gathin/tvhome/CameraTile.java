package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
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
    private final TextView name;
    private final float radius;
    private float outlineRadius;
    private Drawable frame;
    private RtspPlayer player;
    private boolean expanded;
    private int videoWidth;
    private int videoHeight;
    private ViewGroup.LayoutParams collapsedParams;

    public CameraTile(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClipChildren(true);
        radius = 18 * density;
        outlineRadius = radius;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), outlineRadius);
            }
        });
        setClipToOutline(true);
        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = new TextView(context);
        status.setTextColor(0xCCFFFFFF);
        status.setTextSize(14f);
        status.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        status.setText(R.string.camera_connecting);
        addView(status, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        name = new TextView(context);
        name.setTextColor(0xE6FFFFFF);
        name.setTextSize(20f);
        name.setShadowLayer(6f, 0f, 1f, Color.BLACK);
        name.setVisibility(GONE);
        LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        nameParams.setMargins(Math.round(48 * density), Math.round(32 * density), 0, 0);
        addView(name, nameParams);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.TRANSPARENT);
        focused.setCornerRadius(radius);
        focused.setStroke(Math.round(3 * density), 0xF2FFFFFF);
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(Color.TRANSPARENT);
        resting.setCornerRadius(radius);
        resting.setStroke(Math.round(1.5f * density), 0xA6FFFFFF);
        StateListDrawable frame = new StateListDrawable();
        frame.addState(new int[]{android.R.attr.state_focused}, focused);
        frame.addState(new int[]{}, resting);
        this.frame = frame;
        setForeground(frame);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
    }

    boolean isExpanded() {
        return expanded;
    }

    /** The same live picture, enlarged to the whole screen with sound; nothing is reconnected. */
    void expand() {
        if (expanded || player == null) return;
        expanded = true;
        collapsedParams = getLayoutParams();
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setBackgroundColor(Color.BLACK);
        outlineRadius = 0f;
        invalidateOutline();
        setForeground(null);
        name.setVisibility(name.getText().length() > 0 ? VISIBLE : GONE);
        setKeepScreenOn(true);
        player.setMuted(false);
    }

    void collapse() {
        if (!expanded) return;
        expanded = false;
        player.setMuted(true);
        setKeepScreenOn(false);
        name.setVisibility(GONE);
        setBackground(null);
        outlineRadius = radius;
        invalidateOutline();
        setForeground(frame);
        setLayoutParams(collapsedParams);
        surface.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    /** Keeps the picture's own shape while enlarged; bars where it does not fill the screen. */
    private void fit() {
        if (!expanded || videoWidth <= 0 || videoHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.min((float) getWidth() / videoWidth, (float) getHeight() / videoHeight);
        LayoutParams p = new LayoutParams(Math.round(videoWidth * scale), Math.round(videoHeight * scale), Gravity.CENTER);
        surface.setLayoutParams(p);
    }

    /** Sets the stream and the spoken name; the picture is silent, whatever the stream carries. */
    void configure(CameraSettings settings) {
        setContentDescription(settings.label.isEmpty() ? getResources().getString(R.string.camera_default_label)
                : settings.label);
        name.setText(settings.label);
        player = new RtspPlayer(getContext(), surface, false, new RtspPlayer.Listener() {
            @Override
            public void onState(RtspPlayer.State state) {
                status.setVisibility(state == RtspPlayer.State.PLAYING ? GONE : VISIBLE);
                status.setText(state == RtspPlayer.State.RETRYING ? R.string.camera_no_signal
                        : R.string.camera_connecting);
            }

            @Override
            public void onVideoSize(int width, int height) {
                videoWidth = width;
                videoHeight = height;
                fit();
            }
        });
        player.setUrl(settings.url);
    }

    void start() {
        if (player != null) player.start();
    }

    void stop() {
        collapse();
        if (player != null) player.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
