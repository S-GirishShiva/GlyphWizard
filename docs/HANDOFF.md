# Session Handoff

**Updated:** 2026-10-12 · **Branch:** `design/v1-spec` (pushed) · **Last commit:** see `git log -1`

## Where we are
The design phase is complete and the spec is approved. The brainstorming flow (superpowers) reached "write the design doc", so the next steps are:
1. Spec, code guidelines (D45) and CLAUDE.md are in place.
2. **Spec approved 2026-10-12.** Invoke `superpowers:writing-plans` for **M0 only** ("Foundations & hardware spike"). Plans for M1–M8 are written just in time, after the previous milestone, because each one depends on the last one's results (e.g. M0 measurements).

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
