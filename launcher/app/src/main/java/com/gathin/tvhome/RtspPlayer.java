package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Plays one rtsp:// stream into a SurfaceView with the platform's own MediaPlayer, so no player library is added
 * to the launcher. The picture is decoded by the TV's hardware decoder. It runs only between {@link #start()} and
 * {@link #stop()}, opens a connection only while the surface exists, and reconnects after a failure with a growing
 * pause ({@link CameraSettings#retryDelayMs}). All calls come from the main thread.
 */
final class RtspPlayer implements SurfaceHolder.Callback {
    enum State { IDLE, CONNECTING, PLAYING, RETRYING }

    interface Listener {
        void onState(State state);

        /** The stream's picture size, once known, so a full-screen view can keep its shape. */
        default void onVideoSize(int width, int height) { }
    }

    private static final String TAG = "RtspPlayer";
    /** A camera that answers nothing for this long is treated as unreachable. */
    private static final long CONNECT_TIMEOUT_MS = 20_000L;
    /** Buffering for this long after playback began means the stream stalled. */
    private static final long STALL_TIMEOUT_MS = 12_000L;

    private final Context context;
    private final SurfaceHolder holder;
    private final boolean audio;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> fail("timeout");
    private final Runnable retry = this::open;

    private String url = "";
    private boolean wanted;
    private boolean surfaceReady;
    private MediaPlayer player;
    private int generation;
    private int failures;
    private State state = State.IDLE;

    RtspPlayer(Context context, SurfaceView view, boolean audio, Listener listener) {
        this.context = context.getApplicationContext();
        this.holder = view.getHolder();
        this.audio = audio;
        this.listener = listener;
        holder.addCallback(this);
        surfaceReady = holder.getSurface() != null && holder.getSurface().isValid();
    }

    void setUrl(String url) {
        this.url = url == null ? "" : url;
    }

    void start() {
        wanted = true;
        failures = 0;
        open();
    }

    void stop() {
        wanted = false;
        release();
        setState(State.IDLE);
    }

    private void open() {
        if (!wanted || !surfaceReady || url.isEmpty() || player != null) return;
        handler.removeCallbacks(retry);
        final int gen = ++generation;
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            p.setDisplay(holder);
            if (!audio) p.setVolume(0f, 0f);
            p.setOnPreparedListener(mp -> {
                if (gen != generation) return;
                mp.start();
                // The first picture must follow soon; MEDIA_INFO_VIDEO_RENDERING_START cancels this.
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            });
            p.setOnVideoSizeChangedListener((mp, w, h) -> {
                if (gen == generation && w > 0 && h > 0) listener.onVideoSize(w, h);
            });
            p.setOnInfoListener((mp, what, extra) -> {
                if (gen != generation) return false;
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    handler.removeCallbacks(timeout);
                    failures = 0;
                    setState(State.PLAYING);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    handler.removeCallbacks(timeout);
                    handler.postDelayed(timeout, STALL_TIMEOUT_MS);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    handler.removeCallbacks(timeout);
                }
                return false;
            });
            p.setOnErrorListener((mp, what, extra) -> {
                if (gen == generation) fail("error " + what + "/" + extra);
                return true;
            });
            p.setOnCompletionListener(mp -> {
                if (gen == generation) fail("ended");
            });
            p.setDataSource(context, Uri.parse(url));
            p.prepareAsync();
            setState(State.CONNECTING);
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
        } catch (IOException | RuntimeException e) {
            fail(e.getClass().getSimpleName());
        }
    }

    private void fail(String why) {
        Log.w(TAG, "stream failed (" + why + "): " + CameraSettings.redact(url));
        release();
        if (!wanted) return;
        setState(State.RETRYING);
        handler.postDelayed(retry, CameraSettings.retryDelayMs(failures++));
    }

    /** Lets go of the current player; its network teardown can take a while, so that runs off the main thread. */
    private void release() {
        generation++;
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(retry);
        final MediaPlayer p = player;
        player = null;
        if (p == null) return;
        try {
            p.setOnPreparedListener(null);
            p.setOnInfoListener(null);
            p.setOnErrorListener(null);
            p.setOnCompletionListener(null);
            p.setOnVideoSizeChangedListener(null);
            p.setDisplay(null);
        } catch (RuntimeException ignored) {
            // already unusable: release it anyway
        }
        Thread t = new Thread(() -> {
            try {
                p.release();
            } catch (RuntimeException ignored) {
                // nothing left to do
            }
        }, "rtsp-release");
        t.setDaemon(true);
        t.start();
    }

    private void setState(State next) {
        if (state == next) return;
        state = next;
        listener.onState(next);
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        open();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        release();
    }
}
