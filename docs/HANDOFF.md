# Session Handoff

**Updated:** 2026-10-12 · **Branch:** `design/v1-spec` (pushed) · **Last commit:** see `git log -1`

## Where we are
- Branch `m0-foundations`. M0 plan rev 4 executing inline. **Tasks 1–6 done** (Task 6 except Step 9, deferred by user to after Task 8's review and before the merge; absolute luminance left open). See `git log` and `docs/research/hardware-spike-results.md`.
- Ledger with every ruling: `.superpowers/sdd/2026-10-12-m0-foundations-spike/progress.md` (git-ignored; git log is the fallback).
- **Next: Task 7** (CI, supply chain, threat model). Ledger notes for it:
  - the APK script must assert NothingKey is in full and absent from sdkFree;
  - add `:app:detektSdkFreeRelease` and `:spike:detektFullDebug` to the detekt line;
  - build-logic is gone (no validatePlugins; `scripts:` uses `:app:assembleFullDebug`);
  - verification metadata must be generated from a FRESH Gradle home;
  - CI JDK 25;
  - threat model: force-stop leaves the matrix lit; third-party apps unarbitrated; Robolectric downloads outside verification.
- Then **Task 8** (exit review via a subagent code review; Step 9 Obtainium test before merge; ask before any push).
- D56: encoder max 4095 (user decision). **Brightness model validated** (results doc, "Brightness model and calibration"): AW20144 driver gives 256 linear PWM steps, clamped at 4095. **Recommendation for the D46 deep-dive / M1:** CIE L* table in `:core`.
- Spike state on the phone: pattern service stopped; notification access granted to the spike listener (revoke if wanted).
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
