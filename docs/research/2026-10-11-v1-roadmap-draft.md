# GlyphWizard v1 Scope & Roadmap — Draft v0.1

Status: **DRAFT**, for user review, then adversarial review r2 together with agent-architecture draft v0.2. 2026-10-11.

Inputs:
- `decisions.md` (D1–D23)
- All `2026-10-11-*-research.md` reports
- `2026-10-11-agent-architecture-draft.md` + review r1 + targeted reports

## v1 definition of done

A Nothing Phone (4a) Pro owner can:
1. Create, edit, organise, import, and export **unlimited** animations: static, frame-by-frame, and timeline (layers, generator clips, keyframes, easing, transitions).
2. Preview them on screen and **play them on the physical Glyph Matrix**.
3. Assign animations to **triggers**: app notifications, specific contacts, keywords, incoming calls. Options: priority, repeat count / until-unlock, and respect DND.
4. **Chat with an in-app agent** (own key / endpoint) that designs and edits animations with the same commands the UI uses. Supported models are listed from eval results.
5. Install from **GitHub Releases via Obtainium**. The app is open source (licence TBD).

Phone (3) works where the device-agnostic model allows. It is not a v1 test target.

## Milestones

Each milestone ends with something you can run, and teaches a bounded set of Android concepts (D23). Learning concepts are listed per milestone; asides with links go in the implementation plans.

### M0 — Setup & hardware spike *(blocks everything; throwaway code allowed)*
- Dev tooling: SDK env, adb, Android CLI, optional mobile-mcp [dev-tooling report].
- Fetch the SDK `.aar` at build time (never committed) [D18].
- Minimal app: bind the Glyph SDK, push test patterns, plus a **debug glyph mirror** (text grid + PNG dump via adb).
- **Measure / verify** (consolidated from all reports):
  - frame `int[]` ordering and length (169 full-square vs 137?)
  - brightness range (255 vs **2047** raw?) and response curve
  - minimum visible level
  - max sustained frame rate
  - glow / bleed
  - physical diameter and luminance (flash safety)
  - behaviour when locked, face-down, face-up, pocketed
  - priority vs Essential Notifications / AOD toy
  - NotificationListener bind + "Restricted settings" with Obtainium installs
  - backgrounding of a streaming request
  - glyph-ringtone install path (for v1.x)
- Output: `docs/research/hardware-spike-results.md`. Decisions affected: D6 brightness scale, D10 tick ceiling, the flash check.
- Learning: Android Studio, Gradle basics, adb, manifest, permissions, services (binding).

### M1 — `:core` engine *(pure Kotlin/JVM, no phone needed)*
- Document model:
  - layers
  - raster clips (brightness + alpha)
  - generator clips with versioned registry, 3–5 generators
  - keyframes + easing, in/out transitions
  - Normal / Lighten blend modes
  - DeviceProfile
- Command layer with validation and stable IDs; history (binary frames, gzip, compressed cap, turn groups) [D9 as revised by targeted-editing].
- Renderer `render(doc, t, inputs)`, baker with tick and speed [D10], templates [D7].
- Glyph Museum import / export; `.glyphwiz` format with `schemaVersion` + migrations.
- Golden text-grid tests.
- Learning: Kotlin idioms, kotlinx.serialization, coroutines basics, Gradle multi-module.

### M2 — App shell + Frames-mode editor
- Compose app with a manual DI container, document list (files), import / export via the system picker, "open with".
- Frames mode:
  - grid with LED-snap taps and pinch zoom
  - Paint / Paint-off / Erase
  - 5 brightness presets + slider
  - frame strip, onion skin, playback
- **▶ LEDs** button with countdown.
- **Image / GIF → glyph import** [D25]:
  - system photo picker
  - crop / fit to circle
  - luminance + area-average downscale
  - levels: brightness, contrast, gamma, threshold, invert
  - live preview → raster clip; animated GIF / WebP → frames
- Learning: Compose, state / ViewModel / StateFlow, navigation, storage, the Storage Access Framework.

### M3 — Timeline mode
- Layers, clips, generator parameter UI, keyframes with easing presets, transitions.
- Persistent undo / redo with turn cards (later shared with the agent).
- Flash-risk warning.
- Learning: custom Compose layouts / Canvas, gestures, performance.

### M4 — Triggers
- NotificationListener; rules:
  - filter by app / contact (system picker) / keywords
  - repeat / until-unlock
  - priority, respect DND
- Calls via the dialer's notification.
- Player queue with expiry, dedupe, and calls first.
- One gravity-sensor reading at event time; SDK bound once.
- Template inputs filled at trigger time [D7].
- Rules UI.
- Learning: NotificationListenerService, permissions UX, Doze, sensors, DataStore.

### M5 — Agent
- `:agent` with a thin loop and providers: Chat Completions + Anthropic; OpenAI via OpenRouter.
- Defensive stream parser, tested with recorded streams.
- Keystore key storage; endpoint presets: OpenRouter, ClinePass, custom, LAN with cleartext guard.
- Base-tier tools (~10) plus tier filter [D19]; text-grid view + analysis tools.
- Core context + 4 skills; epoch compaction.
- Short foreground service per turn with pause / resume UX; spend caps, traces, probe.
- Learning: networking (Ktor / OkHttp), foreground services, secure storage.

### M6 — Evals, polish, release
- `:evals` JVM CLI: probe + ~20 tasks; dated `SUPPORT.md`.
- Licence, README, CONTRIBUTING, NOTICE.
- GitHub Actions CI: build, test, lint; signed release on tag; Obtainium.
- Accessibility pass.
- Learning: CI/CD, signing, release.

**Order rationale:** M0 removes unknowns that change the model. M1 is testable without a phone. Triggers (M4) come before the agent (M5) because they make the app usable every day sooner and have fewer unknowns; the agent then builds on a stable command layer.

## v1.1 and later (explicitly out of v1)
- **v1.1:**
  - AOD Glyph Toy
  - glyph-ringtone export
  - audio track + beat markers
  - variants (generator-param only, top tier)
  - full-tier tools + vision tier
  - Responses adapter
  - Phone (3) testing
- **Later:**
  - live / continuous content (clocks, music)
  - 25↔13 conversion UI
  - external agents via allowlisted MCP + headless emulator [D14]
  - small-model guided tier
  - personal context for the agent
  - audio → animation generation
  - sharing / hosting
  - Play Store (needs a reporting channel)

## Open questions for the user (ask one at a time)
1. Approve the v1 definition of done and the M0–M6 order?
2. Licence: GPL-3.0-or-later + SDK exception + NOTICE (recommended) vs alternatives.
3. Permanent application ID (e.g. `dev.<handle>.glyphwizard`).
4. Theme / font direction.
5. Remaining items from the research reports (spend caps, probe trigger, models.dev refresh, epoch compaction, tier thresholds, Synthetic preset, etc.).
