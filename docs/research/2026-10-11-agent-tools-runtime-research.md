# Agent Tools and Runtime Research (A2 Tool/API design · A3 Agent runtime on Android)

Date: 2026-10-11
Status: research and recommendations only (no app code). Inputs: `agent-research-brief.md`, `decisions.md` (D1–D15), `2026-10-11-animation-model-synthesis.md`, `2026-10-11-agent-perception-research.md`, and §3 of `2026-10-11-novel-animation-models-research.md`.
Scope: tool catalogue, write semantics, raster painting input, schema generation, MCP-readiness, the agent loop on Android, frameworks, concurrency, and the core module boundary. This report does **not** cover provider adapters or model reliability, context engineering (skills, glossary, examples), or UX, safety and evals. Where those touch this report I flag a hand-off.

Confidence markers (same as the perception report):
- **[verified]**: checked against a primary or near-primary source in this session.
- **[secondary]**: blog, summary, or third-party source.
- **[speculative]**: my reasoning, not backed by direct evidence.
- **[verify]**: from memory or conflicting sources; check before relying on it.

---

## 1. TL;DR and recommendations

1. **Expose about 12–16 task-level tools.** v1 targets mid-capability and stronger models (user scope update, 2026-10-11). Never one tool per command, and never a single `apply_ops` mega-tool. A ~7-tool reduced set is sketched only as the **cost of supporting small/8k-context models later** (§3b). It is not a v1 requirement. Evidence:
   - MCP-GRANITE: ~4 task-level tools beat fine-grained primitives by 16.4% and a monolithic tool by 33.6% on task completion, with argument accuracy almost doubled. A 3.2B model at the right granularity beat a 20.9B model at the wrong one.
   - WorkBench: GPT-4 accuracy fell from 49% to 43% when it saw all 26 tools instead of only the needed ones.
   - OpenAI's guide says "Aim for fewer than 20 functions available at the start of a turn".
   - Canva consolidated its editing tools into `read_design` / `edit_design` **[secondary]**.
   - Per-model support is decided from eval results, not vendor claims (consistent with the T1/T2 tiers in the perception report).
2. **Every write returns the same envelope:** `ok`, changed IDs, a compact text preview of the affected frame(s), lint warnings, and the step number. Failed calls return an **actionable error** with valid alternatives (see §4.4).
   - The MCP spec says validation and business-logic errors belong in the tool result (`isError: true`) "so models can self-correct" **[verified]**.
   - Anthropic says to replace opaque codes with fix-guidance **[verified]**.
   - Blender MCP's builders call visual feedback after every operation the biggest quality lever **[secondary, carried over]**.
3. **Prefer absolute ("set") arguments and replace-all lists to make writes idempotent.** Address objects by short, stable, type-prefixed IDs (`c3`, `L1`) plus an optional human label. Never use array indices.
4. **Transactions:** one user message to the agent opens one undo group (D9). Each tool call is an atomic sub-step inside it: all ops in the call apply, or none do. A `revert` tool lets the agent undo its own sub-steps.
   - **No agent-facing `dry_run` flag in v1.** In-turn revert plus variants cover exploration with less schema surface.
   - The human sees the result live, with a Keep / Undo bar when the turn ends.
5. **Raster painting:** app-rasterized **shape primitives** first, then **row strings** using the *same* legend as the read view, then a **coordinate list** for touch-ups.
   - Primitives: line, rect, circle/ring from a canonical lookup, polygon, text in a bitmap font, fill, mirror, shift/flip.
   - Raw-grid output is unreliable, especially for small models (Asset Forge, Pixel Art Bench, pixel-llm, the Aseprite tool-calling experiment) **[secondary]**.
6. **Make the tool layer MCP-shaped from day one, but don't depend on MCP in v1.** Define each tool once in the pure-Kotlin core: a `@Serializable` args class, a description, hints (`readOnly` / `destructive` / `idempotent`), tier, and an `external` allowlist flag.
   - Generate JSON Schema from the Kotlin types, using `kotlinx-schema` (JetBrains, experimental) or hand-writing with its DSL. Snapshot-test the output.
   - Use flat schemas: enums and a `kind` discriminator with optional fields, and **no `oneOf` / `anyOf`**. This is kinder to small models and provider schema subsets **[speculative; hand-off to the providers lane]**.
   - Later, external exposure is a JVM CLI (`stdio` MCP server via the MCP Kotlin SDK) wrapping the same registry, filtered by the allowlist.
7. **Runtime:** run the loop in an **app-scoped coroutine** owned by an `AgentSession`. Use neither ViewModel scope nor WorkManager. The ViewModel just observes state.
   - Persist the transcript and journal after every step.
   - On process death, the next launch shows "Agent turn interrupted after N steps: Keep / Undo". Don't resume automatically, because that spends the user's money.
   - A `shortService` foreground service (~3 min, Android 14+) is a cheap optional add-on if backgrounding mid-turn turns out to matter.
   - Caps: max steps per turn (default ~15), max calls per step, repeated-identical-call detection, and the perception report's ≤2 revise iterations. Bounded exponential backoff on 429/5xx only.
8. **Build a thin loop rather than adopting a framework, as a judgement call.** Koog (JetBrains, Apache-2.0, 1.x stable since May 2026, multiplatform including Android, OpenAI/Anthropic/Google/OpenRouter/Ollama/DeepSeek clients, persistence, history compression) is the only credible adopt candidate. LangChain4j, Spring AI and Embabel are JVM-server oriented.
   - The loop GlyphWizard needs is roughly "call model → run tools → append → repeat with caps". That is small. The hard parts are provider quirks and lenient parsing for open-weight models, which we need to control directly.
   - Anthropic recommends starting with direct API calls and warns that framework abstractions obscure prompts **[verified]**.
   - **Suggested: a 1-day Koog spike** against two awkward endpoints (an Ollama model and a subscription gateway) before final lock-in. Hand-off: the providers lane owns that verdict.
9. **Concurrency:** **soft-lock plus live apply.** While a turn runs, canvas editing is disabled, but playback, scrubbing and Stop still work. The agent writes to the live document so the preview updates step by step.
   - Variants are forks of the document, presented as a choice.
   - Between turns the user edits freely, and the next turn gets a "user changed X since your last turn" summary from the authored journal.
   - No OT/CRDT. Live merging is unsolved even at tldraw: its multi-agent fairies are "blind" to each other's in-progress work **[secondary]**.
10. **Core boundary:** a plain **Kotlin/JVM module** `:core` holds model, serialization, commands and journal, renderer and baker, device profiles, analysis, text views, and the tool registry. A pure-Kotlin `:agent` module holds the loop and provider clients. **Both must be Android-free** so the JVM-CLI eval harness tests exactly what ships. Keystore, foreground service, Glyph SDK, `Bitmap` encoding and storage stay in `:app` behind small interfaces (§Q8 table).
    - Use only stdlib and kotlinx (serialization, coroutines) so a later move to KMP `commonMain` is mechanical. Don't start with KMP: it buys iOS and web, which aren't goals, and adds AGP 9 multiplatform plumbing for a first-time Android dev.
    - Later, a JVM `:cli` module hosts the headless emulator, eval harness and MCP server.

**Overengineering flags.** Not needed for v1:
- An agent dry-run mode.
- Parallel tool execution: tools run in microseconds, so execute sequentially.
- An MCP dependency.
- KMP.
- WorkManager.
- Draft branches with three-way merge.
- Streaming tool-argument application (tldraw-style).

---

## 2. Findings per question

### Q1. Tool granularity and count: what the evidence says

#### General evidence

| Source | Finding | Confidence |
|---|---|---|
| Anthropic, *Writing effective tools for agents* | "More tools don't always lead to better outcomes." Consolidate multi-step workflows into one tool (e.g. `schedule_event` instead of `list_users` + `list_events` + `create_event`). Namespace by resource. Add a `response_format` enum (concise/detailed): a Slack example used 206 vs 72 tokens. Natural-language names beat UUIDs for reducing hallucinated IDs. Errors should give "specific, actionable" guidance, not tracebacks. Paginate and truncate with defaults (Claude Code caps tool responses at 25k tokens). Evaluate with realistic multi-call tasks. | [verified] |
| Anthropic, *Building effective agents* | Give tool definitions as much prompt-engineering effort as the main prompt. **"Poka-yoke your tools"**: change arguments so mistakes are harder. For example, requiring absolute file paths eliminated a class of SWE-bench errors. The team spent more time on tools than on the overall prompt. Start with direct API calls, because frameworks "obscure the underlying prompts and responses". Use stopping conditions such as max iterations. | [verified] |
| OpenAI function-calling guide | "Aim for fewer than 20 functions available at the start of a turn". "Use enums and object structure to prevent invalid states". "Don't make the model fill arguments you already know". "Combine functions that are always called in sequence". Recommends strict mode (all properties required, `additionalProperties: false`). `parallel_tool_calls: false` gives zero or one call. | [verified] |
| MCP-GRANITE (Paschalides et al., 2026, arXiv 2609.24161) | 81 scenarios × 4 granularities × 9 local models (268M–20.9B), 8,748 trials. ~4 task-level tools gave the best trade-off: +16.4% vs fine-grained, +33.6% vs monolithic, ~2× argument accuracy. Model size correlates only weakly with completion. A monolithic tool led to "no tool called" in 28% of runs (prior report). | [verified abstract]. Caveat: IoT mocks, small models |
| WorkBench (arXiv 2405.00823) | GPT-4: 49% → 43% when given all 26 tools instead of the needed toolkit. Weaker models sometimes overflowed the context window and scored 0. | [secondary via search summary] |
| *How many tools should an LLM agent see?* (arXiv 2605.24660) | A retrieval shortlist of ~7 tools reached nearly the coverage of 50. Sonnet 4.6 selection accuracy was 93.1% with adaptive shortlists vs 87.1% with a fixed 5. | [secondary via search summary] |
| Toolshed (arXiv 2410.14594) | Counterpoint: tool count (1–128) was not a significant predictor on Seal-Tools single-step tasks. The authors suspect count matters more in single-domain, multi-step, stateful settings, which is exactly ours. | [secondary] |
| *Natural Language Tools* (arXiv 2510.14453) | Replacing JSON tool calls with natural-language tool selection gained +18.4 pts accuracy, with the largest gains on open-weight models. | [verified abstract]. Hand-off to providers lane as a fallback for weak or no-tool models |
| *Constraint Tax* (arXiv 2606.25605) | On vLLM/SGLang, enabling a JSON-Schema response constraint *together with* tools suppressed tool calls to ~0% across open-weight models, because grammar masking blocks the tool-call token. | [secondary]. Hand-off: never combine `response_format` JSON schema with tools on self-hosted servers |

**Takeaway [speculative synthesis]:** the evidence agrees on "few, task-shaped, well-described tools with enums". It doesn't support a magic number. For a single-domain, stateful editor, ~12–16 tools for the v1 target (mid-capability and up) sits inside every guideline above. If small models are ever supported, ~6–8 would be the likely size. Our evals should measure it, using the harness from the perception report.

#### Creative-app integrations: which tool shapes worked and which failed

| System | Tool shape | Lessons | Confidence |
|---|---|---|---|
| **Blender MCP** (ahujasid) | Now ~9 core tools. **Code-first**: `execute_blender_code` runs Python. `get_scene_info` gives one line per object with selectable fields and drill-down. `look` renders viewport, camera, multi-angle sheet or animation strip. | They moved *toward* compact, field-selectable reads and a richer `look`. Arbitrary code is "the riskiest" tool, and Safe Mode blocks file, network and process access, returning the block reason so the AI can retry. No undo story: "ALWAYS save your work". Practitioners report that a screenshot after every op was the biggest win, and that `FINISHED` doesn't mean anything changed. | [verified README]; practitioner claims [secondary] |
| **Figma MCP** `use_figma` | One code-execution tool running Plugin API JS, plus markdown **skills** (`/figma-use` foundational, others layered) that teach usage. Agents "read your library first and build with what's already there". Screenshot-and-iterate loop. | Code-mode plus skills works for frontier coding agents. Composing from existing components beats generating from scratch. Figma's in-app agent has an **Undo button in chat** (most recent change), and forum reports say Cmd+Z doesn't cover agent edits. The FAQ warns that output may need manual fixing. | [verified blog/help]; forum [secondary] |
| **tldraw agent starter kit** | Typed **action schemas** (create, update, delete, move, align, …) streamed and applied incrementally. Each action is sanitized before apply: `ensureShapeIdExists`, `ensureShapeIdIsUnique`, `ensureValueIsVec/Number`. Coordinates are offset relative to the chat origin and numbers are rounded, then un-rounded. Simplified shape formats (Blurry / Focused / PeripheralCluster). **Modes** restrict the action set (e.g. critique mode drops drawing actions). `schedule()` lets the agent queue follow-up work. | Key ideas to copy: (a) a **lenient sanitize layer** that fixes or rejects malformed actions before apply; (b) model-friendly *views* that differ from internal storage; (c) per-mode action subsets. That last one is our full/reduced set. Their later "spatial harness" post says they moved from discrete actions toward agents **writing code against the Editor**, plus a canvas **lint** system (e.g. overlapping text). | [verified docs/blog] |
| **Canva MCP** | Edits are a **transaction**: start → perform operations → commit, or cancel discards. Editable element IDs come from the transaction start. "Only report a saved edit after commit returns committed". Later consolidated into `read_design` / `edit_design`. | Transaction-per-edit with commit/cancel is a proven shape, and consolidation into read plus edit happened in production. Bounded exponential backoff on 429. Surface actionable errors "instead of repeatedly retrying". | [verified dev docs]; consolidation [secondary, Canva help page returned 403] |
| **Photoshop AI Assistant** (beta) | Conversational. Edits land in the History panel. | Agent edits as ordinary history entries the user can undo. Reviewers saw wrong selections and blown highlights, which supports showing previews and making undo trivial. | [secondary] |
| **Aseprite MCP experiment** (ljvmiranda, 2025) | 10 tools: `draw_pixels` (list of {x, y, color}), `draw_line`, `draw_rectangle`, `fill_area`, `draw_circle`, layers/frames, `preview_image`. | Opus 4 was best. Qwen3-32B produced unrecognizable art **and blamed the tools** ("draw_line didn't work" when it did). The author says pixel-level primitives limit output and higher-level tools could help. | [verified blog] |
| **Cloudflare Code Mode** | The model writes code against typed tool APIs: 2 tools (`search`, `execute`), ~1k tokens. Reported 32–81% fewer tokens. | Strong for frontier models. It needs a sandboxed interpreter, which conflicts with our "no embedded JS engine" stance. No evidence for small models. | [secondary] |

**What this means for us [speculative]:**
- Code-mode is the frontier trend (Blender, Figma, tldraw harness, Cloudflare). It needs a sandboxed language, though, and evidence for non-frontier models is missing.
- The animation-model synthesis already plans a later "custom tiny language" for expression clips. If that ever exists, a `run_script` tool for frontier tier could reuse it.
- **Not v1.** Typed task-level tools are the right baseline for open-weight mid-tier models, and they're what the eval harness can measure.

### Q2. Draft tool catalogue

See §3 for the catalogue itself. Semantics:

**Batch and transaction semantics**
- **Turn = undo group** (D9). The group opens when the user sends a message and closes when the model returns a final reply with no tool calls, the user presses Stop, a cap is hit, or an error ends the turn.
- **Tool call = atomic sub-step.** Validate every op in the call first, then apply all of them, or none. The journal records `{group, step, tool, args-hash, author=agent:<model>}`.
- **List-valued writes** (`paint.ops[]`, `set_keyframes.keyframes[]`) give batching *inside* a domain without a mega-tool. Cap list length (e.g. ≤64 ops). Over-cap calls return an error telling the agent to split them.
- **Parallel tool calls** from the provider are accepted but executed **sequentially in emitted order**. A failure in call k doesn't abort k+1: each gets its own result, matching what providers expect.
  - [speculative] Optionally abort later *writes* in the same batch after a failed write, returning "skipped: earlier call failed" to avoid compounding.

**Preview before commit**
- **Human-level:** changes apply live, step by step. At turn end the chat shows "Keep / Undo turn", and Undo reverts the whole group. This matches Figma's in-chat Undo and Photoshop's History panel.
- **Agent-level:** no `dry_run` flag in v1. The agent can do a write, inspect it, then `revert` it, which is cheaper than doubling every write schema. If a dry-run is ever added, it **must run through the identical validation path**. A public bug report shows a dry-run issuing approval for a call that the real path rejects **[secondary, GitHub issue saworbit/didi#399]**.

**Idempotency**
- Prefer absolute values (`start_ms: 400`) over deltas (`shift_ms: +100`). Only geometric `shift` inside `paint` is relative, and it's documented as such.
- `set_keyframes` **replaces** the keyframe list for one property, so a retry produces the same state.
- `add_clip` / `add_layer` take an optional agent-chosen `ref` (e.g. `"pulse"`). Re-sending the same `ref` within a turn returns the existing object instead of duplicating it. That protects against provider retries and models that repeat a call [speculative design].
- `remove` of an already-removed ID returns `ok` with a note, not an error.

**Stable IDs**
- Short, type-prefixed, never reused within a document: `L1` layers, `c7` clips, `v2` variants. Accept either the ID or a unique label (`"heart"`). An ambiguous label is an error listing the candidates. This follows Anthropic's note that meaningful names reduce hallucinated IDs, and the prior finding that index arithmetic is the #1 patch failure.
- Keyframes are addressed by `(clip, property, t_ms)`, never by index.

**Error design for self-correction**
- Shape: `{ok:false, code, message, field, valid?, hint?}`. The message says *what is wrong, what is valid, and an example fix*. Examples:
  - `Unknown clip "c9". Existing clips: c2 "ring" 0–800ms (L1), c4 "heart" 400–1200ms (L2).`
  - `ring_pulse@1 has no param "speed". Valid: radius 0..1, width 0..0.5, period_ms 100..5000, phase 0..1. Did you mean "period_ms"?`
  - `Clip c4 would overlap c5 on L2 (400–1200 vs 1000–1600). Layers forbid overlap; use another layer or set start_ms ≥ 1200.`
- **Lenient normalization before validation.** Coerce `"400ms"` → 400, `"ringPulse"` → `ring_pulse`, numeric strings → numbers, `0..1` floats where ints were expected (when unambiguous), and the missing `@version` → latest. Report each coercion as a `warnings[]` entry so the model learns. Prior research found lenient parsing plus validation beat strict JSON mode on ARC-style tasks (Land 2026) **[verified in prior report]**. tldraw's `sanitizeAction` is the same idea **[verified]**.
- **Domain-semantic warnings** (not errors) on success: "frame 3: 0 LEDs lit", "61% of lit LEDs outside mask (clipped)", "clip ends after document end", "brightness 4 is below visible threshold [verify on device]". This is the canvas-lint idea from tldraw's harness post.

### Q3. Raster pixel writing: which input format to give the agent

| Format | Evidence | Verdict |
|---|---|---|
| **Shape primitives rasterized by the app**: line, rect, circle/ring, polygon, polyline, text (bitmap font), flood fill | Asset Forge: raw pixel arrays "produce incoherent results", while primitive drawing commands work **[secondary]**. A math-diagram paper: LLMs handle SVG intermediates far better than direct pixels **[secondary]**. The Aseprite experiment: low-level primitives produced blocky results, and higher-level ones were suggested **[verified blog]**. | **Primary.** Circles and rings snap to a **canonical per-radius lookup** (from the perception report's `small-shapes` skill) so "draw a circle r=5" always looks round. Text uses a bundled OFL/custom 3×5 or 5×7 font. |
| **Structural ops**: mirror (H/V/both/rot4), shift, flip, rotate90, clear region, copy frame | Common requests ("move it left 1", "make it symmetric") map to one exact op instead of repainting. Repainting invites drift. | **Primary**, alongside shapes. |
| **Row strings** using the *same legend as the read view* (`.` off, `1`–`9`, `#` = 255, space = outside mask), with an anchor `(row, col)` and a region | Prior ARC-format work: CSV/row formats are easiest for LLMs to *produce*. Compact, hard-to-produce formats (sparse coords, binary masks) were "substantially worse" (Land 2026, prior report **[verified there]**). ARC practice uses row grids. Pixel Art Bench / pixel-llm: small models often fail to emit valid full grids **[secondary]**. | **Secondary**, for small sketched regions (≤ ~7×7 recommended) and whole-frame redraws by strong models. A lenient parser tolerates missing row labels and ragged rows, padding them with a warning. Quantization is lossy, so writing rows over a full-precision raster requires `quantize_ok: true`, otherwise it errors (guards the lossy-view risk from prior research). |
| **Coordinate list** `[[r, c, v], …]` | Exact. Good for 1–10 pixel touch-ups. Land 2026 found sparse coordinates harder to produce at scale. | **Tertiary**: touch-ups only. Cap ~32 points per op. |
| Full 0–255 arrays (137/489 numbers) | Token-heavy. LLMs miscount. Rejected by both prior reports. | **Not exposed.** |

Other painting rules:
- Brightness as an int 0–255 on each op, with named presets in the glossary ("full" = 255, "dim" ≈ …) [context lane].
- `mode: paint | erase | off` (D6).
- **Generators before raster.** Tool descriptions and the system prompt steer "ring", "pulse", "spinner" and "fill" toward generator clips. Raster is for icons, glyph art and hand touch-ups. [speculative; eval]

### Q4. MCP-shaped from day one? Schema generation and allowlist

**Recommendation: yes for the *shape*, no for the *dependency*.**
- The internal tool registry already looks like MCP: name, description, JSON-Schema input, structured result plus text, `isError` results, and behaviour hints. This costs almost nothing extra.
- The MCP Kotlin SDK isn't needed until the external path exists. Its README lists JVM, Native, JS and Wasm but **doesn't mention Android** **[verified]**. An in-app MCP server listening on a phone port would also be an unnecessary attack surface.

**MCP spec features worth mirroring** **[verified, spec 2025-11-25]:**
- Tool `name` ≤128 chars from `[A-Za-z0-9_.-]`. Use snake_case: some providers reject dots or hyphens [verify per provider, providers lane].
- `inputSchema` (JSON Schema 2020-12 default).
- Optional `outputSchema` plus `structuredContent`, with a text fallback.
- Image content blocks (T2 render results).
- `annotations`: `readOnlyHint` / `destructiveHint` / `idempotentHint` / `openWorldHint`, which default to the cautious values when omitted [secondary on defaults].
- Two error channels: protocol errors vs tool-execution errors that "contain actionable feedback that language models can use to self-correct".
- Security: clients SHOULD show tool inputs, confirm sensitive operations, time out calls, and log usage.

**Generating schemas from Kotlin types:**

| Option | Status | Notes |
|---|---|---|
| **kotlinx-schema** (Kotlin org, JetBrains) | **Experimental**, Apache-2.0, no tagged release seen [verified] | Three routes: KSP compile-time (multiplatform, reads KDoc, no defaults), kotlinx.serialization descriptors at runtime (all platforms), and reflection (JVM only). Emits Draft 2020-12 and OpenAI-style function schemas. Sealed classes become `oneOf` + `$defs`. Recognizes `@Description` and **Koog's `@LLMDescription`**. |
| kt-schema (kpavlov) | Community | KSP or serialization. LLM function schemas. Same author family as kotlinx-schema. |
| Stream29/JsonSchemaGenerator, xemantic-ai-tool-schema | Community; xemantic stale (~2 yrs) | Serialization-descriptor based. |
| Koog's own tool schema derivation | Stable inside Koog | Only if Koog is adopted. |
| **Hand-written schemas** via a tiny DSL, plus golden snapshot tests | Zero dependency risk | ~15 tools × ~10 fields is small. The schema *is* a prompt, so hand-tuning descriptions is a feature (Anthropic: tools deserve as much prompt work as the prompt). |

**Suggested approach [speculative]:**
- Write `@Serializable` args classes, because deserialization and validation need them anyway.
- Generate schemas with kotlinx-schema's serialization route if it behaves. Otherwise hand-write.
- Either way, **commit the emitted JSON as golden files** and fail tests on diff, so a refactor can't silently change what models see.
- Avoid `oneOf`, `anyOf` and `$ref` in exposed schemas. Use a `kind` enum plus optional fields, and validate combinations in Kotlin (that's where the actionable error messages come from anyway).
  - Gemini and OpenAI strict mode each accept different JSON-Schema subsets, and small models handle flat objects better [verify; providers lane].
  - Strict mode requires every property `required`. Optionals then become nullable, which the adapter can do mechanically.

Illustrative only. A flat `paint` op schema, trimmed:

```json
{"name":"paint","description":"Draw on a raster clip frame. Prefer shapes; rows for small sketches; points for touch-ups.",
 "inputSchema":{"type":"object","additionalProperties":false,
  "properties":{
   "clip":{"type":"string","description":"Clip id or unique label, e.g. c4"},
   "frame":{"type":"integer","minimum":0},
   "ops":{"type":"array","maxItems":64,"items":{"type":"object","properties":{
     "kind":{"enum":["line","rect","circle","ring","polygon","text","fill","rows","points","mirror","shift","flip","clear"]},
     "mode":{"enum":["paint","erase","off"]}, "brightness":{"type":"integer","minimum":0,"maximum":255},
     "from":{"type":"array","items":{"type":"integer"}}, "to":{"type":"array","items":{"type":"integer"}},
     "center":{"type":"array","items":{"type":"number"}}, "radius":{"type":"number"},
     "rows":{"type":"array","items":{"type":"string"}}, "at":{"type":"array","items":{"type":"integer"}},
     "points":{"type":"array","items":{"type":"array","items":{"type":"integer"}}},
     "text":{"type":"string"}, "axis":{"enum":["h","v","both","rot4"]}, "dx":{"type":"integer"}, "dy":{"type":"integer"}},
    "required":["kind"]}}},
  "required":["clip","ops"]}}
```

**Allowlist design for future external exposure** [speculative design]. Each `ToolSpec` carries:

```
exposure = { inApp: true, external: true|false }
hints    = { readOnly, destructive, idempotent }
tier     = full|reduced
```

External rules:
1. Tools operate only on the **session document** inside the CLI's sandbox, which is the one passed on the command line. **No path, URL or device arguments in any tool schema.**
2. No trigger, device or settings tools exist at all (D12). The allowlist is a second fence, not the first.
3. Per-session caps on steps, ops per call, document size and frame count.
4. Output is a `.glyphwiz` file the user imports (D14). The importer re-validates everything with the same validators, so a hostile file can't bypass them.
5. `external: false` by default for new tools. Adding one to the allowlist is a reviewed change with a test.
6. `load_skill` and examples can be exposed as MCP *resources/prompts* later.

### Q5. Agent loop on Android

**Where it runs.**

| Option | Fit | Evidence |
|---|---|---|
| ViewModel `viewModelScope` | Survives rotation, but dies with the screen's back-stack entry. Navigating away cancels the turn. | Standard lifecycle **[verify, common knowledge]** |
| **App-scoped `AgentSession`** (singleton holding a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`), with the ViewModel observing `StateFlow` | **Recommended.** Independent of screens. One active session per document. | [speculative design, standard pattern] |
| WorkManager (incl. expedited) | Poor fit: built for deferrable or guaranteed work, not interactive streaming chat. Expedited-job quotas and execution limits apply **[verify]**. | — |
| User-initiated data transfer job (Android 14+) | Meant for long user-initiated *transfers* like downloads. Play policy restricts its use to actual transfers **[secondary]**. | Not our case |
| Foreground service `shortService` | Runs ~3 min. Must start while the app is visible or exempt. On timeout, `onTimeout()` gives a brief chance to stop, then the app gets an ANR. No special Play declaration. **[verified, Android docs]** | **Optional add-on:** start it at turn start so a turn survives a quick app switch. |
| FGS `dataSync` / `specialUse` | `dataSync` has a ~6 h/day cap on Android 15 **[verify]**. `specialUse` needs a Play-reviewed justification. | Overkill |

**Process death mid-turn.** `SavedStateHandle` is only for small UI state and only survives *system*-initiated death, not swipe-from-recents **[verified]**, so the turn must persist itself. [speculative design]
- After each **completed tool step**, append to an on-disk turn log: the transcript sidecar per document (messages, tool calls and results, with images stored as file refs) and the D9 journal entry with `group` open.
- On next open, if an agent group is still open: close it, mark it `interrupted`, and show "Agent stopped after N steps (app was closed). [Keep] [Undo turn] [Continue]". **Continue** sends a new request with the persisted transcript plus a system note. It's never automatic.
- The in-flight HTTP request is simply lost. That's safe, because tool effects only apply after a complete response.

**Cancellation.**
- Stop cancels the turn coroutine, and the OkHttp/Ktor calls cancel with it.
- Tool execution runs inside a short non-cancellable section, so a command is never half-applied.
- Then the Keep / Undo bar appears.
- `shortService` `onTimeout` and screen exit both route to the same cancel path.

**Streaming UI.**
- Stream assistant *text* tokens into the chat.
- Tool-call arguments are accumulated and applied **only when the call is complete**. The chat shows a live status line from the tool name ("adding ring clip…").
- The document preview updates after each step because writes commit per step.
- tldraw applies each action as soon as its streamed JSON completes **[verified]**. That's nice, but it needs a partial-JSON parser per action. Defer.

**Timeouts, retries, backoff** (config defaults [speculative], provider lane to refine):
- Connect ~15 s.
- Read / first-token: configurable, generous for LAN models (e.g. 120 s).
- Per-turn wall clock ~5 min.
- Retries only on transport errors, 408/429/5xx/529:
  - exponential backoff with jitter
  - honour `Retry-After`
  - max 3 attempts
  - then surface a readable error
- Canva's guidance agrees: "bounded exponential backoff for 429" and "surface actionable errors… instead of repeatedly retrying" **[verified]**.
- Model-level failures are *not* transport retries. A malformed tool call goes back to the model as a tool error and counts as a step. Two consecutive parse failures trigger the perception report's fallback.

**Budgets.**
- `maxSteps` per turn (default 15).
- `maxToolCallsPerStep` (8).
- `maxOpsPerCall` (64).
- Per-turn and per-session token/cost estimate cap (perception report: user-settable spend cap).
- **Loop detector:** the same tool with the same args hash 3× in a turn ends the turn with an explanation. Koog's team documented models falling into "tool-calling addiction" patterns after long tool-call runs **[verified blog]**.
- The critique loop is capped at ≤2 revise iterations (perception report).
- When a cap hits, the agent gets one final no-tools call to summarize for the user [speculative].

**Parallel tool calls.**
- Accept them, but execute sequentially (see Q2).
- Read-only tools could run concurrently, but they're microsecond-scale, so it's not worth it.

**Conversation persistence per document.**
- Use the sidecar `<doc>.chat.json` (or one table), capped at e.g. 200 messages with older tool results elided. Context compaction policy belongs to the context lane.
- Never store API keys in it.
- Exclude it from `.glyphwiz` export by default (privacy) [speculative; UX lane].

### Q6. Kotlin/JVM agent frameworks: build or adopt?

| Framework | Android | Maturity | Providers | License | Notes |
|---|---|---|---|---|---|
| **JetBrains Koog** | Yes. KMP; JetBrains' `demo-compose-app` sample runs on Android, iOS and desktop. OkHttp backend recommended on Android. minSdk lowered 35 → 23 in a recent release **[secondary]**. | 1.0 at KotlinConf May 2026 with a 1-year no-breaking-change guarantee for stable modules **[verified]**. README shows 1.3.0 **[verified]**. Pre-1.0 APIs churned (0.3 → 0.5 tutorials differ) **[secondary]**. | Google, OpenAI, Anthropic, DeepSeek, OpenRouter, Ollama, Bedrock **[verified README]**. Custom `baseUrl` via `OpenAIClientSettings`, documented poorly at first **[secondary]**. | Apache-2.0 **[verified]** | Graph strategies, `SimpleTool<Args>` / ToolRegistry, parallel tool calls, streaming, persistence/checkpoints (in-memory/file providers, rollback, `RollbackToolRegistry`), history compression, OpenTelemetry. Requires JDK 17+ and Kotlin ≥2.3.10 **[verified README]**. |
| LangChain4j | Unclear. A 2024 issue asking about Kotlin on Android has no visible answer **[secondary]**. JVM-server oriented. | Mature on JVM | Many | Apache-2.0 [verify] | Heavy for an app. |
| Spring AI | No (Spring) | Mature | Many | Apache-2.0 | Excluded per brief. |
| Embabel (Rod Johnson) | No. Built on Spring AI, GOAP planner. | 1.0 GA reported **[secondary]** | via Spring AI | Apache-2.0 [verify] | Planner-driven. Wrong shape for a chat editor. |
| openai-java (official) | Not mentioned. Java 8+. Works with R8/ProGuard **[verified]**. | Stable | OpenAI, plus any compatible endpoint via `baseUrl` **[verified]** | Apache-2.0 **[verified]** | Jackson + OkHttp. `addTool(Class)` derives schema from Java classes. |
| anthropic-java (official) | Not mentioned. Java 8+. | Stable (2.71.0) | Anthropic | MIT **[verified]** | OkHttp. |
| MCP Kotlin SDK | JVM yes; Android not listed **[verified]** | Official, "maintained in collaboration with JetBrains" | n/a | Apache-2.0 (new) / MIT (old) **[verified]** | Tools registered with hand-built `ToolSchema`. **No** schema generation. Only for the future `:cli`. |

**Recommendation: build a thin loop. This is a judgement call, flagged as such.**

1. The loop itself is small. Roughly:

   ```
   while (steps < max) {
     resp = provider.chat(messages, tools)
     if (no tool calls) break
     results = calls.map(registry::execute)
     append(results)
   }
   ```

   Caps, persistence hooks and cancellation add maybe a few hundred lines [speculative estimate]. Koog's graph strategies add concepts a first-time Android dev must learn, without solving our hard parts.
2. **Our hard parts are provider-specific:**
   - lenient parsing of half-broken tool calls from open-weight models
   - gateways that only implement Chat Completions
   - Ollama templates without tools
   - the Constraint Tax trap

   We need to see and patch raw requests and responses. Anthropic warns frameworks "obscure the underlying prompts and responses" **[verified]**.
3. Both options keep the loop in pure Kotlin, so the eval harness can run it on the JVM.
4. **Risk of building:** reinventing retries, compression and persistence. All three are small for our case (one session, one document, short turns).

**Do a 1-day Koog spike before locking this in.**
- Point Koog's OpenAI client at an Ollama Qwen model and one subscription gateway.
- Register 3 of our tools.
- Check parse leniency, cancellation and APK size delta.

If Koog handles the awkward endpoints *and* allows raw request/response hooks, adopting just its **LLM client layer** (`prompt-executor` / clients) under our own loop is a reasonable middle path. Hand-off: the providers lane owns the client choice. This report owns the loop shape.

### Q7. Concurrency: human edits while the agent edits

| Approach | Precedent | Cost | Verdict |
|---|---|---|---|
| **Soft lock + live apply.** Editing disabled during a turn; view, scrub, play and Stop still work. | Typical chat-agent UX. Figma agent turns run with a Stop button **[verified help]**. | Trivial | **v1** |
| Live concurrent editing on the same store | tldraw agent writes to the live editor store and sanitizes references to deleted shapes (`ensureShapeIdExists`). The harness post says agents adapt when "something it was working on has been deleted". Fairies (multi-agent) are blind to each other's in-progress work; no locking shown. **[verified/secondary]** | Medium. Needs per-call revalidation, and undo groups interleave (tldraw's own undo issues #10846/#8477 from prior report). | Not for v1 |
| Draft branch: agent works on a fork, then Apply/Discard with a 3-way merge by stable IDs | Canva transaction (commit/cancel) **[verified]**. Code-agent review flows. | Medium. Merge UI, conflicts. | Use the fork mechanism **only for variants** |
| OT/CRDT | Figma multiplayer (server-authoritative LWW per property) **[verify]**. Automerge costs (prior report). | High | No (prior synthesis: avoid CRDTs) |

Two additions [speculative design]:
- **Between turns**, the user edits freely. The next turn's context includes a compact "since your last turn the user: moved c4 to 600 ms, deleted c7" summary derived from the authored journal (D9). That stops the agent from blindly reverting the user's fixes. Hand-off: context lane formats it.
- If the user taps the canvas during a turn, offer "Stop agent and edit?". Stopping ends the group cleanly.

### Q8. The pure-Kotlin core module boundary

**Proposed modules [speculative design]:**

```
:core   (kotlin("jvm"), deps: stdlib, kotlinx-serialization, kotlinx-coroutines-core)
        model (Document, Layer, Clip, Keyframe, DeviceProfile, generator registry @versioned)
        io (.glyphwiz read/write + migrations, Glyph Museum import/export)
        commands (typed, validated, inverse) + journal/undo groups (JSON Patch, authorship)
        render/bake: render(doc, t, inputs) -> IntArray brightness; baker; resampler
        raster ops: shape rasterizer, canonical circles, bitmap font, mirror/shift
        analysis: shape/brightness/motion/lint metrics (perception report L2)
        views: text grid, coords, diff, motion summary, doc summary (L1 text)
        image: dot-render to an ARGB IntArray (encoding to PNG happens in platform layer)
        tools: ToolSpec registry, arg classes, handlers -> commands, result envelope, error text,
               schema emission + golden files
:agent  (kotlin("jvm"), deps: :core, HTTP client (Ktor or OkHttp: providers lane), serialization)
        loop, caps, transcript model, provider adapters, lenient tool-call parsing, persistence interface
:app    (Android) Compose UI, ViewModels, AgentSession host, Keystore, files, GlyphMatrix SDK, FGS,
        PNG encoding (Bitmap), transcript storage impl
:cli    (later, JVM) headless emulator, eval harness, MCP stdio server (MCP Kotlin SDK), allowlist
```

**Platform-free vs Android-only: what the eval harness needs.** The eval harness is a JVM CLI with no Android emulator (cross-finding from the evals lane), so it **only tests what ships if everything that shapes model behaviour is platform-free**. That includes the agent loop and the provider clients. Rule: *if it changes the bytes sent to or received from the model, or what a tool does, it lives in `:core` / `:agent`.*

| Must be platform-free (`:core` / `:agent`) | Android-only (`:app`), behind interfaces |
|---|---|
| Document model, commands, validation, journal/undo groups | Compose UI, ViewModels, navigation |
| Renderer/baker, analysis, text views, dot-render to pixel buffer | PNG/WebP **encoding** via `Bitmap` (interface `ImageEncoder`; the CLI uses `javax.imageio` or a tiny pure-Kotlin PNG writer) |
| Tool registry, schemas, lenient normalizer, error texts, result envelope | Glyph Matrix SDK calls (`setAppMatrixFrame`), LED preview |
| **Agent loop**: caps, loop detection, retry/backoff policy, cancellation semantics, transcript model, history compaction | `AgentSession` *host*: app-scoped scope, lifecycle wiring, optional `shortService` FGS, `onTimeout` → cancel |
| **Provider clients/adapters**: request building, streaming parsers, tool-call parsing, capability probe | **Key storage** via Android Keystore/Tink (interface `SecretStore`; the CLI reads env vars or a file) |
| System prompt assembly, skill/knowledge loading (content as resources in the module) | Transcript/journal **storage impl** (interface `SessionStore`; the CLI uses temp files) |
| Persistence *format* of transcript/journal | Network-state UI, notifications, Android permissions |
| HTTP engine choice must run on both, e.g. Ktor with OkHttp engine, or OkHttp directly | (Only the engine *instance* may differ per platform) |

Consequences:
- The HTTP client used in `:agent` must be JVM-plus-Android compatible. OkHttp and Ktor are both fine.
- Anything Android-flavoured (Keystore, FGS, SDK, `Bitmap`, Context, Room) is injected through a small interface. Then a harness run and an in-app run differ only in storage, secrets and image encoding, none of which change model behaviour.
- A CI check that `:core` and `:agent` have no `android.*` imports, which is automatic with `kotlin("jvm")` modules, enforces this.

**Why plain JVM rather than KMP now:**
- Android consumes a `kotlin("jvm")` module directly.
- Tests run as fast plain JUnit with no Robolectric. Google's testing blueprint calls a plain-Kotlin module for non-Android logic good practice for testability **[secondary]**.
- KMP's benefits are iOS and web, neither of which is a goal.
- JetBrains' new KMP structure plus AGP 9 changes are extra moving parts for a first-time Android developer **[verified blog]**.

To keep the door open:
- Avoid `java.*` in `:core` (use kotlinx-datetime if time is needed at all; ms ints suffice).
- No reflection.
- Use kotlinx.serialization, not Jackson.

Moving to `commonMain` later should then be mechanical **[speculative]**.

**Testability:**
- golden-frame render tests
- command inverse/undo property tests
- tool contract tests (schema snapshots, error text snapshots)
- a **scripted fake provider** that replays recorded transcripts through the loop, for deterministic loop tests
- the `:cli` eval harness against real providers

The headless emulator is simply `:core` plus a CLI that renders to text and PNG. No Android emulator is needed for external agents or evals.

---

## 3. Draft tool catalogue

Common result envelope for **all writes** [speculative design]:

```
{ ok, step, changed:[ids], created:{ref->id}, preview:"<text grid of the most relevant frame or a 4-frame strip>",
  warnings:[...], normalized:[...coercions...] }
```

- `preview` is concise by default.
- `detail: "concise"|"detailed"` follows Anthropic's response_format pattern. Detailed adds the full doc summary of the touched clip.
- Read tools return text first. T2 image tools return an image block plus a short caption.

### 3a. FULL set (the v1 set; mid-capability and frontier models; target ≤16 visible)

| # | Tool | Purpose | Args sketch | Returns | Hints |
|---|---|---|---|---|---|
| 1 | `describe_document` | Structured summary: device, tick, duration, loop; layers → clips (id, label, kind, generator@v + params, start/dur, in/out, blend, opacity); keyframes; user-changes-since-last-turn | `detail?`, `clip?` | text summary (+ structured) | read-only |
| 2 | `view_frame` | See a frame | `t_ms` \| `frame`, `scope?: composite\|layer:<id>\|clip:<id>`, `format?: grid\|coords\|exact`, `region?`, `compare_to_t_ms?` (diff view) | masked text grid / coords / diff | read-only |
| 3 | `view_motion` | Motion summary of a range or clip | `from_ms?`, `to_ms?`, `clip?`, `strip?: n≤16` (text filmstrip) | centroid/angle series, step stats, seam, flicker, optional strip | read-only |
| 4 | `analyze` | Deterministic metrics (perception L2) | `target` (frame t / range / clip), `checks: [shape, symmetry, roundness, centering, brightness, motion, flicker, text, lint]` | per check: score, verdict, evidence coords/frames, suggested op | read-only |
| 5 | `render_image` *(T2 only)* | Image for gestalt questions | `t_ms` \| `range`, `style: analytic\|realistic`, `overlays?[]`, `sheet?: n≤16` | PNG block + caption | read-only |
| 6 | `load_skill` *(context lane owns content)* | Progressive-disclosure knowledge, incl. generator catalogue | `name` | markdown | read-only |
| 7 | `add_clip` | New generator or raster clip | `kind: generator\|raster`, `layer` (id/label or `"new"`), `start_ms`, `duration_ms`, `generator?` (`ring_pulse` / `ring_pulse@1`), `params?{}`, `label?`, `ref?`, `frames?` (raster: count or `frame_ms`) | envelope + new id | idempotent via `ref` |
| 8 | `edit_clip` | Change clip properties (absolute set) | `clip`, `set:{start_ms?, duration_ms?, layer?, label?, params?{}, opacity?, blend?: normal\|lighten, in?:{type, ms, ease}, out?:{…}, speed?}` | envelope | idempotent |
| 9 | `set_keyframes` | Replace the keyframe list for one animated property | `clip`, `property` (e.g. `params.radius`, `opacity`), `keyframes:[{t_ms, value, ease}]` (`[]` clears) | envelope + sampled values | idempotent |
| 10 | `paint` | Raster drawing (see Q3) | `clip`, `frame?` \| `frames?: [..] \| "all"`, `ops[]` (flat `kind`-discriminated) | envelope | not idempotent (shift); else effectively idempotent |
| 11 | `manage_layers` | Layer CRUD/order | `action: add\|rename\|reorder\|set_visible\|set_blend`, `layer?`, `name?`, `position?: top\|bottom\|above:<id>\|below:<id>`, `ref?` | envelope | — |
| 12 | `remove` | Delete clips/layers/frames | `ids[]`, `frames?` | envelope (what was removed) | destructive (undoable) |
| 13 | `set_document` | Document settings | `name?`, `tick_ms?`, `duration_ms?`, `loop?: none\|loop\|pingpong`, `playback_speed?` | envelope | idempotent |
| 14 | `revert` | Undo own sub-steps in this turn | `to_step` \| `steps: n` | envelope (restored summary) | destructive (own steps only) |
| 15 | `create_variant` | Fork current doc (or a given variant) for alternatives; subsequent writes take `variant?` | `label`, `from?: main\|<variant>` | `variant` id | — |
| 16 | `present_variants` | End turn offering a choice row | `variants[]` (2–3), `question` | ends turn; UI shows pick | — |

Notes:
- **Macros** such as crossfade, loop ×N and reverse are better as `edit_clip` options or a later `macro` tool once the eval shows demand [speculative].
- `make_transition` is folded into `edit_clip.in/out`. A crossfade is two overlapping clips, per the synthesis.
- Rasterizing a generator happens automatically on `paint` against a generator clip (D8). The result warns "c4 converted from ring_pulse@1 to raster; revert to undo".
- There is no `finish` tool. The turn ends when the model replies without tool calls. `present_variants` is the only turn-ending tool. **[speculative; eval whether small models need an explicit `done`]**

### 3b. Later, not v1: what supporting small/8k-context models would cost

v1 targets mid-capability and stronger models. Small models are supported only if cheap; otherwise they are unsupported for now. If they are added later, the expected cost is:

- **A ~7-tool reduced set** derived from the same registry:
  - `look`: merges describe/view_frame/view_motion.
  - `analyze`: fewer checks.
  - `add_clip`: auto-assigns the layer.
  - `edit_clip`: flat args plus `animate:{param, from, to, ease}`, which compiles to 2 keyframes.
  - `paint`: fewer kinds, ≤16 ops.
  - `remove`.
  - `revert`.
  - No variants, no images.

  This is cheap if each `ToolSpec` already carries a `tier` and its handlers map to the same commands. That's the only v1 design hook worth paying for now: one field.
- **A context budget redesign for 8k windows:** tool schemas plus a core glossary may alone approach ~3–5k tokens [speculative]. That needs the context lane's compaction and runtime skill injection. This is likely the expensive part.
- **Provider work:** lenient parsing of malformed calls, Natural-Language-Tools-style fallback (arXiv 2510.14453), and the Constraint-Tax trap on self-hosted servers. All of these belong to the providers lane.
- **Eval cost:** one more tier in the harness matrix. MCP-GRANITE shows granularity fit can outweigh model size **[verified abstract]**, so the reduced set must be measured, not assumed.

---

## 4. Runtime architecture sketch

```
 UI (Compose)                    AgentSession (app-scoped, one per open doc)               :core
 ───────────                     ─────────────────────────────────────────                ─────
 ChatScreen ── observes ──► StateFlow<AgentUiState>                                      ToolRegistry
  [Stop] [Keep/Undo]             │ runTurn(userMsg):                                       ├─ specs (schema, hints, tier)
 Canvas (soft-locked             │   journal.beginGroup(author=agent:<model>)              ├─ lenient normalize → validate
  during turn; play/scrub ok)    │   loop step=1..maxSteps:                                ├─ handlers → Commands (atomic)
                                 │     req = build(transcript, tools(tier))                └─ result envelope / errors
                                 │     resp = provider.stream(req) ─► text tokens → UI     Renderer/Baker, Analysis, Views
                                 │       (retry: transport only, backoff, Retry-After)      Journal (undo groups, sidecar)
                                 │     if no tool calls → break
                                 │     for call in resp.calls (sequential):
                                 │        withContext(NonCancellable){ registry.execute }
                                 │        persist(step)  ─► transcript sidecar + journal
                                 │     caps: steps, calls/step, ops/call, cost, loop-detect
                                 │   journal.endGroup(status)  → UI shows Keep/Undo
 Optional: shortService FGS started at turn start (Android 14+, ~3 min), onTimeout → cancel
 Next launch: open group found → mark interrupted → [Keep][Undo][Continue]
```

---

## 5. Risks

1. **Schema/provider mismatch.** Strict-mode requirements, Gemini schema subsets, gateways that mangle tool calls, and the Constraint Tax on vLLM/SGLang **[secondary]**. Mitigation: flat schemas, golden schema files, per-provider adapters (providers lane), and the eval harness.
2. **Mid-tier open-weight models still fail multi-step edits** (Qwen3-32B in the Aseprite test blamed working tools). Mitigation: generators-first design, actionable errors, and honest per-model support labels from evals. Small models are out of v1 scope (§3b).
3. **Building the loop costs more than expected**, mainly through retries/streaming parsing across providers. Mitigation: the Koog spike, and adopting its client layer if it fits.
4. **Agent reverts user work between turns.** Mitigation: the journal-derived "user changes since last turn" note, and the analysis-based locality check from the perception report.
5. **Process death / OEM background killing** (Nothing OS behaviour unknown **[verify on device]**). Mitigation: step-level persistence, the interrupted-turn UX, and an optional shortService.
6. **Tool surface creep.** Every new command is tempted to become a tool. Mitigation: the registry requires a tier and an allowlist decision, and an eval must justify any new tool (OpenAI <20 guidance).
7. **kotlinx-schema is experimental.** Mitigation: golden files make the generator swappable, and hand-writing is a viable fallback.
8. **Variants complicate the journal** (forks plus a pick). Mitigation: variants are in-memory forks; picking one applies it as a single step in the turn group, and the others are discarded. [speculative]

---

## 6. Open questions for the user

1. **Lock vs live:** is soft-locking the canvas during an agent turn (with Stop always available) acceptable UX for v1?
2. **Turn end UX:** an explicit Keep/Undo bar after every agent turn, or only Undo (keep by default)?
3. **Backgrounding:** is it acceptable that a turn may die if you leave the app for a few minutes, with the interrupted-turn recovery? Or do you want the `shortService` from day one?
4. **minSdk:** the target devices (Phone 3, 4a Pro) ship recent Android. Is minSdk ≥ 34 acceptable? It simplifies FGS-type handling and `onTimeout` [verify device Android versions].
5. **Build vs adopt:** OK to time-box a 1-day Koog spike, with "build thin loop" as the default if it's inconclusive?
6. **Variants in v1** (`create_variant` / `present_variants`), or defer until the basic loop is evaluated?
7. **Transcript storage:** keep agent chat per document, excluded from `.glyphwiz` exports by default?
8. **Code-mode later:** if the tiny expression language from the synthesis is ever built, should frontier models get a `run_script` tool? (Not v1.)

---

## 7. Sources

**Tool design guidance**
- Anthropic, Writing effective tools for agents: https://www.anthropic.com/engineering/writing-tools-for-agents [verified]
- Anthropic, Building effective agents: https://www.anthropic.com/engineering/building-effective-agents [verified]
- OpenAI function calling guide: https://developers.openai.com/api/docs/guides/function-calling [verified]
- MCP spec, Tools (2025-11-25): https://modelcontextprotocol.io/specification/2025-11-25/server/tools [verified]
- MCP tool annotation defaults (secondary): https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html · https://chatforest.com/guides/mcp-tool-annotations-explained/

**Tool count / granularity papers**
- MCP-GRANITE: https://arxiv.org/abs/2609.24161 [verified abstract]
- How Many Tools Should an LLM Agent See?: https://arxiv.org/abs/2605.24660 [secondary]
- WorkBench: https://arxiv.org/pdf/2405.00823 [secondary]
- Toolshed: https://arxiv.org/pdf/2410.14594 [secondary]
- Natural Language Tools: https://arxiv.org/abs/2510.14453 [verified abstract]
- Constraint Tax: https://arxiv.org/pdf/2606.25605 · summary https://aiweekly.co/alerts/open-weight-llms-drop-tool-calls-silently-under-json-schema [secondary]
- Land 2026, ARC-AGI-2 formats/lenient parsing (via prior report): https://arxiv.org/abs/2606.31543

**Creative-app agents**
- Blender MCP: https://github.com/ahujasid/blender-mcp [verified README]
- Figma, Agents meet the canvas: https://www.figma.com/blog/the-figma-canvas-is-now-open-to-agents/ · write to canvas: https://developers.figma.com/docs/figma-mcp-server/write-to-canvas · agent help: https://help.figma.com/hc/en-us/articles/37998629035799-Work-with-the-Figma-agent-in-design-files · forum on undo: https://forum.figma.com/ask-the-community-7/does-figma-ai-agent-support-undo-55401
- tldraw agent starter kit: https://tldraw.dev/starter-kits/agent · template: https://github.com/tldraw/agent-template · spatial harness: https://tldraw.dev/blog/harnessing-the-agents · fairies talk: https://gitnation.com/contents/agents-on-the-canvas-with-tldraw-3450
- Canva MCP best practices: https://www.canva.dev/docs/apps/mcp/best-practices/ · tool consolidation (secondary): https://www.canva.com/help/mcp-canva-usage/
- Photoshop AI Assistant (secondary): https://www.dpreview.com/news/5482929072/adobe-ai-assistant-for-photoshop-launch-public-beta/ · https://helpx.adobe.com/photoshop/desktop/whats-new/ai-assistant-overview.html
- Aseprite tool-calling pixel art: https://ljvmiranda921.github.io/notebook/2025/07/20/draw-me-a-swordsman/
- Cloudflare Code Mode: https://blog.cloudflare.com/code-mode-mcp/ · https://workos.com/blog/cloudflare-code-mode-cuts-token-usage-by-81
- Asset Forge: https://matthewdeaves.github.io/assetforge/ · Pixel Art Bench: https://huggingface.co/blog/AINovice2005/pixel-art-bench · pixel-llm: https://github.com/mxmarchal/pixel-llm · math diagrams via SVG: https://arxiv.org/pdf/2503.07429
- Dry-run validation pitfall: https://github.com/saworbit/didi/issues/399 [secondary]

**Kotlin / frameworks**
- Koog: https://github.com/JetBrains/koog · 1.0 announcement: https://blog.jetbrains.com/ai/2026/05/koog-1-0-is-out-stable-core-better-interop-and-multiplatform-observability/ · tools: https://docs.koog.ai/tools/ · HTTP clients: https://docs.koog.ai/prompts/http-clients/ · persistence: https://api.koog.ai/agents/agents-features/agents-features-snapshot/ai.koog.agents.snapshot.feature/-persistence/index.html · examples: https://github.com/JetBrains/koog/tree/develop/examples · releases: https://github.com/JetBrains/koog/releases · tool-call addiction: https://blog.jetbrains.com/ai/2025/07/when-tool-calling-becomes-an-addiction-debugging-llm-patterns-in-koog/ · custom endpoint discussion: https://github.com/JetBrains/koog/discussions/757
- kotlinx-schema: https://github.com/Kotlin/kotlinx-schema · kt-schema: https://github.com/kpavlov/kt-schema · JsonSchemaGenerator: https://github.com/Stream29/JsonSchemaGenerator · xemantic-ai-tool-schema: https://github.com/xemantic/xemantic-ai-tool-schema · kotlinx.serialization schema issue: https://github.com/Kotlin/kotlinx.serialization/issues/2432
- MCP Kotlin SDK: https://github.com/modelcontextprotocol/kotlin-sdk
- openai-java: https://github.com/openai/openai-java · anthropic-java: https://github.com/anthropics/anthropic-sdk-java
- LangChain4j Android question: https://github.com/langchain4j/langchain4j/issues/1135 · Embabel 1.0 (secondary): https://www.danvega.dev/blog/embabel-1-0-ga

**Android runtime**
- Foreground service types (shortService, dataSync, specialUse): https://developer.android.com/develop/background-work/services/fgs/service-types
- User-initiated data transfer: https://developer.android.com/develop/background-work/background-tasks/uidt
- ViewModel SavedState: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate
- KMP default structure / AGP 9: https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/
- Plain-Kotlin module testing blueprint: https://github.com/googlesamples/android-testing-templates/blob/master/AndroidTestingBlueprint-kotlinApp/README.md
