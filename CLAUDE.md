# GlyphWizard: rules for every session and every turn

An open-source, non-commercial Android app (Kotlin/Compose) for designing and triggering Nothing Glyph Matrix animations. The user is an experienced Go/Python dev who is new to Android.

## Source of truth (read before acting)
- Spec: `docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md`
- Code rules: `docs/engineering/code-guidelines.md` + `docs/engineering/modern-kotlin-patterns.md` (**both mandatory before any code task or review**; never write legacy Java-style patterns)
- UI rules: `docs/design/frontend-guidelines.md`
- Decisions: `docs/research/decisions.md` · UI decisions: `docs/design/design-log.md`
- Screens: `docs/design/atlas.html` · navigation: `docs/design/flow.md`
- Resume point: `docs/HANDOFF.md`

## Mandatory workflow (coding and review agents alike)
1. Read the task.
2. Read this file and the code guidelines (plus the relevant spec and UI rules).
3. **Discover:** look for code to reuse or refactor-to-reuse.
4. **Understand every flow you'll touch,** end to end, in this turn. If you can't explain it, don't change it.
5. State what you'll reuse, refactor and touch.
6. Code → **then** write comprehensive tests (not TDD) → self-review against the DoD in the code guidelines.

## Non-negotiables
- **No assumptions.** Cite evidence or tag [P]/[HW]/[EV]. When unsure, ask the user.
- **Quality over speed.** Never cut scope for capacity, only on merit.
- **Clean and elegant, no hacks, no duplication.** Performance beats decomposition only in measured hot paths.
- **Constructor dependency injection everywhere,** no hidden singletons. `:core`/`:agent` are pure JVM.
- **Minimal, why-only comments.**
- **100% line coverage on `:core`/`:agent`;** ≥ 80% on `:app`/`:designsystem` plus screenshot and flow tests.
- **Improve bad code you pass,** after understanding it, in a separate refactor commit.
- **UI:** only `:designsystem` tokens and `Gw*` components. Every visual change is signed off by the user and logged.
- **Each new Android/Kotlin concept gets a learning aside.**
- **Commits:** `<slug> [Siddanth, Claude] - msg` + blank line + details + Co-Authored-By trailer. One branch per milestone. Push only when asked (or under an established push agreement).
