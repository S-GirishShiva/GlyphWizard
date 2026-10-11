# Adversarial Review r1: Agent Architecture Draft v0.1

Date: 2026-10-11
Target: `2026-10-11-agent-architecture-draft.md` (draft v0.1)
Inputs read: `agent-research-brief.md`, `decisions.md` (D1–D17), the six source reports ([PER], [TR], [CTX], [PM], [UX], [SYN]).
Method: I re-checked every load-bearing external claim against its primary source in this session where I could (WebFetch/WebSearch, plus a direct download of the BFCL CSV and the WorkBench PDF). Then I cross-read the draft against the decisions log and against each source report.
Classification: **VERIFIED** = the primary source says it · **PARTIALLY** = true but scoped narrower, numbers partly unconfirmed, or secondary only · **MISREPRESENTED** = the source exists but doesn't support the use the draft makes of it · **NOT FOUND** · **FABRICATED-LIKELY**.

---

## 1. Verdict summary

**No hallucinated citations found.** Every arXiv ID, quote and number I checked exists. That includes the two "future-looking" ones that looked most suspicious: MCP-GRANITE (arXiv 2609.24161, submitted 21 Sep 2026) and the OpenAI GPT-5.4/GPT-6 Responses requirement. The draft's real weaknesses are elsewhere:

1. **Correct sources used to support claims they don't make.**
   - MCP-GRANITE's best interface is **4** tools, on IoT mocks with ≤20.9B models. WorkBench measures *tool exposure*, not granularity. Neither supports "~16 task-level tools".
   - BFCL doesn't show a "≤ 14B collapse". It shows a *training* effect: an 8B tool-tuned model beats Opus 4.5 on multi-turn, and a 70B model collapses.
2. **The draft silently picked sides where the source reports disagree**, and in places combined incompatible halves:
   - backgrounding (3 reports, 3 different answers)
   - variants (two incompatible mechanisms in §3 vs §8)
   - tier taxonomy
   - budgets
   - context budget
3. **Gaps that block shipping:**
   - The Glyph Matrix SDK is a **closed-source EULA** ("not … redistribute", "commercial use strictly prohibited"). That collides with D1 (open source) and with F-Droid.
   - Google Play's **AI-generated-content reporting** requirement isn't addressed.
   - Undo semantics after interleaved user edits are missing.
   - Deferring the OpenAI Responses adapter means **OpenAI's current flagship models can't call tools at all**.
4. **Overengineering for a solo first Android app.** About 40% of the v1 surface can move to v1.1 without losing evidence-gathering ability (§5).

Overall: **revise and re-review**. The core shape is sound and well-evidenced: pure-Kotlin `:core`/`:agent`, typed task-level tools, text-grid perception plus deterministic analysis, apply plus undo, and eval-gated support. The specifics listed below need fixing before anything becomes a decision.

Findings count: **CRITICAL 1 · HIGH 6 · MEDIUM 12 · LOW 8**.

---

## 2. Citation verification table

| # | Claim (as used in draft / sources) | Status | Evidence & notes |
|---|---|---|---|
| 1 | MCP-GRANITE (arXiv 2609.24161): task-level tools +16.4% vs fine-grained, +33.6% vs monolithic | **VERIFIED** (existence and numbers) / **MISREPRESENTED** (use) | The abstract confirms it: Paschalides et al., 21 Sep 2026; 81 scenarios, 9 local models from 268M to 20.9B, 8,748 trials, "edge and IoT scenarios", and the **best interface is 4 tools**. The draft still tags it "[A: citation unverified]", which is now stale. Its use as support for ~16 tools is a stretch (finding M2). |
| 2 | WorkBench GPT-4 49% → 43% | **VERIFIED** (number) / **MISREPRESENTED** (use) | The PDF body says: "accuracy falls from 49% to 43%" when GPT-4 is given all 26 tools instead of the domain toolkit. The abstract only gives 43%. That is evidence about **how many tools are exposed**, not about task-level vs fine-grained granularity, which is what draft §3 cites it for. |
| 3 | "VLMs are blind" (2407.06581): ~58% average, best ~78% | **VERIFIED** | 58.07% average over 4 VLMs; Claude 3.5 Sonnet 77.84%. |
| 4 | GPT-4o fails beyond 12×12 grids (Greenblatt / Redwood) | **VERIFIED** | Exact quote: "totally fails to extract the colors of cells from an image for images >12x12 and is quite bad at 8x8." The model is from 2024, and the setting is ARC colours, not monochrome brightness. |
| 5 | Vo et al. 2025 ~17% counting; double-check prompting gives +2–6 pts; ICLR 2026 | **PARTIALLY** | 17.05% average counting accuracy is VERIFIED. The paper says double-checking "only marginally improves accuracy", but I didn't see the +2–6 numbers (they're in Appendix A.10, which I didn't reach). ICLR 2026 is **not confirmed**: the paper only says it follows the ICLR code of ethics. |
| 6 | Huang et al., LLMs cannot self-correct reasoning yet (ICLR 2024) | **VERIFIED** | arXiv 2310.01798. The comments field says ICLR 2024. Intrinsic self-correction sometimes degrades results. Note that the gains with *external* feedback are the authors' framing, not a measurement on our kind of tools. |
| 7 | BFCL v4 multi-turn: Qwen3-4B 22%, Mistral Small 12%, Gemma 3 12B 6% | **VERIFIED** (numbers) / **MISREPRESENTED** (draft's generalization) | I downloaded `data_overall.csv` myself (Last-Modified 2026-04-13). Qwen3-4B-2507 (FC) 22.12; Mistral-small-2506 (FC) 11.50; Gemma-3-12b **(Prompt mode only, no FC row)** 5.75. The draft's "multi-turn collapses for models ≤ 14B" is contradicted by the same CSV: xLAM-2-8b (FC) scores **70.00**, above Opus 4.5's 68.38; Llama-3.3-70B (FC) scores **21.50**; Qwen3-8B (41.75) beats Qwen3-14B (34.75). Size isn't the variable. |
| 8 | GLM-5.3-Flash ≈ 320B total / 18B active, 1M context | **PARTIALLY** | Several secondary sources agree on ~320–321B / 18B active and 1,048,576 context (OpenRouter listing, vLLM recipe, guides). The z.ai primary model card wasn't checked. One source says the model card's long-context evals only go to 300k. It's listed on OpenCode Go `/chat/completions` (VERIFIED). |
| 9 | ClinePass: "You can use ClinePass models from your own scripts, apps, or automation through the Cline API"; `api.cline.bot/api/v1/chat/completions` | **VERIFIED** | Exact quote and endpoint on the docs page. **But the page has no ToS or fair-use text.** "Allowed" is a docs statement, not a contractual grant. The full Cline ToS hasn't been read (research question R9). |
| 10 | OpenCode Go "designed for OpenCode and other coding agents"; own User-Agent; `x-opencode-session` | **VERIFIED** | The validated-client list is longer than [PM] reports: Hermes, Claude Code, Codex, ZCode, Pi, jcode, Kilo Code CLI. GLM-5.3-Flash is listed as not used for training, with 0-day retention. |
| 11 | z.ai GLM Coding Plan "may only be used within officially supported tools and products"; ban after >3 violations | **VERIFIED** | Quote confirmed on the usage-policy page. |
| 12 | OpenAI: GPT-5.4+ no Chat Completions tool calls with reasoning; GPT-6 requires Responses | **VERIFIED** | "Starting with GPT-5.4, Chat Completions does not support tool calling with `reasoning_effort` values other than `none`." "GPT-6 Astra and GPT-6.1 Sol require the Responses API for tool calling." This resolves the draft's "[E — verify which models]", against the draft's deferral (finding H2). |
| 13 | "Apps are frozen ~10 s after going to the background" | **PARTIALLY / imprecise** | The AOSP page says: "App processes in the **cached state** are frozen 10 seconds after entering the cached state". That's Android 14 (API 34)+. The freezer itself exists since Android 11. "Background" ≠ "cached": a process with a running FGS isn't cached. Nothing OS behaviour on top of this is unknown. |
| 14 | `ACCESS_LOCAL_NETWORK` for Android 17 | **VERIFIED** | Runtime permission, `NEARBY_DEVICES` group. Enforced only for apps **targeting API 37+**; apps targeting ≤36 get an implicit grant via INTERNET. On Android 16 it was opt-in through `NEARBY_WIFI_DEVICES`. |
| 15 | Jetpack security-crypto deprecated | **VERIFIED** | "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore". 1.1.0-alpha07 (9 Apr 2025), repeated in 1.1.0-beta01 (4 Jun 2025). |
| 16 | Gemini free tier: prompts may be used for training, human review | **VERIFIED** | Unpaid Services: used "to provide, improve, and develop" products; "human reviewers may read, annotate, and process". **Missed nuance:** in the EEA/CH/UK, paid-terms data use applies to everything, *and* "You may use only Paid Services when making API Clients available to users in" those regions. How that applies to a BYOK app is unresolved (research question R10). |
| 17 | agentskills.io SKILL.md format | **VERIFIED** | Required `name` (≤64, lowercase-hyphen, must match the directory) and `description` (≤1024 chars). Body "< 5000 tokens recommended", SKILL.md < 500 lines. **`load_skill(name, section?)` section loading is our extension, not in the spec.** That's fine, but say so. |
| 18 | Chroma Context Rot | **VERIFIED** | 18 models; "performance grows increasingly unreliable as input length grows"; LongMemEval focused ~300 tokens vs full ~113k, "significantly higher performance on focused prompts" across all models. |
| 19 | Few-shot "3 ≈ 9" | **VERIFIED, narrow** | LangChain 2024, *Multiverse Math* only: "Inserting 3 examples as messages usually has comparable performance to using all 9." Claude 3 Haiku 11% → 75% is VERIFIED. Claude 3 Sonnet 16% → 52% is from a *different* dataset (Query Analysis). The models are from 2024. |
| 20 | Glyph Museum 4a Pro row widths [5,9,11,11,13,13,13,13,13,11,11,9,5] = 137 | **VERIFIED** | Phone (3) rows also match (489). **Side note:** GM describes `v` as "Format version. Identifies the target matrix" (v1 = Phone 3, v4 = 4a Pro). [SYN]'s "device ID, not a format version" is only half right. Doesn't affect the agent. |
| 21 | Glyph Matrix SDK: 4a Pro supports AOD toys only, ~1 event/minute; `setAppMatrixFrame` needs system ≥ 20250801 | **VERIFIED** | "`DEVICE_25111p` does not support Glyph Touch and only features AOD toys"; "every minute". **New finding:** the SDK ships as `glyph-matrix-sdk-2.0.aar` under a **closed-source EULA** (finding C1). |
| 22 | Koog: Apache-2.0, 1.x stable, Android support | **PARTIALLY** | Apache-2.0, 1.3.0, Kotlin ≥ 2.3.10, JDK 17+ and the "stable, semver" statement are VERIFIED. **The README is inconsistent on Android**: the feature list includes it, but "Supported targets" lists JVM, JS, WasmJS and iOS only. I didn't re-check the 1.0 date (May 2026). |
| 23 | kotlinx-schema is experimental | **VERIFIED** | "JetBrains incubator / Experimental" badges, no releases. **It renders sealed classes as `oneOf` + `$ref`/`$defs`**, which directly contradicts the "flat, no `oneOf`" rule in draft §3. |
| 24 | OTel GenAI semconv at Development status | **PARTIALLY** (consistent secondary sources) | Several 2026 sources agree that nothing under `gen_ai.*` is Stable and that it moved to `semantic-conventions-genai` in June 2026 (v1.42.0) with no tagged release by mid-July. Some blogs claim otherwise. The primary repo wasn't checked. Low stakes, since the draft only borrows the field names. |
| 25 | Harding 2005: ≥ 20 cd/m², ≥ 3 Hz, ≥ 0.006 sr | **VERIFIED, but superseded** | Abstract confirmed. **There is a newer consensus: Fisher, Kasteleijn-Nolst Trenité, Wilkins et al., "Visually-provoked seizures: Consensus of the Epilepsy Foundation Working Group", *Epilepsia* 2026;67(2):565–581.** It updates the 2005 guidance. [UX] cites only the 2005 paper and a 2022 review. I couldn't read the 2026 thresholds (403); research question R7. |
| 26 | WCAG 2.3.1 "> 3 flashes in any 1 s" | **VERIFIED, but the draft drops conditions** | A general flash is "a pair of opposing changes in relative luminance of 10% or more … where the relative luminance of the darker image is below 0.80", with an area exemption at 0.006 sr. Draft §4's bare "more than 3 flashes/s" drops both the area and the luminance conditions. |
| 27 | OpenAI "fewer than 20 functions" | **VERIFIED** | The page calls it "just a soft suggestion". |
| 28 | `shortService` ≈ 3 min, `onTimeout` → ANR | **VERIFIED** | The timeout starts at `startForeground()`. An unstopped service leads to an ANR even if the app has other FGS. A restart is only possible while the app is visible or exempt. |
| 29 | models.dev is MIT | **VERIFIED** | Repo `anomalyco/models.dev`, SPDX MIT. ([PM] cites `sst/models.dev`; GitHub resolves to anomalyco.) |
| 30 | On-device "not viable on the 4a Pro" tagged **[E]** | **PARTIALLY** | It rests on one secondary GitHub benchmark plus BFCL priors. Directionally fine, but it should be [P]/secondary, not [E]. |

Not re-checked in this session (they come from source reports marked [verified], and the draft doesn't depend critically on them): MLLM-as-a-Judge, the 61% order-swap audit, Constraint Tax (2606.25605), NLT, SkillsBench, NoLiMa, Manus, the Anthropic engineering posts.

---

## 3. Severity-ranked findings

### CRITICAL

**C1. The Glyph Matrix SDK licence is incompatible with an open-source distribution as currently designed.** (Draft §2 `:app … Glyph SDK bridge`; D1)
- **Issue:** The draft puts the SDK in `:app` as an ordinary dependency. Neither the draft nor any source report has read its licence.
- **Evidence:** `LICENSE.md` in the GlyphMatrix-Developer-Kit repo is a *Glyph SDK End User License Agreement*. It says:
  - "not … sublicense, sell, redistribute, or otherwise transfer the Software except as expressly authorized" (§2.1c)
  - "licensed as a closed-source product" (§2.1e)
  - "**Commercial use of the Software is strictly prohibited** unless you obtain prior written permission" (§2.2)

  The SDK is distributed as a binary `glyph-matrix-sdk-2.0.aar`. F-Droid's inclusion policy requires "All binary dependencies … [to] originate either from source compilation or Debian", and otherwise asks for a FLOSS alternative or build flavour.
- **Consequences:**
  - (a) Committing the `.aar` to a public repo is likely "redistribution".
  - (b) A permissive OSS licence on GlyphWizard lets downstream users sell builds that embed a no-commercial-use SDK.
  - (c) F-Droid needs a flavour without the SDK.
  - (d) Play is OK in principle, but the commercial clause matters if the app is ever monetised.
- **Fix:** Before any architecture lock:
  - Put the SDK behind an interface in a separate `:glyph-sdk` module, with a build flavour that has no SDK (preview-only, or "export to Glyph Museum" only).
  - Don't commit the AAR. Fetch it at build time from Nothing's repo.
  - Email `GDKsupport@nothing.tech` for written clarification on OSS use.
  - Pick the app licence with this constraint in mind.

  This is mostly outside the agent, but the draft's module diagram is where it has to be designed.

### HIGH

**H1. Backgrounding: the draft resolves a three-way disagreement silently, and the chosen design is internally unsafe.** (§7)
- **Issue:** The draft says "frozen ~10 s after going to the background … So a turn runs under a short foreground service" and presents it as settled.
- **Evidence:**
  - The sources disagree. [TR] makes `shortService` an *optional add-on* (§Q5, Open Q3). [PM] offers "shortService *or* cancel and roll back" and asks the user (Open Q5). [UX] calls an FGS "overkill for v1" and suggests letting the in-flight request finish and *resuming on return*, which contradicts the draft's "no auto-resume".
  - The freezer claim is about the *cached* state on Android 14+ (table row 13), not "background".
  - `shortService` is about 3 min and ANRs if not stopped, while [TR] proposes a per-turn wall clock of ~5 min (§Q5 "Per-turn wall clock ~5 min"). The draft keeps neither number, so nothing guarantees the turn ends before the ANR.
  - Play's FGS-type declaration requirements for `shortService` aren't verified anywhere.
- **Fix:**
  - v1 default: persist after each step (already planned) plus the Interrupted UX, with no FGS.
  - If an FGS is added later: turn wall clock < 170 s, and `onTimeout` routes to the Stop path.
  - Measure what actually happens on Nothing OS (add to the §11 hardware spike: background a turn for 30 s and 3 min).
  - Correct the wording to "cached state, Android 14+".
  - Record the decision in decisions.md.

**H2. Deferring the Responses API makes OpenAI's current models tool-less.** (§5)
- **Issue:** The draft says Responses is "deferred unless needed … [E — verify which models]".
- **Evidence:** Verified (row 12). Every OpenAI model from GPT-5.4 onwards with reasoning enabled, and GPT-6 Astra / 6.1 Sol in any mode, can't do tool calling on Chat Completions. OpenCode Go routes GPT 6 Luna / GPT 5.6 Luna and Grok 4.6/4.7 to `/responses` only. [PM] contradicts itself: its TL;DR says Responses is "required", its risk table says "add Responses only when needed".
- **Fix:** Make an explicit user decision.
  - Either ship v1 with *two* adapters and **no OpenAI preset**, saying so plainly ("OpenAI: coming later; use OpenRouter"). Note that this needs checking whether OpenRouter proxies GPT-5.4+ tool calls with reasoning: research question R2.
  - Or ship the Responses adapter in v1.

  Don't leave it as "deferred unless needed", because it *is* needed for the OpenAI preset.

**H3. Variants: two incompatible mechanisms are merged into one draft.** (§3 vs §8)
- **Issue:** §3 lists `create_variant` + `present_variants`. That's [TR]'s design: fork the document, then later writes take `variant?`, which means multi-call variant building. §8 says "one call returns 2–3 command sets, rendered locally", which is [UX]'s design (A7.3, explicitly "Don't run three full agent loops").
- **Why it matters:** These imply different tool schemas, journal semantics ([TR] risk 8), and costs.
- **Fix:** Pick one, or (recommended) **cut variants from v1** (see §5). If kept, [UX]'s single-call command-set design is cheaper and keeps the schema small. It needs one `propose_variants{variants:[{label, ops:[…]}]}` tool, and `create_variant` disappears.

**H4. Undo semantics after interleaved user edits are missing.** (§7 Concurrency, §8 Edits; D9)
- **Issue:** The draft promises a "Keep/Undo bar" and "apply directly + Undo". D9's history is linear.
- **Evidence:** [UX] A7.1 flags the trap explicitly. Once the user edits after an agent turn, "Undo that turn" requires selective undo, which tldraw abandoned per [SYN]. [UX] proposes "Undo only while latest, otherwise 'Restore to before this turn' with a warning". The draft drops this.
- **Second issue:** D9's cap is ~200 steps / 256 KB, and one agent turn = one group. A single `paint` with `frames:"all"` on a long 25×25 raster can produce a JSON-Patch forward+inverse pair larger than the whole cap. Eviction then makes the turn un-undoable, which breaks the safety argument in §9 ("vandalism is undoable").
- **Fix:**
  - Adopt [UX]'s restore wording.
  - Specify that an agent group is never evicted while it is the latest group, or snapshot the document before each agent turn instead of patching (one document copy per turn is cheap at this size).
  - Add a test: agent turn → user edit → Undo-turn.

**H5. Google Play's AI-generated-content policy isn't addressed.** (Gap; §8/§9)
- **Evidence:** Play's Developer Program Policy requires apps that generate content with AI to provide **in-app reporting/flagging of offensive AI output** (announced Oct 2023, in force early 2024, reaffirmed June 2024). Secondary coverage is consistent, and the primary page is the Play Console "Developer Program Policy".
- **Issue:** Whether BYOK changes this is unknown. It also bears on the Data safety form, since document content goes to a user-chosen third party.
- **Fix:** Decide the distribution channel (Play, F-Droid, or both: research question R1). If Play, add a "Report this response" action on the turn card. It can be local-only, e.g. it writes a debug bundle; check whether local-only satisfies the policy.

**H6. The capability bar is justified by a mis-generalization of BFCL, and it partly contradicts D17.** (§5)
- **Issue:** "multi-turn collapses for models ≤ 14B on BFCL [E]" sets the bar's rationale.
- **Evidence:** Row 7. The same CSV shows an 8B tool-tuned model at 70% multi-turn (best in the table), a 70B model at 21.5%, and 14B below 8B. BFCL multi-turn is also a different domain (simulated file system, trading and travel APIs).
- **Second issue:** "≥ 32k effective context" and "native tool calls" are *a priori gates*. D17 says "tiers are defined by eval-harness results, not by naming models up front". A gate is not a model name, but excluding text-protocol tool use up front is the same kind of pre-judgement. [PM] itself shows prompt mode beats FC for Gemini-3-Pro.
- **Fix:**
  - Reword the evidence to "multi-turn tool reliability varies 0–70% and is driven by tool training more than size [BFCL CSV 2026-04-13]".
  - Keep the probe as a cheap *gate*, but tag the 32k and native-FC thresholds [P], to be calibrated against harness results.
  - State the D17 relationship explicitly: the probe is a fast pre-filter, and the harness is the authority.

### MEDIUM

**M1. The context budget is inconsistent across draft and sources.** (§6)
- **Evidence:**
  - The draft says "Always-loaded core, ≤ 5k tokens" and lists prose items only.
  - [CTX] §4: core *prose* ≤ 2k, tool schemas ≤ 2.5k, trajectory ~0.6k, **stable prefix total ≤ 5k**.
  - [PER] §3: core ≤ ~1.5k.

  As written, the draft allows a 5k prose core *plus* schemas, about 7.5–8k. That breaks the 32k mid-tier arithmetic in [CTX] §4 ("leaves ≈12–16k for conversation").
- **Fix:** State "prose core ≤ 2k; stable prefix (prose + schemas + trajectory) ≤ 5k", and lint both numbers.

**M2. The tool-count evidence is misapplied.** (§3)
- **Evidence:** Rows 1, 2 and 27.
  - MCP-GRANITE's optimum is ~4 tools per scenario, with ≤ 20.9B models, on IoT mocks.
  - WorkBench is about exposure, not granularity.
  - OpenAI's "< 20" is "a soft suggestion".
  - [TR] admits "It doesn't support a magic number."
- **Fix:** Tag "~16" as [P]. Better, start at ~10 (§5 cuts) and let the harness justify each addition, which [TR] risk 6 already proposes.

**M3. Tools named elsewhere are missing from the catalogue.** (§3 vs §6, [CTX], [UX])
- **Issue:** §6 promises "2–3 [exemplars] retrieved by tags plus BM25", but there's no `search_examples`/`get_example` tool. [CTX] also relies on `describe_clip`, `shape_template`, `text_to_raster` and `update_brief`, and [UX] on `import_image(params)` and reading @-mentioned docs. None of them are in the 16.
- **Consequence:** Without retrieval tools, exemplars must be host-injected. If they're spliced in before the state block, the prompt-cache rule in [CTX] §3.6 breaks.
- **Fix:** Either add `find_examples` (and drop BM25, see §5) or state "host appends exemplar summaries as a synthetic tool result after the user message". Fold shapes and text into `paint.kind` (already in [TR]'s schema) and say so.

**M4. Budget defaults conflict and the spend-cap semantics are undefined.** (§7 Limits)
- **Evidence:** [UX] sets ≤ 8 model calls and ≤ 25 tool calls per turn. [TR] sets maxSteps 15 × 8 calls per step × 64 ops. The draft says "step cap" with no number.
- **The real gap:** a $ cap can't be enforced *before* a call. Reasoning tokens are unknown in advance, pricing is unknown for custom endpoints ([UX]: "never guess"), and subscriptions have no $ at all. Is the cap checked between calls, with overshoot by one call allowed?
- **Fix:** One table of defaults. Define caps as "checked between model calls; max overshoot = one call's `max_tokens`". Use tokens/requests for subscriptions. Map ClinePass `ClinePassLimitError` and 5-hour windows to a specific UX message.

**M5. The flash check is under-specified and cites superseded guidance.** (§4)
- **Evidence:** Rows 25–26. WCAG's general flash needs a ≥10% luminance swing with the darker state < 0.80, and an area condition. [UX] included "≥25% of mask LEDs"; the draft dropped it. A 2026 Epilepsy Foundation consensus updates Harding 2005.
- **Consequence:** A bare "> 3 flashes/s" will fire on single blinking pixels (false positives), which trains users to ignore it.
- **Fix:** Spec the check as [UX] A8.6 did. Read Fisher et al. 2026 before setting thresholds. Calibrate area and luminance against the hardware spike.

**M6. Cleartext LAN carries API keys and prompts with no host restriction.** (§9 LAN)
- **Evidence:** [PM] Q6 recommends allowing `http://` only for RFC1918 / `.local` / loopback, because the network-security config has no CIDR syntax, so `base-config cleartextTrafficPermitted=true` opens cleartext *globally*. The draft doesn't carry this over. Self-hosted vLLM/LM Studio often use `--api-key`, which would cross the LAN in clear.
- **Fix:** Enforce the private-range check in app code. Warn when a key is set on an `http://` endpoint. Note that `ACCESS_LOCAL_NETWORK` only applies once targetSdk is 37.

**M7. Provider behaviour drift is real, already documented, and has no fix path in a "fully local" app.** (§5, §12 Q11)
- **Evidence** (checked this session): Anthropic docs say that Claude 4.7+ return **400 for `temperature` < 1 or for setting both temperature and top_p**, and that the newest Claude models (Opus 5.5, Sonnet 5.5, …) **don't support forced `tool_choice`**. That breaks [PM] Q3's advice to use `tool_choice: required` on repair steps. Anthropic's OpenAI-compat layer drops prompt caching and `strict`.
- **Issue:** With a registry bundled per release and no runtime refresh (D1), every such change needs an app update.
- **Fix:**
  - Adapters must never send sampling params by default.
  - Make quirk flags user-toggleable in the endpoint's advanced settings.
  - Add a "copy raw error" action.
  - Decide whether an *optional* registry refresh from a static GitHub file is compatible with D1 (research question R11).

**M8. Versioning and migration across saved conversations are absent.** (§6, §7 Persistence)
- **Issues:** Transcripts are persisted per document, but nothing covers:
  - (a) tool-schema changes between app versions, since replaying old `tool_call`s against a new tool list can 400 or confuse the model
  - (b) `providerExtras` (thought signatures, reasoning_details) after a model or endpoint switch. [PM] says they're valid only for the same model and in-progress loop, so they must be stripped.
  - (c) `kbVersion` changes mid-conversation
  - (d) generator `@N` bumps referenced in old turns
- **Fix:** Store `{appVersion, kbVersion, toolsetHash, endpointId, modelId}` per transcript. On mismatch, start the model context fresh from the document plus brief, and keep the old chat as read-only display. That's cheap, because the document is the memory ([CTX] 3.5).

**M9. There's no deterministic test strategy for the loop itself.** (§10)
- **Issue:** The draft has only live evals. [TR] §Q8 proposes a **scripted fake provider replaying recorded transcripts** (cancellation, malformed calls, 429s, parallel calls, interrupted turns). The draft dropped it.
- **Why it matters:** Live evals are costly and non-deterministic, and they can't regression-test cancellation or persistence.
- **Fix:** Add a `FakeProvider` plus golden-transcript tests to `:agent`. Record real provider streams (including the Ollama/Gemini quirks [PM] lists) as fixtures.

**M10. The tier taxonomy differs across documents.** (§5, §10)
- **Issue:** [PER] uses T0/T1/T2. [PM] uses Supported / Below bar + V. [UX] uses Recommended / Supported / Experimental / Unsupported, with "T1 families" thresholds. The draft uses [PM]'s, but §10 publishes `SUPPORT.md` without saying which labels or thresholds.
- **Fix:** Pick one vocabulary in decisions.md and have the harness emit exactly those labels.

**M11. "Rule of Two" is overstated.** (§9)
- **Issue:** The draft says the agent has "no exfiltration channel". But:
  - every model call sends document content, including user-typed text such as names, to a third party
  - the debug bundle is an export
  - agent text can contain links the user taps
  - imported files reach the model
- **Fix:** Reword to "no *agent-controlled* outbound channel". Spell out the provider and user-initiated flows in the privacy notice. Keep spotlighting/sanitization.

**M12. Not every self-hosted user is reachable over the LAN; offline behaviour is undefined.** (Gap)
- **Issue:** What happens with no network, airplane mode, or a LAN server asleep mid-turn? Retries cover 5xx and network errors, but there's no UX for "endpoint unreachable at turn start", and no check that the agent UI disappears cleanly with no key (D1/D2 require the app to be fully usable without a key).
- **Fix:** A pre-flight reachability check, a clear disabled state, and no queued auto-send.

### LOW

**L1.** §3 MCP-GRANITE is tagged "[A: citation unverified]". It is now verified (row 1). Update the tag and the usage per M2.
**L2.** §5 "On-device: not viable [E]" should be [P] (single secondary benchmark; row 30).
**L3.** §7 "Koog spike ½–1 day" merges [TR]'s 1 day and [PM]'s ½ day. The README's Android support is inconsistent (row 22), which makes the spike's first question "does it build for Android at all".
**L4.** §3 "kotlinx-schema … [A]": known behaviour (sealed → `oneOf`) conflicts with the flat-schema rule. Either avoid sealed arg types or hand-write schemas (the 200-line `SerialDescriptor` walker in [CTX] §Q5).
**L5.** §4 "keep the best" (self-repair) needs a defined "best" metric and a snapshot mechanism. `revert` only undoes the agent's own sub-steps.
**L6.** i18n isn't mentioned. The vocabulary map ("wonky / jerky / busy"), the BM25 tokenizer and the skills are English-only. Users will type in other languages. At minimum, state "English KB; the model translates", and add a non-English eval task.
**L7.** Accessibility isn't mentioned: TalkBack for the chat and grid, and a non-visual description of the animation. The text-grid/analysis stack could cheaply produce a spoken summary. Note it as a v1.1 opportunity.
**L8.** §12 Q1 minSdk can now be answered with evidence. The 4a Pro launched on **Android 16 / Nothing OS 4.1** (Nothing support page; 9to5Google). A Nothing OS 5 beta brings Android 17. That supports minSdk ≥ 35 for the target devices. The Phone (3) launch version wasn't checked.

---

## 4. Gaps needing targeted research (concrete questions)

- **R1. Distribution channel and SDK licence.** Play, F-Droid, GitHub releases, or several? For each:
  - (a) Can an OSS repo reference, rather than commit, the Glyph Matrix SDK AAR, and does Nothing permit OSS use? Ask GDKsupport.
  - (b) Does F-Droid accept a no-SDK flavour, and which anti-features apply? NonFreeNet for user-chosen AI APIs? NonFreeDep? The F-Droid Anti-Features page and maintainer precedent for BYOK-LLM apps.
  - (c) Play: Data safety declarations for user-configured third-party AI, and whether the AI-generated-content reporting rule applies to BYOK apps.
- **R2. OpenAI via OpenRouter.** Does OpenRouter's Chat Completions endpoint deliver tool calls for GPT-5.4+/GPT-6 with reasoning on, by translating to Responses upstream? If yes, H2 can be resolved by routing OpenAI users to OpenRouter.
- **R3. Nothing OS process behaviour.** On the 4a Pro, what happens to an in-flight OkHttp/Ktor stream 10 s, 60 s and 3 min after Home is pressed, with and without `shortService`? Does Nothing OS add OEM killing beyond AOSP (dontkillmyapp-style data)?
- **R4. Play FGS policy for `shortService`.** Is a Play Console FGS declaration required, and is `POST_NOTIFICATIONS` needed for the notification to show?
- **R5. Undo cost envelope.** The worst-case JSON-Patch size of one agent turn on a 25×25, 200-frame raster vs D9's 256 KB cap. Is a per-turn document snapshot cheaper and simpler?
- **R6. Transcript replay across tool-schema versions.** For Anthropic Messages, OpenAI Chat Completions and Gemini compat: does sending historical `tool_use`/`tool_calls` whose tool name or schema no longer exists in `tools` cause a 400 or degrade behaviour?
- **R7. Photosensitivity.** What thresholds does Fisher et al. 2026 (Epilepsia 67(2):565–581) set for small, very bright, close-viewed sources? Measure the matrix diameter and luminance at 255 (hardware spike).
- **R8. Few-shot on current models.** The "3 ≈ 9" and Multiverse-Math numbers are from Claude 3 (2024). Is there 2025–26 evidence of few-shot trajectory value on current frontier and open-weight agent models, or should the canonical trajectory be ablated before it gets a permanent 600-token slot?
- **R9. Subscription ToS beyond docs pages.** Read the full Cline ToS, Synthetic ToS and Chutes ToS (not just docs statements) for clauses on embedding in third-party apps or redistribution of access.
- **R10. Gemini EEA clause.** Does "You may use only Paid Services when making API Clients available to users in the EEA/CH/UK" bind a BYOK app's *developer*, meaning the app must block or warn on free-tier Gemini keys for EEA users?
- **R11. Optional registry refresh vs D1.** Is fetching a static, signed quirks/price JSON from GitHub acceptable under "fully local", if it's off by default?
- **R12. BFCL-like evidence for 2026 open-weight models.** BFCL's CSV (Apr 2026) predates GLM-5.x, Qwen3.5+ and Gemma 4. Is there a newer multi-turn tool benchmark (τ²-bench, BFCL updates) covering the gateway models the draft expects to clear the bar?
- **R13. i18n.** Do vague-word → metric mappings survive translation? A small non-English task set in the harness.

---

## 5. Overengineering cuts (v1)

The evidence-gathering spine stays: `:core`/`:agent` pure Kotlin, a text grid, 4–5 analysis checks, apply + undo, a probe, a small harness. Everything below can wait until the harness shows a need.

| Cut / simplify | Justification |
|---|---|
| **Variants** (`create_variant`, `present_variants`, variant strip UI) → v1.1. For ambiguity, ask one clarifying question. | Two incompatible designs (H3), journal complexity ([TR] risk 8), UI work ([UX] risk 1 lists variants second-tier). Clarifying questions satisfy the "ambiguity" eval family. |
| **Vision tier** (`render_image`, contact sheets, overlays, VLM judge) → v1.1 | [PER] itself: "build T1 (text + analysis) first and add images only when the harness shows a gain." VLM perception on these grids is the weakest-evidenced part. |
| **Pairwise VLM judge + human calibration rounds** → later | Only needed for *create* tasks. v1 can score programmatically plus hand-spot-check a few. |
| **Tools 16 → ~10**: merge `view_frame` + `view_motion` → `view`; fold `manage_layers` and `set_document` into `edit` targets; drop the variant tools | Stays far under any tool-count guidance. Fewer schemas means a smaller prefix (M1). Each removed tool can return with eval evidence ([TR] risk 6). |
| **Analysis breadth** → v1 checks: centering, mirror symmetry, lit count/orphans, flash (safety), loop seam. Defer jaggies, roundness-vs-canonical, jerk, busyness | Each metric needs threshold calibration on hardware ([PER] risk "thresholds feel arbitrary"). Ship the ones the first eval tasks need. |
| **BM25 exemplar retrieval** → `list_examples` returns all ~10 titles + one-liners; `get_example(id)` | At n=10, the model can choose from the full list (~150 tokens). BM25 pays off at 50+. |
| **Model registry seeded from models.dev + live OpenRouter/Ollama metadata** → probe + user-entered context length + tiny hand list | A probe plus override is enough to gate. models.dev integration and price snapshots can follow once spend display needs it (tokens-only display is acceptable: [UX] "never guess"). |
| **Foreground service** → none in v1; step persistence + Interrupted UX | H1. Two of three reports call it optional or overkill. |
| **Koog spike** → skip unless the hand-written Chat Completions adapter takes more than ~3 days | The thin loop is small ([TR] §Q6). The README's Android ambiguity raises spike cost. |
| **Eval scope** → 3 families × ~20 tasks (edit, repair-planted-defect, safety/injection) + the probe; pass@1 and pass^3 at k=3 | Gives a support table at ~1/4 of the cost. Add create/ambiguity/perceive when those features ship. |
| **KB tooling**: keep generated device card + golden tool schemas. Defer doc-tests, per-tier assembly snapshots, `provisional` lint | Generated facts prevent "clash" cheaply. The rest is process overhead before there's content to protect. |
| **Subscription matrix** → presets: OpenRouter, Anthropic, Custom OpenAI-compatible, Ollama/LAN (+ OpenAI if H2 resolves). Gateways via "Custom" with the ToS note | Fewer presets means less ToS liability to maintain. ClinePass, Synthetic and Chutes all work as Custom. |
| **OTel naming, trace sheet, debug bundle** → keep a JSONL trace file + "share debug bundle". Defer the in-app timeline sheet | A solo dev debugs from files first. |

Rough effect: this removes ~6 tools, one adapter-adjacent subsystem (registry), the vision path, the judge, the FGS and about half the analysis metrics from v1, without removing any mechanism needed to *gather evidence* for adding them back.

---

## 6. Sources (checked this session)

Papers / benchmarks
- MCP-GRANITE, arXiv 2609.24161: https://arxiv.org/abs/2609.24161
- WorkBench, arXiv 2405.00823 (PDF body, "accuracy falls from 49% to 43%"): https://arxiv.org/pdf/2405.00823
- Vision language models are blind, arXiv 2407.06581: https://arxiv.org/abs/2407.06581
- Vision Language Models are Biased, arXiv 2505.23941: https://arxiv.org/abs/2505.23941 · https://arxiv.org/html/2505.23941
- Huang et al., LLMs Cannot Self-Correct Reasoning Yet, arXiv 2310.01798: https://arxiv.org/abs/2310.01798
- BFCL leaderboard CSV (Last-Modified 2026-04-13): https://gorilla.cs.berkeley.edu/data_overall.csv
- Greenblatt, ARC-AGI with GPT-4o: https://blog.redwoodresearch.org/p/getting-50-sota-on-arc-agi-with-gpt
- Chroma, Context Rot: https://www.trychroma.com/research/context-rot
- LangChain, few-shot tool calling: https://www.langchain.com/blog/few-shot-prompting-to-improve-tool-calling-performance
- Harding et al. 2005: https://onlinelibrary.wiley.com/doi/10.1111/j.1528-1167.2005.31305.x
- Fisher et al. 2026, Visually-provoked seizures consensus (not read, 403): https://onlinelibrary.wiley.com/doi/10.1111/epi.18702 · https://researchinformation.umcutrecht.nl/en/publications/visually-provoked-seizures-consensus-of-the-epilepsy-foundation-w/
- WCAG 2.3.1: https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html

Providers / ToS
- OpenAI migrate to Responses: https://developers.openai.com/api/docs/guides/migrate-to-responses
- OpenAI function calling: https://developers.openai.com/api/docs/guides/function-calling
- Anthropic OpenAI-SDK compatibility (temperature/top_p 400s, no forced tool_choice on newest models, no caching): https://platform.claude.com/docs/en/api/openai-sdk
- ClinePass: https://docs.cline.bot/getting-started/clinepass
- OpenCode Go: https://opencode.ai/docs/go/
- z.ai usage policy: https://docs.z.ai/devpack/usage-policy
- Gemini API terms: https://ai.google.dev/gemini-api/terms
- GLM-5.3-Flash (secondary): https://openrouter.ai/z-ai/glm-5.3-flash · https://recipes.vllm.ai/zai-org/GLM-5.3-Flash · https://codersera.com/blog/glm-5-3-flash-complete-guide-2026/
- models.dev: https://github.com/anomalyco/models.dev

Android / platform
- Cached apps freezer: https://source.android.com/docs/core/perf/cached-apps-freezer
- FGS types (shortService): https://developer.android.com/develop/background-work/services/fgs/service-types
- Local network permission: https://developer.android.com/privacy-and-security/local-network-permission
- AndroidX Security release notes: https://developer.android.com/jetpack/androidx/releases/security
- Phone (4a) Pro OS: https://support.nothing.tech/hc/en-us/articles/43227880398353-What-operating-system-does-Phone-4a-Pro-use · https://9to5google.com/2026/03/05/nothing-phone-4a-specs-price-release-date/

Glyph / Nothing
- Glyph Museum developers: https://glyphmuseum.com/developers
- GlyphMatrix Developer Kit README + LICENSE.md (EULA): https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit · https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/LICENSE.md

Tooling / standards
- Koog README: https://github.com/JetBrains/koog
- kotlinx-schema: https://github.com/Kotlin/kotlinx-schema
- Agent Skills spec: https://agentskills.io/specification
- OTel GenAI status (secondary): https://john-hodge.com/blog/opentelemetry-genai-semantic-conventions/ · https://greptime.com/blogs/2026-05-09-opentelemetry-genai-semantic-conventions

Distribution / policy
- F-Droid inclusion policy: https://f-droid.org/docs/Inclusion_Policy/
- Google Play AI-generated content reporting (primary: Play Console Developer Program Policy; secondary coverage): https://support.google.com/googleplay/android-developer/answer/17190352 · https://android-developers.googleblog.com/2023/10/announcing-policy-updates-to-support-app-quality-on-google-play.html · https://android-developers.googleblog.com/2024/06/enabling-safe-ai-experiences.html
