# GlyphWizard: rules for every session and every turn

An open-source, non-commercial Android app (Kotlin/Compose) for designing and triggering Nothing Glyph Matrix animations. The user is an experienced Go/Python dev who is new to Android.

## Startup reading (every new session; read IN FULL, in order, before any action, review included)
1. `docs/HANDOFF.md`: where we are and what's next
2. `docs/engineering/code-guidelines.md` **and** `docs/engineering/modern-kotlin-patterns.md`: both mandatory before any code task or review; never write legacy Java-style patterns
3. Spec: `docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md` (the source of truth)
4. Decisions: `docs/research/decisions.md` (all entries) · UI decisions: `docs/design/design-log.md`
5. UI rules: `docs/design/frontend-guidelines.md` · screens: `docs/design/atlas.html` · navigation: `docs/design/flow.md`
6. The active plan in `docs/superpowers/plans/`, plus its ledger `.superpowers/sdd/<plan>/progress.md` (git-ignored) when executing
7. Hardware facts: `docs/research/hardware-spike-results.md` (anything that touches the Glyph Matrix)
8. `docs/learning/*.md`: concepts already explained (don't repeat their learning asides)

Reference only: `docs/research/2026-10-11-*.md` (research reports, reviews, superseded drafts).

## Mandatory workflow (coding and review agents alike)
1. Read the task.
2. Read this file, `code-guidelines.md` and `modern-kotlin-patterns.md` (plus the relevant spec, decisions and UI rules).
3. **Discover:** look for code to reuse or refactor-to-reuse.
4. **Understand every flow you'll touch,** end to end, in this turn. If you can't explain it, don't change it.
5. State what you'll reuse, refactor and touch.
6. Code → **then** write comprehensive tests (not TDD) → self-review against the DoD in the code guidelines.

## Working agreements
- **Dev work inline; reviews, QA and research in subagents.** Any fact you'd otherwise assume goes to a research subagent first.
- **Stop and ask on any blocker** (missing device, access, an unclear fact, a step you'd skip or postpone). Never defer or work around it silently.
- **Plan revisions are capped** (agree the cap up front); later findings become execution-time `[verify]` items or ledgered rulings.
- **Verify on the device, not just the API:** an `Ok` result can still light nothing. Check logs and the user's eyes.
- **Push, tag or publish only when the user explicitly asks.** The repo is public.

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
