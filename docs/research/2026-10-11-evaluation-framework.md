# Animation Model — Evaluation Framework

Locked 2026-10-11. Used to score candidate source-of-truth representations for GlyphWizard animations.

## Hard gates (pass/fail — failing any eliminates the option)

| Gate | Test |
|---|---|
| G1 Glyph Museum export, lossless | Any animation exports to `{v, meta, frames:[{p, d}]}` with no loss |
| G2 Glyph Museum import | A GM file can be imported and edited, at minimum as a raw frame-sequence clip |
| G3 Preview == device | One render function feeds both the on-screen preview and the LEDs |
| G4 Device-profile aware | No hardcoded grid size; every frame/document carries a DeviceProfile (grid + LED mask) |
| G5 Cheap playback | Rendered/baked fast enough for ~50 fps from a foreground service, battery-friendly |

## Weighted criteria (total 100)

| # | Criterion | Weight | How to judge |
|---|---|---|---|
| 1 | Agent enablement | 20 | Token cost to read/edit; edit locality; schema-validatable; errors legible enough for self-correction |
| 2 | Visual quality | 20 | Subpixel/anti-aliased motion; perceptual (gamma-correct) fades; first-class easing; ms timing & temporal smoothness; per-pixel hand control always available |
| 3 | Simplicity to build | 15 | Shippable by a first-time Android dev; concept count; renderer size; testability (pure render → golden-frame tests) |
| 4 | Human editing on a phone | 15 | Non-destructive; familiar mental models (frames/layers/timeline); small-touchscreen friendly |
| 5 | Expressiveness | 15 | Static, frame-by-frame, motion, transitions, procedural, **parametric templates** (trigger inputs: initials, counts, icons, loop-until-unlock) |
| 6 | Resolution conversion | 5 | 13↔25 as a clean function, ideally re-render rather than resample |
| 7 | Durability | 5 | Format versioning/migration, diffability, undo/history |
| 8 | Extra wins | 5 | Provenance (human vs agent), remixability, future sharing |

## Optional empirical check

When narrowed to ~2 candidates: run ~10 benchmark prompts (e.g. "spinning arc", "fade the heart out over 400ms", "loop 3×") through an LLM in each representation; measure success rate and tokens. Converts criterion 1 from opinion into data.
