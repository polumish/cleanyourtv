package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CameraSettingsTest {
    private static final String TILE = "rtsp://192.168.100.150:8554/Backyard_sub";
    private static final String FULL = "rtsp://192.168.100.150:8554/Backyard";

    @Test
    public void noAddressMeansOff() {
        assertSame(CameraSettings.OFF, CameraSettings.of("", FULL, 240, 1.33, "Gate"));
        assertSame(CameraSettings.OFF, CameraSettings.of(null, null, 240, 1.33, null));
        assertFalse(CameraSettings.OFF.enabled());
    }

    @Test
    public void onlyPlainRtspAddressesCount() {
        assertTrue(CameraSettings.validUrl(TILE));
        assertTrue(CameraSettings.validUrl("RTSP://user:pw@cam.local/stream1"));
        assertFalse(CameraSettings.validUrl("http://192.168.100.150:1984/api/stream.mp4"));
        assertFalse(CameraSettings.validUrl("file:///sdcard/x.mp4"));
        assertFalse(CameraSettings.validUrl("rtsp://"));
        assertFalse(CameraSettings.validUrl("rtsp:///path"));
        assertFalse(CameraSettings.validUrl("rtsp://host/a b"));
        assertFalse(CameraSettings.validUrl("rtsp://host/a\nb"));
        assertFalse(CameraSettings.validUrl("javascript:alert(1)"));
        StringBuilder long513 = new StringBuilder("rtsp://h/");
        while (long513.length() <= 513) long513.append('x');
        assertFalse(CameraSettings.validUrl(long513.toString()));
    }

    @Test
    public void badTileAddressTurnsTheTileOffRatherThanGuessing() {
        assertSame(CameraSettings.OFF, CameraSettings.of("http://x/y", FULL, 240, 1.33, "Gate"));
    }

    @Test
    public void badFullScreenAddressFallsBackToTheTilesOwn() {
        CameraSettings s = CameraSettings.of(TILE, "not a url", 240, 4.0 / 3.0, "Gate");
        assertTrue(s.enabled());
        assertEquals(TILE, s.fullUrl);
        assertEquals(FULL, CameraSettings.of(TILE, FULL, 240, 4.0 / 3.0, "Gate").fullUrl);
        assertEquals(TILE, CameraSettings.of(TILE, "", 240, 4.0 / 3.0, "").fullUrl);
    }

    @Test
    public void sizeAndShapeAreClamped() {
        assertEquals(CameraSettings.MIN_WIDTH_DP, CameraSettings.of(TILE, "", 10, 1.33, "").widthDp);
        assertEquals(CameraSettings.MAX_WIDTH_DP, CameraSettings.of(TILE, "", 5000, 1.33, "").widthDp);
        assertEquals(CameraSettings.MIN_ASPECT, CameraSettings.of(TILE, "", 240, 0.2, "").aspect, 1e-9);
        assertEquals(CameraSettings.MAX_ASPECT, CameraSettings.of(TILE, "", 240, 40.0, "").aspect, 1e-9);
        assertEquals(4.0 / 3.0, CameraSettings.of(TILE, "", 240, Double.NaN, "").aspect, 1e-9);
    }

    @Test
    public void heightFollowsTheShape() {
        assertEquals(180, CameraSettings.of(TILE, "", 240, 4.0 / 3.0, "").heightDp());
        assertEquals(135, CameraSettings.of(TILE, "", 240, 16.0 / 9.0, "").heightDp());
    }

    @Test
    public void labelIsTrimmedAndShortened() {
        assertEquals("Gate", CameraSettings.of(TILE, "", 240, 1.33, "  Gate  ").label);
        assertEquals(24, CameraSettings.of(TILE, "", 240, 1.33, "abcdefghijklmnopqrstuvwxyz0123456789").label.length());
    }

    @Test
    public void logsNeverCarryTheCamerasPassword() {
        assertEquals("rtsp://cam.local/stream1", CameraSettings.redact("rtsp://admin:secret@cam.local/stream1"));
        assertEquals(TILE, CameraSettings.redact(TILE));
        // an @ in the path is not a user name
        assertEquals("rtsp://h/a@b", CameraSettings.redact("rtsp://h/a@b"));
        assertEquals("", CameraSettings.redact(null));
    }

    @Test
    public void retryPausesGrowThenStop() {
        assertEquals(3_000L, CameraSettings.retryDelayMs(0));
        assertEquals(6_000L, CameraSettings.retryDelayMs(1));
        assertEquals(12_000L, CameraSettings.retryDelayMs(2));
        assertEquals(24_000L, CameraSettings.retryDelayMs(3));
        assertEquals(30_000L, CameraSettings.retryDelayMs(4));
        assertEquals(30_000L, CameraSettings.retryDelayMs(400));
        assertEquals(3_000L, CameraSettings.retryDelayMs(-5));
    }
}
