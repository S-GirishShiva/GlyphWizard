# Hardware spike results (M0)

Measurements for spec §10, taken with the throwaway `:spike` app (plan Task 6). Every value carries `[measured YYYY-MM-DD]`, or is marked *not measured* with the reason.

## Device

| Item | Value |
|---|---|
| Model | Phone (4a) Pro, `A069P` (= SDK `Glyph.DEVICE_25111p`) [measured 2026-10-12] |
| OS | Nothing OS 4.1, build `B4.1-260907-1153`, Android 16, security patch 2026-09-01 [measured 2026-10-13] |
| Glyph service | `com.nothing.thirdparty` 13.01.01 [measured 2026-10-12] |
| SDK | GlyphMatrix SDK 2.0, commit `a65d8e1` |

Vendor Glyph properties (`adb shell getprop | grep -i glyph`) [measured 2026-10-13]:

| Property | Value | Reading |
|---|---|---|
| `ro.vendor.glyph.channels` | 137 | physical LEDs (matches `DeviceProfile.Phone4aPro`) |
| `ro.vendor.glyph.matrix_channels` | 169 | frame size, 13 × 13 |
| `ro.vendor.glyph.row` / `.column` | 13 / 13 | grid |
| `ro.vendor.glyph.brightness.cfg_group_brightness_0` | `5,10,20,25` | system Glyph brightness levels? [U: meaning not documented] |
| `ro.vendor.glyph.brightness.default_level` | 2 | index into the levels above? [U] |
| `ro.vendor.glyph.brightness.uniformization` | 0 | [U] |
| `ro.vendor.glyph.generative.simulation.multiple` | 4 | [U] |

## Integration facts (found during Task 5)

- **`NothingKey` is required.** Without `<meta-data android:name="NothingKey" android:value="test"/>`, `register()` returns true and `setAppMatrixFrame` throws nothing, but GlyphService logs `setAppMatrixColors, session is null` and lights nothing. With it, frames show. Debug mode (`nt_glyph_interface_debug_enable`) was **not** needed (setting unset). [measured 2026-10-13]
- **The first frame after `register()` is dropped:** the service creates its session asynchronously. The frame at +5 ms was dropped, the frame at +51 ms shown. [measured 2026-10-13]
- **GlyphService receives the 169-value frame unchanged** (its `finalColors` debug log equals our FullSquare frame). [measured 2026-10-13]
- **App matrix with no visible Activity:** keeps playing while the app's foreground service runs (Home pressed). [measured 2026-10-13]
- **Stop:** `closeAppMatrix` + `unInit` darkens the matrix within about 1 s. [measured 2026-10-13]

## Step 1: Layout and ordering

| Probe | What was sent | Seen on the matrix (user) | Device logs |
|---|---|---|---|
| `corners`, square | 169-cell grid, row ends lit | both ends of every row; top and bottom rows' middle LEDs dark ("two curved lines") | GlyphService `finalColors` = 169 values, unchanged |
| `single(4,0)`, square | top row, leftmost LED | **top row, left end**, phone upright, viewed from the back | — |
| `corners`, leds | 137 values in LED order | identical to the square layout | GlyphService passes 137 values through; system `NtGlyphServiceImpl.setLightFrame` gets `frameColors[137]` = the correct outline in LED order |

[measured 2026-10-13]

- **No transform:** row 0 is the top and x runs left → right *as seen from the back* (single LED probe; a mirror, flip, rotation or transpose would have moved it).
- **Both layouts are accepted.** The pipeline is app → GlyphService (app-side, `com.nothing.thirdparty`) → system_server `NtGlyphServiceImpl` → lights HAL, which always ends in 137 LED values.
- **Default stays `FullSquare` (169)**, the SDK's documented frame (`DEVICE_25111p_MATRIX_LENGTH = 13`); `LedsOnly` is a verified alternative.
- The system log also exposes `brightness:4095` (12-bit hardware brightness), `mPocketMode` and `mScreenUpward` per frame: useful device-side evidence for later steps.
- Photos: taken on the second phone (to be added to `docs/research/spike-photos/`).

## Step 2: Brightness range

- **Values pass through unchanged up to 2047.** With `max=2047`, GlyphService's `finalColors` and the system layer show 1027 (level 128) and 2047 (level 255), contradicting the README's "capped at 255". Hardware brightness stays `4095` throughout. [measured 2026-10-13]
- **Visible effect of values above 255:** not yet compared by eye (the first attempt was polluted by the charging animation; see Step 7). Until compared, the encoder keeps `maxValue = 255`. Open item: compare `all(255)` at max 255 against max 2047.
- **Caution on system-layer logs:** `NtGlyphServiceImpl` logs every glyph client (the charging animation interleaved its frames with ours), so its frames are attributed only while nothing else plays.

## Step 3: Response curve and minimum visible level

- **The lux-meter app can't see the matrix:** 6.5 lux off vs 6–7 lux at level 128 (its sensor reads the room, not the LEDs). Not used.
- **Relative response from one photo** (`ramp`, 8 bands in a single exposure, so auto-exposure cancels; second phone, room dark; peak pixel 208, no clipping). Middle-row LED peaks:

  | Sent | 31 | 31 | 63 | 63 | 95 | 127 | 127 | 159 | 159 | 191 | 223 | 223 | 255 |
  |---|---|---|---|---|---|---|---|---|---|---|---|---|---|
  | Pixel peak | 31 | 34 | 92 | 116 | 154 | 150 | 157 | 173 | 173 | 208 | 195 | 193 | 205 |

  Steep from 31 to about 95, near-flat from 127 to 255, which agrees with the user's eye ("after the third band they look the same"). The camera's JPEG tone curve also compresses highlights, so this is a **perceived** curve (eye and camera), not a calibrated luminance curve. [measured 2026-10-13]
- **Minimum visible level: 17.** Dark room, one observer: all LEDs at 1, 4 and 16 look off (16 re-checked on request: still off); 17, 18, 20 and 24 are visible. The system layer confirmed it received exactly 16 on all 137 LEDs. The one-step cut-off suggests a driver dead zone at ≤ 16 [U: hypothesis, not tested in daylight or by others]. [measured 2026-10-13]
- **Implication for the editor (spec §5, UI11):** of 0–255, roughly 17–~110 carries most visible differences; levels 1–16 render as off. Decide in M1/M2 how to present this (e.g. map the editor's brightness scale onto the visible range); recorded here as an input, not a decision.
- **Absolute luminance (for the flash rule):** not measured, no calibrated meter (user, 2026-10-13: left open; D37 stays conservative).

## Step 4: Frame rate

`rowsSweep` for about 6 s per rate. Rates come from log timestamps: spike `achievedFps`, GlyphService `finalColors` lines, system `NtGlyphServiceImpl.setLightFrame` lines (first quarter skipped as switch-over). [measured 2026-10-13]

| Requested fps | Spike achieved | GlyphService | System layer | Median interval (p95) |
|---|---|---|---|---|
| 30 | 30.10 | 30.0/s | 30.0/s | 33 ms (35) |
| 60 | 60.07 | 60.0/s | 60.0/s | 17 ms (18) |
| 90 | 90.03 | 89.9/s | 89.9/s | 11 ms (17) |
| 120 | 113.7 | 117.3/s | 117.2/s | 8 ms (11) |

- Delivery to the lights HAL keeps up exactly through 90 fps; at 120 the pipeline saturates at about 114–117 fps (each `setAppMatrixFrame` is a blocking binder call of about 8 ms; the services' per-frame debug logging adds load).
- **For D10** (tick 16–100 ms, so ≤ 60 fps): supported with a wide margin.
- **Not verified:** that the LEDs physically show every delivered frame above 60 fps (needs camera > 120 fps). Cross-check at 30/60 fps with 120 fps slow motion is optional; until then, "panel fps" means delivered-to-HAL fps.

## Step 5: Flash, rise time, glow, pitch

Second phone: 120 fps slow motion (no 240 fps available), photos; analysis with ffmpeg `signalstats` (per-frame mean luma) and ImageMagick. [measured 2026-10-13]

- **Flash renders as clean on/off cycles** (`flash` at 60 fps render):
  - 10 Hz: two clips measure 10.05 and 10.23 Hz; steady 5–6 frames on, 6–8 off at 120 fps.
  - 15 Hz: steady 4 frames on, 4 off (the clip's tail includes the switch to the next pattern, so only its flashing part counts).
  - Input for D37: the matrix reproduces 10–15 Hz flashing faithfully; nothing in the hardware softens it.
- **Rise and fall time ≤ 8 ms** (camera-limited): each edge shows at most one in-between frame, which straddled the switch.
- **No flicker visible at 120 fps:** luma steady while on (26.0, 26.0, 25.9). PWM above 60 Hz would be invisible to this camera, so this is not a proof of no PWM.
- **Glow:** a single LED at 255 shows a point with slight camera bloom and **no visible spill into neighbouring LEDs** (photo and the user's eye).
- **Pitch ≈ 1.7 mm, matrix width ≈ 22 mm (13 LEDs edge to edge), ±10%:** ₹10 bimetallic coin (27 mm) = 174 px, so 0.155 mm/px; LED gaps repeat every ~11.2 px. The coin lay on another phone beside the matrix, so height and angle differ slightly. LED size isn't resolvable (the photo is overexposed).
- **Preview look:** the matrix sits under a round glass window; in room light the unlit LED lattice is visible.
- **Not measured:** absolute luminance (no calibrated meter; left open by user decision, D37 stays conservative).

## Step 6: Auto-timeout

`setGlyphMatrixTimeout` (SDK; behaviour undocumented) via `TIMEOUT`, and a `HOLD` command added to the spike (sends one frame, then nothing, without releasing; user decision 2026-10-13). [measured 2026-10-13]

| Case | Result |
|---|---|
| Timeout **on**, frames streaming at 30 fps, 60 s | never cleared: 1 809 light-on events, 0 off |
| Timeout **on**, single held frame | still lit after 2+ min (user); no light-off event in 90 s of logs |
| Timeout **off**, single held frame | still lit, including after the phone screen went to sleep (user) |

- **No observable effect of `setGlyphMatrixTimeout`** in either direction within these windows; a held frame persists. For M3: a trigger that ends on a held frame must clear the matrix itself (`closeAppMatrix`); the system won't.
- **The system re-applies the current frame on its own** when the screen sleeps: at screen-off (doze), `NtGlyphServiceImpl` received a screen-upward sensor event (`type 65538`) and re-sent the held frame twice (+37 s and +39 s). This matches the user's "flicker in between". Re-application keeps the same frame (128 stayed 128).
- About 0.5 s after the last frame, the lights driver logs `Lights_power_thread wait` (likely the driver idling once frames stop) [U: effect, if any, not visible].

## Step 7: Priority and states

Playback: our app streaming all LEDs at 128, 30 fps. Device log buffer raised to 16 MB for attribution (restored afterwards). [measured 2026-10-13]

- **Charging animation:** with the phone on USB, Nothing's charging animation overrides the app matrix (user turned it off for the other measurements).
- **WhatsApp message, phone face-up and unlocked** (the user has a Glyph Museum notification sequence set up):

  | Phase | Log | Seen |
  |---|---|---|
  | 1 (1.5 s) | Nothing's system notification glyph on **light 108** (`NtAudioGlyphServiceImpl.openAudioLight`, system uid), exclusive | the standard Nothing animation only |
  | 2–3 (25 s) | **Glyph Museum writes the same app-matrix light (123) as us**: 259 of its frames interleaved with ours (last write wins) | both patterns, flickering |
  | after | only our frames | ours alone |

- **Implications for M3 (design inputs, not decisions):**
  - Nothing's system glyphs outrank third-party apps cleanly (separate light, exclusive while it plays).
  - **Third-party apps are not arbitrated against each other**: two apps writing light 123 overwrite each other frame by frame. GlyphWizard should avoid streaming when nothing changes, and document coexistence with other Glyph apps (e.g. Glyph Museum).
- **States (lock → face-down with a WhatsApp message → pocket → face-up):**
  - **Locked, face-up:** our matrix keeps playing; at screen-off the user saw it go off and on (consistent with the system re-applying the frame, Step 6).
  - **Face-down:** when the orientation sensor `type 65538` reported `1.0`, our frames stopped reaching the LED driver for **30.0 s** and resumed on their own when it returned to `0.0`. The app matrix is suppressed face-down (consistent with Nothing's Flip to Glyph). Sensor value meanings (0 up, 1 down, 2 ?) are inferred from the sequence [U].
  - Face-down notification: Nothing's system glyph (light 108) played; Glyph Museum again interleaved on light 123 once face-up.
  - **Pocket:** no distinct signal in the log (`mPocketMode` stayed false in every frame) [U: not separately confirmed].
  - **Back face-up:** playing again (user).
  - The per-frame `mPocketMode` / `mScreenUpward` flags stayed `false` throughout; the face-down suppression shows up as a gap in driver frames, not in those flags.

- **AOD Glyph Toy:** shows while our app has released the matrix (`STOP`); when we play, ours replaces it. This matches the README priority chart (third-party app usage above AOD toys). [measured 2026-10-13]
- **Nothing's glyph notification setting:** Settings → Glyph Interface → Essential notifications. **Not per app** (user). [measured 2026-10-13]

## Step 8: Kill and process

- **Force-stop while all LEDs play at 255: the matrix stays stuck on the last frame** (user: "stuck lit"). No light-off, `closeAppMatrix` or death handling appears in the log; the glyph service doesn't clean up after a dead client (Review Focus 5). [measured 2026-10-13]
  - **Design input for M1/M3:** a crash or kill can leave the LEDs lit indefinitely. Clear the matrix on the next start, avoid long-held bright static frames, and treat this as a known risk in the threat model (power and privacy: a notification glyph left showing).

- **Listener process in the background** (notification access granted, app at Home): oom adj 250, proc state 5, not cached, not frozen. The system-bound listener keeps the process up (input to D28). [measured 2026-10-13]
- **Automatic rebind after a crash** (`am crash`): crash 12:46:41.514, then connection lost (41.579), restart scheduled "in 1000 ms", process restarted (42.612), **listener connected 42.826: 1.31 s, with no app action**. [measured 2026-10-13]
- **Notification access is lost on uninstall** (the user deleted and reinstalled the spike); an `adb install -r` update keeps it. The consent dialog for an adb install shows no restricted-setting block, and lists "Read your notifications", "Reply to messages" and "Change settings" (screenshot in the M0 photos).
- **`shortService`** (D33): started 12:49:46.282, `Short FGS timed out` at 12:52:46.286, so **3 min 0.004 s from `startForeground`**; `onTimeout(startId, type=2048)` came 5 ms later; `stopSelf` → no ANR. Heartbeats every 10 s continued while backgrounded. Afterwards the process stays alive via the listener (proc state 5, not frozen). [measured 2026-10-13]

## Step 9: Restricted settings via Obtainium

## Decisions affected
