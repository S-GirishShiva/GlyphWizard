# Animation Model Research: Source-of-Truth Representation for GlyphWizard

Date: 2026-10-11
Status: Research findings (no code). Input to the document-model design decision.
Scope: Which representation should be canonical for a GlyphWizard animation: (A) pure raster frames, (B) layered timeline with keyframes, or (C) code/expressions. Prior art from LED ecosystems, pixel-art tools, motion-graphics formats, Nothing-specific tools, and LLM-authoring research.

---

## 1. TL;DR and recommendation

**Recommendation: B ("Timeline-lite"), with a small, closed set of clip types including a raster clip and a parametric procedural clip. Add sandboxed expressions (C) later, as one more clip type, behind a deterministic, budgeted evaluator. Treat a baked raster frame sequence (A) as a derived cache and an interchange format, never as the source of truth.**

Every mature system that targets arbitrary pixel layouts uses the same split: the authored, non-destructive model is separate from a baked per-frame channel dump. xLights has effects on layers on models, rendered to FSEQ. WLED has segments, effects, presets and playlists, rendered by firmware. Pixelblaze uses `render2D(index,x,y)` over a pixel map. GlyphWizard should use the same split. With 137 or 489 monochrome pixels, baking is cheap: one minute at 50 fps on Phone (4a) Pro is about 400 KB of bytes. So a rendered cache costs nothing, and "real-time" playback means replaying baked frames.

Refinements to the current leaning:

1. **Time is integer milliseconds, clip-relative for keyframes and absolute for clip placement.** Durations in ms match Glyph Museum `d`, Aseprite frame durations and Divoom `PicSpeed`. Do not use frame indices as the canonical time (fps-dependent, bad for resolution/fps changes), and do not use normalized 0..1 time (ambiguous when clips are resized).
2. **Use absolute start times for clips, not OTIO-style sequential tracks with Gaps.** Sequential tracks ripple: deleting one item shifts every later item. That is an index-arithmetic trap for LLM edits (see the JSON Whisperer findings, section 2.5). Clips on a layer get `start` + `duration`. Overlap on one layer is either disallowed or is the explicit crossfade mechanism, not both.
3. **Transitions: per-clip `in`/`out` transitions (fade/wipe/etc. with duration + easing), xLights-style. Cross-transitions between two clips come from overlapping them on adjacent layers.** Do not model transitions as standalone objects with in/out offsets (OTIO). That adds constraints (no adjacent transitions, no overlapping transitions at one clip's ends, offsets bounded by neighbor durations), and both humans and LLMs violate them.
4. **Keyframes: `{t, value, ease}` with ease from a small named set (`hold`, `linear`, `easeIn`, `easeOut`, `easeInOut`, plus optional `cubicBezier(x1,y1,x2,y2)`).** Hold is a first-class ease, not a separate flag (Lottie's `h` + `i`/`o` handle design is a known source of confusion). Named eases are much easier for LLMs than raw bezier handles.
5. **Device profile is document-level (the target), and every raster asset records its native profile. Do not put a profile on every frame.** Per-frame profiles are redundant and invite corruption: an LLM could emit a frame with the wrong length or profile mid-sequence. Glyph Museum identifies the device once per file (`v`) and rejects frames whose length mismatches. Procedural, shape and text clips are resolution-independent: they render natively at any profile. Only raster clips need resampling.
6. **Procedural clips start as a parametric effect library (WLED/xLights model: `type` + typed params + keyframeable params), not free code.** Expressions come later as `ExpressionClip` with a Pixelblaze-like pure signature `(t_ms, x, y, index) -> 0..1` in normalized coordinates, a tiny custom DSL or a budgeted interpreter, a seeded RNG, and no wall-clock access.
7. **Agent edits go through the same typed command layer as the UI, addressed by stable string IDs (never array indices), validated, batched in transactions, and inverted for undo.** Give the agent a "look" tool that returns an ASCII or row-string rendering of frames at given times. The tldraw agent gets a screenshot plus a simplified shape list for the same reason.

What to explicitly not do: do not adopt Lottie (or an After Effects-shaped model) as the internal format. Do not build Rive-style state machines. Do not let switching device profile wipe data (pauwma's editor does). Do not allow an arbitrary JS engine in v1.

---

## 2. Per-domain findings

### 2.1 Nothing-specific ecosystem (highest relevance)

**Glyph Museum format** ([glyphmuseum.com/developers](https://glyphmuseum.com/developers))
- `{ v, meta?, frames: [ { p: [0..255 per active LED], d?: ms } ] }`. One frame is a static design. `p` lists only physically existing LEDs in reading order (row by row, left to right, rows centered).
- **Important correction to our working assumption: `v` is not a format version. It identifies the target layout.** `v: 1` = Phone (3), 25x25, 489 LEDs. `v: 4` = Phone (4a) Pro, 13x13, 137 LEDs. "New resolutions arrive as a new `v` value with their own row pattern." The array length also identifies the device.
- Compatibility promises: documented fields are never removed or repurposed. Unknown `meta` keys must be ignored. Malformed `meta` must not fail the import. This is a good model for our own versioning policy.
- `d` is "commonly 100" in animations. The page does not define a default when `d` is omitted (**unverified**: what Glyph Museum assumes). The 20 ms minimum frame interval in our brief is not on the developer page (**unverified from the public doc**).
- No resolution conversion is specified.
- Pure raster (option A). As an interchange format it is a flipbook: it cannot carry layers, easing or procedural content. Import gives a single raster clip, and export gives a bake.

**pauwma/GlyphMatrixEditor** ([repo](https://github.com/pauwma/GlyphMatrixEditor), [blog](https://pauwma.com/blog/glyph-matrix-editor-update)). I read `script.js` directly.
- Internal model: `frames[] = { pixels: Map<"row-col", 0..255>, duration: ms }`, with device profiles defined by `gridSize` + `shapePattern` (row widths). The editor's Phone (4a) Pro pattern is `[5,9,11,11,13,13,13,13,13,11,11,9,5]` (sum 137) and Phone (3) is `[7,11,15,17,19,21,21,23,23,25,25,25,25,25,25,25,23,23,21,21,19,17,15,11,7]` (sum 489). **Our brief lists row widths "5,9,11,...,11,9,5"; confirm the middle five rows are 13 wide.**
- Import maps `v` to profile (`{1:'phone3', 4:'phone4a'}`) and hard-rejects any frame whose `p.length` does not match the profile.
- **Switching device clears all frames** ("Switching device will clear all frames. Continue?"). There is no 25<->13 conversion. This is a gap GlyphWizard can fill.
- **Lottie export is a raster flipbook:** each frame becomes a PNG data-URI image asset on its own image layer (`ty: 2`) with `ip`/`op` in/out points. `fr` is derived from the average frame duration, so non-uniform durations get rounded. It is styled as a rendered preview (dot grid, circle mask, "off" color `#1a1a1a`) for Nothing Playground listings. It is not a device-playback format. So "Lottie for glyphs" is a presentation/preview artifact, not a semantic animation model. Nothing Playground ([playground.nothing.tech](https://playground.nothing.tech/)) is Nothing's community hub where Glyph Toys are listed and uploaded. Its exact Lottie requirements were **not found** in public docs.

**Nothing GlyphMatrix Developer Kit** ([GitHub](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit))
- `setAppMatrixFrame(int[])` / `setAppMatrixFrame(GlyphMatrixFrame)` (system build 20250801+). `setMatrixFrame` may conflict with active Glyph Toys. `closeAppMatrix()` stops output. The raw array is described as "25x25" for Phone (3), meaning a full square grid including masked corners. Whether masked cells are ignored, and the row-major order, are **unverified** from the README. Use `Common.getDeviceMatrixLength()` at runtime.
- The SDK has its own mini object model: `GlyphMatrixFrame` with at most 3 objects (`addTop/addMid/addLow`), each with position, rotation (direction inconsistently documented), scale 0-200, brightness 0-255, from a 1:1 bitmap. **Too limited to be our compositor. We should composite ourselves and push raw int arrays.** The 3-layer limit also suggests Nothing considers a few layers enough at this scale.
- No frame-rate limits are documented.
- The device IDs are `Glyph.DEVICE_23112` (Phone 3) and `Glyph.DEVICE_25111p` (4a Pro). Note that the 4a Pro only supports AOD toys.

**Legacy Glyph Composer (Phone 1/2/2a strips)** ([SebiAi tools](https://github.com/SebiAi/custom-nothing-glyph-tools), [Glyphtones guide](https://glyphtones.firu.dev/guide))
- Compositions are OGG audio files with glyph data in the `AUTHOR` metadata tag: base64(zlib(CSV)). Each row is 16.666 ms (60 Hz), there is one column per zone, and brightness runs 0-4095. This is a fixed-rate baked raster: Nothing's own authored format bakes to 60 Hz frames. That supports "bake at a fixed tick for playback."

**Community format divergence:** AK-120/GlyphControllers ([repo](https://github.com/AK-120/GlyphControllers)) uses 13-row string frames with characters as brightness levels. This text-grid representation is readable for humans and LLMs, and it is precedent for an "ASCII-art" agent view (section 4.6).

### 2.2 LED-matrix / pixel-light ecosystems

**xLights** (holiday sequencing; most relevant for device-agnostic layouts)
- Model: sequence -> models (props with arbitrary pixel layouts) -> **layers** per model -> **effects** placed in time on layers. Each effect has typed parameters, and many of them accept **Value Curves**: a parameter varies nonlinearly over the effect's duration, either from a preset shape (ramp, etc.) or drawn by hand ([Value Curves](https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/value-curves), [Glossary](https://manual.xlights.org/xlights/appendicies/glossary)).
- **Render buffers:** each model/group gets a rectangular grid buffer that effects render into. Render styles decide how a group of models maps to a buffer: Default, Per Preview, Single Line, Per Model, and so on ([Rendering](https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/rendering), [Layer Settings](https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/layers/layer-settings), [Model Groups](https://manual.xlights.org/xlights/chapters/chapter-four-tabs/model-groups)). Effects are written against an abstract buffer and then mapped to physical pixels. **This is the key pattern for device-agnostic masks:** render to a logical grid, then sample through the device mask.
- Per-layer post-transforms: rotate/flip, blur, sub-buffer selection (shrinks the effect's area, keyframeable), Roto-Zoom, and "Persistent" (do not clear between frames, for trails).
- Per-effect in/out transitions (fade, wipe, and so on) live on the effect itself, not as separate timeline objects. (**From memory of the xLights UI; not confirmed on the pages fetched.**)
- Output: **FSEQ**, a baked per-frame channel dump with a fixed step time. V2 adds zstd/zlib compression and sparse channel ranges ([FSEQ spec, reverse-engineered](https://github.com/Cryptkeeper/fseq-file-format), [xLights sequence settings](https://manual.xlights.org/xlights/chapters/chapter-five-menus/file/settings/sequences)). This is the authored-model / baked-cache split.
- Pain points: huge group buffers slow rendering ("Max Grid Size, normally 400x400"). Value curves were reported to change when older sequences were reopened ([issue #3482](https://github.com/smeighan/xLights/issues/3482)), a serialization/migration bug and a warning for us. Value curves are quantized (an MCP server notes "snapped to 200 slots per effect").
- **LLM precedent:** [xlights-mcp-server](https://github.com/cwoolum/xlights-mcp-server) lets an LLM author whole sequences through `write_sequence` (a full effect plan: element, layer 0-2, effect, start/end, settings, palette), with `validate_only`, `preview_plan`, `list_models`/`list_effects` (valid vocabulary), and `inspect_sequence`. It never overwrites existing files. Lesson: give the agent the vocabulary, validation before commit, and an inspect/preview loop.

**Vixen 3** ([Display Elements and Groups](https://www.vixenlights.com/docs/concepts/display-elements-groups/))
- Effects apply to element groups at the "highest level" (props), not to individual lights. Location-based effects use the preview's spatial positions. Pixel arch guidance maps blocks of pixels to logical segments, a crude resolution-independence trick.
- Lesson: author against logical structure, not physical LEDs.

**WLED** ([JSON API](https://kno.wled.ge/interfaces/json-api/), [Presets](https://kno.wled.ge/features/presets/), [Mapping](https://kno.wled.ge/advanced/mapping/))
- Model: segments (LED ranges) each running one effect `fx` with params (`sx` speed, `ix` intensity, palette `pal`, colors `col`, `rev`, `mi` mirror, `frz` freeze). **Presets** snapshot full state, and **playlists** sequence presets with per-step durations (in tenths of a second) and a transition time. It is effect-centric with no keyframes: time variation lives inside each effect's code, driven by speed and intensity.
- 2D: `ledmap.json` maps a logical grid onto physical LED indices, with `-1` for gaps. This is essentially our circular mask. Forum pain points: confusion between `2d-gaps.json` and `ledmap.json`, strict JSON parser whitespace sensitivity, and large irregular maps failing ([forum](https://wled.discourse.group/t/irregular-shape-ledmap-whats-supposed-to-happen/12932)).
- Lessons: (1) a compact `type + few normalized params` effect description is easy to store, transmit and tweak. (2) Presets/playlists are a "scene list" model, useful as an optional higher-level "playlist" of documents rather than inside the timeline.

**Pixelblaze** ([expressions README](https://github.com/simap/pixelblaze/blob/v3/README.expressions.md), [mapper README](https://github.com/simap/pixelblaze/blob/v3/README.mapper.md))
- Code-first (option C). Patterns export `beforeRender(delta)` (called once per frame with elapsed ms) and `render(index)` / `render2D(index, x, y)` / `render3D(...)`, called per pixel. **Coordinates are normalized "world units" 0..1** from a pixel map (a JSON array of coordinates or a generating function). The firmware picks the best render variant for the installed map. **This makes the same pattern run on any layout, which is exactly the resolution-independence we want for procedural clips.**
- Time helpers: `time(interval)` is a 0..1 sawtooth looping every 65.536 x interval seconds, plus `wave`, `triangle` and `square`. All numbers are 16.16 fixed point. The language is a restricted ES6 subset (no objects, closures or GC).
- UI parameters are declared in code (`export function sliderSpeed(v)` creates a slider 0..1). This pattern of exposing parameters from a procedural clip as typed knobs is worth copying.
- Pain points: `beforeRender(delta)` accumulates state, so a frame is not a pure function of t and seeking/scrubbing is not deterministic. `time()` is wall-clock based (it syncs across devices but is not reproducible). LLMs write it poorly because it is "NOT REGULAR JAVASCRIPT" and they confuse it with general JS ([forum: custom GPT](https://forum.electromage.com/t/custom-chatgpt-agent-designed-for-writing-pixelblaze-code-feedback-wanted/4580), [coaching an AI](https://forum.electromage.com/t/coaching-an-ai-to-write-patterns/2651)). Lesson: if we add expressions, make them stateless functions of `t`, and either use a language LLMs already know well (a strict JS/GLSL-like expression subset) with a validator and good error messages, or ship heavy docs and examples in the agent prompt.

**ESPHome** ([Light component](https://esphome.io/components/light/))
- `addressable_lambda` effects are C++ lambdas with an `update_interval` and an `initial_run` flag, compiled into firmware. Also stateful and imperative. Mostly irrelevant beyond confirming the "effect = named procedure + interval" pattern.

**FastLED**: an imperative C++ library (fill, blur, palettes, `beatsin8` timing helpers). Not a document model. Its palette and beat helpers are a source of ideas for built-in procedural generators. Not researched further.

**AWTRIX 3 / AWTRIX NG** ([API](https://github.com/Blueforcer/awtrix3/blob/main/docs/api.md), [NG payload](https://blueforcer.github.io/awtrix-ng/reference/payload/))
- 32x8 pixel clocks. Custom apps are JSON with `draw: [{dp:[x,y,color]}, {dl:[...]}, {dr:[...]}]` vector-ish draw commands, plus named background `effect`s and `overlay`s. The docs warn that many draw objects use a lot of RAM. **AWTRIX 3 is discontinued.** NG changed the draw format (`dp` became `"pixel"`), so the format churned. Lesson: short opaque keys (`dp`, `dl`) were later replaced by readable names. Prefer readable keys for LLM friendliness. JSON size at our scale is not a concern.

**Divoom Pixoo** (community docs: [pixoo-toolkit](https://github.com/cyanheads/pixoo-toolkit), [Grayda notes](https://github.com/Grayda/pixoo_api/blob/main/NOTES.md))
- Animations are pushed as raw frames: `PicNum`, `PicOffset`, `PicSpeed` (ms per frame), base64 RGB `PicData`. Reported limits are about 40 frames per animation and freezes after about 300 pushes (**community-reported, unverified**). Pure raster over the wire. Lesson: device APIs want baked frames, so author richly and bake for the device.

**LaMetric Time / Nanoleaf**: not researched in depth. LaMetric icons are small GIF/PNG frame sequences (raster). Nanoleaf's effect model is palette + plugin-based (**unverified, from memory**). Nothing new beyond WLED/xLights.

### 2.3 Pixel-art animation formats

**Aseprite** ([file spec](https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md))
- Frames have **per-frame durations in ms** (the old global speed field is deprecated, so per-frame ms won). Layers carry flags, blend mode (19 modes), opacity, and a child level for groups. **Cels** are the layer x frame intersection, with position, opacity and z-index. Cel types are raw, **linked** (points to another frame's cel, sharing image data) and compressed. **Tags** name frame ranges with a loop direction (forward/reverse/ping-pong/ping-pong reverse) and repeat count. User data can attach to anything. Grayscale mode is 16 bpp (value + alpha).
- Relevance: this is the right shape for our **RasterClip** internals. Store frames with per-frame `durationMs`, store linked/held frames by reference (dedupe identical frames), and add an optional alpha per pixel so raster layers can composite (0 brightness is not the same as transparent). Tags and loop modes (ping-pong) are cheap and wanted: pauwma's editor has once/reverse/ping-pong.
- Irrelevant at our scale: 19 blend modes, tilemaps, palettes and indexed color. For monochrome, `normal (alpha over)`, `add`, `max/lighten`, `multiply` and `mask` cover essentially everything.

**Piskel / GIF / APNG**: flipbooks with per-frame delay. GIF delays are in centiseconds (pauwma converts `frameDelay * 10`), so GIF import quantizes to 10 ms. Interchange only.

### 2.4 Motion-graphics and timeline formats

**Lottie / Bodymovin** ([Lottie spec 1.0](https://lottie.github.io/lottie-spec/1.0.1/), [LAC announcement](https://lottie.github.io/news/announcing-lottie-specification-1.0/), [supported features matrix](https://github.com/airbnb/lottie/blob/master/supported-features.md), [LottieFiles properties](https://lottiefiles.github.io/lottie-docs/properties/))
- Model: composition (`fr`, `ip`, `op`, `w`, `h`) -> layers (shape/image/precomp/null/text) with in/out points and a transform (`ks`: anchor, position, scale, rotation, opacity). Properties are either static (`a:0,k:value`) or animated (`a:1,k:[keyframes]`). Keyframes are `{t (frames), s (value), i/o (bezier ease handles, per dimension), h (hold)}`. The ease y-values are not clamped, which allows overshoot. Precomps nest compositions.
- **Time is in frames at a composition fps.** This loses precision for non-uniform durations: pauwma's exporter has to average frame durations to pick an `fr`.
- Pain points:
  - **Renderer inconsistency**: the same JSON renders differently on Android, web and iOS ([#2508](https://github.com/airbnb/lottie-android/issues/2508), [#1188](https://github.com/airbnb/lottie-android/issues/1188), [#2068](https://github.com/airbnb/lottie-android/issues/2068)). The support matrix varies per platform.
  - **Expressions** are partially supported, and Remotion warns they can make frames render non-deterministically and flicker ([Remotion lottie docs](https://www.remotion.dev/docs/lottie/)).
  - The spec only reached v1.0 in Sept 2024, as a deliberately limited subset of widely implemented features. The spec was written after the implementations, so it is ambiguous in places. One example is the overloaded `i`/`o` meaning bezier ease handles in keyframes but vertex tangents in shapes.
  - Raw Lottie JSON is "extensive invariant structural metadata and formatting tokens", hard for models to learn ([OmniLottie abstract, arXiv 2603.02138](https://arxiv.org/abs/2603.02138)). OmniLottie had to invent a command/parameter tokenization to make generation work.
- Relevance: borrow the concepts (layers with in/out times, a transform group, static-or-animated properties, per-segment easing, hold keyframes). Reject the format: cryptic keys, frame-based time, vector-path machinery we cannot use on 137 pixels, and a precomp/matte/mask zoo.

**Rive** ([state machines](https://rive.app/docs/editor/state-machine/states), [web params](https://rive.app/docs/runtimes/web/rive-parameters))
- Artboards hold timelines plus state machines (directed graph of states, inputs bool/number/trigger, blend states). Interactive runtime animation.
- Relevance: low for v1. An "interactive glyph toy" (react to Glyph button, notifications) could later use a tiny state model ("which document plays on event X"). That belongs above the animation document, like WLED playlists, not inside it.

**CSS Animations / Web Animations API** ([KeyframeEffect](https://developer.mozilla.org/en-US/docs/Web/API/KeyframeEffect))
- Keyframes have **normalized offsets 0..1** within an effect, with even spacing when omitted. **Per-keyframe easing applies to the segment up to the next keyframe.** There is also an effect-level easing and a composite mode (`replace`/`add`/`accumulate`, poorly supported by browsers). `steps(n)` easing gives frame-step animation.
- Relevance: the per-segment easing semantics are the cleanest definition. Store ease on the outgoing keyframe. Normalized offsets are elegant for one-off effects, but they make "resize clip, keep keyframe timing" ambiguous. Prefer clip-relative ms, and possibly offer "stretch keyframes" as an explicit resize option. LLMs know CSS easing names well (`ease-in-out`, `cubic-bezier(...)`, `steps()`), which favors reusing that vocabulary.

**OpenTimelineIO** ([timeline structure](https://opentimelineio.readthedocs.io/en/latest/tutorials/otio-timeline-structure.html), [schema](https://opentimelineio.readthedocs.io/en/latest/api/python/opentimelineio.schema.html))
- Timeline -> Stack (composited bottom-up) -> Tracks (**sequential**: children end-to-end, duration = sum) -> Clip / Gap / Transition / nested Track or Stack. Time is `RationalTime(value, rate)` and `TimeRange(start, duration)`. A Clip has a `source_range` (trim) over a media reference's `available_range`. A **Transition** sits between two items and overlaps them by `in_offset`/`out_offset` without changing track length. Constraints: no two adjacent transitions, no overlapping transitions at one clip's ends, offsets within neighbors. Effects are opaque ("application-specific").
- Relevance: very useful vocabulary. Good ideas: Stack-of-layers compositing, clip trim (`sourceRange`) over a source with an available range, and nesting (a "group" or "precomp" clip). Risky ideas for us: (1) sequential tracks plus Gaps make position implicit. Inserting or deleting ripples everything after it, which is fine for an NLE UI but error-prone for LLM tool calls and for keeping keyframes aligned across layers. (2) Transition-as-separate-object carries the constraints above. (3) Rational time is overkill: our only clocks are ms durations and a render tick.

**After Effects data model**: composition -> layers (in/out points, parent, transform, effects stack, masks/mattes) -> properties with keyframes and expressions. Lottie is a serialization of it, so the same lessons apply. Expressions in AE are JS-like per-property code: powerful, and they made Lottie portability painful (above).

**Motion Canvas / Remotion** (code-first video; [Motion Canvas flow](https://motioncanvas.io/docs/flow/), [Remotion via comparison](https://rendercomp.com/blog/remotion-vs-motion-canvas-comparison/))
- Remotion: every visual value is a pure function of the current frame (`useCurrentFrame()`, `interpolate()`, `spring()`), which makes renders deterministic and seekable. Motion Canvas: generator scripts where `yield*` advances the timeline. This is imperative and the timeline exists only by executing the code.
- Relevance: Remotion's **"frame = pure function of time"** rule is the constraint our renderer and any expression clip must obey. Motion Canvas's model shows the downside of pure code-first: no inspectable timeline for a GUI to edit, so humans editing in a GUI and agents editing structure would conflict. That is the argument against option C as the source of truth.

### 2.5 LLM / agent authoring of animations

- **Raw complex JSON formats are hard for models.** OmniLottie needed a custom tokenizer because raw Lottie is mostly boilerplate ([arXiv 2603.02138](https://arxiv.org/abs/2603.02138)). StarVector/OmniSVG/LLM4SVG all invest in representation design for SVG ([StarVector](https://arxiv.org/html/2312.11556v1), [OmniSVG](https://arxiv.org/html/2504.06263v1); the LLM4SVG details are **unverified**, known only via OmniSVG's related work). Implication: our document should be compact, readable and low on boilerplate. An LLM should be able to read the whole document in a few hundred tokens.
- **LLMs are weak at whole-grid spatial generation.** Pixel Art Bench (24x24 palette-index grids) results are "largely unfavorable" ([HF blog](https://huggingface.co/blog/AINovice2005/pixel-art-bench)). Grid-reasoning benchmarks show accuracy falling with complexity ([SnorkelSpatial](https://snorkel.ai/blog/introducing-snorkelspatial/), [GRASP](https://arxiv.org/pdf/2407.01892)). Implications: (1) push agents toward high-level primitives (shapes, text, procedural effects, transforms, keyframes) where they excel, and (2) when they must edit pixels, use a row-string format (one string per row, one char per brightness level). That format is token-efficient and keeps 2D structure visible in the text. 13x13 is small enough to be workable. 25x25 is harder.
- **Patch versus regenerate** ([JSON Whisperer, arXiv 2510.04717](https://arxiv.org/html/2510.04717v1)): RFC 6902 patches cut output tokens about 31% within about 5% of full-regeneration quality. **Main failure modes: missing related updates, and array index arithmetic** (wrong index after deletions, 0/1-based confusion). Their EASE fix converts arrays to keyed dicts with stable IDs, which helped most on list manipulations. Code-editing studies show search/replace anchors fail 6-19% of the time ([To Diff or Not to Diff, arXiv 2604.27296](https://arxiv.org/pdf/2604.27296)). Iterative patching preserves invariants better than regeneration ([Refine After Generation](https://arxiv.org/html/2608.13292v1)). Implication: **typed commands keyed by stable IDs** beat both raw JSON Patch and full regeneration for our case. Offer a whole-document "replace" only as a validated escape hatch.
- **Command/op-based agent editing in practice:** the tldraw agent starter kit defines typed action schemas (create/update/delete shapes, align, distribute, reorder, and so on). Each action converts a simplified model format into real editor operations. Actions stream and apply one by one, so edits appear live. The agent sees a screenshot plus a simplified shape list of its viewport ([tldraw agent docs](https://tldraw.dev/starter-kits/agent), [repo](https://github.com/tldraw/agent-template)). xlights-mcp-server uses plan-level `write_sequence` with `validate_only` and `preview_plan`. Implication: same command layer for UI and agent, a simplified read model for the agent, a visual feedback tool, and validation with actionable error messages.
- **Domain-specific DSLs confuse LLMs** when they resemble but are not a popular language (Pixelblaze forum). If we add expressions, either restrict to a syntax that is a strict subset of something well known (C/GLSL/JS math expressions) and reject non-subset constructs with clear errors, or teach it with examples in the system prompt.
- **Undo via op log:** a typed command layer naturally yields inverse operations. Agent turns should be grouped into one undoable transaction ("undo what the AI just did"), matching how tldraw/Figma-style tools group changes. (General practice; no single source.)

---

## 3. Cross-cutting patterns: Do's and Don'ts

### Do
1. **Separate the authored model from the baked frames.** xLights -> FSEQ, Glyph Composer -> 60 Hz CSV, WLED -> firmware render. The baked form is the playback and interchange artifact.
2. **Render as a pure function `render(doc, profile, tMs) -> Frame`.** No hidden state across frames: Remotion yes, Pixelblaze `beforeRender` accumulation no. Seeking, scrubbing, preview and baking all depend on it.
3. **Author procedural content in normalized or logical coordinates and sample through the device mask** (Pixelblaze world units, xLights render buffers, WLED ledmap with `-1` gaps).
4. **Use per-frame durations in ms for raster content** (Aseprite, Glyph Museum, Divoom).
5. **Keep keyframe easing per segment with a small named vocabulary** (CSS/WAAPI semantics). Make `hold` a first-class ease.
6. **Use stable string IDs for every addressable entity** (layers, clips, keyframes, raster frames) and never address by array index in commands (JSON Whisperer/EASE).
7. **Validate on every mutation** and return actionable errors (xlights-mcp `validate_only`, Glyph Museum strict length check).
8. **Version the format with an append-only compatibility policy:** never repurpose fields, ignore unknown keys, use a migration chain (Glyph Museum policy, xLights value-curve migration bug as the counterexample).
9. **Expose typed, keyframeable parameters on procedural clips** (Pixelblaze sliders, WLED `sx`/`ix`, xLights value curves).
10. **Give the agent eyes:** a render-to-text or render-to-image tool (tldraw screenshot + simplified shapes).

### Don't
1. **Don't adopt Lottie (or an AE-shaped model) internally.** It is frame-based time, cryptic keys, inconsistent renderers, and costly for LLMs. Export to Lottie only as a raster preview flipbook if Playground needs it (that is what pauwma does).
2. **Don't make code the only source of truth** (Motion Canvas/Pixelblaze style). The GUI cannot edit it structurally and LLM edits become code diffs.
3. **Don't allow stateful or nondeterministic procedural code** (wall clock, unseeded RNG, accumulators). Lottie expressions flicker under deterministic renderers for this reason.
4. **Don't put a device profile on every frame,** and don't let a profile switch destroy content (pauwma clears frames).
5. **Don't use OTIO-style ripple tracks or standalone transition objects** for an LLM-edited document. Their positional invariants are easy to violate.
6. **Don't over-build:** no 19 blend modes, vector paths with bezier tangents, masks/mattes zoo, precomp nesting depth, state machines, rational time, or color. 137 monochrome pixels need maybe 4 blend modes and 1 level of grouping.
7. **Don't embed an unrestricted JS engine in v1.** QuickJS interrupt handlers do not cover some builtins, and soft memory limits have been bypassed ([quickjs-ng #1753](https://github.com/quickjs-ng/quickjs/issues/1753), [Playlish #100](https://github.com/engineerlish/Playlish/issues/100)).
8. **Don't use terse opaque keys** (`dp`, `ks`, `ip`) in the canonical format. AWTRIX NG moved away from them, and LLMs read named keys better. Glyph Museum's `p`/`d` are fine at the interop boundary only.
9. **Don't quantize authoring data to the render tick.** xLights value curves were snapped to slots, and GIF delays are in 10 ms units. Keep exact ms and quantize only when baking.

---

## 4. Specific implications for GlyphWizard

### 4.1 Time representation
- **Canonical unit: integer milliseconds (`Long` or `Int`).** Clip placement is absolute in the document timeline (`startMs`, `durationMs`). Keyframe times are clip-relative (`tMs` from clip start), so moving a clip moves its animation. Raster frames carry `durationMs`.
- **Render/bake tick:** a document-level or app-level `tickMs`, default 20 ms (50 fps, matching the Glyph Museum minimum in our brief). It could be 16.67 ms (60 Hz, Glyph Composer precedent) if the hardware or SDK keeps up. **Device throughput of `setAppMatrixFrame` is undocumented: measure it.**
- Raster clips at the tick boundary: sample-and-hold (the frame active at `tMs`). Note that baked output for Glyph Museum export can use variable `d` per frame: run-length-merge identical consecutive baked frames, so static holds don't explode the file.
- Loop and playback modes (once/loop/ping-pong, repeat count) live at document level, and optionally at raster-clip level for Aseprite-style tags.

### 4.2 Layer / clip / transition model (sketch, not code)
- `Document { schemaVersion, id, name, targetProfileId, durationMs (or derived), loop, layers[] }`
- `Layer { id, name, blend: normal|add|max|multiply|mask?, opacity(keyframeable), visible, clips[] }`. Layers are ordered bottom-to-top, with **no overlapping clips within one layer** (simple invariant, easy to validate).
- `Clip { id, kind, startMs, durationMs, transform (keyframeable: x, y, scale, rotation, opacity/brightness), inTransition?, outTransition?, params }`
  - `RasterClip`: `sourceProfileId`, `frames[{id, durationMs, pixels}]`, `playback: once|loop|pingpong`, optional linked/duplicate frame refs. Pixels are stored at the source profile, either as brightness plus an optional alpha/"transparent" sentinel, or as a separate alpha mask.
  - `ShapeClip` (rect/ellipse/line/ring/dot; stroke/fill brightness), `TextClip` (pixel font, scroll speed) and `ImageClip` could be one "vector-ish" family rendered at the target resolution.
  - `EffectClip` (procedural library): `effect: "radialPulse"|"noise"|"spinner"|"wave"|"sparkle"|...`, typed params with ranges, each optionally keyframed. This is WLED/xLights in miniature and is the main thing an LLM should reach for.
  - `ExpressionClip` (later): source string + declared params, evaluated per pixel (section 4.3).
  - Optional later: `GroupClip` (nested document or precomp, one level), as in OTIO nested stacks.
- **Transitions:** `inTransition`/`outTransition` on clips: `{type: fade|wipe(dir)|iris|dissolve(seed)|..., durationMs, ease}`. Cross-dissolves come from overlapping clips on two layers with complementary fades. A "transition" UI command can create that pair atomically, which gives Premiere-like UX without a separate transition object.
- **Keyframes:** `AnimatedValue = constant | keyframes[{id, tMs, value, ease}]`, where `ease` applies to the segment after that keyframe. Ease set: `hold, linear, easeIn, easeOut, easeInOut, cubicBezier(x1,y1,x2,y2), steps(n)`. Clamp final brightness to 0..255 after evaluation, and allow overshoot in intermediate values (Lottie lesson).

### 4.3 Procedural and expression clips: sandboxing, safety, determinism
- **v1: built-in Kotlin effect implementations only.** They are safe, fast and testable, and the LLM chooses and parameterizes them. This covers most "motion, fades, sparkles" needs.
- **Later: an expression clip** with signature `f(t, x, y, i, params) -> brightness 0..1`, where `t` is clip-relative seconds or ms, `x, y` are normalized -1..1 or 0..1 from the circle center, `i` is the LED index, and `params` are declared knobs. It must be:
  - **pure**: no state across frames, no wall clock, `rand(seed, ...)` hash-based noise only, so the same inputs give the same output and seeking works.
  - **budgeted**: an instruction/step limit per frame, a timeout, and no allocation in the hot path. The cheapest safe route is a small hand-written expression parser to AST (math ops, `sin/cos/abs/min/max/clamp/mix/smoothstep/fract/mod`, `wave/triangle/square`, `noise`, ternary, `let` bindings), compiled to a Kotlin closure tree. That makes the sandbox trivially safe. JS engines (QuickJS via quickjs-kt, Rhino) bring escape surface and limit bypasses (above) and are not needed for 137-489 pixels.
  - **validated before save**: parse, type-check and dry-run at a few `t` values. Reject NaN/Inf, and on error return the line/column and message so the agent can fix it.
- Determinism also matters for baking: identical docs produce identical bakes, so you can cache-key by document hash.

### 4.4 Device profile and resolution conversion
- `DeviceProfile { id ("phone3", "phone4aPro"), gridW, gridH, rowWidths[] (or explicit mask), museumV (1 or 4), sdkDeviceId }`. Treat the mask as data, so a future device is a new profile entry. Mirror Glyph Museum's "new resolution = new id + row pattern".
- **The rendering pipeline works in a logical coordinate space.** Shape, text, effect and expression clips render directly at the target grid with normalized coordinates, so they convert losslessly. Raster clips are the only lossy part.
- **Raster conversion:** store raster at its source profile. At render time, resample to the target. Downscale 25->13 with area-average (box filter, roughly 1.92 source pixels per target pixel), with an optional threshold/"crisp" mode for line art. Upscale 13->25 with nearest neighbor (crisp) or bilinear (soft). Respect masks: only active source LEDs contribute, and only active target LEDs receive. Offer "convert document to profile X" as an explicit command that keeps the original raster (non-destructive) and lets the user preview and optionally "bake raster at new resolution" to hand-touch it.
- Import from Glyph Museum: `v` -> profile, strict `p.length` check (do what pauwma does), and one RasterClip per file with frames' `d` as durations. Export: bake to the chosen profile, merge identical consecutive frames, clamp `d` to at least the 20 ms minimum.
- Pushing to hardware: expand masked `p[]` into the full `gridW*gridH` int array the SDK expects (row-major, zeros outside the mask). **Verify ordering and masked-cell behavior on the device.**

### 4.5 Baking and caching for playback
- Bake = evaluate `render(doc, profile, t)` at every tick to `ByteArray(ledCount)` per frame. 4a Pro at 50 fps is 6.85 KB/s, about 400 KB/min. Phone 3 is about 1.4 MB/min. Hold this in memory, persist it as a compact binary (FSEQ-like: header + raw bytes, optionally zstd or deflate), and key it by `(docHash, profileId, tickMs)`.
- The foreground playback service only reads baked frames and calls `setAppMatrixFrame` at the tick. This decouples playback from editor/render cost and from expression evaluation. Glyph Toy service paths can use the same baked file.
- Live preview while editing: render on demand at the scrub time. Debounce agent edits and re-bake incrementally by invalidating only the affected time range (clip's `[start, end]`).
- Infinite or real-time content (clock, reactive effects) cannot be fully pre-baked. Keep a "live render" mode for documents that declare `durationMs = infinite` or use live inputs. This is the exception path.

### 4.6 Agent-editability
- **One command layer** (Kotlin sealed classes) used by the UI and exposed as LLM tools with JSON Schemas generated from the same definitions. Suggested granularity: `addLayer`, `removeLayer`, `reorderLayer`, `addClip(kind, layerId, startMs, durationMs, params)`, `updateClip(id, patch)`, `moveClip`, `trimClip`, `splitClip`, `deleteClip`, `setKeyframe(clipId, property, tMs, value, ease)`, `deleteKeyframe`, `setTransition(clipId, in|out, spec)`, `crossfade(clipA, clipB, durationMs)` (macro), `setRasterFrame(clipId, frameId, rows[])`, `insertRasterFrame`, `setDocumentProps`. Plus read tools: `getDocument()` (compact summary), `getClip(id)`, `listEffects()` (vocabulary + param schemas), `renderPreview(tMs[] | range, format=rows|png)`.
- **IDs:** short stable string IDs (`L1`, `c3`, `k12`, `f4`), assigned by the app, never by array position.
- **Pixel I/O format for agents:** row strings for the masked grid, for example `" ..##.. "` with a fixed brightness alphabet (`.`=0, `1`-`9`, `#`=255, or hex `0`-`f` for 16 levels). Include the mask shape so the LLM sees the circle. Validate row count and per-row widths and return precise errors.
- **Transactions:** each agent turn or tool batch is one undo group. Apply commands live so preview updates (tldraw streaming pattern), but keep "Undo AI turn" atomic. Undo/redo works through inverse commands stored in an op log, which also gives history, autosave and potential collaboration.
- **Validation layer:** schema validation (types, ranges), document invariants (no overlap on a layer, keyframe times within clip, IDs unique, raster sizes match `sourceProfile`), plus a soft lint (for example "clip outside document duration", "transition longer than clip"). Errors go back to the LLM verbatim.
- **Escape hatch:** `replaceDocument(json)` with full validation, for big rewrites. It should be the rare path.
- **System prompt assets:** a compact schema description, the effect catalogue with examples, and two or three example documents. Keep the full serialized document readable so `getDocument` fits in about 1-3k tokens for typical animations, summarizing raster frames instead of dumping pixels unless asked.

### 4.7 Versioning and migration
- Top-level `schemaVersion: Int` (separate from any profile identifier, avoiding Glyph Museum's overloaded `v`). Keep an ordered list of pure `migrate(n -> n+1)` functions on the JSON tree and test them with golden files from every released version (the xLights value-curve regression is the cautionary tale).
- Policy: additive changes don't bump (unknown keys ignored, then preserved on round-trip if feasible). Renames and semantic changes bump plus migrate, and fields are never repurposed. Effect types carry their own `effectVersion`, so built-in effect behavior changes don't silently change old documents. Expression clips declare `exprVersion` for the language.
- Canonical serialization: deterministic key order and stable float formatting, so document hashes (bake cache keys) and diffs are stable.

---

## 5. Open questions for the user

1. **Glyph Museum `v` semantics:** `v` identifies the device layout (1 = Phone 3, 4 = 4a Pro), not a format version. Is it OK to treat it purely as a profile ID at the import/export boundary?
2. **Per-frame DeviceProfile:** the brief says each frame carries a profile. Are you OK with document-level target profile + per-raster-clip source profile instead? It is safer for agents and still supports mixed-resolution sources.
3. **Playback tick:** fixed 20 ms (50 fps) default? Do you want a 60 Hz option (needs a device throughput measurement of `setAppMatrixFrame`)?
4. **Transparency in raster layers:** should raster pixels support "transparent" distinct from 0 brightness (needed for layering hand-drawn sprites over effects)?
5. **Blend modes:** is `normal`, `add`, `max`, `multiply` (+ `mask`) enough?
6. **Expressions:** is a custom math-expression DSL acceptable (safe and deterministic), or do you specifically want JS so LLMs can write familiar code? This affects sandbox cost.
7. **Live/reactive content** (clocks, notifications, Glyph button, audio): in scope for this document model, or a separate "toy/playlist" layer above it (WLED playlist / Rive state-machine analogue)?
8. **Lottie export for Nothing Playground:** needed? If so, a raster-flipbook exporter (pauwma approach) is enough.
9. **Agent pixel editing:** should the LLM be allowed to set individual pixels at all on Phone 3 (25x25), or be steered to primitives and effects only there?
10. **Undo granularity for AI:** one undo per agent turn, or per tool call?

---

## 6. Sources

Nothing ecosystem
- Glyph Museum developer format: https://glyphmuseum.com/developers
- pauwma/GlyphMatrixEditor (README + `script.js` read directly): https://github.com/pauwma/GlyphMatrixEditor
- Glyph Matrix Editor update blog: https://pauwma.com/blog/glyph-matrix-editor-update
- NothingTec article on the update: https://nothingtec.com/en/2026/03/17/glyph-matrix-editor-actualizacion-novedades/
- Nothing Playground: https://playground.nothing.tech/ ; Glyph Museum toy listing: https://playground.nothing.tech/detail/toy/eoRe7VEwe4xdh5Zf
- GlyphMatrix Developer Kit: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- GlyphMatrix Example Project: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project
- SebiAi custom-nothing-glyph-tools: https://github.com/SebiAi/custom-nothing-glyph-tools
- Glyphtones guide (composition encoding): https://glyphtones.firu.dev/guide
- AK-120/GlyphControllers (string-row frames): https://github.com/AK-120/GlyphControllers
- Sanjay-Dhandapani/nothing-glyph-matrix: https://github.com/Sanjay-Dhandapani/nothing-glyph-matrix

LED / pixel ecosystems
- xLights Value Curves: https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/value-curves
- xLights Rendering: https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/rendering
- xLights Layer Settings: https://manual.xlights.org/xlights/chapters/chapter-four-sequencer/layers/layer-settings
- xLights Model Groups: https://manual.xlights.org/xlights/chapters/chapter-four-tabs/model-groups
- xLights Glossary: https://manual.xlights.org/xlights/appendicies/glossary
- xLights value curve issue #3482: https://github.com/smeighan/xLights/issues/3482
- xLights sequence settings (FSEQ): https://manual.xlights.org/xlights/chapters/chapter-five-menus/file/settings/sequences
- FSEQ v2 reverse-engineered spec: https://github.com/Cryptkeeper/fseq-file-format
- xlights-mcp-server: https://github.com/cwoolum/xlights-mcp-server
- Vixen display elements & groups: https://www.vixenlights.com/docs/concepts/display-elements-groups/
- WLED JSON API: https://kno.wled.ge/interfaces/json-api/
- WLED Presets: https://kno.wled.ge/features/presets/
- WLED Mapping: https://kno.wled.ge/advanced/mapping/
- WLED irregular ledmap forum: https://wled.discourse.group/t/irregular-shape-ledmap-whats-supposed-to-happen/12932
- Pixelblaze expressions README: https://github.com/simap/pixelblaze/blob/v3/README.expressions.md
- Pixelblaze mapper README: https://github.com/simap/pixelblaze/blob/v3/README.mapper.md
- Pixelblaze language reference: https://www.bhencke.com/pixelblaze-expression
- ElectroMage forum, custom GPT for Pixelblaze: https://forum.electromage.com/t/custom-chatgpt-agent-designed-for-writing-pixelblaze-code-feedback-wanted/4580
- ElectroMage forum, coaching an AI: https://forum.electromage.com/t/coaching-an-ai-to-write-patterns/2651
- ESPHome light component: https://esphome.io/components/light/
- AWTRIX 3 API: https://github.com/Blueforcer/awtrix3/blob/main/docs/api.md
- AWTRIX NG payload: https://blueforcer.github.io/awtrix-ng/reference/payload/
- Divoom pixoo-toolkit: https://github.com/cyanheads/pixoo-toolkit
- Grayda pixoo_api notes: https://github.com/Grayda/pixoo_api/blob/main/NOTES.md

Pixel art
- Aseprite file spec: https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md

Motion graphics / timelines
- Lottie spec 1.0.1: https://lottie.github.io/lottie-spec/1.0.1/
- LAC v1.0 announcement: https://lottie.github.io/news/announcing-lottie-specification-1.0/
- Lottie supported features: https://github.com/airbnb/lottie/blob/master/supported-features.md
- LottieFiles property docs: https://lottiefiles.github.io/lottie-docs/properties/
- lottie-android issues: https://github.com/airbnb/lottie-android/issues/2508 , https://github.com/airbnb/lottie-android/issues/1188 , https://github.com/airbnb/lottie-android/issues/2068 , https://github.com/airbnb/lottie-android/issues/2304
- Remotion Lottie (expression nondeterminism): https://www.remotion.dev/docs/lottie/
- Rive states: https://rive.app/docs/editor/state-machine/states ; web params: https://rive.app/docs/runtimes/web/rive-parameters
- MDN KeyframeEffect: https://developer.mozilla.org/en-US/docs/Web/API/KeyframeEffect
- OTIO timeline structure: https://opentimelineio.readthedocs.io/en/latest/tutorials/otio-timeline-structure.html
- OTIO schema: https://opentimelineio.readthedocs.io/en/latest/api/python/opentimelineio.schema.html
- Motion Canvas flow: https://motioncanvas.io/docs/flow/
- Remotion vs Motion Canvas: https://rendercomp.com/blog/remotion-vs-motion-canvas-comparison/

LLM / agent authoring
- OmniLottie (arXiv 2603.02138): https://arxiv.org/abs/2603.02138
- StarVector: https://arxiv.org/html/2312.11556v1
- OmniSVG: https://arxiv.org/html/2504.06263v1
- JSON Whisperer (arXiv 2510.04717): https://arxiv.org/html/2510.04717v1
- To Diff or Not to Diff (arXiv 2604.27296): https://arxiv.org/pdf/2604.27296
- Refine After Generation: https://arxiv.org/html/2608.13292v1
- Pixel Art Bench: https://huggingface.co/blog/AINovice2005/pixel-art-bench
- SnorkelSpatial: https://snorkel.ai/blog/introducing-snorkelspatial/
- GRASP: https://arxiv.org/pdf/2407.01892
- tldraw agent starter kit: https://tldraw.dev/starter-kits/agent ; https://github.com/tldraw/agent-template
- quickjs-kt: https://github.com/kangzj/quickjs-kt
- quickjs-ng interrupt issue #1753: https://github.com/quickjs-ng/quickjs/issues/1753
- Playlish sandbox memory-limit issue: https://github.com/engineerlish/Playlish/issues/100

Unverified items (flagged inline): Glyph Museum default `d` when omitted and the 20 ms minimum (not on the public page); SDK raw-array ordering and masked-cell handling; xLights per-effect in/out transition types (from memory); LLM4SVG details; Divoom frame limits; LaMetric/Nanoleaf models; Nothing Playground's Lottie requirements; `setAppMatrixFrame` max throughput.
