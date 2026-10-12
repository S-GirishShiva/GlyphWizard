# GlyphWizard v1 — Design Spec

**Status:** draft for user review · **Date:** 2026-10-12 · **Authors:** Siddanth, Claude

This spec is the single source of truth for v1. Where it disagrees with an earlier draft, this document wins. Sources it consolidates:

| Source | Path |
|---|---|
| Product & architecture decisions D1–D43 | `docs/research/decisions.md` |
| UI decisions UI1–UI38 | `docs/design/design-log.md` |
| Every screen (approved) | `docs/design/atlas.html` |
| Navigation | `docs/design/flow.md` |
| Evidence | `docs/research/2026-10-11-*.md` (research reports, reviews r1–r3) |
| Superseded drafts | `docs/research/2026-10-11-agent-architecture-draft.md` (v0.3), `docs/research/2026-10-11-v1-roadmap-draft.md` (v0.2) |

Tags used throughout:
- **[E]** evidence-backed (source cited in the research reports)
- **[P]** proposal or judgement
- **[HW]** to be measured on the Phone (4a) Pro during M0
- **[EV]** settled by the eval harness

---

## 1. Product

### 1.1 What it is
GlyphWizard is an open-source, non-commercial Android app for Nothing phones with a Glyph Matrix. It lets you:
- design **unlimited** animations: static, frame-by-frame, or a layered timeline;
- play them on the physical matrix;
- make them light up from **triggers** such as notifications, calls and phone events.

**Human designers come first (D43).** An optional in-app **design agent**, running on the user's own endpoint, edits animations through the same commands the UI uses.

### 1.2 Principles
- **Evidence over assumption (D-ethos).** Any number not yet measured is tagged [P], [HW] or [EV].
- **Quality over speed (D38).** No deadlines; every milestone ships when its quality gates pass.
- **Local first (D1).** No backend. Nothing leaves the phone except prompts sent to the user's chosen agent endpoint, and an opt-in model-info refresh.
- **Sensible defaults, configurable detail (UI2).**

### 1.3 v1 definition of done
On a Nothing Phone (4a) Pro (Android 16):

1. **Create, edit, organise, import and export animations** with no app-imposed count limit. Test: 500 documents list, open and save within the §9 budgets.
   - Frames view and timeline view.
   - Glyph Museum import/export with **semantic** equality on round-trip.
   - Image and GIF import.
2. **Preview on screen and play on the Glyph**, behind the flash-safety gate (D37).
3. **Assign animations to triggers**, using the full trigger set in D40.
4. **Use the in-app agent** on the user's own endpoint, meeting the **D41 bar**: at least one Supported model on a non-frontier route, at least one of them open-weight, and 100% on the safety tasks. If no model qualifies, v1 waits.
5. **Install from GitHub Releases via Obtainium.**
   - The `sdkFree` build is always published.
   - The `full` build is published once Nothing grants permission (D26).

**Out of v1** (D36 and the roadmap):
- variants, the vision tier and the VLM judge
- the agent hook for image import
- the AOD Glyph Toy and glyph-ringtone export (v1.1)
- live content
- external MCP agents
- the small-model tier
- Play Store distribution

---

## 2. Architecture

### 2.1 Modules (D14, D18, D20)
```
:core       pure Kotlin/JVM  document model, commands+validation, history, renderer+baker,
                             analysis (incl. flash), text-grid codec, generator registry,
                             Glyph Museum + .glyphwiz I/O, image-import pipeline core,
                             schema generation, knowledge base (skills, exemplars, cards)
:agent      pure Kotlin/JVM  agent loop, provider adapters (Ktor), stream parser, tool registry
                             + tier filter, context assembly, epochs/compaction, probe,
                             spend accounting, traces
:glyph-sdk  Android          Nothing SDK bridge behind an interface; absent in `sdkFree`
:app        Android          Compose UI, manual DI container, storage, Keystore,
                             foreground service, NotificationListener + trigger runtime,
                             PNG/GIF decode, haptics
:evals      JVM CLI          eval harness over :core + :agent
```
**Rule:** anything that shapes what the model sees, or how tools behave, lives in `:core` or `:agent`, so the JVM harness tests what actually ships.

**Process model (D28):** a single process. The trigger runtime sits behind a narrow boundary: it reads from disk and shares no singletons with the UI or the agent.

### 2.2 Stack (app-foundations report; D31)
| Area | Choice |
|---|---|
| UI | Jetpack Compose with a custom theme over Material 3 components |
| Architecture | UI layer → ViewModel/StateFlow → `:core` commands (the reducer); one-way data flow |
| Navigation | Navigation 3 |
| Dependency injection | Manual DI container (no Hilt/Koin) |
| Documents | JSON files (kotlinx.serialization) + gzip history sidecar |
| Settings & rules | DataStore |
| API keys | Android Keystore (AES-GCM); excluded from backup |
| SDK levels | minSdk 35, targetSdk 36, compileSdk 37 |
| Build | Gradle KTS + version catalog; JDK 17 toolchain |
| Tests | JUnit 5 + kotlin.test + golden text-grid files on the JVM; Robolectric + Compose UI tests in `:app`; Roborazzi screenshots; fakes over mocks |
| CI | GitHub Actions; signed tag → GitHub Release |
| Lint | ktlint + Android Lint |
| Licence | GPL-3.0-or-later + an additional permission for the Nothing SDK, plus a NOTICE (decided in M0) |

---

## 3. Animation model (D5–D11, D25, D29)

### 3.1 Document
`.glyphwiz` is JSON with these top-level fields:

| Field | Meaning |
|---|---|
| `schemaVersion` | format version |
| `deviceProfile` | grid plus mask; 4a Pro = 13×13, 137 LEDs, rows `[5,9,11,11,13,13,13,13,13,11,11,9,5]` |
| `tickMs` | default 20, range 16–100 [P/HW] |
| `layers[]` | the layer stack |
| `meta` | name, author, credits |

**Clips.** Each layer holds clips that have a stable ID, `start` and `duration` in integer milliseconds, and no overlaps within a layer:
- **Raster clips** store frames of brightness + alpha (0–255 each) and a `sourceProfile`. Each frame keeps its own duration.
- **Generator clips** store a versioned reference (`ring_pulse@1`), typed and keyframeable parameters, and are computed per LED from normalised coordinates.

**Keyframes and transitions.** Keyframes are relative to their clip. Easing is one of `hold`, `linear`, `easeIn`, `easeOut`, `easeInOut`, `cubicBezier`, `steps`. In/out transitions (fade, slide, wipe) are clip properties. A crossfade is two overlapping clips on adjacent layers.

**Blend modes:** Normal (alpha-over) and Lighten (max).

**Templates (D7):** typed inputs filled in at trigger time: initials, unread count, progress %, battery %, time, and the app's status-bar icon converted to a glyph.

### 3.2 Rendering
- `render(doc, tMs, inputs) → Frame` is pure.
- The baker samples at `tickMs × speed` (D10). Playback replays baked frames.
- On screen, brightness is shown as **perceived** brightness through the M0 calibration curve; stored values stay visible in the brush and inspector (UI11).
- **Brightness range:** the hardware maximum (255 vs 2047) is [HW]. The model stores 0–255, and the device layer maps to the hardware.

### 3.3 Commands & history (D2, D8, D9, D29, D36)
- One typed command layer serves the UI, the agent and tests. Commands are validated and their errors are written so they can be fixed by whoever sent them. Objects are addressed by stable IDs.
- **History:**
  - frame-granular binary diffs with inverses
  - a gzip sidecar file, capped at **~4 MB compressed / ~200 groups** per document
  - the newest agent turn is pinned
  - authorship is recorded (user / `agent:<model>`)
  - one agent turn = one undo group
  - the history file is **excluded from cloud backup**
- Pixel-editing a generator clip converts it to raster in one undo step, and the clip gets a "was ring_pulse" badge.
- **Agent turn card states:**
  - **Undo**: when the turn is still the latest change.
  - **Undo this turn**: selective, guarded by JSON-Patch `test` ops on `touchedPaths`, and property-tested.
  - **Restore to before this turn**: applied as a new forward group.

### 3.4 Analysis (D37, perception report)
Deterministic checks:
- centering
- mirror symmetry
- lit count and orphan LEDs
- loop seam
- **flash**

**Flash rule:** FAIL when ≥ 25% of mask LEDs each flash more than 3 times in any 1 s window, sustained ≥ 0.5 s. 10–20 Hz is flagged as peak risk; flashing for more than 5 s warns. Luminance comes from the M0 curve. Sources: Fisher 2026, ITU-R BT.1702-3, Ofcom, WCAG 2.3.1.

**Flash policy:** warn and gate, never block.
- A FAIL animation needs confirmation before LED play, before export and before it can be assigned to a trigger.
- The **"slow it down for me"** fix is offered first.
- The agent never introduces a FAIL unless the user asks.

### 3.5 Import/export
- **Glyph Museum JSON**, both directions; note that `v` is a device ID.
- **`.glyphwiz`** with chained migrations.
- **GIF export.**
- **Image/GIF import (D25):**
  1. photo picker
  2. fit to the circle
  3. luminance → area-average downscale
  4. levels: shades / B&W / outline, brightness, contrast, invert

  Animated GIF/WebP becomes frames.
- **Input limits** (anti-zip-bomb) [P]:

  | Limit | Value |
  |---|---|
  | file size | ≤ 10 MB |
  | frames | ≤ 2,000 |
  | layers | ≤ 32 |
  | decompressed bytes | ≤ 50 MB |
  | decoded GIF frames | ≤ 600 |

---

## 4. Triggers (D21, D40, D28; triggers report)

### 4.1 Event sources
**Via the NotificationListener (D40):**
- app, contact and keyword filters
- calls, through the dialer's notification
- notification categories: alarm, reminder, event, missed call, navigation, transport
- progress notifications
- unread-count threshold
- media track change, via media sessions

**Via dynamically registered receivers and callbacks:**
- unlock / lock
- charging start/stop
- battery below X
- ringer mode and DND changes
- audio device connected
- torch on
- scheduled time / hourly chime (inexact alarms)

**Conditions:**
- quiet hours
- day of week
- ringer mode
- DND
- screen locked/off
- charging
- headphones
- face-down at event time (a single gravity read)

**No new permissions beyond notification access.** Contacts come from the system picker; there is no READ_CONTACTS. Every API is verified against the docs before implementation.

### 4.2 Runtime
- **Process lifetime:** the NotificationListener process is not cached or frozen [E]. A `specialUse` foreground service exists only as a fallback.
- **SDK binding:** the SDK is bound once and stays bound.
- **Priority:** our frames lose to Nothing's own notification, ringtone and Contact Identifier lights [E]. The one-time "sound only" tip covers this.
- **Player queue:** expiry, de-duplication, calls first, and a hard cap on "until unlock" (30 min [P]).
- **Rule model:** `when` (event + filters) → `only if` (conditions) → `play` (animation, template inputs, repeat: N / seconds / until unlock / while ringing, priority, respect DND).
- **Persistence:** pending "until unlock" alerts persist across a listener rebind.
- **Privacy:** notification content is held in memory only and is never sent to the agent.

---

## 5. Agent (D12–D19, D27, D30, D33, D35, D41, D42)

### 5.1 Scope
**Design only.** The agent can create and edit documents. It never deletes, exports, touches triggers or plays on the LEDs. Each step is autosaved. Personal context is deferred.

### 5.2 Providers
**Adapters:** OpenAI Chat Completions and Anthropic Messages. OpenAI models are reached via OpenRouter (to be confirmed with one probe run). The Responses adapter is post-v1.

**Presets:**
- OpenRouter
- ClinePass
- Anthropic
- Local network (Ollama, LM Studio, llama.cpp)
- Custom OpenAI-compatible

**Wire rules [E]:**
- Never send sampling parameters.
- Never send `response_format` together with tools.
- Never send `tools: []`.
- OpenRouter with tools ⇒ `provider.require_parameters: true`.
- **Reasoning replay is required:** DeepSeek `reasoning_content`, Gemini `thought_signature`, Claude thinking blocks, OpenRouter `reasoning_details`.
- **Repair** uses `tool_choice:auto` + a structured error + a nudge. Forced tool choice is off by default.

**Stream parser:** defensive. Regression tests replay recorded streams through Ktor `MockEngine`.

**Model support labels:**
- **Supported**: passes the harness, with one threshold (pass^3 ≥ 0.6 [EV]) on ~60–80 tasks × 3 runs.
- **Below bar.**
- **Untested.**
- **"Provisional"**: the probe passed (~20–90 s, cents [P]).

The **probe** runs automatically after an endpoint or model change, showing the cost first. The models.dev snapshot is bundled; refreshing it is manual only.

### 5.3 Tools (base tier, ~12 names) [P/EV]
| Kind | Tools |
|---|---|
| Read | `describe_document`, `view` (text grid / range / contact strip), `analyze`, `list_examples` / `get_example`, `load_skill` |
| Write | `add_clip`, `edit`, `set_keyframes`, `paint` (shapes → row strings → coordinates), `remove`, `revert` |
| Docs | create document (never delete) |

**Every write returns:** `{changedIds, textPreview, lintWarnings}`.

**Schemas:**
- flat
- all fields required, optionals nullable
- `additionalProperties:false`
- no `oneOf`, `$ref` or `const`
- golden files
- MCP-shaped, with no MCP dependency

`minTier`/`maxTier` tags exist; the full tier arrives in v1.1.

### 5.4 Context
- **Frozen, byte-stable prefix** for the session (one session per document), core ≤ 5k tokens:
  - rules
  - device card generated from code
  - grid legend
  - vocabulary map
  - skill index
  - 0–1 worked examples
- **Working cap:** `min(30k, declaredContext − maxOutput − margin)`.
- **No in-place mutation of past turns.** Document state goes in the newest user turn.
- **Epochs:** an epoch is (model, endpoint, key fingerprint, system-prompt hash, tool-schema version, kbVersion, app version, deviceProfileId). When it changes, earlier turns are compacted, behind a visible divider.
- **Skills:** 4, in the agentskills.io format. **Exemplars:** ~10, co-authored by the user and Claude.
- **Language:** replies in the user's language; KB and enums stay English (D34).

### 5.5 Runtime
- A thin custom loop in `:agent` (a Koog spike of ≤ ½ day first).
- **Backgrounding:** a `shortService` per turn; 5 min total and ≤ 150 s in the background. On timeout the turn stops, and returning shows "Paused: Continue / Keep / Undo" (D33). With notification access granted, the process is not frozen anyway.
- **Limits:** caps of 12 steps per turn and $1 or 200k tokens per session. They are hard stops with "Continue anyway", and editable (D42).
- **Loop breaker** for repeated calls.
- **Retries** only on network errors, 429 and 5xx.
- **Safety:** imported text is delimited as data, and images or links are never auto-rendered in chat. LAN cleartext is allowed only for RFC1918 addresses, `.local` and loopback, with a warning when a key travels over `http://`.
- **Traces:** a local trace per turn, a details sheet, and a redacted debug bundle.

### 5.6 Evals
- `:evals` JVM CLI. Suites:
  - perceive
  - edit
  - repair planted defects
  - ambiguity (one clarifying question)
  - safety (injection; strobe requests trip the gate)
  - ~5 non-English tasks
- A ~10-task smoke suite runs during development. The full suite runs before release on the maintainer's OpenRouter and ClinePass models, and community results are welcome.
- Results publish to a dated `SUPPORT.md`. Scripted fake-provider loop tests run in CI.

---

## 6. UI/UX (UI1–UI38; atlas is normative)

### 6.1 Design system tokens
| Token | Dark | Light ("dim paper") |
|---|---|---|
| background | #0B0B0B | #D8D6D2 |
| surface | #1A1A1A | #CBC9C4 |
| fg (UI white) | **#EAE8E3** | #141414 |
| red | #FF5C4D | #9A281F |
| LED on screen | true grey, perceived brightness | true grey (canvas always black) |

- **Type:**
  - display: dot font, bold, ~30 sp, caps (Doto stand-in; our own dot font later)
  - body and labels: one monospace family chosen by the user (Sometype Mono default; Geist Mono, Reddit Mono, Spline Sans Mono, Atkinson Hyperlegible Mono)
  - labels in lowercase
- **Shape:** 8 dp grid. Radii: canvas 20–24, trays 15–16, tools 11–12, frames 10, buttons are pills. Touch targets ≥ 48 dp.
- **Icons:** duotone, 24 dp. The selected tool gets an 18% red tile with a hairline red edge.
- **LEDs:** flat discs, no glow. Unlit LEDs are 1 px rings at 20% by default (12 / 20 / 28 setting).
- **Ambient:** a static blurred copy of the current artwork behind frosted panels; it can be switched off.
- **Red is only for live or current things:** the current frame, the selected tool, live dots, the active chat, recording.

### 6.2 Motion & haptics (UI17, UI18, UI36)
| Moment | Motion |
|---|---|
| Hierarchical navigation | push with parallax |
| Sheets and peers | blur-through |
| Big moments (opening a document, finishing onboarding) | dot ripple |
| Startup | black → dot → ripple → logo → logo settles as home renders |
| Play on Glyph | 3·2·1 dot numerals (one haptic on "1") → flip hint → live mirror. Preview starts instantly. |
| Frame strip | velocity-driven fling; current frame scales up to 1.16 |
| Timeline focus | container-transform maximise/minimise, ~300 ms |

Haptics follow the UI17 map and are tuned on the device in M2. Reduced-motion is respected everywhere.

### 6.3 Screens
The **atlas** is the visual spec, and **flow.md** is the navigation. Key behaviours:

**Home**
- quiet hero (logo + byline)
- swipeable count widget: on ↔ in library
- continue widget that expands to the left
- Triggers + Pause all
- collapsed activity
- library below
- a single centred **New** button

**Editor**
- **Frames view:**
  - configurable info line
  - black canvas with a white outline
  - tool tray; groups replace the frame strip
  - fading strip with the current frame enlarged and red
  - actions row
  - bottom row: Preview · ✦ · Play on Glyph
- **Layers button** sits beside the title (UI37) and toggles to the **timeline view**. Tapping a layer **focuses** it, fully covering the others with the full toolset (UI36, UI38).
- **Agent entry:**
  - ✦ is neutral when there is no chat and red when a chat is active but closed.
  - ✦ expands into a prompt over the buttons. The prompt stays open, and the reply card is anchored above it.
  - "Full chat" opens the sheet; the **manual/agent toggle is visible only while the chat is open** (UI23–UI25, UI37).

**Triggers**
- list with sentence rows grouped people / apps / phone
- **creation wizard**: what → who → only if → play
- saved view: **sentence ⇄ flow**, default set in settings
- editing with sections

**Onboarding**
- minimal: welcome → Glyph check
- **just-in-time** notification access, the restricted-settings walkthrough, and the Nothing tip (path to be verified [HW])

**Settings:** per UI31, plus data & backup and about (with the not-affiliated disclaimer).

### 6.4 Copy rules
- Sentence case, plain verbs.
- An action keeps the same name throughout a flow.
- Errors say what happened and how to fix it.
- All strings live in resources (D34).

---

## 7. Distribution, licensing, legal (D18, D26, D32)
- **Releases:** GitHub Releases + Obtainium. `sdkFree` is always published; `full` after Nothing's written permission.
- **SDK handling:** the `.aar` is never committed. It is fetched at build time, pinned by SHA-256.
- **Licence:** GPL-3.0-or-later with an SDK exception, a NOTICE reserving the name and icon, and a "not affiliated with Nothing" disclaimer.
- **Naming risk:** "Glyph" may be a pending trademark, so a backup app name is kept ready.
- **Developer verification:** register before the 2027 global rollout.
- **Outreach:** the Nothing email draft is in `docs/outreach/` (parallel task). The OpenCode question is pending.

---

## 8. Roadmap (D39; order A)
| Milestone | Scope | Exit criteria (all quality gates in §9 also apply) |
|---|---|---|
| **M0** Foundations & hardware spike | Licence, app ID, scaffold migration, modules, flavours, CI skeleton, debug glyph mirror, **measure** the §10 list | Spike-results doc; D6/D10/D37 parameters confirmed; CI green; installs with the real ID |
| **M1** `:core` | Model, commands, history, renderer/baker, templates, analysis + flash, GM/.glyphwiz I/O, import pipeline core | Golden tests per generator; GM semantic round-trip on ≥ 10 files; undo property tests; flash fixtures; bake benchmark within budget |
| **M2** Shell + frames editor + LED | Compose shell, library, import/export, frames view, undo UI, Play on Glyph + flash gate, crash log, backup rules | No jank (§9); screenshot tests; survives process death; TalkBack basics |
| **M3** Triggers | Onboarding/permissions, listener + rules + wizard + views, player queue, trigger boundary | End-to-end under Doze/locked; latency within budget [HW]; listener recovery after a forced crash measured |
| **M4** Timeline | Layer cards, focus panel, generators UI, keyframes/easing/transitions, selective undo UI | No jank with 10 layers × 200 frames; every UI command covered by a `:core` test |
| **M5** Agent core + evals (JVM) | Loop, adapters, parser, tools, context/KB, epochs, probe, harness | Recorded-stream tests per preset; loop tests; smoke suite on ≥ 1 model; byte-stable prefix test |
| **M6** Image/GIF import UI | Picker, fit, levels, GIF frames | Matches `:core` goldens; large GIFs handled without OOM |
| **M7** Agent UI | ✦ prompt, reply card, sheet, toggle, presets/keys, add endpoint + probe, shortService, caps, traces | Full suite → `SUPPORT.md` meets D41; injection tasks pass; no key ever in logs or exports |
| **M8** Release | Signing, release CI, Obtainium, developer verification, docs, a11y pass, budgets re-verified | Fresh Obtainium install works end to end; all gates pass |

**v1.1:**
- Glyph Toy
- ringtone export + audio track + beat markers
- variants
- full-tier tools + vision
- Responses adapter
- Phone (3) testing

---

## 9. Quality gates (every milestone) [P unless noted]

**Tests**
- `:core`/`:agent` unit tests, plus property tests for commands and undo (`apply∘inverse = id`, interleavings).
- Golden text-grid tests for the renderer.
- Compose UI tests and Roborazzi screenshots of the atlas screens.
- Migration and upgrade-install tests: every `schemaVersion`, every DataStore and history format.
- **The test suite also runs against the minified release build.**

**Performance budgets** (measured; revised after M0 [HW])
| Budget | Target |
|---|---|
| Render + bake, 5 s at 13×13 | < 50 ms on device |
| Janky frames (JankStats, refresh-rate aware; the 4a Pro runs 120 Hz) | < 1% in the editor |
| Trigger event → first LED frame | < 150 ms |
| Cold start to the interactive home screen | < 1 s |
| Editor memory | < 250 MB PSS |
| Trigger runtime battery | ≤ 1% per day at 100 notifications / 10 alerts (Battery Historian) |
| APK size | full ≤ 25 MB · sdkFree ≤ 22 MB |

**Security**
- A threat model doc (M0).
- Fuzzing of the GM, `.glyphwiz` and GIF parsers.
- The §3.5 input limits.
- A pre-release security review (`/security-review`).
- Keys only in the Keystore.

**Supply chain**
- Gradle dependency verification.
- A licence audit of all dependencies, compatible with GPL.
- **A CI check that the `sdkFree` APK contains no SDK classes.**
- Renovate for dependency updates.

**Accessibility**
- TalkBack labels on every control.
- The LED canvas exposes a text description generated by analysis ("heart shape, 72 LEDs lit, frame 4 of 12").
- Contrast ≥ 4.5:1 for text.
- Targets ≥ 48 dp.
- Font scaling to 200% without clipping.
- Reduced motion respected.
- A haptics toggle.

**Review**
- `/code-review` at the end of each milestone, with findings resolved.
- `decisions.md` and `design-log.md` kept up to date.
- Learning asides for each new concept in the milestone plans (D23).

**Commits:** in the agreed format, with a branch per milestone.

---

## 10. Hardware spike checklist (M0) [HW]
- **Display:**
  - SDK frame layout and ordering (GDK: 13×13; check that 137 vs 169 values are accepted)
  - brightness range (255 vs 2047) and response curve at ≥ 4 levels
  - minimum visible level
  - maximum sustained fps
  - PWM / rise time
  - glow
  - diameter and pitch
  - luminance (for the flash rule; needs a light meter or a calibrated camera)
- **Behaviour and priority:**
  - locked, face-down, face-up and pocketed behaviour
  - priority against Essential Notifications and the AOD toy
  - the exact path of Nothing's glyph notification setting
- **Process and permissions:**
  - listener process state, and recovery after a forced crash
  - restricted settings on Obtainium installs
  - `shortService` interplay with the listener process
- **Later work:** the glyph-ringtone install path (v1.1)
- **Backgrounding matrix:** moves to M7.

---

## 11. Open items (to settle before or during the relevant milestone)
- **Licence and application ID: M0.** Two questions for the user: the permanent ID string, and confirming GPL-3.0+ with the SDK exception.
- **Final logo and byline:** tabled (UI22).
- **First generators: M1.** Proposal: ring_pulse, spinner, scroll_text, fill_bar, sparkle (this needs user sign-off).
- **Agent tier thresholds:** [EV] after the first harness round.
- **Synthetic / OpenCode / Kimi presets:** pending their terms.
