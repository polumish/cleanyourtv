package com.gathin.tvcameras;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The four cameras: name, the small stream shown in the grid, the sharp stream shown enlarged. Kept in SharedPreferences. */
final class CameraStore {
    static final int COUNT = 4;
    private static final String PREFS = "cameras";
    private static final String KEY = "json";
    private static final String HOST = "rtsp://192.168.100.150:8554/";

    static final class Camera {
        final String name;
        final String tileUrl;
        final String fullUrl;

        Camera(String name, String tileUrl, String fullUrl) {
            this.name = name;
            this.tileUrl = tileUrl;
            this.fullUrl = fullUrl;
        }
    }

    private CameraStore() { }

    /** The four on Home Assistant's main screen, as Frigate's go2rtc names them. */
    static List<Camera> defaults() {
        List<Camera> list = new ArrayList<>();
        list.add(new Camera("\u0414\u043e\u043c\u043e\u0444\u043e\u043d", HOST + "Backyard_tile", HOST + "Backyard"));
        list.add(new Camera("\u041a\u0430\u043b\u0456\u0442\u043a\u0430", HOST + "kalitka_sub", HOST + "kalitka"));
        list.add(new Camera("\u041c\u0430\u0439\u0441\u0442\u0435\u0440\u043d\u044f", HOST + "maisternia_sub", HOST + "maisternia"));
        list.add(new Camera("\u0421\u0430\u0434", HOST + "sad_sub", HOST + "sad"));
        return list;
    }

    static List<Camera> load(Context context) {
        List<Camera> result = defaults();
        String json = prefs(context).getString(KEY, null);
        if (json == null) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < COUNT && i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String tile = o.optString("tile", "");
                if (!validUrl(tile)) continue;
                String full = o.optString("full", "");
                result.set(i, new Camera(o.optString("name", result.get(i).name), tile, validUrl(full) ? full : tile));
            }
        } catch (JSONException ignored) {
            // a damaged value falls back to the defaults
        }
        return result;
    }

    static void save(Context context, List<Camera> cameras) {
        JSONArray array = new JSONArray();
        try {
            for (Camera c : cameras) {
                array.put(new JSONObject().put("name", c.name).put("tile", c.tileUrl).put("full", c.fullUrl));
            }
        } catch (JSONException e) {
            return;
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    static void reset(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    /** Only rtsp:// addresses, no spaces, a sane length. */
    static boolean validUrl(String url) {
        return url != null && url.length() > 7 && url.length() <= 512 && url.startsWith("rtsp://")
                && url.indexOf(' ') < 0 && url.indexOf('\n') < 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
