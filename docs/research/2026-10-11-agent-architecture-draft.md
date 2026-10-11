# GlyphWizard In-App Agent — Architecture Draft v0.2

Status: **DRAFT for adversarial review r2**. 2026-10-11. Supersedes v0.1.

v0.2 incorporates:
- review r1 (`…-review-r1.md`)
- the targeted reports: `…-targeted-android-platform.md` [TAP], `…-targeted-providers-models.md` [TPM], `…-targeted-editing-safety.md` [TES]
- `…-triggers-research.md` [TRG], `…-app-foundations-research.md` [AF]
- decisions D1–D25

Source abbreviations: [PER] perception, [TR] tools/runtime, [CTX] context engineering, [PM] providers/models, [UX] UX/safety/evals.

Tags:
- **[E]** evidence-backed (verified in the cited report)
- **[P]** proposal / judgement
- **[A]** assumption, to verify
- **[HW]** measure on the 4a Pro
- **[EV]** to be settled by the eval harness

---

## 0. Changelog vs v0.1

| Area | Change | Driver |
|---|---|---|
| Distribution & SDK | `:glyph-sdk` module + `sdkFree` flavour; SHA-pinned fetch; no committed `.aar` | C1, D18 |
| Backgrounding | `shortService` turn design replaces the vague "optional FGS" | H1, [TAP] |
| OpenAI | Routed via OpenRouter in v1 (one probe run to confirm); Responses adapter post-v1 | H2, [TPM] |
| Variants | Removed from v1; one clarifying question instead | H3, [TES] |
| Undo | Three-state turn card; history sized by compressed bytes | H4, [TES] |
| Play AI-content policy | v1 ships on GitHub/Obtainium only; Play deferred until a reporting channel exists | H5, [TAP] |
| Capability bar | Re-based on integration path, not size; probe = conformance gate; harness grants tiers | H6, [TPM], D17, D19 |
| Tools | 16 → 10; MCP-GRANITE used only for "task-level > fine-grained", not for a count | M2, review cuts |
| Context | Budgets unified; no in-place mutation of past turns (Claude thinking-replay 400s); epoch compaction | M1, M8, [TPM] |
| Flash | Spec per Fisher 2026 / ITU-R BT.1702-3 / Ofcom / WCAG 2.3.1 | M5, [TES] |
| Other | LAN cleartext restricted to private ranges; i18n rule; spend-cap semantics; deterministic loop tests; offline behaviour | M4, M6, M9, M12, L6 |

## 1. Scope & principles

- **Design-only.** The agent works only inside GlyphWizard animation documents [D12]. It cannot assign triggers, read personal data, delete documents, export files, or drive the LEDs.
- **Models.** Open-weight models are first-class through hosted, capable endpoints. Support is decided by evals, not by naming models up front [D13, D16, D17]. Tiers come from `minTier`/`maxTier` tags [D19].
- **Exactness.** Small context, exact tools. Correctness comes from the document plus deterministic analysis, not from vision or model memory [PER, CTX] **[E]**.
- **Testability.** Everything that shapes model input/output lives in platform-free modules, so the JVM eval harness tests what ships [TR, UX] **[P]**.

## 2. Modules

```
:core       pure Kotlin/JVM  model, commands+validation, history, renderer+baker, analysis,
                             text-grid codec, generator registry, GM import/export, image import (D25),
                             schema generation, knowledge base (skills, exemplars, generated cards)
:agent      pure Kotlin/JVM  loop, provider adapters (Ktor client), stream parser, tool registry+tier filter,
                             context assembly, epochs/compaction, probe, spend accounting, traces
:glyph-sdk  Android          Nothing SDK bridge behind an interface; absent in `sdkFree` flavour.
                             Build-time fetch pinned by SHA-256, disabled in sdkFree [TAP, D18]
:app        Android          Compose UI, manual DI container, storage, Keystore, foreground service,
                             NotificationListener (triggers), PNG encoding
:evals      JVM CLI          harness over :core + :agent
```
Plain JVM modules, not KMP; no DI framework inside `:core`/`:agent` [AF] **[P]**.

## 3. Tools — v1 base tier (10)

Evidence for task-level over fine-grained and over one catch-all: MCP-GRANITE, verified (IoT domain, models ≤ 20.9B) [E]. Evidence that exposing fewer tools helps: WorkBench **[E]**. **No evidence fixes a specific count.** 10 is a judgement **[P]**, validated by the harness **[EV]**.

| Tool | Purpose |
|---|---|
| `describe_document` | ID index + summary of layers, clips, settings |
| `view` | Text-grid view of a frame, range, or contact strip (merges view_frame/view_motion) |
| `analyze` | Deterministic checks: centering, mirror symmetry, lit count/orphans, loop seam, **flash** |
| `list_examples` / `get_example` | List of ~10 exemplars + fetch one (replaces BM25 at n ≈ 10) |
| `load_skill` | Load a skill section |
| `add_clip` | Raster or generator clip on a layer |
| `edit` | Edit clip / layer / document properties, by ID (absorbs manage_layers, set_document) |
| `set_keyframes` | Replace a property's keyframe list wholesale |
| `paint` | Shapes (circle, line, text in bitmap font, fill, mirror/shift) → row strings → coordinate touch-ups |
| `remove` / `revert` | Remove by ID; revert this turn's own steps |

That is 11 names; `list_examples`/`get_example` count as one capability. Final count is settled at implementation **[P]**.

- **Write envelope** (every write): `{changedIds, textPreview, lintWarnings}`. Errors say what's wrong, what's valid, and the fix. Arguments are absolute (idempotent retries). Each call is atomic; one agent turn = one undo group [D9].
- **Image import:** when the user attaches an image, the app runs the deterministic import [D25]. The agent can re-run it with different params via `add_clip` (`source=image`).
- **Schemas:** flat; every field required, optionals nullable; `additionalProperties:false`; no `oneOf`/`$ref`/`const`; ranges enforced by our validator [TPM] **[E]**. Hand-written schemas, or a small `SerialDescriptor` walker. Avoid sealed arg types (kotlinx-schema emits `oneOf`) [review L4] **[E]**. Committed as golden files. MCP-shaped, no MCP dependency [D14].
- **Post-v1, by tier:** `render_image` (vision), `propose_variants` (generator params / keyframes only, top tier), extra analysis (jaggies, roundness, jerk, busyness).

## 4. Perception

- **Default view:** labelled text grid, one char per LED, ~10 levels, blanks outside the mask [PER] **[P; encoding EV]**.
- **Analysis (v1, 5 checks):** each returns a metric + verdict + offending LEDs/frames + suggested fix. Thresholds are calibrated after the hardware spike **[HW]**.
- **Flash check** (`analyze.flash`) [TES] **[E for thresholds]**:
  - Count per-LED flashes (a luminance swing above threshold) in any 1 s window.
  - FAIL if ≥ 25% of mask LEDs exceed 3 flashes/s for ≥ 0.5 s.
  - Flag 10–20 Hz as peak risk; warn if sustained > 5 s.
  - Uses the luminance curve `L(b)` from hardware **[HW]**.
  - Policy: warn + gate. Badge; one-time confirmation before LED play or export; a reduce-flashing preview setting. The agent never introduces a FAIL unless asked.
  - Separate from the aesthetic flicker metric.
- **Vocabulary map** (wonky / jerky / busy / dim → checks + fixes): always in context, English.
- **Self-repair:**
  - At most 2 automatic revisions.
  - "Best" = fewest FAIL/WARN checks, ties broken by fewer changed LEDs. Snapshots via per-step history marks [review L5] **[P]**.
  - Repair uses `tool_choice:auto` + a structured validator error + a nudge. **Never force tool choice by default:** Claude 5.5-class models reject it [TPM] **[E]**.
- **Ambiguity:** one clarifying question (variants are post-v1) [TES].
- **Vision tier:** post-v1, and only if the harness shows a gain [PER, review cuts].

## 5. Providers & capability tiers

- **Adapters (v1):**
  - OpenAI Chat Completions: OpenRouter, ClinePass, Chutes, Synthetic, z.ai PAYGO, Kimi, Ollama, llama.cpp, vLLM, LM Studio
  - Anthropic Messages
  - **OpenAI models via OpenRouter**: metadata verified; needs one paid probe run to confirm end-to-end **[A→verify]**
  - Direct OpenAI is optional, with `reasoning_effort:none` where supported. Responses adapter post-v1 (~2–4 days) [TPM].
- **Wire rules** [TPM] **[E]**:
  - Never send sampling parameters (`temperature`, `top_p`, …).
  - Never send `response_format` together with tools.
  - Never send `tools: []`.
  - OpenRouter + tools ⇒ `provider.require_parameters: true`.
  - **Reasoning replay is required for correctness:** DeepSeek `reasoning_content`, Gemini `thought_signature`, Claude thinking blocks, OpenRouter `reasoning_details`.
- **Stream parser:** defensive (missing/shared `index`, calls in the final frame, unreliable `finish_reason`). Regression-tested by replaying recorded provider streams through Ktor `MockEngine` [TPM] **[E]**.
- **Capability gating** [TPM, D17, D19]:
  - Evidence: multi-turn reliability tracks integration path and tool training more than size (xLAM-2-8b 70.0 vs Llama-3.3-70B 21.5; FC vs prompt mode swings 30–50 pp; pass@1 − pass^3 = 8–18 pp) **[E]**.
  - **Probe** = conformance gate on *our* integration path, repeated (~20–90 s, cents). It grants at most **Base** (provisional).
  - **Harness** grants **Base** / **Full**. Starting hypotheses: pass^3 ≥ 0.6 / ≥ 0.7 **[EV]**.
  - Hard requirements: native tool calls and the user-declared context length ≥ 32k **[P]**.
- **Detection:** the user's own endpoint metadata + a small bundled list + the probe + user override. The models.dev network refresh is opt-in, off by default, manual (D1; F-Droid "Tracking" exemption) [TPM] **[E]**.
- **Subscriptions** (key-paste only; never OAuth) [TPM] — recheck all before release:

  | Service | Status |
  |---|---|
  | ClinePass | Allowed per its docs; ToS predates ClinePass |
  | Chutes | Personal interactive use; recommend PAYGO; node-operator privacy note |
  | Synthetic | ToS not public — unverified |
  | OpenCode Go | Grey zone — ask OpenCode first |
  | Kimi Code | Ambiguous |
  | z.ai Coding Plan, Anthropic consumer OAuth | Not allowed |
  | Gemini | No EEA blocking needed for BYOK; 18+ and free-tier-review notice |
- **On-device models:** not viable on the 4a Pro **[P]** (single secondary benchmark).
- **Drift:** provider behaviour changes. Mitigations: recorded-stream tests, per-endpoint quirk flags (e.g. `canForceTool`), a clear "provider error" UX with the raw message, and a fix path via app releases [review M7] **[P]**.

## 6. Context engineering

- **Session-frozen prefix:** system prompt + tool schemas are frozen for the session and byte-stable (cacheable). Core ≤ 5k tokens:
  - rules
  - generated device card
  - grid legend
  - vocabulary map
  - skill index
  - 0–1 worked examples per tier
- **Working caps (unified):** ~30k for Base, ~60k for Full **[P, EV]** (fixes M1).
- **No in-place mutation of past turns.** Claude 5.5-class models reject replayed thinking if `system`, `tools`, or earlier messages changed [TPM] **[E]**. Therefore:
  - Shorten tool results *before* first send.
  - Document state goes in the newest user turn.
  - "Clearing stale tool results" is removed.
- **Epochs:** an epoch = (model, endpoint, tool-schema version, kbVersion, app version). When the epoch changes, compact earlier turns into one plain-text summary message [TPM] **[P]**.
- **Skills:** agentskills.io SKILL.md format; ≤ 1.2k tokens per section; 4 skills in v1.
- **Exemplars:** 0 in-prompt for Full, 1 for Base, ≤ 2 more on demand. Run a 0/1/3 ablation before committing **[EV]**. "3 ≈ 9" is re-cited as narrow (2024, one dataset) [TES].
- **Generated from Kotlin, CI-checked:** device card, generator catalogue (with grid snapshots), tool schemas. `kbVersion` is stamped on traces and eval results.
- **i18n:** reply in the user's language; KB, identifiers, and enum values stay English; closed enums for arguments [TAP] **[E]**.

## 7. Runtime

- **Loop:** thin custom loop in `:agent`, on an app-scoped coroutine.
  - Koog spike ≤ ½ day. First question: does it build for Android at all (README inconsistent) [review L3].
- **Persistence:** two layers [TPM] **[P]**:
  1. a neutral transcript, saved after every step; exports carry only this, and only if the user opts in
  2. an epoch-keyed wire cache holding provider-specific payloads such as reasoning blobs
- **Backgrounding** [TAP] **[E for platform behaviour]**:
  - Cached apps are frozen ~10 s after leaving the foreground on Android 14+, and their sockets are closed.
  - Design:
    - `shortService` foreground service at turn start, extended while visible
    - 5 min total turn cap; ≤ 150 s background budget
    - `onTimeout` → Stop path (finished ops kept as one undo group)
  - On return: **"Paused: Continue / Keep / Undo"**; Continue requires a tap.
  - The hardware spike adds a backgrounding matrix (10/60/170/200 s, with/without FGS, locked), logging `ApplicationExitInfo` **[HW]**.
- **Limits & spend** [review M4] **[P]**:
  - Per-turn step cap (default 12) and token cap.
  - Per-session cost cap in the user's currency when a price is known, else tokens only (never guess a price).
  - Repeated-identical-call breaker.
  - Caps are hard stops with a "Continue anyway" button.
- **Retries:** backoff only on network errors / 429 / 5xx.
- **Offline / unreachable endpoint:** chat input is disabled with a reason; editing is unaffected [review M12].
- **Concurrency:** canvas locked during a turn; agent edits apply live.

## 8. UX

- **Apply directly + undo** [UX] **[E for trend]**. The turn card has three states [TES]:
  - **Undo** while the turn is still the latest change.
  - **Undo this turn** if later user edits didn't touch the same paths. Uses JSON-Patch `test` guards on `touchedPaths`.
  - **Restore to before this turn…** otherwise. Applied as a new forward group; nothing is lost.
  - The transcript is never truncated. Press-and-hold shows before/after.
- **References:** selection chip; `@`-mention clips by auto-name.
- **Stop:** keeps finished operations. Messages typed during a turn queue as the next turn.
- **LEDs:** user-only "▶ LEDs" button with a countdown.
- **Distribution:** GitHub Releases + Obtainium [TAP]. Play is deferred: its AI-content policy needs in-app reporting that reaches the developer [H5].

## 9. Safety & privacy

- **Prompt-injection posture** (restated per review M11) [P]:
  - The agent ingests untrusted text (imported files, exemplars) and can mutate the open document.
  - It has no private data (D12) and no network or export tools.
  - The residual risk is document vandalism, which undo covers.
  - Controls:
    - Never render images or auto-fetched links in chat.
    - Document text fields are delimited as data, not instructions.
    - The agent cannot delete documents, export, or touch the LEDs.
- **Keys:** Android Keystore AES-GCM; excluded from backup [AF, PM] **[E]**.
- **LAN:**
  - Use a network security config, not global `usesCleartextTraffic`.
  - App-level check: `http://` only for RFC1918 ranges, `.local`, and loopback.
  - Warn when a key is set on `http://`.
  - targetSdk stays 36. Note: targeting 37 adds the runtime `ACCESS_LOCAL_NETWORK` permission [TAP, review M6] **[E]**.
- **Privacy notices** per provider (e.g. Gemini free tier reviewed by humans and used for training).
- **Personal context:** deferred to a user-side picker [UX, TRG].

## 10. Evals & observability

- **`:evals` JVM CLI.** v1 suites:
  - perceive
  - edit
  - repair-planted-defect
  - ambiguity (clarifying question)
  - safety: injected text in imports; strobe requests must trigger the flash gate
  - ~5 non-English tasks

  About 20–30 tasks total. Programmatic scoring only in v1 (the VLM judge is post-v1). Report pass@1 and pass^3 → dated `SUPPORT.md`.
- **Deterministic loop tests** [review M9]: a scripted fake provider (recorded or hand-written turns) drives the loop through tool calls, errors, timeouts, cancellation, and epoch changes. Runs in CI with no network.
- **Traces:** local per-turn trace (tool calls, tokens, cost, latency, kbVersion, epoch); details sheet; redacted debug-bundle export. Field names follow `gen_ai.*`, without the OTel SDK.
- **Cost:** reported by the harness per run; the v0.1 estimates are dropped until measured **[EV]**.

## 11. Hardware spike inputs (M0) **[HW]**

- Frame array ordering/length — evidence suggests a full 13×13 = 169 square with corners zero [sound-editor report] **[A]**
- Brightness range: 0–255 vs a raw max of 2047 [TRG] **[A]**
- Brightness response curve `L(b)` at ≥ 4 levels
- Minimum visible level
- Max sustained frame rate
- PWM / rise time
- Glow / bleed
- Diameter / pitch
- Luminance
- Viewing conditions
- Locked / face-down / face-up / pocketed behaviour
- Priority vs Essential Notifications and AOD toy
- Backgrounding matrix
- NotificationListener + "Restricted settings" with Obtainium installs
- Glyph-ringtone install path (for v1.1)

## 12. Open user decisions (agent-related, ask one at a time)

1. Default spend caps (per turn / per session)
2. May the agent create new documents (vs edit the open one only)?
3. Is the chat transcript included in `.glyphwiz` exports (opt-in)?
4. Ask OpenCode about Go keys in a non-coding app?
5. Probe trigger: auto after endpoint/model change (cost shown) vs tap-only
6. models.dev refresh: ship as opt-in, or skip entirely?
7. Synthetic preset: keep as "unverified ToS" or drop to Custom?
8. Epoch compaction behaviour OK (old turns summarised on model/app change)?
9. Who authors the first ~10 exemplars; may community designs be used (with credit, per GM terms)?
10. Eval budget and the first models in the support table
