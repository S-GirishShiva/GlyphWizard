# GlyphWizard Glossary

Running example: **"a heart pulses twice, then a ring ripples out and fades."**

```
Layer 1: [Raster clip "heart", 0–1200ms] → keyframes: opacity 100% @800ms → 0% @1200ms, easing easeOut
Layer 2: [Generator clip ring_pulse@1 {speed:1.2}, 600–1800ms] → transition in: fade 200ms
```

## Core model

| Term | Meaning | Backend analogy |
|---|---|---|
| Frame | One snapshot of all LEDs (137 on 4a Pro), each a brightness 0–255 (hardware raw range under test in M0) | A row |
| Raster | Data stored as literal pixels | Materialized data |
| Timeline | The animation's time axis, in milliseconds | — |
| Layer | A track on the timeline; higher layers draw on top | Z-index |
| Clip | A block of content on a layer with start + duration | A scheduled job |
| Generator / recipe | Built-in effect described by parameters (`ring_pulse {speed, width}`); pixels computed from it | SQL view / pure function |
| `@1` versioning | Pins a generator's exact math; changes become `@2` so old animations never change | API versioning |
| Keyframe | "At time X, property P = V"; values between are interpolated | Interpolation between data points |
| Easing | Shape of the in-between motion (`linear`, `easeOut`, …) | Curve `f(progress) → progress` |
| Transition | How a clip enters/leaves (fade, slide, wipe), stored on the clip | — |
| Crossfade | One clip fades out while the next fades in, overlapping | — |
| Parametric template | Animation with inputs (`{initials}`, `{unreadCount}`) filled at trigger time | Templated query with bind params |

## Rendering & quality

| Term | Meaning | Backend analogy |
|---|---|---|
| Render function | `render(animation, timeMs) → frame`, pure | Pure handler |
| Baking / baked cache | Pre-rendering every tick so playback just reads frames | Materialized cache |
| Tick / fps | Bake interval; 20 ms = 50 fps | Cron interval |
| Normalized coordinates | Positions 0.0–1.0 instead of LED numbers; device-independent | Percentages vs pixels |
| SDF (signed distance field) | Shape as math: distance from a point to the shape's edge | — |
| Anti-aliasing / subpixel motion | Partial brightness on LEDs near a shape so motion glides | — |
| Gamma correction | Bending brightness values so fades look perceptually even | — |
| Device profile / mask | Grid size + which cells are real LEDs (137 of 169) | Config struct |
| Resampling | Converting raster pixels between grid sizes | Schema data migration |
| Foreground service | Work Android keeps running while the app is in the background, shown via a persistent notification. Types differ: `shortService` (our agent turns) times out after ~3 min | A supervised worker with a deadline |

## Agent & editing

| Term | Meaning | Backend analogy |
|---|---|---|
| Command layer | Every action is a typed method; UI and AI tool calls both use it | Service layer / handlers |
| Validation | Commands checked before applying, with errors the AI can read | Request validation / 400s |
| Stable IDs | Permanent IDs (`clip_8f2a`), never positions | Primary keys vs offsets |
| Task-level tools | Few meaningful tools vs many tiny ones | Coarse-grained endpoints |
| Look tool / read view | Rendered text picture so the AI can check its work | Observability for the agent |
| Source of truth | Canonical copy; everything else derived (here: the timeline doc) | Primary DB vs caches |
| Op-log | Log of every command (NOT our source of truth) | Event sourcing |
| Provenance journal | Who changed what (you vs `agent:<model>`) | Audit log |
| Schema version / migration | File format version + upgrade functions | DB migrations |
| Golden-frame tests | Compare rendered frames to saved known-good frames | Snapshot tests |

## Agent runtime

| Term | Meaning | Backend analogy |
|---|---|---|
| Tier / support label | Which tool surface a model gets; v1 labels: Supported / Below bar / Untested | Feature flags by client capability |
| Probe | ~20–90 s automated check that an endpoint does tool calls correctly through our code | Health check / contract test |
| Eval harness | JVM CLI running scored tasks against a model to decide support | Integration test suite + benchmark |
| Epoch | (model, endpoint, key, prompt, tools, KB, app version, device) tuple; when it changes, old chat turns are summarised | Cache key / schema version |
| Turn card | Chat card summarising one agent turn, with Undo / Restore | A deploy entry with rollback |
| Frames mode / Timeline mode | Beginner frame-by-frame editor vs layered keyframe editor over the same document | Two UIs over one API |
