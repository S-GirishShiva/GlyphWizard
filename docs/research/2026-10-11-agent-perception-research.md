# Agent Perception Research: how the in-app agent sees and reasons about Glyph Matrix animations

Date: 2026-10-11
Status: research / recommendations (no code). Feeds D2 (BYOK agent) and the "Agent enablement" criterion in `2026-10-11-evaluation-framework.md`.
Confidence markers: **[verified]** = checked against a primary or near-primary source in this session; **[secondary]** = blog / summary / third-party; **[speculative]** = my reasoning, not backed by direct evidence; **[verify]** = from memory or conflicting sources, check before relying on it.

---

## 1. TL;DR and recommendations

**Main point:** don't make the LLM *see* its way to correctness. On 137 LEDs, every exact question ("is it centered?", "is it symmetric?", "how many LEDs are lit?", "is the spin even?") can be computed exactly and cheaply. Current VLMs are measurably bad at these same questions: counting, grid coordinates, symmetry, small images, fine motion. So the agent should get **exact text representations and computed analysis tools as its main way of perceiving**, and use **rendered images only as an extra input for gestalt judgments** ("does this read as a heart?", "is it too busy?"). Text and image together beat either one alone on ARC-style grids (Wen et al. 2025).

Recommendations, in priority order:

1. **The agent's default view is a masked text grid.** It's 13 lines, a fixed-width cell per LED, brightness quantized to one character, row/column labels, and out-of-mask cells shown as blanks. Exact values come from a `get_pixels` tool on demand. Sparse frames can use a coordinate list instead.
2. **Build a deterministic `analyze` toolset before you build any vision loop.** It covers centroid/centering, mirror and rotational symmetry, best-fit circle IoU / radial deviation for roundness, connectivity and orphan pixels, jaggy/line-run regularity, lit count and density, brightness histogram, frame-diff energy, motion trajectory, step variance (jerk), flicker, and loop seam. Each tool returns numbers **plus a one-line plain-language verdict and the offending coordinates**. This gives the agent the *external feedback* that the self-correction literature says LLMs need (Huang et al. 2023).
3. **Keep a vocabulary map from subjective words to metrics and to knobs** ("wonky" → asymmetry / orphan / jaggy metrics → fix ops). Put it in a small always-loaded glossary, with the details in on-demand skill files.
4. **Images are an optional tier-2 input with a fixed recipe.** Render each LED as a crisp round dot on black with visible gaps, at ~40 px pitch (13×13 → ~520 px; 25×25 → ~600 px at 24 px), axis labels on, and no bloom for the analytic view. Contact sheets (≤16 frames) cover motion. Show a glow render only when the question is about appearance. Video input stays optional (Gemini only) and isn't needed, since we hold the exact frames.
5. **Run a bounded critique loop.** Render → analyze → (optionally) look → revise. Cap it at 2 auto-iterations, stop when the metrics stop improving, and never let a VLM's "looks good" override a failing metric. When intent is ambiguous ("rounder", "less busy"), **offer 2–3 variants side by side** and let the user pick. Pairwise choice is more reliable than scoring, for both humans and VLM judges.
6. **Model tiers.** T0 = no tool calling (unsupported). T1 = tool calling, text only (fully functional through text grids and analysis tools). T2 = + image input (adds visual gestalt checks). Report support per model from the eval harness, not from vendor claims.
7. **Build the eval harness early** (sketch in §4). It should separate *perception probes* from *editing tasks*, use seeded-defect tasks scored by the same analysis library, and use swap-order pairwise VLM judging calibrated against a few human rounds.

Spec corrections found during research:
- **The 4a Pro row widths are `[5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5]` (sum 137)**, not a smooth 5,9,11,13… ramp. The Phone (3) rows are `[7,11,15,17,19,21,21,23,23,25,25,25,25,25,25,25,23,23,21,21,19,17,15,11,7]` (sum 489). Rows are centered, and indexing runs row-major from the top-left LED. **[verified, glyphmuseum.com/developers; sums checked]**
- The official GlyphMatrix Developer Kit says the **Phone (4a) Pro supports only AOD toys**, and an AOD toy receives an event roughly once per minute. Animation playback through the in-app path (`setAppMatrixFrame`, system version ≥ 20250801) is a separate route. **[secondary: README summarized via fetch; confirm on the device]** This doesn't affect perception research, but it matters for what "preview on physical LEDs" can do.

---

## 2. Findings per question

### Q1. Representations: how should the agent *see* frames and animations?

#### Evidence on VLM weaknesses that apply directly to us

| Finding | Relevance | Source |
|---|---|---|
| BlindTest: 4 frontier VLMs averaged ~58% on 7 trivial-for-humans tasks (best ~78%, Claude 3.5 Sonnet). Counting rows/columns of an **empty grid** averaged ~34%. **Grids with text in the cells** were much easier (Sonnet 3.5: 88.7% vs 59.8%). Errors were typically off by 1–2. | Our frames are exactly "empty grids with some dots lit". Off-by-one localization is the failure mode, and labels/text in cells help. | Rahmanzadehgervi et al. 2024, [arXiv 2407.06581](https://arxiv.org/abs/2407.06581), [ACCV](https://openaccess.thecvf.com/content/ACCV2024/papers/Rahmanzadehgervi_Vision_language_models_are_blind_ACCV_2024_paper.pdf) **[verified, numbers vary by paper version]** |
| "VLMs are biased": ~17% counting accuracy on counterfactual images of familiar things (e.g., a 4-stripe Adidas logo is counted as 3). "Double-check" prompting helps only +2–6 pts. | "Make the heart rounder": the model's prior of what a heart looks like will override what is actually lit. It may report a canonical heart that isn't there. | Vo et al. 2025, [arXiv 2505.23941](https://arxiv.org/abs/2505.23941) (ICLR 2026) **[secondary: abstract-level]** |
| GPT-4o "totally fails to extract the colors of cells from an image for images >12x12 and is quite bad at 8x8". The SOTA ARC approach therefore fed **multiple ASCII views** (grid with chess-style cell labels, per-color connected components, normalized shapes, diffs) alongside images. | 13×13 is right at that failure threshold, and 25×25 is past it. Older model (2024), but the pattern holds. | Greenblatt, [Redwood blog](https://blog.redwoodresearch.org/p/getting-50-sota-on-arc-agi-with-gpt) **[verified]** |
| Text vs image on ARC-AGI (9 encodings): **structured text gives precise coordinates for sparse features. Images capture 2D shape but are resolution-sensitive** (patch aliasing). **Combining them improves** perception by ~8 pts. | This is the core design justification for text plus image. | Wen, Wang, Bilal 2025, [arXiv 2511.15717](https://arxiv.org/abs/2511.15717) **[verified: abstract; models/encodings not checked]** |
| VRIQ: symmetry/pattern errors at a 54% rate in VLMs. | Don't ask the VLM "is it symmetric?". Compute it. | [arXiv 2602.05382](https://arxiv.org/abs/2602.05382) **[secondary]** |
| MotionBench (CVPR 2025): VLMs are poor at fine-grained motion. Single-frame views mislead, and higher frame rates help but leave a big gap. Direction is a weak axis (NextMotionQA 2026). | "The spin feels jerky" can't be judged by a VLM looking at a contact sheet. Compute motion metrics. | [arXiv 2501.02955](https://arxiv.org/abs/2501.02955); [NextMotionQA](https://arxiv.org/html/2606.04773) **[secondary]** |
| Anthropic's vision docs warn that images **under ~200 px on an edge may degrade performance**. Tokens ≈ w×h/750. | A raw 13×13 PNG is useless. Upscale it. A 520×520 render costs ≈ 360 tokens. | [Anthropic vision docs](https://docs.anthropic.com/en/docs/vision) **[verified via search snippet; newer models may differ]** |
| Asset Forge (LLM pixel-art sprites): **asking an LLM for raw pixel arrays "produces incoherent results"**, while primitive drawing commands work. Giving the judge **PNG + ASCII grid** "dramatically improved" spatial-coverage and prompt-adherence scoring vs code-only metrics. | 1) Prefer generators/primitives over painting pixel by pixel. 2) Show the judge both views. | [Asset Forge](https://matthewdeaves.github.io/assetforge/) **[secondary, single practitioner, numbers inconsistent on the page]** |
| Pixel Art Bench: models emit a palette + 24×24 index grid as JSON, judged by a VLM for Elo. Small local models (3B) struggle to emit valid grids at all. | Weak models can't be trusted to emit raw grids. Typed commands and generators protect them. | [HF blog](https://huggingface.co/blog/AINovice2005/pixel-art-bench), [pixel-llm](https://github.com/mxmarchal/pixel-llm) **[secondary]** |

#### Text encodings: comparison for our case

There's no study on 137-LED circular monochrome grids specifically, so the following is **[speculative]** but grounded in the ARC practice above:

| Encoding | Pros | Cons | Verdict |
|---|---|---|---|
| Raw 0–255 CSV per cell | Exact | ~169 × 2–3 tokens per frame plus separators; numbers are hard to eyeball as shape; the 2D structure is lost in tokenization | Only via `get_pixels(region)` for exact edits |
| Hex `00`–`FF` | Exact, fixed width | Same as above; hex digits tokenize unpredictably | No |
| **Quantized single char** (`.` off, `1`–`9` levels, `#` = 255, space = outside mask) | Fixed-width columns keep the 2D shape visible to the model; ~1 token per cell or less; the circular mask is explicit | Lossy (shows ~11 levels) | **Default frame view** |
| Density ramp (` .:-=+*#%@`) | Very "visual" | Characters carry no ordinal meaning to a tokenizer; ambiguous | Optional, not default |
| Coordinate list `(r,c)=v` | Exact and compact for sparse frames; no off-by-one counting | No shape gestalt | Auto-switch when < ~25 LEDs lit |
| Run-length / connected components (ARC style) | Gives object-level reasoning ("2 components; component A = 14 LEDs, bbox …") | Needs computing | Delivered by `analyze`, not as raw view |

Concrete default (illustrative, 4a Pro). Row/column labels use hex digits so every column stays one character wide:

```
    0123456789ABC
 0      .....
 1    .........
 2   ....99.99..
 3   ...9999999.
 4  ..999999999..
 ...
```

Notes:
- Label rows and columns. BlindTest shows text in cells and labels reduce off-by-one errors, and Redwood used chess-style labels.
- Always state the coordinate convention in the system prompt (row-major, origin top-left, center = (6,6) on 13×13, (12,12) on 25×25).
- For 25×25, a frame is ~650 characters. That's fine for single frames but expensive for whole animations, so animations get summaries (below), not frame dumps.

#### Images

- **Upscale with nearest-neighbor round dots, not square pixels, for the analytic view.** Round dots on black with visible gaps match both the device and the "circles" that VLMs at least partially handle. They also keep the gaps that make LED count and adjacency visible. Square pixels merge into blobs, which is good for silhouette and bad for counting. **[speculative: A/B in the eval harness]**
- **Pitch:** ~40 px per LED for 13×13 (≈520 px, ≈360 Claude tokens). ~24 px per LED for 25×25 (≈600 px). Both stay above the ~200 px degradation floor and well under the 1568 px long-edge cap. **[speculative sizing, derived from documented limits]**
- **Axis labels** on two edges (0–12). Optional faint grid ring for the mask boundary.
- **Two render styles:** `analytic` (crisp dots, labels, no glow) for localization questions, and `realistic` (glow/bleed, gamma, dark phone back) for "how does it feel" questions and for showing the user. Don't ask a VLM to localize on the glow render. **[speculative]**
- **Highlight overlays:** draw analysis results onto the render (e.g., red ring around orphan pixels, mirror axis line, centroid cross, ideal circle outline). This turns VLM perception into *verification of a marked claim*, which is much easier than open-ended detection. This is the same idea as "set-of-mark" visual prompting. **[speculative for our domain]**

#### Motion representations

| Representation | Use |
|---|---|
| **Motion summary text** (computed): per-segment centroid path, angular position per frame for rotational content, per-frame changed-LED count, step-size stats, detected loop seam jump, flicker events | **Primary**. This is what "jerky" gets evaluated against |
| **Contact sheet / filmstrip** image (e.g., 4×4 of evenly sampled frames, frame index and ms printed under each) | T2 gestalt ("does it read as a pulse expanding?") |
| **Onion skin** (prev frames at decreasing brightness, or a different tint in the analytic render) | Shows trajectory in one image. Good for spin/path questions |
| **Difference map** (frame t vs t+1; +/− marked) as text and/or image | Flicker, stray pixel changes, "what moved" |
| **Keyframe-level view** (the timeline document itself: clips, generator params, easing curves) | Often the best view. Jerkiness is frequently a *document* property (linear easing, uneven keyframe spacing, frame-time quantization), visible without looking at pixels at all |
| **Video/GIF input** | Gemini accepts video natively (default 1 fps sampling, configurable, ~258 tokens/frame, or 66 at low res) ([docs](https://ai.google.dev/gemini-api/docs/video-understanding)). Claude and OpenAI chat APIs take images, not video **[verify]**. 1 fps sampling is useless for a 50 fps animation unless you raise fps, so **not recommended**. We have exact frames, so computed motion analysis dominates. |

### Q2. Translating subjective feedback into measurable properties

There's no published "LED glyph quality metric" literature. The mapping below combines pixel-art craft knowledge, basic image/shape analysis, and animation principles. **[speculative design, standard techniques]**

| User says | Likely meanings (agent should disambiguate) | Metrics (`analyze` tools) | Typical fixes |
|---|---|---|---|
| "wonky", "lopsided" | asymmetry; off-center; irregular stair-steps; stray pixel | mirror-symmetry IoU per axis (V, H, diagonals) + **best axis**; rotational symmetry for radial designs; centroid offset; jaggy score; orphan count | mirror half; snap to symmetric; re-rasterize via generator with AA |
| "not round", "make it rounder" | boundary deviates from circle; flats/corners at 13×13 | IoU vs best-fit rasterized disc/ring of same area; radial distance std of boundary LEDs; compare to a **lookup of known-good rasterized circles per radius** (pixel artists use canonical circles: at small radii only certain run patterns look round) | swap to canonical circle; use soft-edge (AA) brightness on boundary LEDs |
| "not centered" | geometric vs brightness-weighted vs optical center | bbox center, brightness-weighted centroid vs (6,6); margin to mask edge per side (the mask is circular, so a shape can be centered but clip the mask in one direction) | translate by integer offset; note that odd/even shape widths can't center exactly on 13 columns, so explain this to the user instead of looping |
| "too dim", "too bright" | mean/peak lit brightness; contrast between parts | brightness histogram, mean of lit LEDs, % at 255, min nonzero level (very low levels may be invisible or uneven on hardware **[verify on device]**) | scale/gamma curve; raise floor |
| "too busy", "cluttered", "noisy" | too many lit LEDs; too many components; too much changing per frame | lit fraction, component count, edge density (lit-unlit neighbor transitions), **temporal activity** = mean fraction of LEDs changing per frame, number of simultaneous moving elements | remove elements; simplify; lower density; reduce simultaneous motion; add rest frames |
| "jerky", "choppy", "stuttery" | uneven step sizes; linear easing where ease expected; duplicate/held frames; spatial quantization (positions jump in whole-LED steps); loop seam jump | per-frame displacement / angular step series → **coefficient of variation**, second difference (jerk); duplicate-frame runs; seam discontinuity (last→first frame diff vs typical diff); easing-type check | use easing; subdivide steps; render motion with sub-LED anti-aliasing (brightness interpolation between neighbors); fix loop seam; even keyframe spacing |
| "flickery", "blinky" | LEDs toggling on/off at high frequency | per-LED sign-change count per second; high-frequency temporal energy (e.g., > ~8 Hz on/off) | temporal smoothing; hold minimum on-time |
| "too fast/slow" | duration, speed in LEDs/s or rev/s | angular velocity, cycle duration | retime |
| "blurry", "mushy" | too much AA / mid-brightness at 13×13 | fraction of lit LEDs at mid levels; edge contrast | sharpen thresholds |
| "can't read it" (text) | glyph too large/compressed, stroke gaps < 1 LED, too many characters | glyph height ≤ ~7 rows with ≥1-LED gaps; characters visible at once (≤ 2–3 on 13×13 with a 3×5 font **[speculative]**); scroll speed in chars/s; match against font bitmaps | use dot-matrix font; scroll; reduce characters |

Pixel-art craft knowledge that turns into lint rules **[secondary/common practice; no single authoritative source found]**:
- **Orphan pixels**: isolated lit LEDs with no 8-neighbor.
- **Doubles / L-corners** in 1-px lines ("pixel-perfect" lines remove the extra corner pixel).
- **Jaggies**: irregular run-lengths in a slope (2,1,3,1 instead of 2,2,2 or 3,2,3,2).
- **Banding** (staircased AA aligned with the edge).
- **Silhouette readability**: shape should read when every lit LED is set to full.
- Animation principles that survive at 13×13: **easing (slow in/out)**, **timing/spacing**, **anticipation** (a small counter-move before a big one), **follow-through** (overshoot then settle), **staging/one focal motion at a time**, and **holds**. Squash/stretch is mostly impractical at 13 px but can be read as brightness pulse. **[speculative mapping]**

**Critic tools vs pure vision.** Every row above is computable in microseconds on 137 or 489 LEDs, and the literature says VLMs are weak at exactly these things. Tools win for the measurable layer. Vision adds value only for *semantic* questions (is it recognizably a heart? does the overall composition look balanced?) and even there, with the bias caveat (Vo et al.). **[speculative synthesis, evidence-backed components]**

Important design detail: tools should return **verdict + evidence + suggested op**, e.g.
`roundness: 0.78 (fair). Boundary deviates most at rows 2 and 10 (flat tops: 5-LED runs). Canonical r=5 circle uses runs 3/7/9/11. Suggest: apply ring generator r=5 or replace rows 2,10.`
This keeps weak models on track and costs few tokens.

### Q3. Self-critique loops (render → look → critique → revise)

| System | Loop | Notes |
|---|---|---|
| **LL3M** (Blender code agents) | coding agent → VLM critic on multi-view renders → verification agent; iterative | The authors admit VLMs "may still struggle to accurately identify spatial artifacts". Critic and verifier were Gemini 2.0 Flash per a summary. [arXiv 2508.08228](https://arxiv.org/abs/2508.08228) **[secondary]** |
| **BlenderAlchemy** | edit generator + VLM state evaluator in a search (explore/prune), not a single revise chain | "Out-of-the-box VLMs have a poor understanding of the visual consequences of Blender program edits." [arXiv 2404.17672](https://arxiv.org/abs/2404.17672) **[secondary]** |
| **SceneCraft** | Blender script → VLM self-critique → revise | [arXiv 2403.01248](https://arxiv.org/abs/2403.01248) **[secondary; loop details unverified]** |
| **Render-in-the-Loop** (SVG) | feeds intermediate renders back during generation | 1.5× latency. Additive only (can't delete primitives), which limits self-correction. [arXiv 2604.20730](https://arxiv.org/abs/2604.20730) **[secondary]** |
| **IntroSVG**, **RefineSVG**, **RLRF / StarVector** | generate–critique–refine; RL with rendering reward (pixel + semantic + code-efficiency) | Training-time methods (not usable with BYOK), but they show the render signal is valuable. [RLRF arXiv 2505.20793](https://arxiv.org/abs/2505.20793) **[verified abstract-level]** |
| **Vision-guided frontend refinement** | critic in loop; distills refinements | [arXiv 2604.05839](https://arxiv.org/html/2604.05839v1) **[secondary]** |
| **Huang et al. 2023** "LLMs cannot self-correct reasoning yet" | intrinsic self-correction often fails or *degrades* answers. Gains come with **external** feedback | [arXiv 2310.01798](https://arxiv.org/abs/2310.01798) **[verified abstract]** |
| **MLLM-as-a-Judge** | VLM judges align with humans on **pairwise comparison**, much less on absolute scoring/ranking | [arXiv 2402.04788](https://arxiv.org/abs/2402.04788) **[verified abstract]** |
| Image-edit judge audit 2026 | **Swapping display order reversed up to 61% of pairwise decisions** | [arXiv 2610.01670](https://arxiv.org/html/2610.01670) **[secondary]** |

I found no study measuring oscillation in iterative graphics refinement specifically. That remains an evidence gap.

Implications for GlyphWizard **[speculative design grounded in the above]**:
- **External feedback = the analysis tools.** That's our cheap, exact "oracle" for the measurable layer. The VLM is a weak, biased oracle for the semantic layer.
- **Iteration budget:** 1 automatic verification pass after every edit (analysis only, ~free). Up to 2 revise iterations when a metric targeted by the user request fails. Then stop and show the user. Most value in reported loops comes from the first 1–2 rounds. Beyond that, oscillation and drift risk rises, and the BYOK user pays per call.
- **Stopping rules:** target metric reached; or no improvement > ε for an iteration; or the edit starts changing things unrelated to the request (diff locality check); or the budget is hit.
- **Anti-oscillation:** keep the best-so-far by metric, and always allow reverting to it (the provenance journal from D5 helps here). Don't let a revise undo a previous user-approved property: carry "constraints satisfied so far" forward as checks.
- **Judge overconfidence:** never accept "looks good" from a VLM when a metric fails. When using a VLM to compare variants, query both orders and accept only consistent verdicts.
- **Ambiguity handling:** for subjective requests, generate 2–3 variants that differ along the hypothesized axis (e.g., "rounder" via canonical circle vs AA edge vs larger radius) and show them as a choice row. That's the most reliable judge available (the human, pairwise), and it costs only renders.

### Q4. Domain knowledge and context engineering

**What the agent must know (always in context, ~1–2k tokens target) [speculative budget]:**
- Device facts: grid size, row widths/mask, coordinate convention, center, LED count, brightness 0–255, monochrome, 20 ms tick, physical appearance (round dots, glow). Plus "a 1-LED change is a ~1/13 shift: visible".
- The representation legend (text grid characters, coordinate format).
- The **vocabulary map** (one line per term: word → metrics → common fix ops), compressed from the Q2 table.
- Working rules: prefer generators/primitives over raw pixel painting (Asset Forge, Pixel Art Bench); run `analyze` after edits; offer variants on ambiguity; respect the iteration cap.

**On-demand skill files (progressive disclosure):** Anthropic's Agent Skills pattern keeps only name+description (~tens to ~100 tokens per skill) always loaded, loads the body on trigger, and loads references/scripts as needed ([overview, secondary](https://www.firecrawl.dev/blog/agent-skills)). The pattern isn't provider-specific: implement it as an in-app `load_skill(name)` tool usable by any tool-calling model. Candidate skills:
- `small-shapes`: canonical rasterized circles/rings per radius for 13 and 25; hearts, stars, arrows, check/cross at sizes that read; pixel-perfect line rules; AA at low res.
- `motion`: easing choices, spin anti-aliasing, pulse/breathe timing, loop seams, anticipation/follow-through at 13 px, flicker limits.
- `text-on-matrix`: dot-matrix fonts (3×5, 5×7), scrolling speeds, characters-per-view. Note the licensing: Nothing's own Ndot 57 is proprietary and must not be redistributed, while **NDOT 47 (Interactivate) is OFL** ([source](https://github.com/GeneralKaos666/ndot-firacode-nerd)) **[secondary]**.
- `composition-circular-mask`: safe area, using the edge ring, radial symmetry, avoiding clipping at the 5-wide top/bottom rows.
- `nothing-style`: Nothing's dot-matrix aesthetic (monochrome, dots, restraint). No official Glyph Matrix design guideline was found, so this would be authored by us from observation **[gap]**.
- `generator-catalog`: per generator, its params, value ranges, and what each param does visually.

**Example library (few-shot):** a curated set of ~20–50 good glyph animations, each stored as the document (clips + params) + a 1–2 sentence description + tags + a computed metrics fingerprint. Retrieve 1–3 by tag/keyword (no embeddings needed at this scale) when the user asks for something similar. Don't keep them all in context. **[speculative]**

**Token-lean tactics:**
- Use stable prefixes (system + tool schemas + glossary) for prompt caching on providers that support it.
- Send the state *summary* (clip list, params, analysis summary) by default and frames only on request.
- Use sparse coordinate lists for sparse frames.
- Keep compact tool schemas (short descriptions; enums instead of prose).
- Summarize/evict old tool results in long sessions.

### Q5. Model support for BYOK

Capability snapshot. **[verify all at implementation time; the model landscape changes monthly]**

| Provider path | Tool calling | Image input | Video input | Notes |
|---|---|---|---|---|
| Anthropic (Claude Haiku/Sonnet/Opus families) | yes | yes (docs: ≤1568 px long edge historically, <200 px degrades) | no | prompt caching; native API differs from OpenAI format |
| OpenAI (GPT-4.1/5.x families) | yes | yes | no (frames as images) **[verify]** | Chat Completions/Responses |
| Google Gemini (Flash / Pro) | yes | yes | **yes** (fps configurable) | cheapest strong vision tier historically **[verify pricing]** |
| OpenRouter | depends on model; OpenAI-compatible API | depends | depends | one key → many models; good for "any provider" |
| Ollama on LAN (OpenAI-compatible endpoint) | model-dependent: qwen2.5vl/llava/llama3.2-vision reportedly **lack** tool templates. Qwen3-VL and Gemma 4 reportedly have vision + tools | model-dependent | no | [secondary sources](https://www.promptquorum.com/prompt-bites/which-ollama-models-support-vision), conflicting; check the model library tags |
| On-device (LiteRT-LM, Gemma 4 E2B/E4B) | function calling with constrained decoding (documented) | yes (documented) | no | ~52 tok/s decode for E2B on GPU per Google blog. Combined vision + tools on Android not shown in an official example. [LiteRT-LM](https://ai.google.dev/edge/litert-lm/overview) **[secondary]**. Experimental tier at best |

**Tiers (the support claim to publish):**
- **T0, unsupported:** no tool calling. Possibly a JSON-mode fallback later, but don't promise it.
- **T1, text-only tool agent:** full editing through text grids, summaries, and analysis tools. Expected to work for most tasks with mid-size models. Small local models are likely to fail multi-step edits (Pixel Art Bench / pixel-llm show 3B models fail at even emitting grids) **[speculative extrapolation]**.
- **T2, vision tool agent:** adds render/contact-sheet tools and semantic checks.
- **"Recommended"** label only for models that pass the eval harness threshold (§4).

Graceful degradation: capability flags per configured model (`tools`, `vision`, `max_images`, `context`), detected from a probe call plus a user override. Image tools are hidden from T1 models instead of failing at call time. If tool-call parsing fails twice, the agent falls back to suggesting the edit to the user as text.

**Mobile constraints:**
- Latency: each tool round trip is a full model call (~1–10 s), so batch tool calls when the provider supports parallel tool use. The deterministic analysis runs locally and instantly.
- Rendering images on-device (Canvas → PNG/WebP) is trivial.
- Networking: call providers directly from the app (no backend, per D1).
- Key storage: Android Keystore-backed encryption. Note that Jetpack `security-crypto` (EncryptedSharedPreferences) was deprecated in 2025 **[verify]**, so use Keystore AES-GCM directly or Tink. Never log keys or include them in exports.
- Let the user set a per-session spend/turn cap.

**Cost per typical session [rough estimate]:** system + tools + glossary ≈ 4–6k tokens (cacheable). Per model call, state summary + tool results ≈ 1–3k, plus an image ≈ 0.4–1.5k tokens (single render vs contact sheet). A 10-message editing session with ~3 calls per message ≈ 30 calls × ~8–10k input ≈ 250–300k input tokens and ~15–30k output. At mid-tier list prices of a few $/M input, that's roughly **$0.3–1 without caching and noticeably less with caching**. Cheap/flash tiers come to cents. Treat this as an order-of-magnitude estimate, and get real numbers from the harness.

### Q6. Evaluation

Prior art: Asset Forge's human-calibrated rubric with MAD tracking across rounds; Pixel Art Bench's VLM-judged Elo; the MLLM-as-a-Judge finding that pairwise beats scoring; Wen et al.'s perception-vs-reasoning separation; VGBench/SVGenius for graphics understanding/editing splits ([VGBench](https://arxiv.org/abs/2407.10972), [SVGenius](https://arxiv.org/abs/2506.03139)). Proposal in §4.

### Q7. What is novel vs solved, and the biggest risks

**Mostly solved / borrowable:**
- tool-calling agents over a typed command layer
- render-critique loops as a pattern
- "text beats vision for exact cell work, both together are best" (ARC evidence)
- progressive-disclosure skills
- pairwise judging

**Novel (no direct prior art found):**
- Perception of *animated, monochrome, circular-masked 137/489-LED* imagery by general LLMs.
- A **subjective-word → LED-metric → fix-op** vocabulary for this medium.
- Motion-quality metrics for very low-res displays, where spatial quantization dominates perceived smoothness.
- A BYOK, tier-graded support claim backed by a domain eval.
- Rendering style choice (round-dot vs square, glow vs crisp) for VLM perception at this scale is untested anywhere I found.

**Biggest risks:**
1. Metric gaming: the agent optimizes symmetry/roundness and destroys intent (e.g., symmetrizes a deliberately asymmetric "wink").
2. VLM bias/hallucination ("it's a perfect heart").
3. Render ≠ physical LEDs (brightness curve, glow, minimum visible level). Metrics computed on values may not match what people see.
4. Small/local models failing tool schemas.
5. Cost/latency annoyance on mobile.
6. Provider API drift in a no-backend app.

---

## 3. Proposed "agent perception stack" for GlyphWizard

```
            ┌───────────────────────── user chat ─────────────────────────┐
            │                                                             │
   L4  Interaction policy: clarify-or-offer-variants, iteration caps, spend caps
   L3  Critique loop: edit → auto-analyze → (T2: look with overlays) → revise ≤2 → present
   L2  Analysis tools (deterministic, local, instant): shape / brightness / motion / lint
   L1  Representations: doc summary · text grid · coord list · diffs · motion summary
                        (T2) analytic render · realistic render · contact sheet · onion skin · overlays
   L0  Ground truth: document (clips, generators, keyframes) + baked frames (20 ms tick)
   K   Knowledge: always-on core (device facts, legend, vocab map, rules) + load_skill(...) + example retrieval
```

**L1 representation tools (all read-only):**
- `describe_document()`: clips, layers, generator ids+params, keyframes/easing, duration. Default context.
- `view_frame(t | frame, format=grid|coords|exact, region?)`
- `view_diff(a, b)`: text +/− grid and changed count.
- `motion_summary(clip?|range?)`: computed trajectory/angle series, step stats, seam, flicker events.
- T2: `render(frame|t, style=analytic|realistic, overlays=[centroid, axis, orphans, ideal_circle, …])`, `contact_sheet(range, n≤16, onion=false)`.

**L2 analysis tools** (single `analyze(target, checks=[…])` or a few grouped tools to keep schemas small):
- `shape`: lit count, components, bbox, centroid offset, margins to mask, symmetry (best axis + scores), roundness vs canonical disc/ring, jaggy/orphan/double counts.
- `brightness`: histogram, mean/peak, low-level count, contrast between components.
- `motion`: step CoV, jerk, held frames, seam jump, angular velocity, temporal activity, flicker rate.
- `text`: characters visible, glyph height, stroke gaps.
- Output contract: `score`, `verdict` (plain words), `evidence` (coords/frames), `suggest` (ops).

**L3 loop policy:** see Q3. All thresholds tunable, and set by the eval harness, not guessed.

**K knowledge:**
- Core block ≤ ~1.5k tokens.
- 6 skills (see Q4) loaded via a `load_skill` tool. One frontmatter line each is always present.
- Example library retrieval by tag.

**Model tiers:** T1 gets L0–L2 + K. T2 adds the image parts of L1 and VLM checks in L3. Tiers are published per model from eval results.

---

## 4. Proposed eval harness sketch

Runs off-device (JVM/desktop sharing the Kotlin core: renderer, analysis, command layer) against real provider APIs. **[speculative design]**

**Task families (target ~60–100 tasks to start):**
1. **Perception probes (no editing):** questions with exact answers on given frames/animations. Examples: "how many LEDs lit", "where is the centroid", "which side is heavier", "is it mirror-symmetric", "which direction does it spin", "at which frame does it stutter". Run each probe under each representation config: text only / image only / text+image / with overlays. *This directly answers which representations work for which models.*
2. **Seeded-defect repair:** start from a good glyph and inject a known defect, then give a vague complaint. Defects: shift 1 LED off-center; asymmetric pixel; orphan; flat-topped circle; uneven keyframe spacing; linear easing on a bounce; flicker; loop seam jump; text too dense. Complaints: "looks wonky", "spin is jerky", etc. **Pass = defect metric back within threshold AND unrelated-region diff ≤ tolerance (edit locality).**
3. **Instruction edits:** "fade the heart out over 400 ms", "make the ring pulse twice as fast", "loop 3×". Checked programmatically on the document and baked frames.
4. **Creation from text:** "a spinning arc", "a heart that beats". Programmatic sanity checks (non-empty, centered, within mask, loops cleanly) + **pairwise VLM judge vs reference or vs other models**, run in both display orders and counted only if consistent + periodic small **human pairwise rounds** to calibrate the judge (track agreement, as Asset Forge tracked MAD).
5. **Ambiguity handling:** "make it rounder" on a shape with multiple valid fixes. Pass if the agent asks or offers variants, rather than silently guessing wrong.

**Metrics per model × config:**
- pass rate (k = 3–5 runs, report mean ± spread)
- tool calls, invalid tool calls, tokens in/out, $ cost, wall time
- iterations used, regressions introduced

**Outputs:**
- a support matrix (model → tier → pass rates) published in the repo; this backs the "works with X" claims
- threshold tuning for the critique-loop stop rules
- a regression suite to rerun when prompts/tools change

**Reuse:** the same `analyze` library is both the agent's tool and the grader. That's efficient, but there's a risk the agent optimizes the grader. Mitigate this with a few held-out checks the agent can't call, plus human/VLM pairwise checks.

---

## 5. Risks and open questions for the user

**Risks:** see Q7. In addition:
- **Over-engineering the vision path.** Recommend building T1 (text + analysis) first and adding images only when the harness shows a gain.
- **Analysis thresholds feel arbitrary** until calibrated on real device viewing.

**Open questions:**
1. **Physical brightness response:** is the LED output linear in 0–255? Is there a minimum visible level? How strong is bleed between neighbors? This is needed for the realistic render and for "too dim" metrics. Can you photograph test patterns (gradient ramp, single LEDs, checkerboard) on the 4a Pro?
2. **Real refresh rate / timing** of the matrix vs our 20 ms tick: does the device actually show 50 fps through `setAppMatrixFrame`?
3. **4a Pro "AOD toys only"** (Developer Kit). How does this constrain what animations can be deployed outside the app? (Relevant beyond this research.)
4. Should the agent be allowed to **auto-iterate** (spends the user's tokens) or always ask first? Proposed default: 1 silent analysis pass, ≤2 revise iterations, configurable.
5. **Variant UI:** is a "pick one of 3" strip in the chat acceptable UX?
6. **Example library curation:** who authors the initial 20–50 exemplars? Can we use community Glyph Toys with permission?
7. **Fonts:** is NDOT 47 (OFL) or a custom 3×5/5×7 font acceptable for text features, given Ndot 57 licensing?
8. **Which providers to support first:** suggest OpenAI-compatible (covers OpenAI, OpenRouter, Ollama, many others) + native Anthropic + native Gemini.

---

## 6. Sources

- Rahmanzadehgervi, Bolton, et al., *Vision language models are blind* (2024): https://arxiv.org/abs/2407.06581 · ACCV: https://openaccess.thecvf.com/content/ACCV2024/papers/Rahmanzadehgervi_Vision_language_models_are_blind_ACCV_2024_paper.pdf · project: https://anhnguyen.me/2024/vlms-are-blind/
- Vo et al., *Vision Language Models are Biased* (2025): https://arxiv.org/abs/2505.23941 · https://github.com/anvo25/vlms-are-biased
- Wen, Wang, Bilal, *How Modality Shapes Perception and Reasoning: A Study of Error Propagation in ARC-AGI* (2025): https://arxiv.org/abs/2511.15717
- Greenblatt, *Getting 50% (SoTA) on ARC-AGI with GPT-4o*: https://blog.redwoodresearch.org/p/getting-50-sota-on-arc-agi-with-gpt
- Mitchell et al. / Xu et al., *LLMs and the Abstraction and Reasoning Corpus: object-based representations*: https://arxiv.org/abs/2305.18354
- VRIQ benchmark: https://arxiv.org/abs/2602.05382
- MotionBench (CVPR 2025): https://arxiv.org/abs/2501.02955 · NextMotionQA: https://arxiv.org/html/2606.04773
- Anthropic vision docs: https://docs.anthropic.com/en/docs/vision
- Gemini video understanding: https://ai.google.dev/gemini-api/docs/video-understanding · tokens: https://ai.google.dev/gemini-api/docs/tokens
- Asset Forge (LLM pixel art + calibrated judge): https://matthewdeaves.github.io/assetforge/
- Pixel Art Bench: https://huggingface.co/blog/AINovice2005/pixel-art-bench · pixel-llm: https://github.com/mxmarchal/pixel-llm
- VGBench: https://arxiv.org/abs/2407.10972 · SVGenius: https://arxiv.org/abs/2506.03139
- LL3M: https://arxiv.org/abs/2508.08228 · https://github.com/threedle/ll3m
- BlenderAlchemy: https://arxiv.org/abs/2404.17672
- SceneCraft: https://arxiv.org/abs/2403.01248 (details not verified)
- Render-in-the-Loop: https://arxiv.org/abs/2604.20730 · RefineSVG: https://arxiv.org/abs/2607.27699 · IntroSVG (CVPR 2026): https://openaccess.thecvf.com/content/CVPR2026/papers/Wang_IntroSVG_Learning_from_Rendering_Feedback_for_Text-to-SVG_Generation_via_an_CVPR_2026_paper.pdf
- RLRF (rendering-aware RL, StarVector): https://arxiv.org/abs/2505.20793
- Vision-guided iterative refinement for frontend code: https://arxiv.org/html/2604.05839v1
- Huang et al., *LLMs Cannot Self-Correct Reasoning Yet* (ICLR 2024): https://arxiv.org/abs/2310.01798
- MLLM-as-a-Judge: https://arxiv.org/abs/2402.04788 · image-edit judge bias audit: https://arxiv.org/html/2610.01670
- Agent Skills / progressive disclosure (secondary): https://www.firecrawl.dev/blog/agent-skills
- Ollama vision/tool support (secondary, conflicting): https://www.promptquorum.com/prompt-bites/which-ollama-models-support-vision · https://www.betterclaw.io/blog/ollama-models-tool-calling-support
- LiteRT-LM: https://ai.google.dev/edge/litert-lm/overview · https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/
- Glyph Museum developer spec (row widths): https://glyphmuseum.com/developers
- Nothing GlyphMatrix Developer Kit: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- Glyph Matrix Paint: https://github.com/samkex/glyph-matrix-paint
- NDOT 47 OFL / Ndot 57 licensing note (secondary): https://github.com/GeneralKaos666/ndot-firacode-nerd
