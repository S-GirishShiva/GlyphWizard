# Agent Research Brief (shared context for research agents)

Read this first, then read `decisions.md`, `2026-10-11-animation-model-synthesis.md`, and `2026-10-11-agent-perception-research.md` in this directory.

## Product
GlyphWizard: open-source, fully local Android app (Kotlin; author is an experienced Go/Python backend+AI dev but first-time Android dev) for designing animations on the Nothing Phone Glyph Matrix — a monochrome circular LED matrix. Phone (4a) Pro: 13×13 grid, 137 LEDs, rows [5,9,11,11,13,13,13,13,13,11,11,9,5]. Phone (3): 25×25, 489 LEDs. Brightness 0–255 per LED.

## Animation model (decided)
Layered timeline of clips: raster clips (per-pixel brightness+alpha) and declarative versioned generator clips (e.g. `ring_pulse@1` with typed params, computed per LED from normalized coords). Keyframes with easing, in/out transitions, two blend modes. Baked to frames (default 20ms tick) for playback. Parametric templates filled at trigger time. One typed, validated command layer used by both UI and agent; stable IDs; persistent capped diff history with authorship (user / agent:<model>); one agent turn = one undo group.

## Agent (decided so far)
- In-app agent; user brings their own key/endpoint.
- **Design-only, sandboxed**: can only create/edit animation documents in GlyphWizard. Cannot assign triggers, read contacts/notifications/installed apps, or change device settings (deferred).
- **Open-weight models are first-class**: OpenAI-compatible endpoints, subscription gateways (e.g. OpenCode Go and similar), OpenRouter, self-hosted (Ollama/vLLM/llama.cpp on LAN), and SMALL models (e.g. GLM flash-class, Qwen small, etc.) — not just frontier APIs.
- Perception: text grid view by default, computed analysis tools (symmetry, centering, roundness, motion smoothness, flicker…), optional image renders for vision models, capped self-repair loop, glossary mapping vague words → metrics.
- Thorough context engineering (knowledge base, skills, examples) is required — models are assumed unfamiliar with glyph matrices.
- Future (not v1): external agents get the same tool library + a headless emulator through an allowlist; they output a .glyphwiz file the user imports. Core engine therefore lives in a pure-Kotlin module.

## Ethos
No decisions based on assumptions. Cite sources with links; mark unverified/speculative claims explicitly; prefer evidence (benchmarks, papers, postmortems, GitHub issues) over opinion. Flag overengineering — this is a solo, first-Android-app passion project. Don't write app code.
