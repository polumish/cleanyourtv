package com.gathin.tvcameras;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * One camera: a rounded 16:9 tile with a thin white frame (thick when focused) showing the small stream, muted.
 * {@link #expand()} enlarges it to the whole screen with sound: the small picture and its sound take over at once, the
 * sharp stream starts on a second surface and replaces it when its first picture is ready.
 */
final class CameraTile extends FrameLayout {
    private final SurfaceView surface;
    private final SurfaceView fullSurface;
    private final TextView status;
    private final TextView label;
    private final float radius;
    private final float density;
    private final Drawable frame;
    private final RtspPlayer player;
    private final RtspPlayer fullPlayer;
    private float outlineRadius;
    private boolean expanded;
    private boolean fullShown;
    private int videoWidth;
    private int videoHeight;
    private int fullWidth;
    private int fullHeight;
    private ViewGroup.LayoutParams collapsedParams;

    CameraTile(Context context, CameraStore.Camera camera) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        radius = 18 * density;
        outlineRadius = radius;
        setFocusable(true);
        setClickable(true);
        setClipChildren(true);
        setBackgroundColor(Color.BLACK);
        setContentDescription(camera.name);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), outlineRadius);
            }
        });
        setClipToOutline(true);

        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        fullSurface = new SurfaceView(context);
        fullSurface.setZOrderMediaOverlay(true);
        fullSurface.setVisibility(INVISIBLE);
        addView(fullSurface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        status = text(context, 14f);
        status.setText(R.string.connecting);
        addView(status, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        label = text(context, 16f);
        label.setText(camera.name);
        LayoutParams labelParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        labelParams.setMargins(Math.round(16 * density), Math.round(12 * density), 0, 0);
        addView(label, labelParams);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.TRANSPARENT);
        focused.setCornerRadius(radius);
        focused.setStroke(Math.round(3 * density), 0xF2FFFFFF);
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(Color.TRANSPARENT);
        resting.setCornerRadius(radius);
        resting.setStroke(Math.round(1.5f * density), 0xA6FFFFFF);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, resting);
        frame = states;
        setForeground(frame);

        player = new RtspPlayer(context, surface, false, new RtspPlayer.Listener() {
            @Override
            public void onState(RtspPlayer.State state) {
                if (fullShown) return;
                status.setVisibility(state == RtspPlayer.State.PLAYING ? GONE : VISIBLE);
                status.setText(state == RtspPlayer.State.RETRYING ? R.string.no_signal : R.string.connecting);
            }

            @Override
            public void onVideoSize(int width, int height) {
                videoWidth = width;
                videoHeight = height;
                fit();
            }
        });
        player.setUrl(camera.tileUrl);
        if (!camera.fullUrl.equals(camera.tileUrl)) {
            fullPlayer = new RtspPlayer(context, fullSurface, false, new RtspPlayer.Listener() {
                @Override
                public void onState(RtspPlayer.State state) {
                    // The sharp picture is ready: it covers the small one, which hands the sound over.
                    if (state == RtspPlayer.State.PLAYING && expanded) {
                        fullShown = true;
                        status.setVisibility(GONE);
                        fullPlayer.setMuted(false);
                        player.setMuted(true);
                    }
                }

                @Override
                public void onVideoSize(int width, int height) {
                    fullWidth = width;
                    fullHeight = height;
                    fit();
                }
            });
            fullPlayer.setUrl(camera.fullUrl);
        } else {
            fullPlayer = null;
        }
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
    }

    private TextView text(Context context, float size) {
        TextView t = new TextView(context);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(size);
        t.setShadowLayer(6f, 0f, 1f, Color.BLACK);
        return t;
    }

    boolean isExpanded() {
        return expanded;
    }

    void expand() {
        if (expanded) return;
        expanded = true;
        collapsedParams = getLayoutParams();
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        outlineRadius = 0f;
        invalidateOutline();
        setForeground(null);
        label.setTextSize(22f);
        setKeepScreenOn(true);
        player.setMuted(false);
        if (fullPlayer != null) {
            fullPlayer.setMuted(true);
            fullSurface.setVisibility(VISIBLE);
            fullPlayer.start();
        }
    }

    void collapse() {
        if (!expanded) return;
        expanded = false;
        fullShown = false;
        if (fullPlayer != null) {
            fullPlayer.stop();
            fullSurface.setVisibility(INVISIBLE);
        }
        player.setMuted(true);
        setKeepScreenOn(false);
        label.setTextSize(16f);
        outlineRadius = radius;
        invalidateOutline();
        setForeground(frame);
        setLayoutParams(collapsedParams);
    }

    /** Each picture keeps its own shape inside the tile; bars where it does not fill it. */
    private void fit() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        fitSurface(surface, videoWidth, videoHeight);
        fitSurface(fullSurface, fullWidth, fullHeight);
    }

    private void fitSurface(SurfaceView view, int width, int height) {
        if (width <= 0 || height <= 0) return;
        float scale = Math.min((float) getWidth() / width, (float) getHeight() / height);
        int w = Math.round(width * scale);
        int h = Math.round(height * scale);
        ViewGroup.LayoutParams current = view.getLayoutParams();
        if (current != null && current.width == w && current.height == h) return;
        view.setLayoutParams(new LayoutParams(w, h, Gravity.CENTER));
    }

    void start() {
        player.start();
    }

    void stop() {
        collapse();
        if (fullPlayer != null) fullPlayer.stop();
        player.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
