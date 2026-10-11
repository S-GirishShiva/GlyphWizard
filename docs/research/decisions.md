# Design Decisions Log

| # | Date | Decision | Rationale / ref |
|---|---|---|---|
| D1 | 2026-10-10 | Open source, fully local (no backend/auth); import/export required | User vision |
| D2 | 2026-10-10 | Humans and in-app BYOK AI agent are first-class; both use one command layer | User vision |
| D3 | 2026-10-11 | Device-agnostic model; build/test on 4a Pro (13×13/137). Design so resolution conversion is easy later | User |
| D4 | 2026-10-11 | Evaluation framework locked | `2026-10-11-evaluation-framework.md` |
| D5 | 2026-10-11 | Core model = hybrid: layered timeline of raster + generator clips, baked cache, shared command layer, provenance journal. Device profile at document level + `sourceProfile` per raster clip | `2026-10-11-animation-model-synthesis.md` |
| D6 | 2026-10-11 | Raster pixels = brightness + alpha (0–255 each). Tools: paint / paint-off / erase. Blend modes: Normal (alpha-over) and Lighten (max) only | GM import = opaque; export flattens |
| D7 | 2026-10-11 | v1 = baked playback + parametric templates rendered at trigger time. Live/continuous rendering (clocks, music, sensors) deferred; render(doc, t, inputs) signature keeps it open | — |
| D8 | 2026-10-11 | Pixel-editing a generator clip auto-converts it to raster (no dialog); one undo step restores formula+params; clip shows "was <generator>" badge | Versioning (@N) is for generator math, not user edits |
| D9 | 2026-10-11 | Persistent undo/redo via capped diff history (JSON Patch forward+inverse, ~200 steps/256KB), sidecar file, entries carry author (user / agent:<model>) + label; one agent turn = one group. Document stays source of truth | JetBrains Local History, vim undofile precedent |
| D10 | 2026-10-11 | Tick is a document setting (range ~16–100ms, default 20ms, ceiling pending hardware spike). Playback speed is a separate clock multiplier (e.g. 0.5×–2×), re-bakes; per-clip speed possible later | — |
| D11 | 2026-10-11 | GM export = plain flattened frames; editable data lives in own `.glyphwiz` JSON | GM spec silent on unknown keys |
| D12 | 2026-10-11 | Agent scope (v1): design-only, sandboxed to GlyphWizard animation documents. Cannot assign triggers/rules or touch device settings; user does that manually. Personal context (contacts, installed apps) deferred unless research shows it's a cheap win | User |
| D13 | 2026-10-11 | Open-weight models are first-class: OpenAI-compatible endpoints, subscription gateways (e.g. OpenCode Go), self-hosted, and small models (e.g. GLM flash-class) must be supported, not just frontier APIs | User |
| D14 | 2026-10-11 | Only in-app agent for v1. Future external path = same tool library + headless emulator exposed to external agents via an allowlist (not a separate app); output is a .glyphwiz file imported by the user. Implication: model+commands+renderer live in a pure-Kotlin module with no Android deps | User |
| D15 | 2026-10-11 | In-app agent gets thorough context engineering (knowledge base, skills, glossary, examples) — models are assumed unfamiliar with glyph matrices (consistent with perception research) | User + `2026-10-11-agent-perception-research.md` |
| D16 | 2026-10-11 | Model support is boxed: v1 targets mid-capability+ models. Small/8k-context models supported only if cheap to achieve; otherwise marked unsupported for now and revisited later | User |
| D17 | 2026-10-11 | Model support tiers are defined by eval-harness results (pass the suite → supported), not by naming models up front | Evidence ethos |
| D18 | 2026-10-11 | GlyphWizard is non-commercial. Glyph SDK isolated in its own `:glyph-sdk` module behind an interface; `.aar` never committed (fetched at build); an SDK-free build flavour (preview + GM export) keeps the core fully open. User emails Nothing developer programme for explicit permission to ship the SDK in a free OSS app. Project licence TBD (must reconcile "open source" with "non-commercial") | Review r1 C1; SDK EULA §2.1(b)(c), §2.2 verified |
| D19 | 2026-10-11 | Capability tiers: every tool, skill, and context block carries a `minTier`; context assembler filters by the model's tier. Base tier ≈ 10 coarse task-level tools; capable models unlock more surface (fine-grained edits, paint, image renders, variants). Tiers only exist where evals prove them (D17); lower "guided" tier later | Review r1 H6; MCP-GRANITE (verified), WorkBench, BFCL |
| D20 | 2026-10-11 | Whole stack in Kotlin (no Go/Python in-app). Python allowed only for offline eval analysis | Tool calls = command layer; cross-language boundary cost; eval harness must test shipped code |
| D21 | 2026-10-11 | Triggers (notification/call/condition → animation) are a v1 must-have | User |
| D22 | 2026-10-11 | Process: stay on superpowers (brainstorming → spec → writing-plans); GSD port only if needed later | User |
| D23 | 2026-10-11 | Every new concept in specs/plans gets a short "Learning aside" (plain explanation + 1–3 quality resources) | User learning goal |
| D24 | 2026-10-11 | v1 definition of done + M0–M6 order approved (triggers M4 before agent M5) | `2026-10-11-v1-roadmap-draft.md` |
| D25 | 2026-10-11 | Image/GIF → glyph import in v1 (M2): system photo picker, crop/fit to circle, luminance → area-average downscale, levels (brightness/contrast/gamma/threshold/invert), live preview, output = raster clip; animated GIF/WebP → frames. Deterministic app feature; agent may call it later with params | User; Nothing OS custom-icon precedent (user confirmed it works on 4a Pro) |
