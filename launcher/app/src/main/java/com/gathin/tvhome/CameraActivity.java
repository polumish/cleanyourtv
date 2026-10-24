package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The camera tile opened full screen, with sound (config.json camera_full_url, or the tile's own stream). Plays only
 * while it is on screen; Back returns to Home.
 */
public class CameraActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_LABEL = "label";

    private SurfaceView surface;
    private FrameLayout root;
    private TextView status;
    private RtspPlayer player;
    private int videoWidth;
    private int videoHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        float density = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        status = new TextView(this);
        status.setTextColor(0xE6FFFFFF);
        status.setTextSize(22f);
        status.setShadowLayer(6f, 0f, 1f, Color.BLACK);
        status.setText(R.string.camera_connecting);
        root.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        if (label != null && !label.isEmpty()) {
            TextView name = new TextView(this);
            name.setText(label);
            name.setTextColor(0xE6FFFFFF);
            name.setTextSize(20f);
            name.setShadowLayer(6f, 0f, 1f, Color.BLACK);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            p.setMargins(Math.round(48 * density), Math.round(32 * density), 0, 0);
            root.addView(name, p);
        }
        setContentView(root);
        player = new RtspPlayer(this, surface, true, new RtspPlayer.Listener() {
            @Override
            public void onState(RtspPlayer.State state) {
                status.setVisibility(state == RtspPlayer.State.PLAYING ? TextView.GONE : TextView.VISIBLE);
                status.setText(state == RtspPlayer.State.RETRYING ? R.string.camera_no_signal : R.string.camera_connecting);
            }

            @Override
            public void onVideoSize(int width, int height) {
                videoWidth = width;
                videoHeight = height;
                fit();
            }
        });
        player.setUrl(getIntent().getStringExtra(EXTRA_URL));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
    }

    /** Keeps the picture's own shape inside the screen, bars where it does not fill it. */
    private void fit() {
        int rw = root.getWidth();
        int rh = root.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || rw <= 0 || rh <= 0) return;
        double scale = Math.min((double) rw / videoWidth, (double) rh / videoHeight);
        int w = (int) Math.round(videoWidth * scale);
        int h = (int) Math.round(videoHeight * scale);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            surface.setLayoutParams(lp);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.start();
    }

    @Override
    protected void onStop() {
        player.stop();
        super.onStop();
    }
}
