# Sound Pairing & Human Editor UX: Research

2026-10-11. Builds on `agent-research-brief.md`, `decisions.md` (D1–D23), `2026-10-11-animation-model-synthesis.md` and `../glossary.md`. No app code.

**Evidence tags:**
- **[V]**: verified in a primary source fetched today (official docs, AOSP source, repo source or docs).
- **[S]**: secondary source (review sites, blogs, search snippets).
- **[U]**: unverified, from memory, or not re-fetched.
- **[E]**: my estimate or judgement.

---

## TL;DR

**Sound (Part A)**
- **Don't play sounds from our app when an alert fires.** It mostly fails, and when it works it collides with the source notification's own sound.
  - A sound with `USAGE_NOTIFICATION` is muted by Do Not Disturb (AOSP `ZenModeHelper` [V]) and by silent/vibrate mode.
  - Flip to Glyph puts the phone in silent mode [V, Nothing support]. So the LEDs-only moment is exactly when our sound can't play.
  - When the ringer is on, the source app's notification already makes its own sound, so ours would be a second sound.
- **The valuable sound feature is exporting a Nothing "composition" ringtone.**
  - Format: an Ogg/Opus file with the glyph frames base64+zlib-encoded in the `AUTHOR` tag, at 60 Hz, with brightness 0–4095 [V, SebiAi docs].
  - The format already supports the Glyph Matrix. Phone (4a) Pro uses `169cols` (a row-major 13×13 grid with the masked corner cells set to 0) and Phone (3) uses `625cols` [V, SebiAi docs].
  - The OS plays the audio and lights together, so we don't have to solve A/V sync ourselves.
  - Recommended scope: v1.x.
- **Beat-synced auto-keyframes are feasible on-device.**
  - A useful onset detector is about 40 lines (Glyphonic's energy-ratio detector [V, source read]). Decoding uses the platform's `MediaExtractor`/`MediaCodec`.
  - Recommended scope: v1.x as "beat markers" on the timeline. Full auto-generation comes later.

**Editor (Part B)**
- **Ship two views of one model:**
  - **Frames mode** (default): flipbook of a single raster clip, frame strip, onion skin.
  - **Timeline mode** (opt-in): layers, clips, keyframes, effects.
- This follows Procreate Dreams, whose Flipbook view hides the Timeline and whose Compose mode hides keyframe tracks [V, handbook]. It also follows the recurring "steep learning curve" complaint about keyframe-heavy mobile apps such as Alight Motion [S].
- **Cell size:** a 13×13 grid filling the screen width gives cells of about 26 dp (≈4 mm). The evidence-based tap minimum is 7.7–9.6 mm (Parhi et al. 2006 [S]) and the Android guideline is 48 dp [V].
  - Mitigations: pinch zoom, a circle-aware hit-test, strokes with undo, and an optional offset cursor (Dotpict precedent [S]).
  - Phone (3)'s 25×25 grid (~13 dp cells) *requires* zoom.
- **Brightness:** 5 preset swatches, a custom slider and an eyedropper.
- **Alpha:** three tools (Paint / Paint-off / Erase) per D6, with a non-colour cue for "transparent".
- **LED preview:** you can't see the back while editing. Use a "▶ LEDs" button with a 3 s countdown and haptic so the user can flip the phone, plus an opt-in "mirror current frame" toggle.

---

## Part A: Sound

### A1. How Nothing pairs glyphs with sound (verified)

**Composition format** (SebiAi `docs/9_Technical Details.md` [V]; Glyphtones guide [V]):
- **Audio:** an `.ogg` file. The audio "only determines the final length of the composition".
- **Required audio codec:** Opus. SebiAi's GlyphModder warns "wrong codec … expected: opus" and re-encodes with `-c:a opus` [V, `GlyphModder.py` lines 625–678].
- **Vorbis-comment tags:**

| Tag | Content |
|---|---|
| `TITLE` | Name shown in the normal ringtone menu |
| `ALBUM` | Sound pack (cosmetic) |
| `AUTHOR` | base64(zlib(CSV)) light data. One row per 16.666 ms (60 Hz), one column per zone, values 0–4095, rows end with `\r\n` |
| `CUSTOM1` | base64(zlib) Composer UI dots (cosmetic) |
| `CUSTOM2` | Zone count string. Must match the device |
| `COMPOSER` | `"v1-" + Build.DEVICE + " Glyph Composer"`. Must match the device or the import is rejected |

- **Glyph Matrix support exists:**

| Device | `CUSTOM2` | `COMPOSER` | Data layout |
|---|---|---|---|
| Phone (4a) Pro | `169cols` | `v1-FroggerPro Glyph Composer` | Row-major 13×13. Only 137 cells are visible (index ranges listed in the doc); the rest "can be set to 0" |
| Phone (3) | `625cols` | `v1-Metroid Glyph Composer` | Row-major 25×25. 489 cells visible |

- **Side benefit:** this independently answers an open hardware-spike question for the composition path. The layout is the full square grid, row-major, with masked cells present and ignored. Whether `setAppMatrixFrame(int[])` uses the same layout is still **[U]** until the spike, but it's a strong hint.
- **Official tooling:** Nothing's *Glyph Composer app does not support Matrix phones*. But "the same techniques can still be used to get custom ringtones working" [V, SebiAi]. SebiAi ships `VideoToGlyphMatrix.py`: a 60 fps video goes to an NGlyph file, then GlyphModder writes the `.ogg` [V, `docs/6_…Glyph Matrix.md`].
- **Installation:** import in Composer, or place the file in `Ringtones/Compositions/` (the manual path is required when import fails) [V, Glyphtones guide]. On Phone (3), Nothing support says custom tones are added under Glyph Interface → Ringtones [S]. The exact flow for a matrix composition on the **4a Pro** is **[U]** and must be tested on hardware.
- **Interop bonus:** SebiAi added `ToNGlyph.py` in July 2026, which converts *pauwma Glyph Museum JSON* to NGlyph [V, commit message]. So the community already bridges GM JSON → ringtone, and our GM export already feeds that pipeline.

**Can a third-party app set a glyph ringtone?**
- **Writing the file:** `MediaStore.Audio` accepts files in `Ringtones/` with no permission for our own files [V, shared-media docs]. So writing `Ringtones/Compositions/<name>.ogg` should work. Whether the subfolder is honoured via `RELATIVE_PATH` is [U].
- **Setting it as default:** `RingtoneManager.setActualDefaultRingtoneUri(ctx, TYPE_RINGTONE, uri)` exists [V]. It needs the special `WRITE_SETTINGS` grant (Settings.System write; standard knowledge, not stated on that method's page) [U].
- **Unknown:** whether Nothing's ringer plays the embedded glyph data when the tone is chosen through the standard Android picker rather than Nothing's Glyph ringtone list [U]. Community guides always route through Nothing's own Glyph Interface or Composer list.
- **Recommendation:** write the file, then deep-link or instruct the user to pick it in Glyph Interface → Ringtones. Don't request `WRITE_SETTINGS`. That matches D12's spirit (the user assigns, not the app).

**Prior art for the Matrix:**
- **Better Nothing Glyph Composer** (web) lists Phone (3), has "Auto Generate Glyphs … (in Alpha)", and writes the `.ogg` with embedded data [V, README; S for matrix depth].
- **GLYPH Studio** and **Glyphify** (Play) claim Matrix ringtone or video-composition support [S]. The listings could not be fetched.
- **SebiAi/GlyphVisualizer** is a desktop *player* for compositions (Qt), not an audio visualiser [S].

### A2. Playing a sound with our own alert

**AudioAttributes usages** [V, reference + AOSP `AudioAttributes.java`, `ZenModeHelper.java`]:

| Usage | Doc meaning | DND (zen) class | Fit for us |
|---|---|---|---|
| `USAGE_NOTIFICATION` | "usage is notification" | `SUPPRESSIBLE_NOTIFICATION`: muted whenever zen is on | Correct semantics. Muted in DND and silent mode |
| `USAGE_NOTIFICATION_EVENT` | "attract the user's attention, such as a reminder or low battery warning" | Same as above | Slightly better semantics for a "glyph alert". Same muting |
| `USAGE_NOTIFICATION_COMMUNICATION_*` | Deprecated in API 33: "use USAGE_NOTIFICATION, handled the same way" | — | Don't use |
| `USAGE_ALARM` | "wake-up alarm" | `SUPPRESSIBLE_ALARM`: plays in priority DND unless alarms are disallowed | **Abuse** for notification sounds. It would bypass the user's silent intent. Don't use |
| `USAGE_ASSISTANCE_SONIFICATION` | UI sounds | `SUPPRESSIBLE_SYSTEM` | OK for *editor* UI clicks only |

- **How DND enforces this:** `ZenModeHelper.applyRestrictions()` sets AppOps `OP_PLAY_AUDIO`/`OP_VIBRATE` restrictions **per usage, for all apps**, except packages exempt in priority mode [V]. So an app that "plays its own sound" can't escape DND by using a non-notification player; DND keys on the usage, not on the API used.
- **Silent and vibrate modes:** these mute the ringer-affected streams (ring, notification, system). Notification-usage audio maps to `STREAM_NOTIFICATION` [U: AudioService detail, not re-fetched].
- **Flip to Glyph:** "triggers silent mode, with Glyph-light-only alerts for notifications and calls" [V, Nothing support article via search; S 9to5Google].

So **our sound plays only when the ringer is on and DND is off**. That's the same moment the source app's notification sound plays. Our trigger fires *after* the notification is posted (NotificationListener), so the two sounds overlap or play back-to-back. We can't suppress another app's sound. The best we can do is deep-link the user to that app's channel settings so they mute it themselves (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`; whether it works for *other* apps' channels is [U]).

**Verdict [E]:** paired sound for notification triggers has low value and real annoyance risk. Don't build it in v1. If it ever ships, it should be opt-in per rule, use `USAGE_NOTIFICATION_EVENT`, request transient-may-duck audio focus, and default to off.

**Player choice and latency** (relevant for editor preview, not alerts):

| API | What it is | Latency / sync facts |
|---|---|---|
| `SoundPool` | Pre-decodes short clips to 16-bit PCM via MediaCodec "without … latency of decompressing during playback". Each sound is limited to 1 MB decoded (~5.6 s at 44.1 kHz stereo) [V] | Lowest start latency. Too short for ringtone-length audio. Fine for UI blips |
| `MediaPlayer` | Simple file/stream player | `getTimestamp()` gives a `MediaTimestamp` anchored to system time, so the current position can be extrapolated [V] |
| Media3 `ExoPlayer` | Google's recommended player | `currentPosition` is in ms. The default `AudioTrack` buffer is >250 ms [S, ExoPlayer #8970]. Internally it uses `AudioTrack` timestamps; whether its position already compensates for output latency is [U]. Offload mode updates position rarely, so disable offload for sync [S, Qualcomm] |
| `AudioTrack` | Raw PCM | `getTimestamp()` gives the rendered frame position plus nanoTime. Docs say poll every 10 s to 1 min once stable, and interpolate at app level [V] |

**A/V sync recipe for the editor (later) [E]:**
1. Use **the audio clock as master**. Each display frame (Compose `withFrameNanos`), read the player position and render `frame(t)`.
2. Push the same `t` to the LEDs via `setAppMatrixFrame`.
3. LED pipeline latency is unknown. Add a user-calibratable **A/V offset** (±200 ms slider, a rhythm-game convention) and measure it during the hardware spike.

For the **ringtone export** none of this matters: the OS owns sync, and we only need to bake at **16.666 ms** (D10's tick range 16–100 ms already allows ~16 ms). Brightness maps 0–255 → 0–4095 (×4095/255). The response curve is unknown, so the same gamma question applies.

### A3. Audio-reactive / beat-synced keyframes

**Prior art:**
- **Glyphonic** (browser, Phone (4a) bar) [V, read `js/glyphFormat.js`]:
  - Splits the audio into bands with `OfflineAudioContext` biquad filters.
  - Takes 20 ms RMS energy windows and flags a hit when energy is above 1.4× the mean of the last 20 windows.
  - Keeps the local maximum within ±6 windows and enforces an 80 ms minimum gap.
  - It's about 40 lines and has no dependencies. It writes the Ogg comment itself (`oggComment.js`, `crc32.js`).
- **Better Nothing Glyph Composer**: auto-generate is in alpha. Its tests use metronome fixtures [V, README].
- **Live visualisers** (BeatFlare, Glyphix, better-nothing-music-visualizer) are FFT-on-mic or MediaProjection, so *live* rendering. D7 defers that [S].
- **TarsosDSP** (Java) has percussion onset, complex-domain onset and BeatRoot beat induction, but parts are **GPL** [V, README]. That conflicts with the TBD licence (D18). Avoid it, or decide on the licence first.

**On-device feasibility [E]:**
1. **Decode:** `MediaExtractor` + `MediaCodec` to PCM. Platform APIs, no dependency.
2. **Detect:** an energy or spectral-flux onset detector in pure Kotlin. It fits in the core module (D14), so it's also testable headlessly.
3. **Cost:** a 30 s clip at 44.1 kHz is ~1.3 M samples per channel. This is milliseconds to well under a second on a phone [E].
4. **Encode for export:** Ogg/Opus via `MediaMuxer.MUXER_OUTPUT_OGG` (API 29+ [V]) with the platform Opus encoder (availability on Nothing OS is [U]). Then rewrite the comment header ourselves. Glyphonic shows that's small.

**Product shape [E]:**
- **v1.x, "beat markers":** detected onsets appear as tick marks on the timeline ruler, and keyframes/clip edges snap to them. This needs no new model concept: markers are document metadata, and the agent can read them too.
- **Later, "generate from audio":** map onsets/bands to generator params (e.g. `ring_pulse` on each kick). This is where the hybrid model shines, but it needs UX and evaluation work.

### A4. Sound scope recommendation

| Feature | Scope | Why |
|---|---|---|
| Sound with notification-trigger animations | **Not v1; probably never default** | Muted in DND, silent mode and Flip to Glyph [V], and doubles the source app's sound when not muted |
| Export as **Glyph ringtone** (.ogg + embedded 60 Hz matrix data) for 4a Pro / Phone 3 | **v1.x** | Verified format, OS-owned sync, unique value, no special permission. Needs a hardware test of the install path and an Opus encode |
| Editor audio track: load audio, waveform, scrub, A/V-synced preview | **v1.x** (with ringtone export) | Needed to author a ringtone. Use Media3 with offload off and an audio-clock master |
| Beat markers + snapping | **v1.x** (late) | Small pure-Kotlin detector. Big authoring win |
| Auto-generate animation from audio | **Later** | Product and eval risk. Live reactive mode is deferred by D7 |
| Live music-reactive glyph | **Later** (D7) | Needs MediaProjection or mic permissions. Others already do it |

---

## Part B: Human editor UX on a phone

### B1. Survey

| App | Model / timeline | Notable UX | Praise / complaints |
|---|---|---|---|
| **pauwma Glyph Matrix Editor** (web, Phone 3 + 4a Pro) [V, README] | Frame-by-frame timeline, per-frame duration; once / reverse / ping-pong playback | Brush, eyedropper, fill, clear, invert, mirror, flip, rotate, selection move/copy/paste, undo; **onion skin with opacity**; text-to-pixel in 3 pixel fonts (Ndot, NType, Ranyth); emoji-to-pixel; image/GIF/video import with brightness, contrast and threshold; multi-project autosave; exports GIF/WebM/MP4/Lottie/JSON | Device switch wipes frames (prior research). The de-facto standard tool set for this medium |
| **Glyph Museum** app + web editor [S] | Frames; timing/speed per animation | **Real-time preview on the phone's own matrix**; event effects (unlock, charge, volume); 1–5 loops | Praised as "the app Nothing should have shipped". No tool detail public |
| **open-glyph** (kaelvalen, Android, Phone 3) [S: search snippet only; repo 404 today] | Draw then send to the matrix via the Glyph button | Symmetry modes, transforms, 40-step undo, 3×5 bitmap text, effects generator, dithered image import | Treat as unverified |
| **Dotpict** [S] | Grid pixel art, light animation | **Offset cursor mode** so the finger doesn't hide the pixel, plus a "pen/finger trace" mode | "Clean … doesn't drown you in tools", snaps to grid |
| **Pixel Studio** [S] | Layers + frame animation, GIF/MP4, music on export | Mobile-first | 93% positive on Steam; complaints about paywalled features and file management |
| **Pixaki** (iPad) [S, vendor] | Per-layer timelines, static layers, cels (Aseprite-compatible) | Pinch zoom to 250×; onion skin up to 10 frames with 3 tint modes; drag-reorder frames | Price; palette UI eats canvas space |
| **FlipaClip** [S] | Frame strip + layers | Simple frame-by-frame | Simplicity praised. Onion skin bugs are the #1 forum complaint (shows the wrong frame); layer deletion not undoable; paywalled layers; small-screen strain |
| **RoughAnimator** [S] | Unlimited layers, per-drawing exposure | Onion skin before/after counts in the Tools panel | "Clunky", "overwhelming for beginners" |
| **Procreate Dreams** [V, handbook] | Tracks; **Compose / Perform / Keyframe** modes; Flipbook content | Compose mode **hides keyframe tracks**; dragging the timeline handle **hides the timeline and enters Flipbook** (full-screen frame-by-frame); tap-and-hold *between* keyframes to set that segment's easing; slide on the ruler to zoom time | A strong model for our two-mode idea |
| **Alight Motion** [S, mostly SEO sites] | Layers + keyframes + bezier graph editor | Defaults to linear easing | "Steep learning curve", "fiddly on a phone screen" |
| **CapCut** [S] | Clip timeline | **One ◆ button at the playhead**: tap to add a keyframe, change a property to auto-create the next, tap again on a keyframe to delete | Simplest mobile keyframe UX found. Button placement varies by version |

**Patterns worth copying [E, from the table]:**
- A frame strip with onion skin as the beginner surface.
- A ◆-at-playhead keyframe button (CapCut).
- Easing set by tapping a keyframe *segment* (Procreate Dreams).
- Presets instead of curve editors.
- Undo on *everything*, including layer delete (the FlipaClip complaint; D9 covers this).
- Onion skin correctness must be tested (FlipaClip's most common bug).

### B2. Painting a 13×13 circle by finger

**Size math [E, using 1 dp = 1/160 in ≈ 0.159 mm]:**

| Grid | Canvas width | Cell size | Evidence-based target | Verdict |
|---|---|---|---|---|
| 13×13 (4a Pro) | ~340 dp | ≈ 26 dp ≈ 4.1 mm | 9.2 mm discrete / 9.6 mm serial; no significant error difference at ≥ 7.7 mm for serial taps (Parhi, Karlson & Bederson, MobileHCI 2006 [S, abstract]). Android: ≥ 48 dp [V] | Single taps will miss sometimes; strokes are fine |
| 25×25 (Phone 3) | ~340 dp | ≈ 13.6 dp ≈ 2.2 mm | Same as above | Zoom is mandatory |

**Mitigations (cheap to expensive) [E]:**
1. **Circle-aware hit-test.** Masked cells aren't targets, so a touch maps to the nearest *real* LED centre. It's free accuracy at the rim.
2. **Paint on stroke, not on tap-up.** Drag across cells; the whole stroke is one undo step (D9).
3. **Pinch zoom + two-finger pan** on the canvas. The default fits the width; 2× gives 52 dp ≈ 8.3 mm cells, inside the Parhi range.
4. **Loupe / offset cursor** (Dotpict; "Shift" by Vogel & Baudisch, CHI 2007 [U, not re-fetched]) as an opt-in "precise mode". This is v1.x.
5. **Feedback:** the cell under the finger is highlighted with a ring, plus a soft haptic tick on entering a new cell (see B5).

**Tools:**

| Tool | v1? | Notes |
|---|---|---|
| Paint (brush 1 px) | ✅ | Uses the current brightness |
| Paint-off | ✅ | D6: alpha 255, brightness 0. "This LED is forced off" |
| Erase | ✅ | D6: alpha 0, transparent; lower layers show through |
| Fill (flood) | ✅ | Respects the mask |
| Line | ✅ | Bresenham. Drag start to end with a live preview |
| Shapes: rect, ellipse/circle (outline/filled) | ✅ circle, rect | Circle is *the* glyph shape. Snap the centre to (6,6) on 13×13 |
| Eyedropper | ✅ | Long-press on canvas = pick (no tool switch) |
| Mirror symmetry (H, V, both, 4-way radial) | ✅ H/V/both | Cheap. Huge win on a round grid. Radial (rotational) symmetry is v1.x |
| Transforms: flip, rotate 90°, shift/nudge with wrap | ✅ | pauwma parity |
| Text | v1.x | Bitmap font (3×5 / 5×7). On 13 px, text is a scrolling generator, which ties into templates |
| Selection move/copy/paste | v1.x | Hard on 137 px with fingers. Frame-level copy is enough at first |
| Brush sizes, dithering, gradients | Later | Generators cover gradients better |

**Brightness (0–255):**
- **Swatches:** 5 presets, Off, 25%, 50%, 75%, 100% (0, 64, 128, 191, 255), always visible.
- **Custom:** a slider in a sheet for exact values.
- **Previews:** the swatch preview shows what each value looks like **on the LED** after gamma (brightness curve still to be measured; spike).
- **Why presets:** at 13×13, LED brightness differences below ~10% are hard to perceive [U: needs the hardware brightness-curve measurement]. Presets also make the agent's 16-level text view and human edits line up.
- **Recent values:** also offer "recently used" values (pauwma has an eyedropper; Pixel Studio uses palettes [S]).

**Alpha display (D6):**
- **Transparent** = no fill, small grey centre dot or checker hint.
- **Off** = solid black disc with a thin ring.
- **Lit** = glow.
- This keeps transparent and off distinguishable without relying on colour (B5).
- In Frames mode with one layer, the difference rarely matters. Show it subtly and fully only in Timeline mode [E].

### B3. Progressive disclosure: two modes over one model

Evidence:
- Procreate Dreams separates **Flipbook** (frames, no timeline) from **Timeline** (tracks), and hides keyframe tracks except in Keyframe/Perform modes [V].
- Simple frame-by-frame apps are praised for simplicity (FlipaClip, Dotpict [S]); keyframe-rich apps draw "steep learning curve" complaints (Alight Motion, RoughAnimator [S]).
- Pixaki's Intro tier caps layers and frames [S]. That's monetisation, but it shows a "small first" surface.

**Proposal [E]:**
- **Frames mode (default for new docs):**
  - Edits one raster clip on one layer: frame strip, per-frame duration, onion skin, playback modes.
  - An **Effects** button adds a *generator* as a new layer.
  - If the doc has more than one layer or clip, Frames mode shows a banner: "This animation has 3 layers. Open timeline". It never hides content.
- **Timeline mode (opt-in toggle in the top bar, remembered per doc):**
  - Layers as rows, clips as blocks, a playhead and a ◆ keyframe button.
  - Tapping between two ◆ opens the easing presets from our small named set. `cubicBezier` lives in an "Advanced" disclosure.
  - Clip sheet: in/out transitions, blend mode (Normal/Lighten, D6), speed.
- **Both modes are just views over the same document and command layer (D2).** Switching changes nothing, so there's no conversion and no data loss.
- The **agent works in either**. Its edits can create layers; Frames mode then shows the banner.
- **What stays hidden in Frames mode:** blend modes, keyframes, transitions, generator params (beyond the Effects sheet's simple sliders), and tick settings.
- **When to promote the user [E]:** offer "Try timeline" after they add a second effect, or when they long-press a frame's duration repeatedly. Never auto-switch.

### B4. Preview on screen vs on the physical LEDs

- **On-screen preview is primary.** Render round LED dots with glow, gamma-corrected so they look like the hardware, on a black background, plus a "LED look" toggle (glow on/off). Pauwma's Lottie preview style (dot grid, circle mask, off colour `#1a1a1a`) is precedent (prior research).
- **"▶ LEDs" button:** plays the whole animation (or loop N×) on the matrix after a **3 s countdown with haptic ticks**, so the user can flip the phone. A tap anywhere or a flip-back (proximity/accelerometer [U]) stops it. The flash-safety check runs first (existing UX research).
- **"Mirror" toggle (opt-in):** pushes the *current frame* to the LEDs on every edit, useful with a mirror or a second person. It must be throttled, and it must stop on background and release the matrix (`closeAppMatrix`) [V, prior research].
- **Constraints:** `setAppMatrixFrame` may conflict with active Glyph Toys. The 4a Pro only supports AOD toys (prior research). Behaviour when an AOD toy is active is a spike item.
- **Glyph Museum** offers a "real-time preview on your own phone's matrix" [S], so users will expect at least the ▶ LEDs button.

### B5. Accessibility basics (Compose)

- **TalkBack on a Canvas grid:** a `Canvas` is one semantics node, invisible to TalkBack unless we add semantics. Option chosen [E]: make the grid a single focusable node with a **virtual cursor**:
  - The `contentDescription` reads "Row 4, column 7, 50 percent, layer Heart".
  - Moving and painting use `customActions` (Move up/down/left/right, Paint, Paint off, Erase, Read row). The `customActions` pattern is [V, Compose semantics docs].
  - The alternative of 137 separate nodes is navigable but slow. The agent's ASCII grid view also gives blind users an alternative authoring path.
- **Targets:** all non-canvas controls ≥ 48 dp [V]. Material components enforce this; custom ones (swatches, frame thumbnails, ◆) must set the size themselves.
- **Contrast:** text ≥ 4.5:1 (small) / 3:1 (large) [V]. LED states must be distinguishable without colour or brightness alone (B2 shapes).
- **Haptics:**

| Constant | Use |
|---|---|
| `SEGMENT_FREQUENT_TICK` (API 34, "very soft … many in quick succession") [V] | Each new cell entered during a stroke |
| `CLOCK_TICK` (API 21) [V] | Frame changes while scrubbing |
| `CONFIRM` (API 30) [V] | Save / export done |
| `GESTURE_THRESHOLD_ACTIVATE` (API 34) [V] | Drag-to-reorder thresholds |

  Fall back gracefully below API 34, and respect the system haptics setting.
- **Motion:** honour the system "remove animations" setting for UI chrome, and keep the existing flash warning before LED playback.
- **Testing:** use Accessibility Scanner plus Compose UI tests with semantics matchers [U: standard tooling].

### B6. Proposed v1 editor (MVP) vs later

**v1 (MVP):**
- Library: list, new, duplicate, delete, import/export (`.glyphwiz`, GM JSON, GIF import).
- Frames mode, complete: the B2 v1 tools, 5 brightness swatches + slider, eyedropper, mirror H/V, onion skin (±1, opacity), frame strip (add / duplicate / delete / reorder / per-frame duration), playback once/loop/ping-pong, speed 0.5–2× (D10).
- Effects sheet: the first 3–5 generators with simple sliders. Each adds a layer.
- Timeline mode, minimal: layers (add/reorder/hide/delete), clip move/trim, ◆ keyframes for opacity and generator params, preset easings, Normal/Lighten.
- Pinch zoom + pan, circle-aware hit-test, undo/redo (D9) with authorship badges.
- ▶ LEDs with countdown; "LED look" preview; flash warning.
- A11y: grid cursor semantics, 48 dp targets, haptics.
- The agent sheet (from the UX research) docks below.

**v1.x:**
- Audio track + ringtone export.
- Beat markers.
- Offset-cursor precise mode.
- Text tool.
- Radial symmetry.
- Selection.
- Mirror-to-LEDs live toggle.
- Onion skin ±N with tint.

**Later:**
- Generate from audio.
- `cubicBezier` curve editor.
- Brush sizes and dithering.
- Per-clip speed.
- Live/reactive content (D7).

**Text wireframes (portrait):**

```
① Library
┌──────────────────────────────────┐
│ GlyphWizard                 ⚙  ＋ │
│ ┌──────┐ ┌──────┐ ┌──────┐       │  looping 96dp thumbnails
│ │ ◯◉◯ │ │ ♥   │ │ ≋   │       │
│ └──────┘ └──────┘ └──────┘       │
│ Heartbeat   Ring    Wave         │
│ [Import]                         │
└──────────────────────────────────┘
```

```
② Editor: Frames mode (default)
┌──────────────────────────────────┐
│ ◀ Heartbeat   [Frames|Timeline] ⋯│  ⋯ = export, settings
│ ┌──────────────────────────────┐ │
│ │        ● ● ● ● ●             │ │  canvas: pinch zoom, 2-finger pan
│ │     ●  ◉ ◉ ○ ◉ ◉  ●          │ │  ◉ lit  ● off  ○ transparent
│ │    ... 13×13 circle ...      │ │  ghost = onion skin (prev frame)
│ └──────────────────────────────┘ │
│ ✎ ⊘ ⌫ ▧ ╱ ◯ ⇋      ↶ ↷        │  paint/off/erase/fill/line/shape/mirror
│ [■0][■25][■50][■75][■100][⋯]     │  brightness swatches (+slider)
│ ┌─┐┌─┐┌─┐┌─┐┌─┐ ＋             │  frame strip, long-press = menu
│ │1││2││3││4││5│                  │  (duplicate, delete, duration)
│ └─┘└─┘└─┘└─┘└─┘  100ms          │
│ ▶  ⟲loop  ½× 1× 2×  👻  ✨Effects  ▶LEDs │
└──────────────────────────────────┘
```

```
③ Editor: Timeline mode
┌──────────────────────────────────┐
│ ◀ Heartbeat   [Frames|Timeline] ⋯│
│ ┌ canvas (shrinks to ~40%) ────┐ │
│ └──────────────────────────────┘ │
│ 0s     0.5     1.0     1.5   ◆   │  ruler: slide = zoom time; ◆ = key @ playhead
│ L2 ring   ░░░[ring_pulse]░░░     │       │
│ L1 heart  [▓▓ raster ▓▓]◆───◆    │  playhead │ ; tap between ◆ = easing
│ ＋ layer                          │
│ ▶  00:00.84 / 01.80   ▶LEDs      │
└──────────────────────────────────┘
```

```
④ Clip sheet (tap a clip)
┌──────────────────────────────────┐
│ ring_pulse@1          ⋯ delete   │
│ Speed      ━━━━●━━  1.2  ◆       │  ◆ = keyframe this param
│ Width      ━━●━━━━  0.3  ◆       │
│ In:  [none|fade|wipe] 200ms      │
│ Out: [none|fade|wipe]            │
│ Blend: [Normal|Lighten]          │
└──────────────────────────────────┘
```

```
⑤ ▶LEDs flow
"Flip your phone, playing in 3… 2… 1…" (haptic each tick)
→ plays N loops → haptic CONFIRM → screen: [Play again] [Back]
```

```
⑥ v1.x Export → Glyph ringtone
┌──────────────────────────────────┐
│ Export as Glyph ringtone         │
│ Audio: [Pick file…] song.mp3 0:12│
│ Device: Phone (4a) Pro (169 cols)│
│ Length: audio 12.0s / anim 1.8s  │
│   [Loop animation ✓]             │
│ [Save to Ringtones/Compositions] │
│ Then: Settings → Glyph Interface │
│       → Ringtones → pick it      │
└──────────────────────────────────┘
```

---

## Recommended scope (combined)

**v1:**
- The B6 MVP editor (Frames-first + minimal Timeline).
- On-screen and ▶LEDs preview.
- A11y basics.
- **No sound.**

**v1.x:**
- Audio track in the editor (Media3, audio-clock master, offset calibration).
- **Glyph ringtone export** (Ogg/Opus + `AUTHOR`/`CUSTOM2`/`COMPOSER` tags, 16.666 ms bake, 0–4095 mapping).
- Beat markers + snapping.
- Precise-cursor mode, text tool, selection.

**Later:**
- Generate-from-audio.
- Live audio-reactive mode (D7).
- Optional per-rule alert sound (opt-in, `USAGE_NOTIFICATION_EVENT`), only if users ask.

## Risks

1. **Composition format is reverse-engineered.** Nothing could change the tags or reject `169cols` files. Mitigation: golden-file tests against SebiAi outputs, and the feature lives in its own module.
2. **Install path on the 4a Pro is unverified.** Whether a matrix composition plays when chosen via Glyph Interface vs the Android picker is unknown. A hardware test is a gate before building.
3. **The Opus encoder may be missing.** MediaCodec Opus encoding availability on Nothing OS is [U]. The fallback is a bundled encoder (licence and size cost).
4. **Glyph data length vs audio length.** "If the data is longer than the audio it will not be played" [V]. Validate on export.
5. **Finger accuracy.** 26 dp cells are below the guidelines. If user testing shows frustration, promote the precise mode to v1.
6. **Two-mode confusion.** Users may not find Timeline, or may be surprised by layers the agent created. Mitigation: the banner, plus the same document in both views.
7. **Onion-skin and playback correctness.** This is FlipaClip's top complaint. Add golden-frame tests.
8. **Licence contamination** from GPL DSP libraries (TarsosDSP). Write our own small detector.
9. **LED conflicts.** Mirror/▶LEDs may conflict with active AOD toys. Spike item.

## Open user questions

1. Is a **Glyph ringtone export** wanted at all? It's the only sound feature with clear value. If so, is v1.x acceptable, or is it a v1 must-have?
2. OK to **drop sound for notification triggers** (DND, silent mode and doubling make it poor)?
3. **Default mode for new documents:** Frames (recommended) or Timeline?
4. **Brightness presets:** 5 levels (0/25/50/75/100%), or 4 / 8? It should match the agent's quantisation.
5. Is **Phone (3) (25×25)** editor support a v1 target? It forces zoom and the precise cursor earlier.
6. **TalkBack depth:** is a virtual-cursor grid acceptable for v1, or is a11y v1.x?
7. Should "▶ LEDs" also offer a **live mirror** in v1?

---

## Learning asides (D23)

- **Compose Canvas.** `Canvas`/`drawBehind` give you a `DrawScope` to draw circles, rects and paths each frame. That's how the 13×13 LED grid gets painted: one composable, 137 `drawCircle` calls, recomposed when the frame changes. [Graphics in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview)
- **Pointer input & gestures.** `Modifier.pointerInput { detectDragGestures / detectTapGestures }` turns touches into strokes, and `transformable` / `detectTransformGestures` handle pinch-zoom and pan. You map touch offsets to grid cells yourself. [Understand gestures](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures) · [Multi-touch](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch)
- **Semantics / TalkBack.** Compose builds a semantics tree that accessibility services read. Custom-drawn things need `Modifier.semantics { contentDescription; customActions }` or they're invisible to TalkBack. [Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) · [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)
- **Haptics.** `View.performHapticFeedback(HapticFeedbackConstants.X)` asks the system for a named, tuned vibration. Prefer it over raw `Vibrator` patterns because it respects user settings. [Haptics overview](https://developer.android.com/develop/ui/views/haptics) · [HapticFeedbackConstants](https://developer.android.com/reference/android/view/HapticFeedbackConstants)
- **AudioAttributes & DND.** Every sound declares a *usage* (notification, alarm, media…). The system applies volume streams, DND and silent mode **by usage**, not by which player you use. [AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes) · [NotificationManager.Policy](https://developer.android.com/reference/android/app/NotificationManager.Policy)
- **Audio focus.** Apps "request focus" before playing so others duck or pause. A short alert would request transient, may-duck focus. [Manage audio focus](https://developer.android.com/media/optimize/audio-focus)
- **SoundPool / MediaPlayer / Media3 ExoPlayer.**
  - SoundPool: tiny pre-decoded clips with instant start.
  - MediaPlayer: simple, legacy.
  - Media3 ExoPlayer: the modern, recommended player for anything longer.
  - Sync anything visual to the player's position, never to a separate timer. [SoundPool](https://developer.android.com/reference/android/media/SoundPool) · [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer)
- **Frame clock.** `withFrameNanos` (Compose) / `Choreographer` call you once per display refresh. That's where you read the audio position and render frame(t). [withFrameNanos](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary#withFrameNanos)
- **MediaExtractor / MediaCodec / MediaMuxer.**
  - MediaExtractor: reads compressed audio out of a file.
  - MediaCodec: decodes it to PCM samples for beat detection, or encodes PCM to Opus.
  - MediaMuxer: writes an Ogg container (API 29+).
  [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) · [MediaMuxer.OutputFormat](https://developer.android.com/reference/android/media/MediaMuxer.OutputFormat)
- **MediaStore.** Under scoped storage, apps save shared files (e.g. into `Ringtones/`) by inserting into a MediaStore collection rather than writing paths. No permission is needed for your own files. [Access media files](https://developer.android.com/training/data-storage/shared/media)
- **Ogg Vorbis comments.** Ogg files carry `KEY=value` text tags in a header packet. Nothing hides glyph data in them. Editing them means rewriting that header packet and its page CRCs, a small, well-documented job. [Xiph Vorbis comment spec](https://xiph.org/vorbis/doc/v-comment.html) · [Opus in Ogg, RFC 7845](https://www.rfc-editor.org/rfc/rfc7845)

---

## Sources

**Nothing / composition format**
- SebiAi custom-nothing-glyph-tools: [repo](https://github.com/SebiAi/custom-nothing-glyph-tools) · [Technical Details](https://github.com/SebiAi/custom-nothing-glyph-tools/blob/main/docs/9_Technical%20Details.md) · [Compositions for Glyph Matrix](https://github.com/SebiAi/custom-nothing-glyph-tools/blob/main/docs/6_Creating%20compositions%20for%20Glyph%20Matrix.md) · `GlyphModder.py` (opus check) [V]
- Glyphtones guide: https://glyphtones.firu.dev/guide [V]
- Nothing support, Flip to Glyph: https://support.nothing.tech/hc/en-us/articles/22960523298833-What-is-the-Flip-to-Glyph-feature [V via search] · ringtone Glyph light: https://support.nothing.tech/hc/en-us/articles/33048353090961-How-do-I-adjust-the-Glyph-light-for-ringtones [S]
- 9to5Google Glyph guide: https://9to5google.com/2023/07/17/ultimate-guide-nothing-phone-2-glyph-interface/ [S]
- Better Nothing Glyph Composer: https://github.com/Krishnagopal-Sinha/better-nothing-glyph-composer [V README] · https://better-nothing-glyph-composer.pages.dev/
- Glyphonic: https://github.com/Zafin-Mohamed/glyphonic (`js/glyphFormat.js` read) [V]
- SebiAi GlyphVisualizer: https://github.com/SebiAi/GlyphVisualizer [S]
- Visualisers: https://github.com/stilkin/beatflare · https://github.com/Aleks-Levet/better-nothing-music-visualizer · https://github.com/oliver-lebaigue-bright-bench/glyphix [S]
- GLYPH Studio: https://play.google.com/store/apps/details?id=tech.abra.glyphstudio · Glyphify: https://play.google.com/store/apps/details?id=com.frank.glyphify [S]
- TarsosDSP: https://github.com/JorenSix/TarsosDSP [V README]

**Android platform**
- AudioAttributes: https://developer.android.com/reference/android/media/AudioAttributes [V]
- AOSP `ZenModeHelper.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/ZenModeHelper.java [V]
- AOSP `AudioAttributes.java` (`SUPPRESSIBLE_USAGES`): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioAttributes.java [V]
- NotificationManager.Policy: https://developer.android.com/reference/android/app/NotificationManager.Policy [V]
- SoundPool: https://developer.android.com/reference/android/media/SoundPool [V] · AudioTrack: https://developer.android.com/reference/android/media/AudioTrack [V] · MediaPlayer: https://developer.android.com/reference/android/media/MediaPlayer [V]
- Audio latency: https://developer.android.com/ndk/guides/audio/audio-latency [V]
- ExoPlayer buffer issue: https://github.com/google/ExoPlayer/issues/8970 · timestamp issue: https://github.com/google/ExoPlayer/issues/3830 · Qualcomm offload: https://www.qualcomm.com/developer/blog/2023/08/audio-offload-support-exoplayer [S]
- RingtoneManager: https://developer.android.com/reference/android/media/RingtoneManager [V] · shared media: https://developer.android.com/training/data-storage/shared/media [V] · MediaMuxer.OutputFormat: https://developer.android.com/reference/android/media/MediaMuxer.OutputFormat [V]
- Accessibility (48 dp, contrast): https://developer.android.com/guide/topics/ui/accessibility/apps [V] · Compose semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics [V] · multi-touch: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch [V] · HapticFeedbackConstants: https://developer.android.com/reference/android/view/HapticFeedbackConstants [V]

**Editors / UX**
- pauwma Glyph Matrix Editor: https://github.com/pauwma/GlyphMatrixEditor (README) [V] · https://glypheditor.pauwma.com/
- Glyph Museum: https://play.google.com/store/apps/details?id=com.pauwma.glyphmuseum · https://glyphmuseum.com/guides/how-to-customize-nothing-phone-glyph-matrix · https://nothingtec.com/en/2025/11/10/glyph-museum-nothing-phone-3/ [S]
- open-glyph: https://github.com/kaelvalen/open-glyph [S; 404 on 2026-10-11]
- Procreate Dreams Timeline & Modes: https://help.procreate.com/dreams/handbook/interface-and-gestures/timeline [V] · Flipbook mini: https://help.procreate.com/dreams/handbook/draw-and-paint/flipbook-mini [S]
- Dotpict: https://play.google.com/store/apps/details?id=net.dotpicko.dotpict [S]
- Pixel Studio: https://play.google.com/store/apps/details?id=com.PixelStudio · Steam reviews: https://steamcommunity.com/app/1204050/reviews/ [S]
- Pixaki: https://pixaki.com/ [S, vendor]
- FlipaClip: https://filmora.wondershare.com/video-editor-review/flipaclip-review.html · onion-skin forum: https://flipaclip.userecho.com/communities/1/topics/4392-onion-skips-the-frame [S]
- RoughAnimator guide: https://www.roughanimator.com/userguide-tablet/ [S]
- Alight Motion: https://reviewed.app/app/alight-motion/ [S, low confidence]
- CapCut keyframes: https://videowizardtools.com/keyframes-in-capcut/ [S, low confidence]
- Parhi, Karlson, Bederson 2006: https://www.microsoft.com/en-us/research/wp-content/uploads/2006/01/parhi-mobileHCI06.pdf [S, abstract] · NN/g touch targets: https://www.nngroup.com/articles/touch-target-size/ [S]
- Vogel & Baudisch, "Shift", CHI 2007 [U, not re-fetched]
