# Session Handoff

**Updated:** 2026-10-12 · **Branch:** `design/v1-spec` (pushed) · **Last commit:** see `git log -1`

## Where we are
- Design is done and the spec is approved.
- **The M0 plan is final and approved (rev 4, 2026-10-12):** `docs/superpowers/plans/2026-10-12-m0-foundations-spike.md`. Reviews r1–r3 sit beside it. Rev 4 is the last revision; later findings are resolved as `[verify]` items during execution.
- **Now:** executing M0 **inline** (superpowers:executing-plans), starting at Task 1 (four blocking questions to the user). Check the task checkboxes and `git log` for progress.
- **Work split:** dev in the main session; reviews, QA and research in subagents.
- **Java:** JDK 25 daemon and toolchain; Android target 25 (gated, fallback 21); `:core`/`:agent` `-Xjdk-release=17` (to be logged as D5x in Task 2).
- After M0: the D46 architecture deep-dive (it also covers the convention-plugin question), then the M1 plan.

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
