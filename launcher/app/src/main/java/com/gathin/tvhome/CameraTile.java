package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The small live camera picture in Home's top right corner (config.json camera_rtsp_url). Muted; it plays only
 * while Home is on screen ({@link #start()} from onResume, {@link #stop()} from onPause) and is a focus target
 * that opens the full-screen view with sound on OK. Nothing is built unless {@link #configure} is called.
 */
public class CameraTile extends FrameLayout {
    private final SurfaceView surface;
    /** Sharp stream shown over the tile's own while enlarged (camera_full_url); only built when it differs. */
    private final SurfaceView fullSurface;
    private RtspPlayer fullPlayer;
    private final TextView status;
    private final TextView name;
    private final float radius;
    private float outlineRadius;
    private Drawable frame;
    private RtspPlayer player;
    /** The last picture of the previous visit, shown dimmed until the live one arrives. */
    private final ImageView cached;
    private final TextView cachedAt;
    private File cacheFile;
    private boolean expanded;
    private int videoWidth;
    private int videoHeight;
    private int fullWidth;
    private int fullHeight;
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
        fullSurface = new SurfaceView(context);
        fullSurface.setZOrderMediaOverlay(true);
        fullSurface.setVisibility(INVISIBLE);
        addView(fullSurface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        cached = new ImageView(context);
        cached.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cached.setAlpha(0.55f);
        cached.setVisibility(GONE);
        addView(cached, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cachedAt = new TextView(context);
        cachedAt.setTextColor(0xE6FFFFFF);
        cachedAt.setTextSize(12f);
        cachedAt.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        cachedAt.setVisibility(GONE);
        LayoutParams atParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        atParams.setMargins(Math.round(12 * density), 0, 0, Math.round(8 * density));
        addView(cachedAt, atParams);

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
        // The tile's own picture and sound take over at once; the sharp stream joins in when its first picture is ready.
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
        if (fullPlayer != null) {
            fullPlayer.stop();
            fullSurface.setVisibility(INVISIBLE);
        }
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
        fullSurface.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    /** Keeps each picture's own shape while enlarged; bars where it does not fill the screen. */
    private void fit() {
        if (!expanded || getWidth() <= 0 || getHeight() <= 0) return;
        fitSurface(surface, videoWidth, videoHeight);
        fitSurface(fullSurface, fullWidth, fullHeight);
    }

    private void fitSurface(SurfaceView view, int width, int height) {
        if (width <= 0 || height <= 0) return;
        float scale = Math.min((float) getWidth() / width, (float) getHeight() / height);
        view.setLayoutParams(new LayoutParams(Math.round(width * scale), Math.round(height * scale), Gravity.CENTER));
    }

    /** Sets the stream and the spoken name; the picture is silent, whatever the stream carries. */
    void configure(CameraSettings settings) {
        setContentDescription(settings.label.isEmpty() ? getResources().getString(R.string.camera_default_label)
                : settings.label);
        name.setText(settings.label);
        player = new RtspPlayer(getContext(), surface, false, new RtspPlayer.Listener() {
            @Override
            public void onState(RtspPlayer.State state) {
                if (state == RtspPlayer.State.PLAYING) {
                    cached.setVisibility(GONE);
                    cachedAt.setVisibility(GONE);
                }
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
        if (!settings.fullUrl.isEmpty() && !settings.fullUrl.equals(settings.url)) {
            fullPlayer = new RtspPlayer(getContext(), fullSurface, false, new RtspPlayer.Listener() {
                @Override
                public void onState(RtspPlayer.State state) {
                    // Sharp picture ready: it covers the tile's own stream, which hands the sound over.
                    if (state == RtspPlayer.State.PLAYING && expanded) {
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
            fullPlayer.setUrl(settings.fullUrl);
        }
    }

    /** Where this tile keeps its last picture (named after its stream address); shown at once if it is there. */
    void setCacheFile(File file) {
        cacheFile = file;
        showCache();
    }

    private void showCache() {
        File file = cacheFile;
        if (file == null || !file.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null) return;
        cached.setImageBitmap(bitmap);
        cached.setVisibility(VISIBLE);
        cachedAt.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(file.lastModified())));
        cachedAt.setVisibility(VISIBLE);
    }

    /** Keeps what the tile shows right now for the next start. Needs a live picture; asynchronous. */
    void saveSnapshot() {
        if (cacheFile == null || expanded || cached.getVisibility() == VISIBLE || surface.getWidth() <= 0
                || getVisibility() != VISIBLE || !surface.getHolder().getSurface().isValid()) return;
        final int w = Math.min(640, surface.getWidth());
        final int h = Math.max(1, surface.getHeight() * w / surface.getWidth());
        final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final File target = cacheFile;
        try {
            PixelCopy.request(surface, bitmap, result -> {
                if (result != PixelCopy.SUCCESS) return;
                File tmp = new File(target.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                } catch (IOException e) {
                    return;
                }
                tmp.renameTo(target);
            }, new Handler(Looper.getMainLooper()));
        } catch (IllegalArgumentException ignored) {
            // the surface went away between the check and the copy
        }
    }

    void start() {
        showCache();   // every start, not only the first: the stream needs a moment to reconnect
        if (player != null) player.start();
    }

    void stop() {
        collapse();
        if (fullPlayer != null) fullPlayer.stop();
        if (player != null) player.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
