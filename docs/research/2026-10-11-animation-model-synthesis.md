# Animation Model — Research Synthesis & Scoring

2026-10-11. Combines:
- `2026-10-11-animation-model-research.md` (conventional models: raster, layered timelines, code-first, LED ecosystems)
- `2026-10-11-novel-animation-models-research.md` (novel/hybrid: op-logs, baked+recipes, SDF, ASCII grids, node graphs, trackers, agent tooling)

Scored against `2026-10-11-evaluation-framework.md`. Scores are 1–5 per criterion, weighted to /100. They are judgement calls informed by the reports, not measurements.

## Convergence

Both agents, researching independently, landed on the same shape:

> **Editable model = timeline of clips. Clips are either raster (hand-drawn frames, GM-identical) or declarative generators (typed params, never code). Baked raster frames are a derived cache used for playback and Glyph Museum export. UI and agent share one validated command layer addressing things by stable IDs.**

## Scoring

| Candidate | Gates | Agent 20 | Visual 20 | Simple 15 | Human 15 | Expr 15 | Conv 5 | Dur 5 | Extra 5 | **Score** |
|---|---|---|---|---|---|---|---|---|---|---|
| **Hybrid: timeline + raster clips + recipe clips + baked cache + shared commands + provenance journal** | pass | 5 | 5 | 3 | 4 | 5 | 4 | 4 | 5 | **89** |
| B Layered keyframe timeline (shapes/text/effects) | pass | 4 | 5 | 3 | 4 | 4 | 4 | 4 | 3 | **80** |
| C Code-first (expressions) | **fails G2** (GM import not editable) | 4 | 4 | 3 | 1 | 5 | 5 | 2 | 2 | 68 |
| F SDF/field as primary model | pass | 3 | 5 | 3 | 2 | 3 | 5 | 3 | 3 | 67 |
| D Op-log as source of truth | pass | 3 | 3 | 2 | 3 | 3 | 3 | 3 | 5 | 59 |
| G Node graph | pass | 2 | 4 | 1 | 2 | 5 | 4 | 3 | 3 | 58 |
| H ASCII/hex grid as storage | pass only if 2 hex/LED | 3 | 2 | 5 | 3 | 2 | 2 | 4 | 2 | 58 |
| I Tracker (patterns + order list) | pass | 3 | 2 | 4 | 3 | 3 | 2 | 3 | 3 | 58 |
| A Pure raster | pass | 2 | 2 | 5 | 3 | 2 | 2 | 3 | 2 | 53 |

The losers still contribute pieces to the hybrid:
- **SDF math (F)** → how recipe generators compute brightness per LED from normalized (x, y). Gives resolution independence and anti-aliasing for free.
- **ASCII grid (H)** → the agent's *read* view ("look" tool), and row strings for small pixel edits. Not storage: it's lossy at 16 levels, and LLMs write grids poorly.
- **Op-log (D)** → an optional provenance/history *journal*, not the source of truth (tldraw abandoned command-based undo).
- **Tracker (I)** → named reusable frames inside raster clips.
- **Code-first (C)** → a later sandboxed expression clip type with a custom tiny language, step limits and seeded noise. No embedded JS engine.

## Key findings

**Corrections to our facts**
- Glyph Museum `v` is a **device ID** (1 = Phone 3, 4 = 4a Pro), not a format version.
- The 20 ms minimum is Glyph Museum's app behaviour, not their spec.
- The SDK's `GlyphMatrixFrame` caps at 3 objects, so we composite ourselves and push raw `int[]`.
- pauwma's editor wipes frames on device switch, so a converter is a real gap we can fill.

**Model decisions the evidence supports**
- **Time:** integer ms. Clips have an absolute start and a duration; keyframes are clip-relative. Snap to a tick (default 20 ms) only when baking.
- **Layers:** absolute placement and no overlap within a layer. Sequential/ripple tracks are an LLM trap.
- **Transitions:** in/out properties on clips. A crossfade is two overlapping clips created by one macro command. No standalone transition objects.
- **Easing:** a small named set (`hold`, `linear`, `easeIn`/`easeOut`/`easeInOut`, `cubicBezier`, `steps`), per keyframe segment.
- **Device profile:** at document level, plus a `sourceProfile` on each raster clip. Per-frame profiles are redundant and corruptible. Conversion still works: generators re-render natively, and raster resamples (area-average down, nearest up).
- **Versioning:** separate `schemaVersion`, never repurpose fields, ignore unknown keys, chained migrations, golden test files.

**Agent tooling**
- Address things by **stable IDs**, never array indices. Index arithmetic is the #1 LLM patch failure.
- A few **task-level tools** beat many fine-grained ones or one catch-all (one 2026 benchmark, with caveats).
- **Every write returns feedback**: a rendered preview plus warnings (e.g. "0 LEDs lit"). Blender-agent builders cite this as the biggest quality lever.
- Lenient parsing plus validation beats forced strict JSON mode.
- Each agent turn is one undo step, and provenance records human vs `agent:<model>`.

**Avoid:** Lottie/After Effects as the internal format, Rive-style state machines, stateful or wall-clock procedural code, many blend modes, CRDTs, node graphs, and changing a published generator's math in place (version it: `ring_pulse@1`, `@2`).

## To verify on hardware (spike)
- SDK `int[]` ordering and expected length on 4a Pro (169 grid vs 137 LEDs?)
- Maximum sustainable `setAppMatrixFrame` rate
- Brightness response curve (for gamma correction)
- Behaviour when locked / face-down / AOD toy active

## Open questions (consolidated)
1. Core model: adopt the hybrid?
2. Device profile at document + raster-clip level instead of per frame?
3. Can raster layers have transparency (pixel "off" vs "transparent")?
4. Should GM exports embed our editable data (unknown keys may not be tolerated)?
5. First 3–5 built-in generators?
6. Does hand-editing a generator clip convert it to raster ("rasterize layer")?
7. Undo: session-only, or saved history journal?
8. Assume vision-capable models for the agent?
9. Live/reactive content (clock, counters, music) in v1, or baked-only?
10. 60 Hz option or fixed 50 Hz (20 ms tick)?
