package com.gathin.tvcameras;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Name and the two rtsp:// addresses of each of the four cameras; saved on the device, no rebuild needed. */
public class SettingsActivity extends Activity {
    private final List<EditText[]> fields = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(32 * density);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad / 2, pad, pad);
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        column.addView(title);

        List<CameraStore.Camera> cameras = CameraStore.load(this);
        for (int i = 0; i < CameraStore.COUNT; i++) {
            CameraStore.Camera c = cameras.get(i);
            TextView head = new TextView(this);
            head.setText(getString(R.string.camera_n, i + 1));
            head.setTextColor(0xFF9CC9FF);
            head.setTextSize(18f);
            head.setPadding(0, Math.round(16 * density), 0, 0);
            column.addView(head);
            EditText name = field(column, getString(R.string.field_name), c.name);
            EditText tile = field(column, getString(R.string.field_tile_url), c.tileUrl);
            EditText full = field(column, getString(R.string.field_full_url), c.fullUrl);
            fields.add(new EditText[]{name, tile, full});
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, pad / 2, 0, 0);
        Button save = new Button(this);
        save.setText(R.string.save);
        save.setOnClickListener(v -> save());
        Button reset = new Button(this);
        reset.setText(R.string.reset);
        reset.setOnClickListener(v -> {
            CameraStore.reset(this);
            finish();
        });
        buttons.addView(save);
        buttons.addView(reset);
        column.addView(buttons);
        setContentView(scroll);
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x88FFFFFF);
        e.setTextSize(16f);
        parent.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void save() {
        List<CameraStore.Camera> result = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            EditText[] f = fields.get(i);
            String name = f[0].getText().toString().trim();
            String tile = f[1].getText().toString().trim();
            String full = f[2].getText().toString().trim();
            if (full.isEmpty()) full = tile;
            if (!CameraStore.validUrl(tile) || !CameraStore.validUrl(full)) {
                Toast.makeText(this, getString(R.string.bad_url, getString(R.string.camera_n, i + 1)),
                        Toast.LENGTH_LONG).show();
                return;
            }
            result.add(new CameraStore.Camera(name.isEmpty() ? getString(R.string.camera_n, i + 1) : name, tile, full));
        }
        CameraStore.save(this, result);
        finish();
    }
}
