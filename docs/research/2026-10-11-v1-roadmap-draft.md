# GlyphWizard v1 Scope & Roadmap — Draft v0.2

Status: **DRAFT for review r3**. 2026-10-11.

Changes since v0.1: review r2 fixes; order A chosen (daily use first); no deadlines [D38]. Each milestone has quality exit criteria instead of time estimates.

Inputs: `decisions.md` (D1–D38), all `2026-10-11-*` research reports, agent draft v0.3, reviews r1 and r2.

Tags:
- **[P]** = proposal
- **[E]** = evidence-backed
- **[HW]** = settled by measurement

## v1 definition of done (testable)

On a Nothing Phone (4a) Pro (Android 16), the app lets you:

1. **Create, edit, organise, import and export animations** with no app-imposed count limit. Test: 500 documents can be listed, opened and saved within the performance budgets below.
   - Frames mode: static and frame-by-frame.
   - Timeline mode: layers, generator clips, keyframes, easing, transitions.
   - Glyph Museum import and export round-trips losslessly.
   - Image and GIF import works.
2. **Preview on screen and play on the physical Glyph Matrix** (full build), behind the flash-safety gate [D37].
3. **Assign animations to triggers.** Sources: app notifications, contacts (via the system picker), keywords, incoming calls. Rule options: priority, repeat / until-unlock, respect DND. Additional trigger kinds are an open question (Q-T1).
4. **Use an in-app agent** with your own endpoint. It designs and edits through the shared command layer. Models are labelled Supported / Below bar / Untested [D27, D30].
5. **Install from GitHub Releases via Obtainium.**
   - The `sdkFree` build is always published.
   - The full build is published once Nothing grants permission [D26].
   - The app is open source; the licence is decided in M0.

The Phone (3) is out of v1 test scope. The model stays device-agnostic [D3] and is checked only by `:core` unit tests on a 25×25 profile.

## Quality gates (apply to every milestone) **[P]**

- **Tests:**
  - `:core`/`:agent` logic is unit-tested.
  - Property tests for the command layer and undo: `apply∘inverse = id`, plus interleaved-edit cases.
  - Golden text-grid tests for rendering.
- **Static checks:** ktlint and Android Lint pass with zero warnings at the agreed severity. No suppressions without a comment explaining why.
- **Performance budgets** (measured, not assumed; revised after M0 [HW]):
  - render + bake of a 5 s, 13×13 animation: < 50 ms on device
  - editor interaction: no frame over 16 ms (JankStats)
  - trigger event → first LED frame: < 150 ms
  - cold start: < 1 s
- **Review:** each milestone ends with a code review (`/code-review`) and its findings resolved.
- **Docs:**
  - every new concept gets a learning aside in its plan [D23]
  - `decisions.md` is updated
  - user-facing text lives in string resources [D34]
- **Commits:** follow the format in memory; one branch per milestone, merged after review.

## Milestones (order A, D-chosen 2026-10-11)

### M0 — Foundations & hardware spike
- **Legal / identity:**
  - Licence decision, LICENSE + NOTICE.
  - Permanent application ID.
  - Nothing email sent (parallel task) [D26].
- **Scaffold migration:**
  - `com.example` → the real ID.
  - minSdk 35 / target 36 / compile 37 [D31].
  - Views → Compose.
  - Java 17.
  - Module skeleton: `:core`, `:agent`, `:glyph-sdk`, `:app`, `:evals` [AF].
  - `sdkFree` / `full` flavours; SDK fetched at build time with a pinned SHA-256 [D18].
- **CI skeleton:** GitHub Actions (build, test, lint).
- **Spike** (throwaway app code in its own module or branch):
  - Bind the SDK and push test patterns.
  - **Debug glyph mirror:** a text-grid log, plus a PNG dump via adb [dev-tooling].
- **Measure [HW]:**
  - frame array layout (GDK: 13×13; check 137-vs-169 acceptance and ordering)
  - brightness range (255 vs 2047 raw) and response curve at ≥ 4 levels
  - minimum visible level
  - max sustained fps
  - PWM / rise time
  - glow and bleed
  - diameter / pitch
  - behaviour when locked / face-down / face-up / pocketed
  - priority vs Essential Notifications and AOD toy
  - NotificationListener process state; listener recovery after a forced crash [D28]
  - "Restricted settings" with Obtainium installs
  - glyph-ringtone install path (for v1.1)
- **Luminance** needs a light meter or a calibrated camera. If neither is available, the flash check uses conservative defaults, marked provisional **[P]**.
- **Exit criteria:**
  - `docs/research/hardware-spike-results.md` with data
  - D6 / D10 / D37 parameters confirmed or amended
  - CI green on an empty multi-module build
  - the app installs with the real ID

### M1 — `:core` engine (pure Kotlin / JVM)
- **Model:**
  - DeviceProfile, layers
  - raster clips (brightness + alpha)
  - generator registry (versioned) with the first generators (Q-G1)
  - keyframes + easing, transitions, Normal / Lighten blend modes
- **Commands:** validation with self-correcting error messages; stable IDs.
- **History:** frame-granular binary diffs, gzip, caps, selective-undo guards [D29, D36].
- **Rendering:**
  - renderer `render(doc, t, inputs)`
  - baker (tick + speed) [D10]
  - templates [D7]
- **Analysis:** centering, mirror symmetry, lit count / orphans, loop seam, **flash** [D37].
- **I/O:**
  - text-grid codec
  - Glyph Museum import / export
  - `.glyphwiz` with `schemaVersion` + migration framework
  - image-import pipeline core: decode-agnostic luminance → area-average → levels
- **Exit criteria:**
  - Golden tests for every generator at ≥ 3 timestamps on both profiles.
  - GM round-trip byte-identical on ≥ 10 real community files (with credit) or synthetic files covering all edge cases.
  - Undo property tests pass.
  - Flash check validated against hand-built PASS / FAIL fixtures.
  - Bake within budget, measured with a JVM micro-benchmark.

### M2 — App shell + Frames editor + LED playback
- **Shell:**
  - Compose app, manual DI container, Navigation 3
  - document list (files)
  - import / export via the system picker; "open with"
  - settings screen (skeleton)
- **Frames mode:**
  - LED-snap taps, pinch zoom
  - Paint / Paint-off / Erase, brightness presets + slider
  - frame strip, onion skin, playback
  - **undo / redo toolbar**
- **LEDs:** ▶ LEDs with countdown, through `:glyph-sdk`, behind the **flash gate**.
- **Diagnostics:** local crash log (`ApplicationExitInfo`) + debug bundle; backup rules [D29].
- **Exit criteria:**
  - Editor interaction is jank-free.
  - Screenshot tests for key screens.
  - Files survive process death.
  - TalkBack can navigate the document list and the editor basics.

### M3 — Triggers
- **Onboarding:**
  - notification-access disclosure
  - Restricted-settings walk-through
  - "Sound only" explainer for Nothing priority clashes [TRG]
- **NotificationListener + rules:** app / contact / keyword filters; call notifications; priority; repeat / until-unlock; respect DND.
- **Flash gate:** FAIL animations need explicit opt-in before they can be assigned to a trigger [D37].
- **Player:**
  - queue: expiry, dedupe, calls first
  - SDK bound once
  - one gravity-sensor read
  - templates filled at trigger time
- **Boundary:** a narrow trigger boundary, sharing no in-memory state with the UI [D28].
- **Exit criteria:**
  - End-to-end alerts under Doze and with the screen locked.
  - Event-to-frame latency within budget [HW].
  - Survives a forced crash: listener rebinds, measured.
  - Rules persist across app updates.

### M4 — Timeline mode
- Layers, clip arrangement, generator parameter UI, keyframe editing + easing presets, transitions.
- Selective undo surfaced in the UI.
- **Exit criteria:**
  - No jank with 10 layers × 200 frames.
  - Every command reachable in the UI is also covered by a `:core` test.

### M5 — Agent core + eval harness (JVM)
- **`:agent`:**
  - loop
  - Chat Completions + Anthropic adapters, defensive stream parser
  - tool registry with tier filter, context assembly
  - epochs / compaction, probe, spend accounting, traces
- **Knowledge base:** core prompt, 4 skills, ~10 exemplars, generated cards; a CI check that generated cards are current.
- **`:evals`:** smoke suite (~10 tasks) and full suite (~60–80 × 3) [D27].
- **Scripted fake-provider loop tests** in CI.
- **Exit criteria:**
  - Recorded-stream regression tests for each preset's provider.
  - Loop tests cover errors, timeouts, cancellation, epoch changes.
  - The smoke suite runs on at least one free / OpenRouter model.
  - Prompt prefix byte-stable (test).

### M6 — Image / GIF import UI
- Photo picker, crop / fit to circle, levels with live preview, animated GIF / WebP → frames [D25].
- **Exit criteria:** the conversion matches the `:core` golden outputs; large GIFs handled without OOM, measured.

### M7 — Agent UI
- Chat + live preview; turn cards (3 states); selection chips and `@`-mentions.
- Endpoint presets (5) and Keystore keys; LAN guard [D35].
- `shortService` turns with pause / resume [D33]; spend caps; trace sheet; privacy notices.
- Backgrounding matrix measured [HW].
- **Exit criteria:**
  - Full suite run on the maintainer's models; `SUPPORT.md` published.
  - Injection safety tasks pass.
  - No key ever appears in logs or exports (test).

### M8 — Release
- **Release pipeline:** signing; tag → GitHub Release (`sdkFree` always, `full` once permitted); Obtainium config.
- **Developer verification** before the 2027 global rollout.
- **Project docs:** README, CONTRIBUTING, user docs.
- **Final checks:**
  - full accessibility pass
  - performance budgets re-verified on device
- **Exit criteria:** a fresh install via Obtainium works end-to-end; all quality gates pass.

## v1.1 and later
- **v1.1:**
  - AOD Glyph Toy
  - glyph-ringtone export + audio track + beat markers
  - variants
  - full-tier tools + vision tier + the agent image-import hook
  - Responses adapter
  - Phone (3) testing
- **Later:**
  - live / continuous content
  - 25↔13 conversion UI
  - external agents via an allowlisted MCP + headless emulator [D14]
  - small-model guided tier
  - personal context for the agent
  - audio → animation
  - sharing / hosting
  - Play Store

## Open questions (one at a time, after r3)
- **Q-T1:** extra v1 trigger kinds beyond notifications and calls (unlock, charging, battery-low, time windows)?
- **Q-G1:** the first generators.
- **Q-L1:** licence. Q-ID1: application ID. Q-UI1: theme / font.
- Agent items listed in draft §12.
