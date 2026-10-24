# Camera tile

An optional live camera picture on Home, in the top right under the Inputs, Wi-Fi and Settings pills. It is **off by
default** and built only when a theme names a camera, so a launcher without the key behaves as before.

```json
{
  "camera_rtsp_url": "rtsp://192.168.100.150:8554/Backyard_sub",
  "camera_full_url": "rtsp://192.168.100.150:8554/Backyard",
  "camera_tile_width_dp": 240,
  "camera_tile_aspect": 1.333,
  "camera_label": "Backyard"
}
```

- **The tile** plays `camera_rtsp_url`, muted. Pick the camera's *sub stream*: low resolution and bitrate (a 640×480
  H.264 stream at 15 fps is about 0.3 Mbit/s). The picture is decoded by the TV's hardware decoder; nothing is
  transcoded.
- **OK on the tile** opens `camera_full_url` full screen, with sound. Without that key the tile's own stream opens.
  Back returns to Home.
- **Remote:** Down from the top pills moves to the tile, Down from the tile to the dock, Up to the pills.
- **When it plays:** only while Home is on screen. In an app, on the screensaver or with the screen off the stream is
  closed, so it costs nothing. If the camera is unreachable the tile says so and retries after 3, 6, 12, 24 and then
  every 30 seconds.
- **Player:** the platform's `MediaPlayer`, so no library is added and the APK barely grows.
- **Permission:** this adds `android.permission.INTERNET` to the launcher, which upstream's launcher does not have. With
  the key unset no socket is opened. Anything on the network that can reach the camera's address can watch the
  stream: use a camera or restream address that is meant to be seen on the home network, and mind that an `rtsp://`
  address with a user name and password is stored in plain text in `config.json` on the TV.
- **Frigate / go2rtc:** the restream is `rtsp://<host>:8554/<stream name>`; use the sub stream for the tile. Frigate's
  `latest.jpg` is not a live picture: with no motion in view it is refreshed only every ~10 s.

Only `rtsp://` addresses are accepted; anything else leaves the tile off.
