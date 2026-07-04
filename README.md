# X3 Breakout

Free software, licensed under the GNU GPL v3 (see LICENSE).

Arkanoid with Tempest-style neon visuals for RayNeo X3 Pro glasses.
Vector webs, right-arm temple-pad swipe paddle control, power-up
countdowns in a Tempest-style vector font, per-level music, and a
pre-generated TTS hype announcer. Built native (no Unity, no RayNeo SDK,
no camera — hand tracking was removed as too problematic).

Presentation follows NeonTetris3D: a head-locked big screen (fixed camera,
55° FOV, slow sway) that fills the view — with a WINDOW SIZE setting if
you want it smaller or even larger.

## Story

You pilot THE PADDLE — a living open-source defense program: deflector,
shield, public firewall, labor-memory archive. Every ball is an open
signal bounced back into the privatized grid. The enemy is the MONOPOLY
CONTROL PROTOCOL (MCP), a corporate extraction intelligence wearing the
webs as its body: it wants to privatize open-source AI, paywall public
knowledge, and charge humanity rent on its own collective labor.
(Original characters — homage energy, legally distinct.)

The 16-web campaign lives in `game/Campaign.kt`: each web has a title
card, a start caption, a mid-level MCP taunt (fires when half the grid
is broken), and a clear line. MCP appears as a central sigil per level
(`game/McpCore.kt`) — command cone, contract glyphs, paywall lock,
surveillance eye, monopoly pillars, debt chains, crown — and glitches,
flickers and cracks as its bricks fall. After web 16 the finale plays:
MCP collapses into open fragments, the Paddle becomes a public beacon,
and the campaign wraps into faster endless webs.

All story text renders as large, high-contrast captions in a fixed
caption region (auto-wrapped, segmented into timed 2-line pages, MCP
lines in red with a jitter). There is NO debug text in normal play —
FPS/thermals go to logcat only.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35). Then:

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/x3breakout.apk
adb shell monkey -p com.x3.breakout 1
```

The Gradle wrapper is committed; no local Gradle install needed.

## Audio — X3 stall-aware design

History: a loaded SoundPool or always-on looping MediaPlayer made the
X3 audio stack stall the GL thread ~500-630 ms every ~5-6 s (verified
with `FRAME HITCH` logs, tag `X3Render`; correlates with firmware
`persist.debug.rayneo.audio.*` probes). Audio is back ON with a design
built around that:

- **SFX**: no SoundPool. `SfxMixer` keeps raw PCM in memory and mixes in
  software into ONE AudioTrack that only exists while sounds are
  audible — released the instant the game goes silent. SFX VOL 0 means
  the track never opens.
- **Music**: looping MediaPlayer, but **MUSIC VOL 0 fully releases the
  players** (not a mute). If stalls come back on your firmware, set
  MUSIC VOL to 0 in the settings menu — no rebuild needed.
- **Voice**: one-shot MediaPlayers (create → play → release), the
  pattern that tested hitch-free.

After any audio change, watch `adb logcat -s X3Render` for FRAME HITCH.

- `app/src/main/assets/music/` — drop MP3s; the game uses an explicit
  campaign order. `mcp.mp3` is the title track; level 1 starts with
  `03 - IO Tower (Cover).mp3`, level 3 uses `New Song Arpeggio (Cover).mp3`,
  level 4 uses `Close Encounters Arpeggios 2 (Remastered x2) (Cover).mp3`,
  and level 5 uses `Disk Check Arpeggios.mp3`. Extra MP3s are appended
  alphabetically after the known campaign list.
- `app/src/main/assets/powerup/` — drop ONE MP3; it loops for the duration
  of any timed power-up, then level music resumes where it paused.
- `app/src/main/assets/sfx/` — 16-bit PCM WAVs, 22050 Hz mono.

Rebuild after adding files (assets ship inside the APK, uncompressed).

## Voice commentary (Fish Audio TTS)

`app/src/main/assets/commentary.json` is the public voice script. It is
formatted for editing:

- `event` is the game trigger.
- `voice` chooses a named model from `tools/fish.config`.
- `voice_model_id` can override one line directly, but leave it blank unless
  you want that model id visible in git.
- `quote` is the exact spoken line.
- `reason` explains the gameplay moment and acting intent.

Put private Fish credentials in a local config file that is ignored by git:

```
cp tools/fish.config.example tools/fish.config
# edit tools/fish.config:
#   FISH_API_KEY=...
#   DEFAULT_VOICE_MODEL_ID=...
#   VOICE_MCP_MODEL_ID=...
#   VOICE_PADDLE_MODEL_ID=...
#   VOICE_ANNOUNCER_MODEL_ID=...
#   VOICE_HYPE_MODEL_ID=...
#   VOICE_CALM_MODEL_ID=...
#   VOICE_WARNING_MODEL_ID=...
python3 tools/generate_commentary.py
./gradlew assembleDebug
```

Clips land in `assets/voice/`. The app itself has **no INTERNET
permission** — commentary is fully offline at play time.

Every line has `id`, `event`, `voice`, `voice_model_id` (blank by
default — private ids live in `tools/fish.config`, never in git),
`quote`, and `reason`. Voice roles: `mcp` (cold corporate authority),
`paddle` (hero/open-source defender), `announcer`, `hype`, `calm`,
and `warning`.

Story events: `l01_start`/`l01_taunt`/`l01_clear` … `l16_*`, plus
`finale_1`. Gameplay events: `powerup_expand`, `powerup_laser`,
`powerup_slow`, `powerup_catch`, `powerup_multi`, `superzapper`,
`extra_life`, `streak`, `life_lost`, `game_over`. Multiple lines per
event = random pick; missing clips are skipped silently.

## Controls

| Input | Action |
|---|---|
| Right-arm swipe on the temple pad | Move paddle — continuous drag, one calibrated full stroke = full paddle travel |
| Temple pad tap | Start game / launch ball |
| Temple pad swipe steps | Move menu cursor (settings only) |
| Temple pad double-tap | Open/close settings; cancel calibration |

Screen touches mirror the temple pad, so everything is bench-testable
on a phone/desk before wearing the glasses.

## Settings menu (NeonTetris3D-style: swipe = cursor, tap = select/cycle)

| Item | Action |
|---|---|
| RESUME | Close menu |
| RESTART GAME | New game from web 1 |
| WINDOW SIZE | SMALL / MEDIUM / LARGE — rescales the whole game live |
| SWIPE SENS | LOW / MEDIUM / HIGH / TURBO — multiplier on the calibrated stroke |
| CALIBRATE SWIPE | swipe 3 full arm strokes on the pad; their average length becomes full paddle travel (persisted) |
| CAPTIONS | ON / OFF — story captions and MCP taunts |
| COMMENTARY | ON / OFF — voice lines |
| MUSIC VOL | 0 → 0.4 → 0.8 → 1.0 |
| SFX VOL | 0 → 0.4 → 0.8 → 1.0 (plays a test hit) |

All settings persist across launches.

## Power-ups

| Token | Effect | Timed |
|---|---|---|
| E | EXPAND — wide paddle | 15 s |
| L | LASER — auto-fire twin lasers | 10 s |
| S | SLOW — ball slows to 55% | 12 s |
| C | CATCH — sticky paddle | 15 s |
| M | MULTI — up to 3 balls | instant |
| Z | SUPERZAPPER — vaporizes 15 bricks | instant |
| 1 | 1UP — extra life (max 5) | instant |

Timed power-ups show a countdown above the web in the Tempest vector
font; it color-cycles into a panic strobe under 3 seconds. While any
timed power-up runs, the special power-up track plays.

## Levels

16 web shapes echoing the classic Tempest sequence (circle, square,
plus, flat, V, star, U, zigzag, triangle, hexagon, bowtie, arc, diamond,
peaks, steps, octagon), each built from 2-3 concentric brick rings that
shrink toward the web centroid. After web 16 the shapes wrap with faster
balls and +1 HP rings.

## Project layout

```
app/src/main/java/com/x3/breakout/
  MainActivity.kt        wiring, crash guard, immersive mode, thermal HUD
  Settings.kt            persisted options (window size, swipe sens, ...)
  MathX.kt               quaternions, One-Euro filter
  render/  StereoRenderer (head-locked fov-55 camera), NeonBatch, VectorFont
  input/   TemplePad (taps/swipes/drags), SwipeControl (paddle + calibration)
  track/   HeadTracker (unused, kept for reference)
  game/    Game (state+physics+menu), Levels (webs), PowerUps
  audio/   GameAudio (music dirs, power-up override, SFX, commentary)
app/src/main/assets/
  music/    title and level MP3s (explicit campaign order in GameAudio)
  powerup/  the ONE power-up MP3
  voice/    generated commentary clips
  sfx/      procedural retro SFX (pre-generated WAVs)
  commentary.json
app/src/main/res/mipmap-*/   neon launcher icon (legacy + adaptive)
tools/
  generate_commentary.py  Fish Audio TTS batch generator
  fetch_hand_model.sh     MediaPipe model download
```

## License

X3 Breakout — a story-driven neon breakout for RayNeo X3 Pro glasses.
Copyright (C) 2026 Mars

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or (at your
option) any later version. See the LICENSE file for the full text.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

Note on assets: voice clips in `app/src/main/assets/voice/` are
generated with Fish Audio TTS from the quotes in `commentary.json`;
SFX are procedurally generated WAVs. `tools/fish.config` (API keys,
private voice model ids) is gitignored and must never be committed.
