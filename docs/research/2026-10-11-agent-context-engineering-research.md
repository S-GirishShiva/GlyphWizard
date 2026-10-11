# Agent Context Engineering & Knowledge Base Research (area A5)

Date: 2026-10-11
Status: research and recommendations only, no code. Feeds D13 (open-weight and small models are first-class), D14 (reuse by external agents later) and D15 (thorough context engineering).
Builds on `2026-10-11-agent-perception-research.md` §Q4 ("K" layer) without repeating it. Tool and runtime design, provider reliability, and UX/safety/evals are covered by other agents. This report touches them only where they set the context budget.

**Scope update (user, 2026-10-11):** v1 targets **mid-capability and larger models (≈32k+ windows)**. Small models with 8–16k windows are supported only if that is cheap; otherwise they are marked unsupported for now. The KB below is therefore designed for the **mid and large tiers**. Small-model material is kept as background evidence, and §4.1 estimates what it would cost to add an 8–16k tier later. Wherever the text below says "small tier", read it as "future, optional".

Confidence markers (same as the perception report): **[verified]** = checked against a primary or near-primary source in this session · **[secondary]** = blog, summary, or third party · **[speculative]** = my reasoning, not directly evidenced · **[verify]** = conflicting, from memory, or version-dependent; check before relying on it.

---

## 1. TL;DR and recommendations

**Main point:** For a narrow-domain agent the important context-engineering decision is not how much knowledge to include. It is how much to keep *out* of the window, and how cheaply the right piece can be brought in. The evidence agrees across Anthropic, Chroma, NoLiMa, Manus and the tool-loadout papers: accuracy drops as context grows, and it drops sooner and harder for small models. Our domain helps here. The document is an external, exact source of truth, the analysis tools are exact oracles, and the knowledge needed for any one request is small. So the knowledge base (KB) should be a **small always-on core plus focused on-demand skills plus a few retrieved worked examples**. Much of it should be **generated from the Kotlin source of truth** and **tested in CI** like code.

Recommendations, in priority order:

1. **A budgeted core prompt, in a fixed order for caching:** role and rules → device facts (generated from the `DeviceProfile`) → representation legend → short glossary → skill index → 1 canonical worked trajectory. Target ≈2–3k tokens of prose for mid and large tiers, excluding tool schemas. Even 32k+ models should run with a working-set cap far below their window (§4). Fail CI when a file exceeds its budget (§Q5).
2. **Skills follow the Agent Skills / SKILL.md pattern, delivered through a `load_skill(name, section?)` tool.** Only name and description are always visible. Skill bodies are ≤1.2k tokens and split into sections loadable on their own. Mid-tier models decide for themselves when to load. A cheap deterministic keyword router that preloads the likely skill is an **optional add-on**: worth it if evals show missed loads on mid models, and required if a small tier is ever added **[speculative; eval it]**. Keep the format compatible with the open agentskills.io spec so the same folder can serve external agents later (§Q7).
3. **The example library is retrieved by tags + BM25/TF-IDF, not embeddings.** At 20–60 items, an on-device embedding model costs more than it saves. Lexical selection beat embedding selection for few-shot picking in one 2025 study. Expose it as `search_examples(query)` → titles + 1-line descriptions, then `get_example(id)`.
4. **Few-shot: 1 static canonical trajectory in the prefix, plus 2–3 retrieved exemplars on demand. Never dozens.** Evidence: 3 well-chosen examples ≈ 9; message-formatted examples beat string-formatted ones for some model families; models from GPT-4o down to Gemma 3 4B peak and then *decline* as more examples are added. Our domain is unfamiliar to every model, which is the case where examples help most (LangChain "Multiverse Math": Claude 3 Haiku 11% → 75%).
5. **Document state goes in context as an ID index + summary, never full JSON by default.** It is regenerated each turn and placed at the *end* of the context (Manus-style "recitation"). Detail comes through read tools (`describe_clip(id)`, `view_frame`). Each write returns a compact diff plus analysis verdicts.
6. **Compaction is cheap because the document lives outside the context.** Keep a short agent-maintained or app-maintained **design brief** (goal, approved constraints, user preferences). Drop old tool results and replace them with a stub that names the tool to re-call. Compact in bulk when the tier's working-set cap is reached. A future small tier would instead rebuild the context from scratch each turn.
7. **Prompt caching is designed in, not assumed:** a byte-stable prefix, deterministic JSON serialization, a tool set fixed per session (tier chosen at session start, so no mid-session tool add/remove), and loaded skills *appended* as tool results instead of edited into the system prompt. Caching is automatic on OpenAI, DeepSeek, Gemini (implicit), xAI, Moonshot, Z.AI and vLLM V1. It is explicit on Anthropic and Alibaba. Minimum prefix sizes are 1024–4096 tokens. The mid/large prefix (≈5–8k with tool schemas) clears all of them.
8. **Generate, don't hand-write, anything the code knows.** Device facts, generator catalogue (params, ranges, defaults, units), tool docs, and **text-grid snapshots of each generator at canonical params** come from the pure-Kotlin core through a Gradle task. CI fails if the generated KB is stale.
9. **Test the KB at three levels:** (a) static lint (token budgets, frontmatter, links, every exemplar parses, renders and matches its stored metrics fingerprint); (b) deterministic "doc-tests" (every command snippet in a skill runs against the command layer); (c) live evals in the shared harness with **KB ablations** (with/without each skill, 0/1/3 examples). Stamp `kbVersion` into every eval result and every agent provenance entry.
10. **Small (8–16k) models later, if ever:** an estimated ~3–5 extra dev-days for an assembly profile, a router and a per-turn rebuild, plus ongoing eval cost. The real risk is model capability, not packaging. See §4.1.
11. **Start small and grow from failures:** core + 4 skills + ~10 exemplars for v1. Add knowledge only where an eval shows a failure it fixes. SkillsBench found curated focused skills help (+16 pp average). It also found they hurt on 16 of 84 tasks, and that self-generated skills don't help at all.

**Overengineering flags:** no vector DB or embeddings, no sub-agents, no separate compressor model, no RAG over tool schemas (we have ~10 tools, not 100), no promptfoo/Node toolchain if the Kotlin harness can do it.

---

## 2. Findings with sources

### Q1. State of the art and what applies here

| Source | Key claim | What it means for GlyphWizard |
|---|---|---|
| Anthropic, *Effective context engineering for AI agents* (2025) | Context is "a finite resource with diminishing marginal returns"; find "the smallest set of high-signal tokens". System prompt at the "right altitude" (not brittle if-else, not vague). Minimal, unambiguous tool sets. Few-shot: "curate a set of diverse, canonical examples" and avoid "a laundry list of edge cases". Just-in-time retrieval through lightweight identifiers. Compaction ("start by maximizing recall… then improve precision"). Structured note-taking outside the window. **[verified]** | Core prompt states *principles + procedures*, not every rule. Document, frames and exemplars are referenced by ID and fetched on demand. A "design brief" note is our structured note. |
| Anthropic, *Equipping agents for the real world with Agent Skills* (2025) + open standard at agentskills.io (Dec 18 2025) | SKILL.md with required `name` + `description` frontmatter. Level 1 (name/description) always loaded, level 2 body loaded when relevant, level 3+ linked files and scripts as needed. Advice: evaluate first, find gaps, then write skills; split large skills; watch real usage. **[verified]** (adoption by Codex CLI and others **[secondary]**) | Adopt the folder format verbatim. Our "scripts" are deterministic Kotlin tools (analysis, canonical shapes), so bulky reference data (circle tables, font bitmaps) can live in tools instead of tokens. |
| Manus, *Context Engineering for AI Agents* (Ji, 2025) | KV-cache hit rate is "the single most important metric". ~100:1 input:output. Cached input 10× cheaper on Claude. Stable prefix (a timestamp at the top kills the cache). Append-only, deterministic serialization. **Mask, don't remove tools** mid-task. File system as external memory, with restorable compression (drop the content, keep the URL). **Recitation** (todo.md at the end of context) against lost-in-the-middle. **Keep errors in context**. "Don't few-shot yourself into a rut": add controlled variation. **[verified]** | Directly applicable: (1) the document = our file system, (2) state summary at the end = recitation, (3) keep validation errors visible, (4) fixed tool set per session, (5) vary the serialization of repeated exemplars. |
| LangChain, *Context engineering for agents* (2025); Breunig, *How Long Contexts Fail* / *How to Fix Your Context* (2025) | Write / select / compress / isolate. Failure modes: poisoning, distraction, confusion, clash. Tool loadout: Llama 3.1 8B **fails with 46 tools, succeeds with 19** ("Less is More", arXiv 2411.15399). RAG-MCP: selection breaks above ~30 tools, up to 3× better with retrieval. **[verified via Breunig's summary; papers secondary]** | Keep the tool count low (~10–12). A future small tier must see **fewer** (≤~10, fewer if possible). "Clash" means generated docs must never contradict hand-written skills, so generate shared facts from one source. |
| Cognition, *Don't build multi-agents* (2025) | Share full context; "actions carry implicit decisions". Compression by an LLM "is hard to get right". **[verified]** | Single agent. No sub-agents. Prefer *lossless* externalization (doc + brief) over LLM summarization. |
| Chroma, *Context Rot* (2025): 18 models, ~194k calls | Performance becomes "increasingly unreliable as input length grows", even on trivial tasks. Distractors compound. LongMemEval: focused ~300-token prompts beat full ~113k prompts for **all** models. **[verified]** | Even large-context models should run with a *working-set cap* far below their window (§5). |
| Liu et al., *Lost in the Middle* (TACL 2024) | U-shaped accuracy: info at the start/end is used best. **[verified, abstract-level]** Newer models are reportedly less affected **[secondary]**. | Rules at the start (prefix), current state + brief at the end. Avoid placing key state between old tool results. |
| NoLiMa (Modarressi et al., ICML 2025) | When retrieval requires inference rather than literal matching, 11/13 models fall to half their short-context score by 32k. **Gemma 3 4B effective length < 1k, Gemma 3 12B ≈ 1k**, Llama 3.1 70B ≈ 2k. **[secondary via search snippets; leaderboard numbers verify]** | For small models, everything beyond a few thousand tokens is weakly used. The small tier must be **aggressively minimal**, and knowledge must sit near where it's used (in the tool result, not 6k tokens back). |
| IFScale (Jaroslawicz et al., 2025) | Instruction-following degrades as the number of simultaneous instructions grows. Best frontier ≈68% at 500 instructions. Smaller models decay earlier and linearly. **Primacy bias** toward earlier instructions. **[secondary/abstract]** | Core rule list ≤~10 rules for small models, most important first. Move situational rules into the skill or tool result where they apply. |
| He et al., *Does Prompt Formatting Have Any Impact…* (2024) | Same content in plain/Markdown/JSON/YAML: up to 40% swings on GPT-3.5; larger models more robust; no universal best format. **[secondary/abstract]** | Pick one format and eval it per model family. Expect small models to be format-sensitive. |
| Kutschka & Geiger, *Notation Matters* (2026) | Token-optimized formats (TOON/TRON) save 18–27% tokens but cost up to 9–14 pp accuracy vs JSON on tool use. **[verified, abstract]** | Don't invent a clever compact state notation. Use plain JSON for tool arguments and short Markdown/key-value for summaries. |
| SkillsBench (arXiv 2602.12670, 2026) | Curated skills +16.2 pp average (range +4.5 to +51.9). 16/84 tasks got worse. Self-generated skills ≈0 or negative. Haiku 4.5 + skills > Opus 4.5 without. Focused skills with 2–3 modules beat comprehensive docs. **[secondary]** | Skills are worth it, especially for weaker models. Keep them focused and *curated by us*. Don't let the agent write its own skills. Ablate per skill because some can hurt. |

**What changes for small models (8–32k windows, weak long-context use)** **[speculative synthesis of the above]**:
- **Effective context ≪ advertised context.** Plan the small tier around ~4–6k of *useful* tokens, even on a 16k window.
- **Model-driven retrieval is less reliable.** Deciding to call `load_skill` is itself a tool-selection decision, and small models are worst at those (Less is More, Nanbeige BFCL "multiple" 43%). Add **host-side preloading** (deterministic router) and keep `load_skill` as a backup.
- **Fewer tools, flatter schemas, enums over prose.** The tool catalogue can dominate a small prompt (one study: ~2.2k of 2.4k prompt tokens were tool catalogue **[secondary]**).
- **Rebuild context per turn instead of accumulating history.** The doc + brief carry continuity losslessly.
- **More worked examples, fewer rules.** But only 1–2 (over-prompting declines appear early in 4–8B models).
- **Local runtimes may silently truncate.** Ollama's default `num_ctx` is small (4096 per FAQ; 2048 on older versions; VRAM-dependent on newer) and **silently discards leading context**, which is exactly where our system prompt lives. **[secondary, verify per version]** The provider agent should own the fix. The budget here assumes the app knows the *real* configured window.

### Q2. Knowledge inventory and evidence base

For each knowledge area: what the agent needs, where the content comes from, and evidence quality. "Gen" = generated from code. "Hand" = authored. "Measure" = must be measured on hardware (no public source).

| # | Area | What the agent needs (compressed) | Source of truth / evidence | Quality |
|---|---|---|---|---|
| K1 | **Matrix geometry** | Grid size, row widths, mask, coordinate convention (row-major, origin top-left, center (6,6) / (12,12)), LED count, odd-width centering limits | `DeviceProfile` in Kotlin (**Gen**). Row widths from Glyph Museum spec, sums checked in perception report. Dev Kit: 13×13 / 25×25, `getDeviceMatrixLength()` | **[verified]** |
| K2 | **Brightness model** | 0–255 per LED, 255 default, values capped. Perceived brightness is non-linear (CIE 1931 lightness or gamma ~2–2.8 is the usual LED correction), so "half brightness" ≠ 128. Fades should be perceptually even | GlyphMatrix Dev Kit (range) **[verified]**. CIE-1931 LED PWM literature (jared.geek.nz, Adafruit gamma guide, Linux backlight patch) **[secondary]**. **Whether Nothing's driver already applies a curve is unknown: Measure** | partial |
| K3 | **Physical appearance** | Round dots with gaps, glow/bleed between neighbours, minimum visible level, how the matrix looks under the back glass, viewing distance | **No public source found. Measure** (photograph ramps, single LEDs, checkerboards). Perception report open question 1 | **gap** |
| K4 | **Timing** | Tick (default 20 ms = 50 fps), real device refresh, frame-time quantization, speed multipliers | D10. Device max rate: **Measure** (hardware spike) | partial |
| K5 | **Document model & commands** | Clips/layers/keyframes/easing names/transitions/blend modes, IDs, one turn = one undo group, validation error format | Kotlin command layer + schemas (**Gen**). Glossary.md (hand, already exists) | own spec |
| K6 | **Generator catalogue** | Per generator: purpose, params (type, range, default, unit), what each param does visually, **text-grid snapshots at 2–3 param settings**, version (`@1`) | Generator registry annotations (**Gen**) + snapshots rendered by the headless renderer (**Gen**) + 1–2 hand-written "use when" lines | own spec |
| K7 | **Small-shape craft** | Canonical rasterized circles/rings per radius, icon silhouettes that read at 13×13 (heart, star, arrow, check, cross, bell…), pixel-perfect lines, jaggies (irregular run lengths), orphans, doubles, hand-placed AA, "silhouette test" (set all lit to full; does it read?) | Pixel-art craft (Derek Yu tutorial, Pixel Joint tutorial) **[secondary, practitioner consensus]**. Canonical circles: **Gen** by a deterministic rasterizer + hand-curated overrides. No academic source for low-res icon legibility found **[gap]** | practitioner |
| K8 | **Text on the matrix** | 13×13 fits ~2–3 chars of a 3×5 font; 5×7 for uppercase/numerals; 7×9 needed for lowercase/continuous reading (25×25 only); scroll speeds; letter spacing ≥1 LED | Human-factors dot-matrix legibility: FHWA ATIS guidelines (5×7 for uppercase/numeric, 7×9 for continuous reading/lowercase; size differences vanish above ~22 arcmin), Snyder 1979, Maddox et al. 1977 (more dots per symbol → fewer errors) **[secondary, old but direct]**. Scroll speed for 13 columns: **no source; tune by eval/user test** | decent |
| K9 | **Fonts & licensing** | Use a bundled font tool; never ask the model to draw letters freehand. Licences: **Ndot 57 / NDot 55 are Nothing proprietary, do not redistribute.** **NDOT 47 by Interactivate is OFL 1.1** on FontStruct, but its author notes letters may be close to Nothing's design. **Tom Thumb 3×5 is CC0/CC-BY 3.0/MIT** (keep Swetland's BSD notice) | FontStruct NDOT 47 page; Vdot-Player & ndot-firacode-nerd bundling under OFL **[secondary]**. Tom Thumb on OpenGameArt/FontStruct **[secondary]** | verify licences before shipping |
| K10 | **Motion principles** | Which of the 12 principles survive at 13 px: slow-in/slow-out, timing/spacing, anticipation, follow-through/overshoot, staging (one focal motion), holds, arcs, secondary action (sparingly). Squash/stretch only as brightness pulse. Sub-LED AA for smooth motion (spatial steps of a whole LED read as jumps). Loops: seam continuity, period divides duration. Durations: UI convention ~200–500 ms per motion (Material M1: ~300 ms mobile; enter ~225, exit ~195; M3 200–500 ms tokens, now moving to springs) | Thomas & Johnston, *The Illusion of Life* (1981) via Wikipedia **[secondary]**. Material Design M1/M3 duration pages **[secondary snippets]**. Apparent-motion psychophysics: large displacements are perceived as discrete jumps; Dmax ~15 arcmin and scales with element size (Anstis 1970; mantis/PNAS papers) **[secondary]**. 13 px mapping **[speculative]** | mixed |
| K11 | **Flashing limits** | No more than 3 flashes in any 1-second period (WCAG 2.3.1 general threshold). Avoid sustained high-frequency full-matrix on/off | W3C WCAG 2.x SC 2.3.1 **[verified from memory of the spec; link below; verify the wording]**. Safety policy belongs to the UX/safety agent; the KB only carries the fact | standard |
| K12 | **Nothing's design language** | Public statements only: Glyph = "less distraction, more meaning", "glanceable", "subtle expression", "not… another display that adds noise", "do more with less", "symbols, abstract visuals, and dynamic feedback" (Adam Bates, Head of Design). Brand typography is dot-matrix (NDot) modelled on industrial printers, monochrome-leaning palette (some red accent) | Design Milk interview with Adam Bates **[verified quotes]**. nothing.wiki brand reference (community wiki) and third-party brand breakdowns **[secondary]**. **No official Glyph Matrix design guideline exists** (Dev Kit has none; confirmed again this session) | thin, gap |
| K13 | **Vocabulary glossary** | vague word → candidate meanings → metrics → fix ops | Perception report Q2 table **[speculative design, standard techniques]**. Thresholds from eval calibration | own |
| K14 | **Exemplars** | 20–60 documents with title, tags, 1–2 sentence description, "why it works" notes, metrics fingerprint | Authored by the user/us. Community Glyph Toys only with explicit licence/permission (open question) | own |
| K15 | **Procedures ("workflows")** | How to create from a prompt (pick generator vs raster → draft → analyze → revise ≤2 → present), how to handle a complaint (map word → metric → variants), how to do text | Perception report Q3 loop policy + Anthropic's "procedures not rules" advice | own |

Note on evidence: K3, K4, and the gamma part of K2 **cannot be researched, only measured**. They should land in a `device-physics` skill whose numbers are marked `provisional` until the hardware spike. The KB lint (§6) can enforce that `provisional` facts carry a measurement ticket.

### Q3. Packaging

#### 3.1 Always-in-context core
Ordered for primacy (IFScale, Lost in the Middle) and cache stability (Manus):

1. **Role & hard rules** (≤10 rules, most important first): design-only scope; act through tools, never claim an edit you didn't make; prefer generators/primitives over raw pixel painting; run `analyze` after edits; on ambiguity offer 2–3 variants; ≤2 auto-revise iterations; never trust a visual "looks good" over a failing metric; keep the user's approved constraints.
2. **Device card** (Gen from the `DeviceProfile`, only the *active* device): size, row widths, center, brightness range, tick, 2 lines of physical facts.
3. **Representation legend**: text-grid chars, coordinate format, what `.`/space mean.
4. **Short glossary**: ~12 one-line entries (word → metric names → typical fix), in the same order as the analysis tools.
5. **Skill index**: `name — one-line description (use when …)`, Gen from frontmatter.
6. **One canonical worked trajectory** (small/mid tiers): one complete, short "request → tool calls → results → answer" example. **[speculative; ablate]**

Tool schemas sit alongside, in the provider's tool field. Their descriptions are part of the KB (Gen from Kotlin annotations) and count against the budget.

#### 3.2 On-demand skills
- **Discovery:** the skill index in the core plus `load_skill(name, section?)`. The result arrives as a tool result (append-only, so it is cache-friendly and in recency position).
- **Granularity:** a skill body ≤1.2k tokens, with `## sections` loadable individually (`load_skill("motion", "loops")`). That gives SkillsBench-style "2–3 focused modules" and lets small models load ~300–500 tokens at a time.
- **Host-side preloading (optional for mid; required for a future small tier):** a deterministic router (keywords and tags from the user message, plus the current doc's clip types) injects at most one skill section into the turn's context before the model call. Example: "scroll", "text", "letters", "name", "initials" → `text-on-matrix`; "spin", "jerky", "smooth", "bounce" → `motion`. Cheap, explainable, testable. **[speculative; measure with ablation vs model-driven loading]**
- **Tools replace bulk knowledge (Agent Skills level 3 "scripts"):** canonical circles, font bitmaps, easing curves and icon templates live behind tools (`shape_template("circle", r=5)`, `text_to_raster(...)`). The model needs to know they *exist*, not their pixels. This is the biggest token saving available. **[speculative but directly from the Skills "scripts" idea + Asset Forge finding that primitives beat raw pixels]**
- **Unload:** there is no unload in an append-only context. Compaction (3.5) removes old skill bodies, and the router re-injects them if still relevant.

#### 3.3 Exemplar retrieval: cost-benefit

| Option | Cost | Benefit at 20–60 items | Verdict |
|---|---|---|---|
| All exemplars in prompt | 10–40k tokens | Over-prompting declines in small models; context rot | No |
| On-device embeddings (e.g. small sentence-embedding model via ONNX/LiteRT) | +20–100 MB APK/model, a native runtime, an index build step, multilingual issues | Marginal over lexical search at this corpus size; TF-IDF beat embeddings for few-shot selection in the "Few-shot Dilemma" study **[secondary]** | No (revisit at >500 items) |
| Provider embeddings API | Extra key/provider, network, not offline, violates "fully local" spirit for LAN users | Same | No |
| **Tags + BM25/TF-IDF over title + description + tags** (pure Kotlin, ~100 lines, or a tiny library) | Negligible | Good enough; explainable; testable | **Yes** |
| Let the model choose from the index (`search_examples` returns 5 titles + one-liners) | ~150 tokens per search | Model applies semantics on top of lexical recall | **Yes, combined with BM25** |

Exemplar record format (proposal): `id, title, tags[], device, description (≤40 words), why_it_works (≤60 words), document (compact JSON), fingerprint (metrics), license/author`. The default view the model gets is description + why_it_works + *document summary* (≈200–400 tokens). Full JSON only on request, for copying/adapting.

#### 3.4 Document state in context

| Representation | Tokens (13×13 doc, ~5 clips) **[speculative estimates]** | Use |
|---|---|---|
| ID index (`clip_8f2a raster "heart" L1 0–1200ms; clip_91c0 ring_pulse@1 L2 600–1800 {speed:1.2}`) | ~15–30 per clip | **Always**, end of context |
| Summary (index + keyframes/easing per clip + latest analysis verdicts + duration/loop) | ~300–600 | Default for mid/large; small gets index + verdicts only |
| Full document JSON | 1–5k+ (raster frames dominate) | Never by default; `describe_clip(id)` per clip |
| Frames | ~200 per 13×13 text grid; ~700 per 25×25 | On demand via `view_frame` |

Rules: regenerate the state block each turn *after* the conversation (recitation). Serialize deterministically (stable key order, fixed float formatting). Write tools return `{changed ids, compact diff, analysis verdicts, warnings}` and never echo the whole document (blender-agent "every write returns feedback" finding in the synthesis doc).

#### 3.5 Conversation compaction
- **Lossless first:** the doc and the undo journal are external memory (Manus "file system as context"). The agent never has to remember pixels.
- **Design brief** (structured note, ≤300 tokens): goal, approved constraints ("keep the wink asymmetric"), user preferences ("likes slow fades"), open questions. Maintained by an `update_brief` tool or by the app from explicit user approvals **[speculative; the app-maintained version is simpler and more reliable for small models]**. This also carries the perception report's "constraints satisfied so far" forward.
- **Tool-result clearing:** after N turns, replace old `view_frame`/`analyze`/`load_skill` results with `[elided: view_frame(t=400) — call again if needed]`. Keep error results longer (Manus "keep errors in").
- **(Future small tier only) per-turn rebuild:** prefix + brief + state + last 1–2 exchanges. No running transcript.
- **Mid/large:** compact when the working set passes the tier cap (§5). Summarize old user/agent exchanges into the brief, with recall-first tuning per Anthropic. Avoid LLM-written summaries of tool output (Cognition: "hard to get right").
- Compaction invalidates the cache from the compaction point onward, so do it rarely and in bulk, not every turn.

#### 3.6 Caching across providers

| Provider/host | Mode | Min prefix | Read price | Notes |
|---|---|---|---|---|
| Anthropic | explicit `cache_control` (≤4 breakpoints) or automatic top-level | 512–4096 depending on model (**Haiku 4.5: 4096**) **[secondary, conflicting; verify]** | 0.1× (write 1.25× for 5 min, 2× for 1 h) | Prefix shorter than the minimum is silently not cached |
| OpenAI | automatic | 1024 | 0.25–0.5× | — |
| Google Gemini | implicit (2.5+) or explicit | 1024 (Flash) / 4096 (Pro) | 0.25× | — |
| DeepSeek | automatic | — | 0.1× | — |
| xAI, Moonshot, Z.AI, Groq (some models) | automatic | — | 0.2–0.5× | — |
| Alibaba Qwen (via OpenRouter) | explicit | — | 0.1× | — |
| OpenRouter | passes through provider caching; **sticky routing** keeps the same provider for 10 min, or from the first request when `session_id` is set | — | — | **Send a stable `session_id`** |
| vLLM (self-hosted) | automatic prefix caching, on by default in V1 | block-granular | n/a (latency win) | — |
| llama.cpp server / Ollama | prompt/KV reuse for a shared prefix while the model stays loaded | — | n/a | **[verify defaults per version]** |

Sources: OpenRouter prompt-caching docs **[verified]**, vLLM docs/issues **[secondary]**, Manus. For LAN/self-hosted users the win is **prefill latency** on consumer GPUs, not money. A stable prefix matters even more there **[speculative]**.

Design consequences: (1) nothing dynamic in the system prompt (no date or doc name; the device card changes only when the device changes); (2) tool set and tier fixed at session start; (3) skill loads appended; (4) exemplars retrieved into tool results, not spliced into the prefix; (5) the state block goes last; (6) the Anthropic adapter places one breakpoint after the prefix and one rolling breakpoint near the end.

### Q4. Few-shot exemplars vs instructions

| Evidence | Finding |
|---|---|
| LangChain few-shot tool-calling experiments (2024) **[verified]** | Few-shot helped across the board. Claude 3 Sonnet 16% → 52% with 3 *semantically similar* examples as messages. In an agentic loop with tools whose semantics contradict pretraining ("Multiverse Math"), Claude 3 Haiku went 11% → 75% with 3 message-formatted trajectories. **3 ≈ 9 examples.** Similar > static. Messages > strings for Claude. OpenAI models gained little. |
| Min et al., EMNLP 2022 **[secondary/abstract]** | Demonstrations teach *format, input distribution and label space* more than the exact labels. |
| Tang et al., *The Few-shot Dilemma* (2025) **[secondary]** | Performance peaks and then declines with more examples for GPT-4o, Llama 3.1 8B and Gemma 3 4B. TF-IDF selection > embeddings > random. Optimal count is model-specific. |
| Few-shot degradation study (arXiv 2609.15990, 2026) **[verified abstract]** | Effects are task- and model-dependent (+24 pp vs +3.4 pp; two models degraded). Test per task. |
| Anthropic context-engineering post **[verified]** | Prefer diverse canonical examples over enumerating edge cases in rules. |
| Manus **[verified]** | Repetitive examples cause mimicry. Vary them. |
| SkillsBench **[secondary]** | Curated procedural knowledge lifts small models more than large ones. |

**Recommendation [speculative, to be confirmed by harness ablation]:**
- Our domain is the "Multiverse Math" case: tools and a medium unfamiliar to every model. So **examples of *tool trajectories*** (how to go from "make a heart that beats" to `add_raster_clip` + `add_keyframes` + `analyze`) are likely worth more than prose rules, especially for small and mid models.
- **Count:** 1 static canonical trajectory (prefix) + 2–3 retrieved exemplars (mid/large; 1 for a future small tier). Never more than 3 at once.
- **Format:** the static trajectory is written as real chat messages (user / assistant tool_call / tool result / assistant) only if the provider adapter can emit valid fake tool-call messages for that API. Otherwise it is a compact, clearly delimited transcript in the system prompt. Some OpenAI-compatible chat templates mishandle synthetic tool-call history **[speculative; provider agent to confirm]**. Retrieved exemplars arrive as tool results (document summary + why it works), not as fake turns.
- **Diversity:** the static trajectory and exemplar set should cover generator vs raster vs text cases. Rotate surface wording (Manus).
- **Instructions still matter for *policy*** (scope, iteration caps, variant offering). Examples teach *procedure and format*.

### Q5. Authoring, versioning, testing, sync with code

**Repository layout (proposal):**
```
core/src/main/resources/knowledge/          # pure-Kotlin module (D14), shipped in APK + headless CLI
  kb.toml                                   # kbVersion (semver), budgets per tier, file → tier load policy
  core/  role.md  legend.md  glossary-short.md  trajectory-canonical.md
  generated/  device-<id>.md  generators/<id>@<v>.md  tools.md  skill-index.md   # never hand-edited
  skills/<name>/SKILL.md (+ sections, references/)
  exemplars/<id>.json                       # document + metadata + fingerprint
```

**Sync with code (Gen):**
- Generator registry: each generator declares `id@version`, params (name, type, min/max/default, unit, `@Desc` text, `visualEffect` text). A Gradle task renders **text-grid snapshots** at default/min/max of the most important param with the same `view_frame` formatter the agent sees, so docs and perception match by construction.
- Tool schemas: JSON Schema from `@Serializable` command classes. Options: JetBrains `kotlinx-schema` (incubator, "super experimental"; descriptions need `@SerialDescription`), kpavlov `kt-schema`, or Koog's generator **[secondary]**. A ~200-line hand-rolled `SerialDescriptor` walker is also viable and avoids an experimental dependency **[speculative]**. Descriptions written once in Kotlin feed both the tool schema and `tools.md`.
- `DeviceProfile` → `device-<id>.md`.
- CI: `./gradlew generateKb && git diff --exit-code knowledge/generated` fails on stale docs.

**Testing ladder:**
1. **Static lint (unit tests, every build):** token count per file and per tier assembly vs `kb.toml` budgets (use a fixed tokenizer approximation, e.g. chars/3.5, plus a real count for 1–2 reference tokenizers in the harness); SKILL.md frontmatter valid (name ≤64 chars, description present, per the agentskills spec); internal links resolve; glossary metric names exist in the analysis registry; generator ids referenced in skills exist; no `provisional` fact without an issue link.
2. **Exemplar tests:** every exemplar parses, migrates to the current `schemaVersion`, renders, and reproduces its stored metrics fingerprint within tolerance (golden tests). This catches generator math changes too.
3. **Doc-tests:** every fenced command/tool-call snippet in skills and trajectories is executed against the command layer in the headless core and must validate. This prevents "clash" between KB and code.
4. **Prompt assembly snapshot tests:** the assembled prefix per tier is byte-identical across runs (cache stability) and matches a committed snapshot. Changes are reviewed like code.
5. **Live evals (eval harness, nightly/manual, BYOK cost):** task families from the perception report, run per model × tier × **KB variant** (full / minus skill X / 0-1-3 exemplars / router on/off). Report pass rate, tokens, cost, `load_skill` call rate and accuracy (did it load the right one?). A skill whose ablation shows no gain or a loss gets cut or rewritten (SkillsBench: some skills hurt).
6. **Provenance:** `kbVersion` + tier stamped into each agent journal entry and each eval result, so regressions can be bisected to KB changes.

Promptfoo-style YAML suites are the common industry pattern **[secondary]**, but the graders here are the Kotlin analysis library, so a JUnit-based runner in the harness avoids a second toolchain **[speculative; harness agent decides]**.

**Authoring process:** eval first, then write (Anthropic Skills guidance). Each skill change needs (a) a failing eval case it fixes and (b) no regression on the suite. Hand-written facts carry a source link in an HTML comment that is stripped at assembly, so it costs zero tokens.

### Q6. Tier budgets

See §4.

### Q7. Reuse by external agents (future allowlisted MCP + headless emulator)

- **Format:** skills as agentskills.io-compliant folders (SKILL.md + frontmatter). Claude Code, Codex CLI and other clients can then load the same files natively **[adoption breadth secondary]**.
- **Location:** inside the pure-Kotlin core module resources, the same jar that holds the headless renderer, so app, CLI and MCP server ship one KB version.
- **Exposure via MCP:** the MCP server would expose the same tools; skills as MCP *resources* (`glyphwiz://skills/motion`) and/or the same `load_skill` tool; exemplars via the same `search_examples`. Optionally, MCP *prompts* for the canonical workflows. **[speculative]**
- **Write for a generic reader:** skill bodies reference tool names and concepts, never app UI ("tap the timeline"). App-specific UX policy (variant strip, spend caps) lives in the app's core prompt, not in skills.
- **Tier assembly is a function** `assemble(tier, device, provider) → messages`, which a headless CLI can call to print the recommended system prompt for external agents.
- External frontier agents have large windows, so they would get the "large" assembly. No extra work.

---

## 3. Proposed knowledge-base structure

Sizes are **targets** in tokens, not measurements **[speculative]**. Load policy: **A** = always in prefix, **R** = optional keyword router may pre-inject, **T** = on demand via tool, **G** = generated from code.

| Path | Content | Target size | Load policy | Source |
|---|---|---|---|---|
| `core/role.md` | Scope, ≤10 rules, loop policy, tone | 350 | A | Hand (perception report L3/L4) |
| `generated/device-<id>.md` | Active device card | 150–250 | A, G | DeviceProfile |
| `core/legend.md` | Grid chars, coordinates, coord-list format | 150 | A | Hand (must match formatter, doc-tested) |
| `core/glossary-short.md` | 12 vague words → metric names → fix | 350 | A | Hand (perception Q2) |
| `generated/skill-index.md` | name + "use when" per skill | 120–200 | A, G | Frontmatter |
| `core/trajectory-canonical.md` | 1 worked tool trajectory | 400–700 | A (mid), A or T (large; ablate) | Hand, doc-tested |
| `generated/tools.md` / schemas | Tool schemas + descriptions | 2–3k (full set) | A (provider tool field), G | Kotlin annotations |
| `skills/generators/` | Catalogue: per generator purpose, params, snapshots | ~150–250 per generator; section per generator | T (+R when a generator is named) | G + 2 hand lines each |
| `skills/shapes-small/` | Canonical shapes, silhouette test, pixel-perfect lines, jaggies, AA, icon guidance; points to `shape_template` tool | ≤1,200 (4 sections) | T, R | Hand + Gen tables |
| `skills/motion/` | Principles that survive at 13 px, easing choice, timing at 50 fps, sub-LED AA, loops/seams, flash limit | ≤1,200 (5 sections) | T, R | Hand, cited |
| `skills/text-on-matrix/` | Chars per view, font choice by grid, scroll speed, spacing; points to `text_to_raster` | ≤800 | T, R | Hand, cited |
| `skills/composition-circular-mask/` | Safe area, edge ring, radial symmetry, odd/even centering, top/bottom 5-wide rows | ≤600 | T, R | Hand + Gen safe-area map |
| `skills/device-physics/` | LED glow, perceptual brightness, minimum visible level, `provisional` until measured | ≤600 | T | Measure + cited |
| `skills/nothing-style/` | Public Nothing statements + our observed conventions, clearly labelled as such | ≤400 | T | Design Milk quotes + hand |
| `skills/glossary-full/` | Full vocabulary table with disambiguation questions | ≤1,500 (section per word group) | T | Hand (perception Q2) |
| `skills/workflows/` | create-from-prompt, fix-complaint, make-variants, text-message | ≤500 each | T, R | Hand |
| `exemplars/*.json` | 10 (v1) → 50 items | 200–400 per summary view; full JSON on request | T (`search_examples`/`get_example`) | Hand/user |

v1 minimum: role, device, legend, glossary-short, skill-index, trajectory, tools (G) + skills `generators` (G), `shapes-small`, `motion`, `text-on-matrix` + 10 exemplars. Everything else after the first eval round.

---

## 4. Tier budgets (proposal) **[speculative; calibrate with the harness]**

Principle: budget against the **effective** context, not the advertised window (Chroma, NoLiMa). Leave room for the reply and for reasoning tokens (thinking models can spend thousands). v1 supports **mid** and **large** only (scope update). The minimum supported configured window is **32k**. The app should refuse or warn below that rather than let the host truncate, because truncation silently drops the system prompt (Ollama default `num_ctx` **[secondary]**).

| Component | **Mid** (32–128k window) | **Large** (200k+) |
|---|---|---|
| Core prose (role, device, legend, glossary-short, skill index) | ≤2k | ≤3k (can inline glossary-full) |
| Tool schemas (full set, ~10–12 tools) | ≤2.5k | ≤3k |
| Canonical trajectory | ~0.6k | ~0.6k (optional; ablate) |
| **Stable prefix total** | **≤5k** | **≤6–8k** |
| Loaded skill sections | ≤4k (up to ~3 skills) | ≤8k (as needed) |
| Exemplars | ≤1.2k (≤3 summaries) | ≤2.5k (≤3, full JSON allowed) |
| Design brief + state block | ≤1.2k (summary) | ≤2k (summary + per-clip keyframes) |
| Conversation + tool results (working set) | ≤15k | ≤40k |
| Reserved for output (+ reasoning) | ≥4k (≥8k for thinking models) | ≥8k |
| **Total working cap** | **≈30k, even on a 128k window** | **≈60–70k, even on a 1M window** |
| Compaction trigger | ~25k | ~55k |
| Image renders (T2) | ≤2 renders or 1 contact sheet | ≤4 |

Notes:
- With a ≈5k prefix, the mid tier clears every provider's cache minimum (1024–4096) **[verify Anthropic per model]**.
- A 32k-window mid model is the tight case: 5k prefix + 4k skills + 1.2k exemplars + 1.2k state + 4–8k output leaves ≈12–16k for conversation. That fits ~5–10 editing exchanges before compaction.
- 25×25 frames cost ~3.5× a 13×13 frame. On Phone (3) the agent should prefer region views and coordinate lists.
- The working-set caps are deliberately far below the windows (Chroma: focused ~300-token prompts beat full ~113k prompts for every model tested).

### 4.1 Cost estimate: adding an 8–16k small tier later **[speculative]**

The KB design above already contains most of what a small tier needs (sectioned skills, generated device card, BM25 exemplars, external document state). Additional work if it's ever wanted:

| Item | What | Est. effort |
|---|---|---|
| Small assembly profile | Core ≤1.2k, reduced tool set ≤8 tools / ≤1k schema tokens (Less is More: Llama 3.1 8B fails at 46 tools and succeeds at 19), prefix ≤2.7k | 0.5–1 day (config + budget lint), *plus* the tools agent must define a reduced tool set |
| Keyword router | Host-side preloading of one skill section per turn, since small models can't be trusted to call `load_skill` | 1 day + eval |
| Per-turn context rebuild | Prefix + brief + index-only state + last 1–2 exchanges; app-maintained brief becomes mandatory | 1 day |
| Eval coverage | Run the suite on 2–3 small models, k=3–5 runs; local hosting cost is time, not money | 1–2 days of runs + tuning |
| Ongoing | Every KB change must also pass small-tier budgets and evals | ~10–20% extra eval time per change |

Total ≈3–5 dev-days plus ongoing eval time. Budget: ≈8k working cap (core 1.2k, tools 1k, trajectory 0.5k, 1 skill section 0.6k, 1 exemplar 0.4k, index state 0.6k, last exchange ≤2k, output ≥1.5k). **The packaging is cheap. The blocker is capability.** NoLiMa puts Gemma 3 4B/12B effective length around 1k. Pixel-art benchmarks show 3B models fail to emit valid grids. Small models are weakest at multi-tool selection. Recommendation: keep the KB small-tier-*compatible* (sections, budgets per file), but don't build the small profile until a mid-tier eval baseline exists and a candidate small model passes perception probes.

---

## 5. Risks

1. **Knowledge that's confidently wrong** (gamma, minimum visible brightness, glow) gets baked into prompts before hardware measurement. Mitigation: `provisional` tag + lint + measurement tickets.
2. **Skill misfire** (the model never loads a skill, or loads the wrong one; more likely on weaker mid models). Mitigation: eval the load-accuracy metric; add the keyword router if needed.
3. **Exemplar mimicry / homogenized output** ("every animation is a ring pulse"). Mitigation: ≤3 examples, diversity, Manus-style variation, eval for diversity.
4. **KB–code drift / context clash.** Mitigation: generation + doc-tests + stale-check.
5. **Over-investing in the KB before the agent loop exists.** SkillsBench and Anthropic both say evaluate first. Mitigation: v1 minimum set only.
6. **Cache assumptions fail** (minimums, provider routing, self-hosted defaults). Cost estimates in the perception report assumed caching. Mitigation: read cache usage fields and show them in a debug panel.
7. **Licensing**: fonts (NDOT 47 author's own caveat about similarity to Nothing's design) and community exemplars.
8. **Brand/impersonation risk** in `nothing-style`: describe the aesthetic, don't ship Nothing's proprietary fonts or claim official guidance.
9. **Evidence gaps:** no study of LLM knowledge packaging for tiny-display design; NoLiMa small-model numbers from snippets; Anthropic cache minimums conflicting.

---

## 6. Open questions for the user

1. **Hardware measurements:** can you photograph/measure brightness ramps, single-LED glow, and minimum visible level on the 4a Pro? K2/K3 depend on it.
2. **Who authors the first ~10 exemplars**, and may we include community Glyph Toys/Glyph Museum designs (with permission)? What licence for user-contributed exemplars?
3. **Font choice:** Tom Thumb 3×5 (CC0) as the default + NDOT 47 (OFL, with the author's caveat), or design our own 3×5/5×7? Do you want to avoid anything "Nothing-like" for legal comfort?
4. **Window floor:** confirm 32k configured context as the v1 minimum (refuse/warn below), with small models marked unsupported until §4.1 is justified by evals.
5. **Brief maintenance:** app-maintained (from explicit user approvals, deterministic) or agent-maintained (`update_brief` tool)? I lean app-maintained for v1.
6. **External reuse:** is publishing the skill folder as a standalone Agent Skill (usable in Claude Code/Codex with the headless CLI) a goal, or only MCP later?
7. **Do you want a "Nothing-style" skill at all**, given there's no official guidance and it would be our interpretation?

---

## 7. Sources

Context engineering
- Anthropic, Effective context engineering for AI agents: https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Anthropic, Equipping agents for the real world with Agent Skills: https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills
- Agent Skills open standard: https://agentskills.io · adoption overview (secondary): https://www.unite.ai/anthropic-opens-agent-skills-standard-continuing-its-pattern-of-building-industry-infrastructure/
- Manus, Context Engineering for AI Agents: Lessons from Building Manus: https://manus.im/blog/Context-Engineering-for-AI-Agents-Lessons-from-Building-Manus
- LangChain, Context engineering for agents: https://www.langchain.com/blog/context-engineering-for-agents
- Breunig, How Long Contexts Fail: https://www.dbreunig.com/2025/06/22/how-contexts-fail-and-how-to-fix-them.html · How to Fix Your Context: https://www.dbreunig.com/2025/06/26/how-to-fix-your-context.html
- Cognition, Don't Build Multi-Agents: https://cognition.com/blog/dont-build-multi-agents
- Chroma, Context Rot: https://www.trychroma.com/research/context-rot
- Liu et al., Lost in the Middle: https://arxiv.org/abs/2307.03172
- NoLiMa: https://arxiv.org/abs/2502.05167 · https://github.com/adobe-research/NoLiMa
- IFScale: https://arxiv.org/abs/2507.11538
- Less is More (tool loadout): https://arxiv.org/abs/2411.15399 · RAG-MCP: https://arxiv.org/abs/2505.03275
- He et al., Does Prompt Formatting Have Any Impact on LLM Performance?: https://arxiv.org/abs/2411.10541
- Kutschka & Geiger, Notation Matters: https://arxiv.org/abs/2605.29676
- SkillsBench: https://arxiv.org/abs/2602.12670
- Tool-catalogue token share (secondary): https://arxiv.org/html/2605.17774

Few-shot
- LangChain, Few-shot prompting to improve tool-calling performance: https://www.langchain.com/blog/few-shot-prompting-to-improve-tool-calling-performance
- Min et al., Rethinking the Role of Demonstrations: https://aclanthology.org/2022.emnlp-main.759/
- Agarwal et al., Many-Shot In-Context Learning: https://arxiv.org/abs/2404.11018
- Tang et al., The Few-shot Dilemma: https://arxiv.org/abs/2509.13196
- Few-shot degradation study: https://arxiv.org/abs/2609.15990

Caching / runtimes
- OpenRouter prompt caching (per-provider table): https://openrouter.ai/docs/features/prompt-caching
- vLLM prefix caching: https://docs.vllm.ai/en/stable/api/vllm/config/cache/ · default-on issue: https://github.com/vllm-project/vllm/issues/40896
- Anthropic cache minimums (secondary, conflicting): https://github.com/jt-mchorse/llm-cost-optimizer/issues/266 · https://dev.to/makaminski1337/prompt-cache-floors-are-per-model-and-the-cheap-model-has-the-highest-one-f5j
- Ollama context length FAQ: https://docs.ollama.com/faq · silent truncation discussion: https://news.ycombinator.com/item?id=43274296

Code → docs
- kotlinx-schema: https://github.com/Kotlin/kotlinx-schema · intro: https://kpavlov.me/blog/kotlinx-schema/ · kt-schema: https://github.com/kpavlov/kt-schema · Koog structured output: https://docs.koog.ai/structured-output/
- Promptfoo-style CI regression (secondary): https://lucaberton.com/blog/llm-regression-testing-ci-promptfoo/

Domain knowledge
- GlyphMatrix Developer Kit: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- Glyph Museum developer spec: https://glyphmuseum.com/developers
- Adam Bates interview, Design Milk: https://design-milk.com/the-nothing-phone-3s-glyph-matrix-turns-notifications-into-pixel-art/
- Nothing brand reference (community wiki): https://nothing.wiki/nothing/brand_reference
- NDOT 47 (OFL): https://fontstruct.com/fontstructions/show/2047444/ndot-45-inspired-by-nothing-1 · bundling examples: https://github.com/Javidan-Qasimov/Vdot-Player · https://github.com/GeneralKaos666/ndot-firacode-nerd
- Tom Thumb 3×5: https://opengameart.org/content/tom-thumb-tiny-ascii-font-3x5 · https://github.com/gheja/tom-thumb-ttf
- FHWA ATIS human-factors guidelines (dot-matrix legibility): https://www.fhwa.dot.gov/publications/research/safety/98057/ch03.cfm
- Snyder 1979: https://doi.org/10.1177/001872087902100408 · Maddox et al. 1977: https://journals.sagepub.com/doi/abs/10.1177/001872087701900111
- CIE 1931 LED PWM: https://jared.geek.nz/2013/02/linear-led-pwm/ · Adafruit gamma: https://cdn-learn.adafruit.com/downloads/pdf/led-tricks-gamma-correction.pdf · https://gist.github.com/mathiasvr/19ce1d7b6caeab230934080ae1f1380e
- Pixel art: Derek Yu https://www.derekyu.com/makegames/pixelart.html · Pixel Joint tutorial https://pixeljoint.com/forum/forum_posts.asp?TID=11299
- Twelve principles of animation: https://en.wikipedia.org/wiki/Twelve_basic_principles_of_animation
- Material motion durations: https://m1.material.io/motion/duration-easing.html · https://m3.material.io/styles/motion/easing-and-duration
- Apparent motion: Anstis 1970 https://anstislab.ucsd.edu/files/2012/11/1970-Phi-Movement-as-a-Subtraction-Process.pdf · https://www.pnas.org/doi/10.1073/pnas.1213997110
- WCAG 2.3.1 Three Flashes or Below Threshold: https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html
