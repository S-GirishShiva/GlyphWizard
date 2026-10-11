# Novel and Hybrid Animation Models for GlyphWizard

**Date:** 2026-10-11
**Scope:** Representations *beyond* the conventional ones (pure raster, Lottie/OTIO/Aseprite-style keyframe layers, code-first Pixelblaze, WLED/xLights), which a separate research track covers.
**Context:** Monochrome circular LED matrix (Phone (4a) Pro: 13x13 grid, 137 LEDs; Phone (3): 25x25, 489 LEDs), brightness 0-255, playback by pushing `int[]` frames at about 20-60 fps. Interop target is the Glyph Museum (GM) JSON format. Humans and in-app LLM agents edit the same document through the same command layer.

Legend: **[V]** = verified against the cited source during this research. **[U]** = unverified, inferred, or from secondary sources. **[E]** = my own estimate or engineering judgement.

---

## 1. TL;DR and top recommendation

No single novel model wins on all six goals. Each one is strong on one axis and costly on another:

- **Op-logs** are good for undo and provenance, and bad as the stored source of truth.
- **SDF and field models** are good for resolution independence, and bad for interop and hand-drawn art.
- **ASCII grids** are good for an agent to read, and only fair for it to write.
- **Node graphs** are overkill at this scale.

The best-scoring design is a small hybrid I'll call **"Baked clips + recipes + journal"**:

| Layer | Recommendation |
|---|---|
| **Canonical storage** | A GlyphWizard project JSON holding a **timeline of clips**. A clip is either a `raster` clip (explicit frames with full 0-255 values, GM-compatible) or a `recipe` clip (a declarative generator such as `ring_pulse`, `orbit_dot`, `fade`, `text_scroll` or `wipe`, with typed, versioned parameters and normalized coordinates). Both kinds are plain data, never code. |
| **In-memory model** | An immutable Kotlin data-class tree mirroring the JSON, plus a memoized **render cache**: baked `IntArray` frames per clip, keyed by a hash of the clip and the device profile. |
| **Edit operations** | One command set (the command pattern), used by both the UI and agent tool calls. Every command is validated, inverse-able (undo), and grouped into **transactions** (one agent turn equals one undo step). Commands are appended to a **journal** with an actor tag (`user`, `agent:<model>`, `import`). |
| **Agent-facing view** | (a) A compact **structural summary** of clips, params and durations as text or JSON. (b) An on-demand **hex-row "contact sheet"** of rendered frames for reading. (c) **Semantic, task-level tools** for writing, such as `add_recipe_clip`, `set_param`, `draw_shape` and `paint_rows`, rather than raw 137-number arrays. Every write returns a short rendered preview. |
| **Playback** | Only ever plays the **baked `IntArray` frames**. Recipes are evaluated off the UI thread when the cache is invalid, never per tick. |
| **Export** | Bake to GM `{v, frames:[{p,d}]}`, which is lossless by construction because raster values are kept at full 8-bit. Recipe parameters and the journal travel only in the native `.glyphwiz` file (or a sidecar), not inside GM files, until it is confirmed that GM importers tolerate unknown top-level keys. **[V]** GM says importers should ignore unknown keys in `meta`, but it does not say so for the top level. |
| **Import** | A GM file becomes **one raster clip**: a lossless round trip. Optionally, a later "detect recipe" pass can propose structure, but never silently. |

The ideas borrowed from the novel candidates:

- **From event sourcing:** the journal, used for undo and provenance only. It is not replayed to rebuild the document.
- **From baking and caching (Houdini, Blender):** "recipe + baked cache", where recipes are the non-destructive part.
- **From SDF and field graphics:** recipes are evaluated per LED from normalized `(x, y)` coordinates, Pixelblaze-`render2D` style, so they are resolution-independent for free.
- **From trackers:** named reusable frames or clips plus an order list. This is cheap reuse and token-efficient.
- **From the LLM grid research:** let agents *read* grids, but have them *write* through semantic tools. Where they must write pixels, use one character per cell, row-per-line, with a validator.

Do **not** build: a general node graph, an expression language, CRDT multiplayer, a constraint solver, or the op-log as the source of truth (see section 5).

---

## 2. Candidates

Scores run 1-5 against the six goals. **G1** = GM lossless mapping, **G2** = LLM-friendliness, **G3** = non-destructive human editing, **G4** = resolution independence, **G5** = cheap, deterministic, simple to implement, **G6** = undo, provenance, diff and migration wins.

### Summary table

| Candidate | G1 GM | G2 LLM | G3 Non-destr. | G4 Res-indep. | G5 Simple/cheap | G6 Wins | Verdict |
|---|---|---|---|---|---|---|---|
| A. Event-sourced op-log as source of truth | 4 (via bake) | 3 | 4 | 2 | 2 | 5 | Use as **journal**, not truth |
| B. Baked raster + generator recipes | 5 | 4 | 4 | 4 | 4 | 4 | **Core of recommendation** |
| C. SDF / field / implicit | 3 (export only) | 3 | 5 | 5 | 3 | 3 | Use **inside** recipes, declaratively |
| D. Text / ASCII grid formats | 4 (8-bit) / 2 (hex) | 4 read / 3 write | 1 | 1 | 5 | 3 | **Agent view + diffs**, not storage |
| E. Node graph / dataflow | 3 | 2-3 | 5 | 4 | 1 | 3 | Overkill, skip |
| F. Tracker (patterns + order list) | 5 | 4 | 3 | 2 | 5 | 3 | **Borrow the reuse idea** |
| G. Constraint / declarative intent | 2 | 4 (authoring) / 2 (editing) | 3 | 4 | 1 | 2 | Leave intent to the agent, not a solver |
| H. Grammar / L-system / cellular automata | 3 | 3 | 3 | 2 | 4 | 2 | Just one more recipe type |
| I. "Diffusion-style" / ML generated | 3 | 2 | 1 | 2 | 1 | 1 | Out of scope |

---

### A. Event-sourced / op-log documents

**What it is.** The source of truth is an append-only log of commands such as `addFrame` or `setPixel`. The current document and frames are projections, rebuilt by replaying the log. This is attractive here because agent tool calls *are* commands, so the log becomes a perfect audit trail.

**Prior art.**
- Martin Fowler, Event Sourcing: https://martinfowler.com/eaaDev/EventSourcing.html
- **tldraw** moved *away* from a command-based undo system to automatic **diff-based** history. Changes accumulate until a "mark", then are flushed as one undo entry. Remote changes and `history: 'ignore'` writes are excluded from undo. **[V]** https://tldraw.dev/sdk-features/history, https://github.com/tldraw/tldraw/pull/3364
- tldraw's open issues show the hard edges. Rolled-back transactions can leave entries on the undo stack (#10846), and undo-driven changes are labeled with source `user`, so they are hard to tell apart from real edits (#8477). **[V]** https://github.com/tldraw/tldraw/issues/10846, https://github.com/tldraw/tldraw/issues/8477
- **Automerge** keeps full op history in a compressed binary form at about 30% overhead on a benchmark text **[V]** (https://automerge.org/blog/automerge-2/). One editor document with about 200k ops took about 40 s to load before the team changed sync strategy **[V]** (https://automerge.org/blog/merry-commitmas/). automerge-repo compacts by snapshotting at "heads" and dropping incremental chunks **[U, third-party]** (https://patternist.xyz/posts/concurrent-compaction-in-automerge-repo/).
- A real-world argument that compaction destroys queryable history without bounding growth: https://github.com/jfolcini/agaric/issues/4700 **[U]**
- Photoshop's History panel is a bounded op-log (default 50 states) with "snapshots". It is session-scoped and not persisted by default **[U, from general knowledge]**.

**Pros.** Undo and redo are free. Provenance per op ("agent X changed fade from 200 to 400 ms") makes time-travel possible. It is a natural fit for tool calls.

**Cons and pitfalls.**
- **Replay is the source of truth, so every old command must remain executable forever.** Version migration becomes "migrate every historical command", which is much harder than migrating a snapshot.
- Corruption anywhere in the log corrupts everything after it.
- Logs grow with fine-grained ops. A finger-drag paint stroke is hundreds of `setPixel`s. You need compaction and snapshots, at which point you have built a snapshot format anyway.
- Projections need a rendering of every op on load. That is cheap for 137 LEDs, but the cost is *complexity*, not CPU.
- Determinism: replay must be bit-exact across app versions.
- An LLM cannot read a log to understand "what the animation is". It needs the projection anyway.

**Verdict.** Keep the *benefits* (undo, provenance, audit) by storing a **snapshot as truth plus a journal of commands as history**. This is what tldraw effectively does with diffs **[V]**. The journal is optional and compactable, and losing it loses only history, not the animation.

---

### B. Baked raster + generator "recipes" (provenance-annotated)

**What it is.** Frames are canonical and GM-compatible. Ranges of frames carry, or are produced by, a *recipe*: a named generator plus its parameters. Editing a recipe parameter re-bakes that range. This is the "smart object" or "adjustment layer" pattern.

**Prior art.**
- Houdini's File Cache SOP and Blender's bake and cache systems both separate an expensive procedural definition from a cached result that is invalidated when inputs change **[U, general knowledge]**. Houdini docs: https://www.sidefx.com/docs/houdini/nodes/sop/filecache.html
- Photoshop Smart Objects and adjustment layers, and Figma components and instances, are non-destructive references to a source plus parameters **[U, general knowledge]**.
- **Directly relevant prior art in the Glyph ecosystem:** the **Glyphic / GlyphControllers** format already mixes the two kinds of animation. `"type":"procedural"` animations name a `generator` (`ripple`, `level_fill`, `eq_bars`, `charge_flow` and others) with `params`. Explicit animations use **named reusable frames** written as **13 strings of 13 hex characters** (`0-f` times 17, `.` = off, `#` = full). Invalid entries are skipped and listed under "PROBLEMS" instead of crashing. **[V]** https://github.com/AK-120/GlyphControllers

**Pros.**
- GM export is a trivial bake. GM import becomes a raster clip, so the round trip is lossless.
- Non-destructive where it matters. "Change the fade length" is a recipe param edit.
- LLM-friendly: `{"type":"orbit_dot","period_ms":1000,"radius":0.8,"dir":"cw"}` is short, semantic and easy to validate against a schema.
- Resolution independence for recipe clips, because recipes sample normalized coordinates (see C).

**Cons and pitfalls.**
- **Recipe versioning.** If `ring_pulse` v1 changes its math in v2, old files render differently. Mitigations: version each generator (`"type":"ring_pulse@1"`), keep old implementations, and optionally store the baked frames alongside as ground truth.
- **"Detaching".** A human who hand-edits pixels inside a recipe range must either convert it to raster (losing editability, as with rasterizing a smart object) or you need an "overlay" raster layer on top. Recommend: offer "Convert to frames" explicitly, as Photoshop does with "Rasterize". Defer overlays.
- **Library creep.** Each generator is code to maintain. Start with about 6-10.
- Determinism needs care: integer time, seeded RNG, and fixed rounding (see section 5).

**Verdict.** **Core of the recommendation.**

---

### C. SDF / field-based / implicit graphics

**What it is.** Shapes are functions `f(x,y,t)`, either a signed distance (`< 0` inside) or a direct brightness field. Each LED samples the field at its physical center. Anti-aliasing comes naturally by mapping distance to brightness with a smoothstep.

**Prior art.**
- Inigo Quilez, 2D distance functions (circle, box, segment, arc, ring, star and others): https://iquilezles.org/articles/distfunctions2d/ **[U, well-known reference]**
- Shadertoy (https://www.shadertoy.com) uses per-pixel `mainImage(fragCoord)`, the canonical field model.
- **Pixelblaze `render2D(index, x, y)`** with a pixel map normalized to 0..1 means "the same code works on any grid size". The docs suggest distance-based brightness instead of snapping to integer pixels for smooth motion on irregular maps. **[V]** https://electromage.com/docs/intro-to-mapping/, https://github.com/simap/pixelblaze/blob/master/README.mapper.md

**Can it be declarative (no code)?** Yes, and it should be. Expose a **fixed vocabulary of primitives and modifiers as JSON**, not an expression language, for example:
```json
{"shape":"ring","cx":0,"cy":0,"r":0.6,"width":0.12,"softness":0.05,
 "level":255,"anim":{"r":{"from":0.1,"to":0.9,"ms":800,"ease":"outQuad"}}}
```
The renderer is roughly 100 lines of Kotlin: about 6 SDF primitives, union by max brightness, smoothstep falloff. **[E]**

**Pros.** Best possible resolution independence: 13x13 and 25x25 render natively from the same description. Perfect non-destructiveness. Very compact. LLMs are good at naming shapes and numbers.

**Cons and pitfalls.**
- **Hand-drawn pixel art does not fit.** A drawn smiley is not an SDF. You still need raster.
- **At 13x13, sampling hides intent.** A 0.05-wide line may hit zero LEDs. You need minimum-width clamps or supersampling (4 samples per LED is cheap).
- Free-form expressions (Shadertoy or Pixelblaze code) are a security and robustness hazard for LLM output and a big implementation lift (parser, sandbox). Avoid them.
- Export to GM is bake-only, so there is no round trip back to SDF.

**Verdict.** Use it as the **evaluation strategy for recipe clips** (each LED has normalized `(x,y)`, and recipes are fields). Do not make it a separate document model.

---

### D. Text / ASCII grid formats for LLMs

**What it is.** Frames written as rows of characters (one char per LED, or per cell with a mask character) instead of JSON number arrays.

**Evidence.**
- **ARC-AGI-2 solver (Land, 2026)** benchmarked **9 grid formats**: space-separated, semicolon, XML-tagged, CSV, Python lists, sparse coordinates, ASCII symbols, binary masks and compact pipe-delimited. CSV won. Suboptimal formats cost "on the order of 10%". **Compact formats that are hard for LLMs to *produce* (sparse coordinates, binary masks) were substantially worse.** Separately, *forcing strict JSON output via API-level response formats underperformed*, and robust lenient parsing plus validation was "worth it". **[V]** https://arxiv.org/abs/2606.31543 (sections 4.2 and 8.4)
- **Text2Space (COLM 2026)**: a **read-write asymmetry**. LLMs *interpret* ASCII layouts effectively but *struggle to produce* them from text, and construction errors propagate. **[V, abstract level]** https://arxiv.org/abs/2604.14641
- **Tokenization:** BPE merges adjacent digits or characters unpredictably (`10100011` might become `[1010][00][11]`), which breaks column alignment. Inserting a space before each cell gives one token per cell on some tokenizers. **[U, from search snippets]** ASCIIEval: https://arxiv.org/abs/2410.01733. Bit-puzzle paper: https://arxiv.org/pdf/2606.23672. Early ARC prompting: https://arxiv.org/pdf/2305.18354. That 2023 paper also found **object-based representations** help LLMs considerably on ARC **[U, title and abstract level]**.
- **Pixel Art Bench (2026)**: LLMs generating palette-indexed 24x24 grids as JSON. Small models pass structural checks but fail semantically, and tiny models fail even structure. **[V, blog level]** https://huggingface.co/blog/AINovice2005/pixel-art-bench
- Ecosystem precedent: GlyphControllers' 13-char hex rows **[V]** (above).

**Encoding comparison for one 13x13 frame** (token counts are **[E]** rough estimates for modern BPE tokenizers):

| Encoding | Lossless 0-255? | Approx. tokens/frame | Read | Write | Notes |
|---|---|---|---|---|---|
| GM JSON `p:[...]` (137 ints) | Yes | 300-450 | Poor (no 2D shape) | OK, but row boundaries are invisible | Reading order with centered row widths 5, 9, 11... is easy to get wrong |
| CSV rows, 13x13 with mask cells | Yes | 400-550 | **Best (ARC evidence)** | Good | Most verbose |
| Hex rows, 1 char per cell (`0-f`, `.`) | **No (16 levels)** | 80-200 (merging) | Good | Good for simple art | Lossy: 255/17 quantization |
| Hex rows, space-separated | No | ~170-190 | Good | Good | One token per cell is more likely |
| 2-hex-digit per cell (`00`-`ff`) | Yes | 250-350 | Fair | Fair | Lossless but noisy |
| Run-length (`5.3f5.`) | Either | Lowest | **Poor** | **Poor** | Matches "compact formats are hard to produce" |
| Sparse coords `[(x,y,v)...]` | Yes | Low for sparse | Fair | Poor (ARC) | Fine for "set 3 pixels" ops |

**Key pitfall for GlyphWizard:** hex chars quantize to 16 levels. Since GM is 0-255, **any agent-text view using 1 char per cell is a lossy view** and must never be written back over full-precision data without an explicit decision. Mitigations: make hex rows a *read* and *sketch* format; when an agent writes hex rows, the result is a new raster clip at quantized levels, flagged in provenance. Fine gradients come from recipes, not hand-entered hex.

**Verdict.** Use it as the **agent read view** (a contact sheet of rendered frames) and as a **sketch input** for simple frames. It is also excellent for **human-readable diffs in tests and git**. Do not use it as canonical storage.

---

### E. Node graphs / dataflow

**What it is.** TouchDesigner, Blender Geometry Nodes, Nuke, ComfyUI: a DAG of operator nodes with typed parameters, serialized as JSON.

**Evidence for LLM editing.** ComfyBench (CVPR 2025) found agents resolve only about 15% of "creative" workflow tasks. Its ComfyAgent represents workflows *as code*, which reportedly beat raw JSON graphs and element lists **[U for the ablation, secondary summary]**. ComfyGPT needed SFT plus GRPO-trained models and multi-agent error correction to generate valid workflow JSON **[V, abstract level]**. Graph JSON with node IDs and edge references is easy for LLMs to corrupt with dangling links and type mismatches. https://arxiv.org/abs/2409.01392, https://arxiv.org/abs/2503.17671

**Verdict.** Too heavy to build (graph editor UI on a phone, type system, evaluator) and harder for agents than flat clips. A *linear* stack (clip, then optional per-clip modifiers such as `brightness`, `mirror`, `reverse`) captures about 90% of the value **[E]**. Skip general graphs.

---

### F. Tracker-style sequencing (MOD, XM, Renoise)

**What it is.** Music trackers store reusable **patterns** plus an **order list** (pattern sequence), with per-row effect commands. Very compact. Repetition costs one byte per repeat.

**Prior art.** XM format and OpenMPT documentation: https://wiki.openmpt.org/Manual:_Patterns, https://wiki.openmpt.org/Manual:_Order_List **[U, general knowledge]**. In the Glyph ecosystem, GlyphControllers' named frames referenced by name from an ordered `frames` list are exactly this pattern **[V]**.

**Pros.** Huge token savings for loops, such as "frames A, B, C repeated 8 times" or a heartbeat. Edits to a shared pattern propagate, which is non-destructive reuse. It is trivial to implement and to bake.

**Cons.** "Edit one instance only" needs copy-on-write or a "make unique" action, as with Figma "detach instance". The effect-column model is cryptic for humans and not needed.

**Verdict.** **Borrow** named, reusable clips and frames plus an order list (`sequence: ["intro", "loop", "loop", "loop", "outro"]`, or `{"ref":"loop","repeat":3}`). Skip effect columns.

---

### G. Constraint / declarative intent

**What it is.** The document stores intent ("dot orbits clockwise once per second, starting at 12 o'clock"), and a solver or compiler produces frames.

**Assessment.** For this domain the "compiler" *is* the LLM agent plus the recipe library. Storing natural-language intent as truth is non-deterministic, because recompiling with a different model gives different frames. A real constraint solver (Cassowary-style) has no natural home in a 137-pixel animation. **[E]**

**Verdict.** Store intent as a **provenance annotation** (`"note": "user asked: dot orbits clockwise once per second"`) next to the recipe the agent chose. That gives you explainability without non-determinism.

---

### H. Grammar / L-system / cellular automata

**What it is.** Rule-based generation, such as Game of Life on the circular mask, rule-30 rings, or L-system branches.

**Assessment.** Fun, tiny, deterministic with a seed, and naturally resolution-dependent: a CA at 13x13 behaves differently at 25x25. It fits as **one recipe type** (`{"type":"life@1","seed":42,"steps":60}`). It is not a document model. **[E]**

### I. Others considered briefly

- **Keyframed "vector sprites"** (the shape plus transform tracks that Lottie uses) belong to the other research track. Note only that SDF recipes with `anim` param tracks (C) cover the useful subset.
- **Learned or generative models** (diffusion on 13x13) are out of scope: non-deterministic, not editable, and heavy.
- **CRDTs for multiplayer** (Automerge, Yjs, Loro): no multi-device co-editing requirement, so the cost is unjustified. If needed later, a snapshot-plus-commands design migrates to CRDTs more easily than a raw-array design does. **[E]**

---

## 3. Agent-tooling lessons from creative-app integrations

**Evidence base:**
- Anthropic, "Writing effective tools for AI agents" (Sep 2025) https://www.anthropic.com/engineering/writing-tools-for-agents. Principles: few, well-chosen tools; namespacing; return meaningful, token-efficient context; clear descriptions; evaluate with agents. **[U, read via summaries]**
- **MCP-GRANITE (2026)**: same capabilities at four granularities, 9 models, 8,748 runs. **Task-level (about 4 tools)** reached 0.49 task completion, versus 0.42 for fine-grained tools (8-10) and 0.36 for one monolithic tool. Argument accuracy nearly doubled versus fine-grained (0.40 vs 0.20). A monolithic tool led to "no tool called" in 28% of runs. A 3.2B model at the right granularity beat a 20.9B model at the wrong one. **[V]** https://arxiv.org/abs/2609.24161. Caveat: tested on IoT mock servers and small local models.
- **Blender MCP** practitioner reports: screenshot or visual feedback after each operation was "the single biggest quality improvement". Separating planning from code generation helped. Operator `FINISHED` does not mean anything changed, so verify actual state. Multi-step agent actions make undo awkward. One server exposes 22 of 556 tools up front with a tool-search escape hatch. **[V, practitioner blogs and forum]** https://devtalk.blender.org/t/3d-agent-blender-ai-assistant-built-a-multi-agent-system-on-top-of-blenders-mcp-and-python-api-and-sharing-architecture-learnings-for-the-lab-discussion/44260, https://www.strayspark.studio/blog/blender-mcp-server-v4-556-tools-22-tool-front-door, https://www.mixar.app/blender-agent/blender-mcp-guide
- **Figma MCP** "write to canvas" works best when the agent composes from *existing* components and variables, and Figma ships markdown "skills" to guide workflows. Their FAQ warns that agent output may need manual fixing. **[V, docs level]** https://developers.figma.com/docs/figma-mcp-server/write-to-canvas, https://help.figma.com/hc/en-us/articles/39252411778583-Figma-MCP-server-FAQs
- **Edit formats (aider and Diff-XYZ):** whole-file rewrite is the easiest for weak models but token-heavy. Search/replace-style edits work best for strong models. Function-call-wrapped code edits underperformed for older models. LLMs miscount line offsets. **[V]** https://aider.chat/docs/benchmarks.html, https://arxiv.org/abs/2510.12487
- The ARC solver found strict API-enforced JSON output *hurt* accuracy, while lenient parsing plus validation helped **[V]**.

**Do:**
1. **Use about 4-8 task-level tools**, not one per command and not one `apply_ops` mega-tool. For example: `inspect` (summary or contact sheet), `add_clip` (recipe or raster), `edit_clip` (set params, retime, reorder, delete), `paint` (rows, shapes, or sparse pixels on a raster frame), `preview` (render range to text and play on LEDs), `undo`/`checkpoint`.
2. **Return the rendered result from every write.** That means a compact hex contact sheet of the affected frames plus warnings ("frame 3: 0 LEDs lit", "line width < 1 LED"). This is the text analogue of Blender's "screenshot after every op". If the user's model supports vision, optionally attach a PNG upscaled to about 13x13 times 16.
3. **Reference by stable IDs, never by index alone.** Clips get short IDs (`c3`) that survive reorders. Indices shift, which matches the aider lesson that LLMs miscount offsets.
4. **Validate before applying, with actionable errors.** "`radius` must be 0..1.5, got 4; did you mean 0.4?" Be lenient on input (accept `"#"`/`"f"`/`255`; tolerate missing optional fields), strict on what you store.
5. **Bundle an agent turn into one transaction**, so one "Undo" reverts the whole agent action. This fixes the Blender undo pain. Tag it with provenance: actor, model, and the prompt excerpt.
6. **Give the agent a dry-run** (`preview` without commit) and a **checkpoint/restore**.
7. **Keep summaries compact**: params plus durations, not raw frames, unless asked. Use progressive disclosure, so contact sheets are produced on demand and per range.
8. **Ship a "skill" or system prompt** describing the coordinate system (normalized, origin at center, y up or down, decided once), the recipe catalog with examples, and the limits.

**Don't:**
- Make the agent emit 137-int arrays as its primary write path. That is tokens-heavy and error-prone (row boundaries are invisible), and the read-write asymmetry applies.
- Let agents emit executable code or expressions.
- Rely on API-level strict-JSON modes as the only safety. Validate in your command layer instead.
- Expose dozens of micro-tools (`setPixel`, `setFrameDuration` and so on) individually.

---

## 4. Proposed hybrid architecture for GlyphWizard

### 4.1 Canonical storage format (`.glyphwiz.json`, illustrative, not final)
```json
{
  "format": "glyphwizard", "schema": 1,
  "device": {"id": "phone4a_pro", "gm_v": 4, "grid": 13, "leds": 137},
  "frames": {                         // named reusable raster frames (tracker idea)
    "heart_small": {"p": [0,0,255, ...137 ints...]}
  },
  "clips": [
    {"id": "c1", "kind": "raster", "frames": [{"ref": "heart_small", "d": 120}, {"p": [...], "d": 80}]},
    {"id": "c2", "kind": "recipe", "type": "ring_pulse@1",
     "params": {"r0": 0.1, "r1": 0.9, "width": 0.15, "ms": 800, "ease": "outQuad", "level": 255},
     "fps": 30}
  ],
  "sequence": [{"clip": "c1"}, {"clip": "c2", "repeat": 3}],
  "provenance": {"c2": {"by": "agent:claude-...", "note": "user: 'pulse like a heartbeat'"}},
  "baked": {"hash": "sha256:...", "gm_v": 4}   // optional cache stamp
}
```
- The raster `p` stays **full 8-bit GM layout** (centered rows, reading order), so import and export are byte-identical.
- Pixel data lives in the **device-native layout**. A future `device` change triggers an explicit "convert" operation (below), never an implicit one.

### 4.2 In-memory model
- Immutable Kotlin data classes mirroring the JSON, using `kotlinx.serialization`.
- `DeviceProfile`: grid size, row widths, LED index to `(col,row)`, and **normalized `(x,y)` per LED** in [-1,1] with the circle radius equal to 1. This is the Pixelblaze-map idea and the single key to resolution independence.
- `Renderer`: `clip + profile -> List<Frame(IntArray, durationMs)>`, memoized by `(clipHash, profileId)`.

### 4.3 Edit operations / commands
- A single `sealed interface Command` with `apply(doc): Result<Doc>` and an inverse, or simply store the previous immutable doc. With immutable state, undo can be a stack of document snapshots: tiny docs, trivial code. **[E]**
- Every command is used by the UI *and* exposed (grouped) as agent tools.
- **Journal** (optional, Phase 2): an append-only list of `{t, actor, txId, command}`. Truncate or compact freely, because the snapshot is the truth.

### 4.4 Agent-facing representation
1. `inspect()` returns a summary: device, clips with IDs, kinds, params, durations, sequence, total length.
2. `inspect(clip, frames=0..5)` returns a contact sheet. Example 4a Pro frame, 1 hex char per LED, `' '` outside the mask, rows aligned to the 13-wide grid:
   ```
       00f00
     000fff000
    00ffffff000
   ...
   ```
   It is labelled "quantized view (16 levels)".
3. Writes go through tools that take semantic params, or `paint(rows=[...])` for sketches with a lenient parser.

### 4.5 Playback
- `PlaybackService` receives baked `List<IntArray>` and durations only. It knows nothing about recipes. Bake happens on edit (debounced), off the main thread. For 137 LEDs times a few hundred frames, that is microseconds to milliseconds **[E]**.

### 4.6 Export / import
- **GM export:** sequence, then baked frames, then `{v: profile.gm_v, frames:[{p, d}]}`. Add `meta` only for published-attribution data per the GM spec. Keep GlyphWizard extensions out of GM files (or test GM tolerance first).
- **GM import:** detect device from `v` or array length (489 or 137) **[V]**, producing one raster clip with all values preserved.
- **Device conversion** (25x25 to/from 13x13):
  - Recipe clips: re-render with the new profile. This is exact and free.
  - Raster clips: resample using normalized LED coordinates. Downscale by area average or max over source LEDs whose centers fall in each target cell. Upscale by bilinear interpolation or nearest. Mark the result `provenance: converted_from`.
  - This asymmetry is a strong argument for pushing agents and users toward recipes for geometric animation.

---

## 5. Risks and overengineering warnings

- **This is 137 bytes per frame.** A 10-second, 30 fps animation is about 41 KB raw. Storage efficiency, compaction, CRDTs, and binary formats are non-problems. Optimize for *clarity of code*, not bytes. **[E]**
- **Biggest real risk: building a generator framework before any animation plays.** Recommended phasing:
  1. Raster clips only, plus GM import/export, plus snapshot undo.
  2. The command layer exposed as agent tools, plus a text contact sheet.
  3. 3-5 recipe types (`fade`, `ring_pulse`, `orbit_dot`, `wipe`, `text_scroll`), with "Convert to frames".
  4. Named reusable frames and the sequence order list.
  5. Journal and provenance UI.
  6. Device conversion.
- **Recipe determinism:** use integer milliseconds for time, compute `t = frameIndex * frameMs`, and round brightness via `(v + 0.5).toInt().coerceIn(0,255)`. Avoid `Random()` without a seed. Kotlin/JVM float math is IEEE-deterministic on one platform (Java 17+ is strictfp by default), but avoid relying on cross-version transcendental-function results for golden tests. Store a baked hash so regressions are detectable. **[E, partly U]**
- **Generator versioning debt:** never change a published generator's math in place. Add `@2`.
- **Lossy agent views:** guard against an agent rewriting a full-precision raster through the 16-level hex view, or GM round trips silently lose gradients. Require an explicit `quantize: true` or a separate tool.
- **Op-log as truth** is a trap (section 2A): migration of old commands, replay bugs, and corruption amplification.
- **Node graphs and expression languages** cost large UI and security budgets with weak LLM evidence (ComfyBench).
- **Normalized coordinates convention:** decide once (origin center, +y up or down, radius-1 circle) and write it in the agent skill. Mismatched conventions are the most likely class of agent error **[E]**.
- Mark all **[E]** token estimates as needing measurement. Run a tiny eval: 20 prompts times 3 tools, scored by humans, before locking the agent view.

---

## 6. Open questions for the user

1. **Do GM files need to carry GlyphWizard editability?** Shared GM files could be "flat only", with the native format for remix. Or should we test whether GM's importer ignores an extra top-level key (`x_glyphwizard`)?
2. **Which recipe types matter first?** Pick 3-5 from: fade in/out, ring pulse, orbiting dot, wipe or sweep, scrolling text or digits, sparkle (seeded), progress arc, Game of Life.
3. **Should hand-editing a recipe clip auto-convert it to raster**, with a warning, or should we support a raster overlay on top of recipes (more complex)?
4. **How much history to keep?** Session-only undo (simple), or a persisted journal with provenance (who and which model did what)?
5. **Agent model assumptions:** do we assume vision-capable models, so a PNG preview can be returned, or design for text-only first?
6. **Coordinate convention** for agents: normalized [-1,1] with origin at center (recommended), or integer grid coordinates?
7. **Is 16-level quantization acceptable for agent-sketched frames**, or must agents be able to write exact 0-255 values (two hex digits per cell)?

---

## 7. Sources

**Glyph ecosystem**
- Glyph Museum developer format: https://glyphmuseum.com/developers **[V]**
- GlyphControllers / Glyphic JSON (hex rows, named frames, procedural generators): https://github.com/AK-120/GlyphControllers **[V]**
- Nothing GlyphMatrix Developer Kit: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- Glyph Matrix Editor: https://pauwma.github.io/GlyphMatrixEditor/

**Event sourcing, history and CRDTs**
- Fowler, Event Sourcing: https://martinfowler.com/eaaDev/EventSourcing.html
- tldraw history: https://tldraw.dev/sdk-features/history **[V]**; PR #3364: https://github.com/tldraw/tldraw/pull/3364; issues #10846 and #8477 **[V]**
- Automerge 2.0: https://automerge.org/blog/automerge-2/ **[V]**; Merry Commitmas: https://automerge.org/blog/merry-commitmas/ **[V]**
- automerge-repo compaction (third-party): https://patternist.xyz/posts/concurrent-compaction-in-automerge-repo/ **[U]**
- Op-log compaction critique: https://github.com/jfolcini/agaric/issues/4700 **[U]**
- Loro document size: https://www.loro.dev/docs/performance/docsize

**Baking, procedural and fields**
- Houdini File Cache: https://www.sidefx.com/docs/houdini/nodes/sop/filecache.html **[U]**
- Inigo Quilez 2D SDFs: https://iquilezles.org/articles/distfunctions2d/ **[U]**
- Pixelblaze mapping: https://electromage.com/docs/intro-to-mapping/ **[V]**, https://github.com/simap/pixelblaze/blob/master/README.mapper.md **[V]**

**LLMs and grids, graphics and edit formats**
- Land 2026, ARC-AGI-2 solver (9 grid formats, CSV best, strict JSON hurt): https://arxiv.org/abs/2606.31543 **[V]**
- Text2Space, read-write asymmetry: https://arxiv.org/abs/2604.14641 **[V, abstract]**
- LLMs and ARC, object-based representations: https://arxiv.org/abs/2305.18354 **[U]**
- ASCIIEval: https://arxiv.org/abs/2410.01733 **[U]**
- Modality and error propagation in ARC-AGI: https://arxiv.org/abs/2511.15717 **[U]**
- Pixel Art Bench: https://huggingface.co/blog/AINovice2005/pixel-art-bench **[V, blog]**
- SVGenius: https://arxiv.org/abs/2506.03139; SVGEditBench V2: https://arxiv.org/abs/2502.19453; VGBench: https://arxiv.org/abs/2407.10972 **[U]**
- ComfyBench: https://arxiv.org/abs/2409.01392; ComfyGPT: https://arxiv.org/abs/2503.17671 **[U/V abstract]**
- Aider edit-format benchmarks: https://aider.chat/docs/benchmarks.html, https://aider.chat/docs/unified-diffs.html **[V]**; Diff-XYZ: https://arxiv.org/abs/2510.12487 **[U]**

**Agent tooling in creative apps**
- Anthropic, Writing effective tools for agents: https://www.anthropic.com/engineering/writing-tools-for-agents **[U, via summaries]**
- MCP-GRANITE tool-granularity benchmark: https://arxiv.org/abs/2609.24161 **[V]**
- Blender MCP lessons: https://devtalk.blender.org/t/3d-agent-blender-ai-assistant-built-a-multi-agent-system-on-top-of-blenders-mcp-and-python-api-and-sharing-architecture-learnings-for-the-lab-discussion/44260, https://www.strayspark.studio/blog/blender-mcp-server-v4-556-tools-22-tool-front-door, https://www.mixar.app/blender-agent/blender-mcp-guide **[V, practitioner]**
- Figma MCP write to canvas: https://developers.figma.com/docs/figma-mcp-server/write-to-canvas; FAQ: https://help.figma.com/hc/en-us/articles/39252411778583-Figma-MCP-server-FAQs **[V, docs]**

**Trackers**
- OpenMPT manual, Patterns and Order List: https://wiki.openmpt.org/Manual:_Patterns, https://wiki.openmpt.org/Manual:_Order_List **[U]**
