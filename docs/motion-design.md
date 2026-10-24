# Motion and glass system (TV launcher + Cameras app)

Source: ui-ux-pro-max (style `liquid-glass`, UX rules `duration-timing`, `exit-faster-than-enter`, `spring-physics`,
`stagger-sequence`, `reduced-motion`, `excessive-motion`, loading feedback), adapted to a 10-foot D-pad UI on a weak TV GPU.

## Principles
1. Glass is for navigation and controls only (dock, pills, frames). Content (video, wallpapers) stays on its own layer.
2. Animate at most 1-2 elements per view. Transform and alpha only; no live blur (blur is faked with the dimmed wallpaper).
3. Motion explains: focus moves, a tile grows into the screen, a stream connects. Nothing moves "for decoration".
4. Exit is shorter than enter (about 65%). One shared set of tokens for every screen.
5. Reduced motion / reduced transparency: no shimmer, no scale; swaps become a 120 ms cross-fade; glass fill becomes opaque.

## Tokens
| Token | Value |
|---|---|
| bg | #05070B |
| glass fill | white 10 % |
| glass edge (rest / focus) | white 35 % 1.5 dp / white 95 % 3 dp |
| text primary / secondary | #F2F5FA / 70 % |
| focus accent | #9CC9FF |
| stale / warning | #FFBE3C |
| radius | tile 18 dp, dock 28 dp |
| min label size | 16 sp on tiles (32 px at 1080p), 22 sp enlarged |
| focus move | spring, 220 ms, damping 0.8 |
| focus scale | 1.00 to 1.04, enter 180 ms decelerate, exit 120 ms |
| expand | 280 ms decelerate; collapse 180 ms accelerate |
| tile stagger | 40 ms per tile in grid order |
| connecting shimmer | one 1.6 s linear sweep, only while CONNECTING, stops at first picture |
| stale cache | frame at 55 % alpha with its time, until the live picture arrives |

## Camera tile states
connecting (skeleton + shimmer) -> cached (dimmed last picture + time) -> live -> no signal (static, retry in 3/6/12/24/30 s).

## Rive scope
Rive scenes carry: focus glass highlight between tiles, connecting shimmer, expand/collapse morph. A Rive artboard is
drawn at tile size, never full screen. The previous hand-made UI stays as a fallback chosen by a theme key.
