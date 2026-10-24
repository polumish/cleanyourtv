package com.gathin.tvhome;

/**
 * Settings of the optional camera tile on Home (config.json camera_rtsp_url and friends). Off unless a theme gives an
 * rtsp:// address: nothing is created, no socket is opened and no permission is exercised while it is off.
 * Plain Java on purpose, so the rules can be unit-tested without Android.
 */
final class CameraSettings {
    static final CameraSettings OFF = new CameraSettings("", "", 240, 4.0 / 3.0, "");

    static final int MIN_WIDTH_DP = 120;
    static final int MAX_WIDTH_DP = 480;
    static final double MIN_ASPECT = 1.0;
    static final double MAX_ASPECT = 2.4;
    private static final int MAX_URL = 512;
    private static final int MAX_LABEL = 24;

    /** The small stream shown in the tile, muted (a camera's sub stream: low resolution, low bitrate). */
    final String url;
    /** The stream the full-screen view plays, with sound; the tile's own when the theme names none. */
    final String fullUrl;
    final int widthDp;
    /** Width divided by height of the tile, the picture's own shape (4:3 for a 640x480 stream). */
    final double aspect;
    final String label;

    private CameraSettings(String url, String fullUrl, int widthDp, double aspect, String label) {
        this.url = url;
        this.fullUrl = fullUrl;
        this.widthDp = widthDp;
        this.aspect = aspect;
        this.label = label;
    }

    /**
     * Builds the settings from raw theme values. A tile address that is not a plain rtsp:// URL turns the tile off
     * rather than guessing; a bad full-screen address falls back to the tile's own; numbers are clamped.
     */
    static CameraSettings of(String url, String fullUrl, int widthDp, double aspect, String label) {
        String tile = url == null ? "" : url.trim();
        if (!validUrl(tile)) return OFF;
        String full = fullUrl == null ? "" : fullUrl.trim();
        if (!validUrl(full)) full = tile;
        double shape = Double.isNaN(aspect) ? 4.0 / 3.0 : Math.max(MIN_ASPECT, Math.min(MAX_ASPECT, aspect));
        String name = label == null ? "" : label.trim();
        if (name.length() > MAX_LABEL) name = name.substring(0, MAX_LABEL).trim();
        return new CameraSettings(tile, full, Math.max(MIN_WIDTH_DP, Math.min(MAX_WIDTH_DP, widthDp)), shape, name);
    }

    boolean enabled() {
        return !url.isEmpty();
    }

    int heightDp() {
        return (int) Math.round(widthDp / aspect);
    }

    /** rtsp:// only, one line, no spaces or control characters, a host after the scheme. */
    static boolean validUrl(String s) {
        if (s == null || s.length() <= "rtsp://".length() || s.length() > MAX_URL) return false;
        if (!s.regionMatches(true, 0, "rtsp://", 0, "rtsp://".length())) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c == 0x7f) return false;
        }
        return s.charAt("rtsp://".length()) != '/';
    }

    /** The address without a user name and password, for the log: rtsp://user:pass@host/x becomes rtsp://host/x. */
    static String redact(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        int at = url.indexOf('@');
        int slash = scheme < 0 ? -1 : url.indexOf('/', scheme + 3);
        if (scheme < 0 || at < 0 || (slash >= 0 && at > slash)) return url;
        return url.substring(0, scheme + 3) + url.substring(at + 1);
    }

    /** Wait before the nth reconnect after a failure: 3 s, 6 s, 12 s, 24 s, then 30 s at most. */
    static long retryDelayMs(int failures) {
        int n = Math.max(0, Math.min(failures, 4));
        return Math.min(30_000L, 3_000L << n);
    }
}
