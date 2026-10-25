package com.gathin.tvcameras;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The four cameras as a 2x2 grid of live tiles. The arrows choose one, OK enlarges it to the screen with sound,
 * Back shrinks it (and leaves the app from the grid). Holding OK on a tile, or Menu, opens the settings.
 */
public class MainActivity extends Activity {
    private FrameLayout root;
    private final List<CameraTile> tiles = new ArrayList<>();
    private int lastWidth;
    private int lastHeight;
    /** Start the four streams one after another (true) or all at once (false). */
    private static final boolean SEQUENTIAL = true;
    private boolean started;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int startToken;
    /** A camera asked for by a link or a voice command (see {@link #cameraArg}); opened enlarged once the tiles are up. */
    private String pendingCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Loads Rive's native renderer; without it the tiles keep their plain frame.
            app.rive.runtime.kotlin.core.Rive.INSTANCE.init(getApplicationContext(), app.rive.runtime.kotlin.core.RendererType.Rive);
        } catch (Throwable ignored) {
            // no Rive on this device
        }
        pendingCamera = cameraArg(getIntent());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeTiles());
    }

    private void buildTiles() {
        for (CameraTile tile : tiles) root.removeView(tile);
        tiles.clear();
        List<CameraStore.Camera> cameras = CameraStore.load(this);
        for (int i = 0; i < CameraStore.COUNT; i++) {
            CameraTile tile = new CameraTile(this, cameras.get(i), new java.io.File(getCacheDir(), "snapshot-" + Integer.toHexString(cameras.get(i).tileUrl.hashCode()) + ".jpg"));
            tile.setOnClickListener(v -> expand((CameraTile) v));
            tile.setOnLongClickListener(v -> {
                openSettings();
                return true;
            });
            tiles.add(tile);
            root.addView(tile, new FrameLayout.LayoutParams(1, 1));
        }
        TextView hint = new TextView(this);
        hint.setText(R.string.hint);
        hint.setTextColor(0x88FFFFFF);
        hint.setTextSize(13f);
        hint.setTag("hint");
        root.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        lastWidth = 0;
        placeTiles();
        tiles.get(0).requestFocus();
    }

    /** 2x2, each tile 4:3, centred with an even gap. */
    private void placeTiles() {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0 || tiles.isEmpty() || anyExpanded() || (w == lastWidth && h == lastHeight)) return;
        lastWidth = w;
        lastHeight = h;
        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(24 * density);
        int marginX = Math.round(48 * density);
        int marginTop = Math.round(36 * density);
        int marginBottom = Math.round(48 * density);
        int cellW = (w - 2 * marginX - gap) / 2;
        int cellH = (h - marginTop - marginBottom - gap) / 2;
        // 4:3 like the camera tile on Home (and like the small streams), so the picture fills it without bars
        int tileH = Math.min(cellH, cellW * 3 / 4);
        int tileW = tileH * 4 / 3;
        int left = (w - (2 * tileW + gap)) / 2;
        int top = marginTop + (h - marginTop - marginBottom - (2 * tileH + gap)) / 2;
        for (int i = 0; i < tiles.size(); i++) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(tileW, tileH, Gravity.TOP | Gravity.START);
            p.leftMargin = left + (i % 2) * (tileW + gap);
            p.topMargin = top + (i / 2) * (tileH + gap);
            tiles.get(i).setLayoutParams(p);
        }
    }

    private boolean anyExpanded() {
        for (CameraTile t : tiles) if (t.isExpanded()) return true;
        return false;
    }

    private void expand(CameraTile tile) {
        if (tile.isExpanded()) {
            collapse(tile);
            return;
        }
        // Only the enlarged camera plays: the others stop and come back when it is shrunk.
        for (CameraTile t : tiles) {
            if (t != tile) {
                t.stop();
                t.setVisibility(View.INVISIBLE);
            }
        }
        tile.bringToFront();
        tile.expand();
    }

    private void collapse(CameraTile tile) {
        tile.collapse();
        for (CameraTile t : tiles) t.setVisibility(View.VISIBLE);
        if (started) startSequence(tile);
        tile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        for (CameraTile t : tiles) {
            if (t.isExpanded()) {
                collapse(t);
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU && !anyExpanded()) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Streams start one after another in grid order: the next one when the previous shows its first picture, or after
     * three seconds if it does not (a camera that is down must not hold the rest back). {@code except} is already playing.
     */
    private void startSequence(CameraTile except) {
        final int token = ++startToken;
        List<CameraTile> order = new ArrayList<>();
        for (CameraTile t : tiles) if (t != except) order.add(t);
        if (!SEQUENTIAL) {
            for (CameraTile t : order) t.start();
            return;
        }
        startStep(order, 0, token);
    }

    private void startStep(List<CameraTile> order, int index, int token) {
        if (token != startToken || !started || index >= order.size()) return;
        final boolean[] advanced = {false};
        Runnable next = () -> {
            if (advanced[0]) return;
            advanced[0] = true;
            startStep(order, index + 1, token);
        };
        order.get(index).start(next);
        handler.postDelayed(next, 3000);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        // Rebuilt every time: the settings screen may have changed a stream.
        buildTiles();
        if (!openPendingCamera()) startSequence(null);
    }

    /** Already open and asked again ("Калітка", then "Майстерня"): switch straight to the other camera. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingCamera = cameraArg(intent);
        if (!started || pendingCamera == null) return;
        buildTiles();
        if (!openPendingCamera()) startSequence(null);
    }

    /** The camera named in a tvcameras://open?camera=... link or in a "camera" extra (what the Assistant passes), or null. */
    private static String cameraArg(Intent intent) {
        if (intent == null) return null;
        String value = intent.getStringExtra("camera");
        if (value == null && intent.getData() != null) value = intent.getData().getQueryParameter("camera");
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 0-3 for a number 1-4, a camera's own name, or its Frigate name / usual spoken forms; -1 when nothing fits. */
    private int cameraIndex(String wanted) {
        String w = wanted.toLowerCase(java.util.Locale.ROOT);
        if (w.length() == 1 && w.charAt(0) >= '1' && w.charAt(0) <= '4') return w.charAt(0) - '1';
        List<CameraStore.Camera> cameras = CameraStore.load(this);
        for (int i = 0; i < cameras.size(); i++) {
            if (cameras.get(i).name.toLowerCase(java.util.Locale.ROOT).equals(w)) return i;
        }
        String[][] aliases = {
                {"backyard", "intercom", "doorbell", "домофон", "задний двор"},
                {"kalitka", "gate", "калітка", "калитка", "ворота"},
                {"maisternia", "workshop", "майстерня", "мастерская"},
                {"sad", "garden", "сад"}};
        for (int i = 0; i < aliases.length; i++) {
            for (String alias : aliases[i]) if (alias.equals(w)) return i;
        }
        return -1;
    }

    /** Starts only the asked camera and enlarges it at once, so it is on screen in about a second. */
    private boolean openPendingCamera() {
        String wanted = pendingCamera;
        pendingCamera = null;
        if (wanted == null) return false;
        final int index = cameraIndex(wanted);
        if (index < 0 || index >= tiles.size()) return false;
        final CameraTile tile = tiles.get(index);
        Runnable open = () -> {
            if (!started || tile.isExpanded()) return;
            tile.start();
            expand(tile);
        };
        if (root.getWidth() > 0) {
            root.post(open);
        } else {
            root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    root.removeOnLayoutChangeListener(this);
                    root.post(open);
                }
            });
        }
        return true;
    }

    @Override
    protected void onPause() {
        // Before the streams stop: keep each camera's last picture for an instant start next time.
        for (CameraTile t : tiles) t.saveSnapshot();
        super.onPause();
    }

    @Override
    protected void onStop() {
        started = false;
        startToken++;
        for (CameraTile t : tiles) t.stop();
        super.onStop();
    }
}
