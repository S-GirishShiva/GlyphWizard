# Session Handoff

**Updated:** 2026-10-12 · **Branch:** `design/v1-spec` (pushed) · **Last commit:** see `git log -1`

## Where we are
- Branch `m0-foundations`. M0 plan rev 4 executing inline. **Tasks 1–5 done** (see `git log`); **Task 6 (hardware measurements) in progress.**
- Ledger with every ruling: `.superpowers/sdd/2026-10-12-m0-foundations-spike/progress.md` (git-ignored; git log is the fallback).
- Task 6 results so far: `docs/research/hardware-spike-results.md`. Steps 1 (layout: both work, no transform), 2 (values to 2047 pass through; visible effect unchecked), 3 (perceived curve steep to ~95; min visible level 17) and 4 (delivery ≤ 90 fps exact, ~115 max), 5 (flash clean at 10/15 Hz, rise ≤ 8 ms, no glow, pitch ≈ 1.7 mm) and 6 (timeout: no effect; held frames persist; system re-applies on screen-off; HOLD command added) done. **Next: Step 7** (priority and states), then Steps 8–9 (auto-timeout, priority/states, kill/process, Obtainium; **ask before pushing any tag**).
- Key facts: `NothingKey=test` is required (fixed); the charging animation overrides the app matrix (turn it off while measuring); `NtGlyphServiceImpl` logs are shared by all glyph clients.
- Photos: `manual-test-pics/` (move the relevant ones into `docs/research/spike-photos/` as small JPEGs).
- Work split: dev inline; research/review/QA in subagents. Stop and ask on any blocker.

## Read these first (in order)
0. `CLAUDE.md` (auto-loaded) and `docs/engineering/code-guidelines.md`
1. `docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md`: **the source of truth**
2. `docs/design/frontend-guidelines.md`: UI code rules (`:designsystem`, R1–R14)
3. `docs/research/decisions.md`: D1–D46
4. `docs/design/design-log.md`: UI1–UI38
5. `docs/design/atlas.html`: every approved screen. `docs/design/flow.md`: navigation
6. Reference only: `docs/research/2026-10-11-*.md` (research reports, reviews r1–r3, superseded drafts)

## Working agreements (also in Claude memory)
- **No assumptions:** cite evidence or tag [P]/[HW]/[EV]. Research → plan → adversarial review → refine.
- **Quality over speed**, no deadlines. Never cut scope for capacity, only on merit.
- **The user is new to Android** (experienced in Go/Python). Every new concept gets a learning aside.
- **Challenge the user's views** when evidence supports it.
- **Every visual detail is signed off by the user.** Re-read the design log before any mockup, and self-screenshot.
- **Commits:** `<slug> [Siddanth, Claude] - message`, blank line, details, then the Co-Authored-By trailer. Branch per milestone.

## Parallel tasks (user)
- Send the Nothing SDK permission email (`docs/outreach/2026-10-11-nothing-sdk-permission-email.md`).
- Ask OpenCode about Go keys in non-coding apps (draft not written yet).
- Decide in M0: the permanent application ID, and confirm GPL-3.0+ with the SDK exception.

## Environment notes
- Phone (4a) Pro on USB, Android 16 / API 36. `android` CLI, adb, scrcpy and mobile-mcp are installed.
- Non-interactive shells don't load `~/.zshrc`: use `$HOME/Android/Sdk/platform-tools/adb` if adb versions mismatch.
- Visual companion: superpowers brainstorming `scripts/start-server.sh --project-dir <repo>`. Mockups live in `.superpowers/` (gitignored).

## Architecture
Module level is final. The internal architecture deep-dive comes before M1 (D46).
