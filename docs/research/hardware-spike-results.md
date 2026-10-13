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

## Step 6: Auto-timeout

## Step 7: Priority and states

## Step 8: Kill and process

## Step 9: Restricted settings via Obtainium

## Decisions affected
