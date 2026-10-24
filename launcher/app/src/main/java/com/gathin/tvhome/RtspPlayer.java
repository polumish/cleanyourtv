package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

/**
 * Plays one rtsp:// stream into a SurfaceView with Media3 ExoPlayer. The platform MediaPlayer cannot be used: it
 * asks for UDP, which go2rtc's RTSP server refuses (461), and it does not know go2rtc's AAC (MPEG4-GENERIC).
 * ExoPlayer is told to use RTP over TCP. The picture is decoded by the TV's hardware decoder. It runs only between {@link #start()} and
 * {@link #stop()}, opens a connection only while the surface exists, and reconnects after a failure with a growing
 * pause ({@link CameraSettings#retryDelayMs}). All calls come from the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
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
    private ExoPlayer player;
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
        try {
            ExoPlayer p = new ExoPlayer.Builder(context).build();
            player = p;
            if (!audio) {
                p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
                p.setVolume(0f);
            }
            p.setVideoSurfaceHolder(holder);
            p.addListener(new Player.Listener() {
                @Override
                public void onRenderedFirstFrame() {
                    if (gen != generation) return;
                    handler.removeCallbacks(timeout);
                    failures = 0;
                    setState(State.PLAYING);
                }

                @Override
                public void onVideoSizeChanged(VideoSize size) {
                    if (gen == generation && size.width > 0 && size.height > 0) listener.onVideoSize(size.width, size.height);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (gen != generation) return;
                    handler.removeCallbacks(timeout);
                    if (playbackState == Player.STATE_BUFFERING && state == State.PLAYING) {
                        handler.postDelayed(timeout, STALL_TIMEOUT_MS);
                    } else if (playbackState == Player.STATE_ENDED) {
                        fail("ended");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (gen == generation) fail("error " + error.getErrorCodeName());
                }
            });
            p.setMediaSource(new RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(CONNECT_TIMEOUT_MS)
                    .createMediaSource(MediaItem.fromUri(url)));
            p.setPlayWhenReady(true);
            p.prepare();
            setState(State.CONNECTING);
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
        } catch (RuntimeException e) {
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

    /** Lets go of the current player (ExoPlayer releases its network session on its own thread). */
    private void release() {
        generation++;
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(retry);
        final ExoPlayer p = player;
        player = null;
        if (p == null) return;
        try {
            p.release();
        } catch (RuntimeException ignored) {
            // nothing left to do
        }
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
