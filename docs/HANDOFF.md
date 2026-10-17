# Session Handoff

**Updated:** 2026-10-17 · **Branch:** `master` (M0 merged: PR #1, #2) · **Last commit:** see `git log -1`

## Where we are
- **M0 is done.** Plan rev 4 Tasks 1–8 executed; ledger: `.superpowers/sdd/2026-10-12-m0-foundations-spike/progress.md` (git-ignored; git log is the fallback).
  - CI green on GitHub (checks, detekt + canary, full-build); supply chain, threat model and licence audit in `docs/engineering/`.
  - Exit review (subagent) fixed: vacuous detekt canary, brightness rounding onto the driver's PWM steps, `NothingGlyphDisplay` session ownership, guard script. Deferred items with reasons are in the ledger (Task 8 Step 2).
  - Hardware facts: `docs/research/hardware-spike-results.md` (Steps 1–9).
- **Next: the D46 internal architecture deep-dive** (research → draft → adversarial review → user sign-off), **then the M1 (`:core`) plan.** Agenda items already recorded:
  - convention plugin for the ~4 lines of quality wiring repeated per module;
  - the shared result type (`GlyphResult` is local to `:glyph-sdk`);
  - `DeviceProfile`/`TextGrid` are provisional (add validation);
  - brightness model: CIE L* table in `:core` (results doc, "Brightness model and calibration");
  - playback must clear the matrix itself (force-stop leaves it lit; auto-timeout does nothing) and handle the dropped first frame after `register()`;
  - detekt custom rule for `else` on sealed `when` (#25) and multi-line mutable `object`s.

## Open items
- **Prerelease cleanup:** delete the GitHub prerelease and tag `spike-m0-test` (confirm via the API afterwards).
- **On device, later:** re-measure the minimum visible level with the round-up encoder (expected 1); walk the restricted-settings unlock path on a browser/file-manager install (`adb install --package-source 3` may reproduce it [U]).
- **M2 notes:** in-app open-source notices (Apache §4 texts); backup rules replace the template files; debug mirror throttling/PNG dumps; placeholder text sits under the status bar (insets).
- **M3 notes:** onboarding shows the restricted-settings walkthrough only when access is actually restricted (Obtainium installs aren't); third-party glyph apps are not arbitrated.
- **Phone state:** spike installed via Obtainium with notification access granted (revoke or uninstall if wanted).

## Read these first
Follow the **Startup reading** checklist in `CLAUDE.md` (in full, in order, before acting).

## Working agreements (also in Claude memory)
- **No assumptions:** cite evidence or tag [P]/[HW]/[EV]. Research → plan → adversarial review → refine.
- **Quality over speed**, no deadlines. Never cut scope for capacity, only on merit.
- **The user is new to Android** (experienced in Go/Python). Every new concept gets a learning aside.
- **Challenge the user's views** when evidence supports it.
- **Every visual detail is signed off by the user.** Re-read the design log before any mockup, and self-screenshot.
- **Commits:** `<slug> [Siddanth, Claude] - message`, blank line, details, then the Co-Authored-By trailer. Branch per milestone; push only when asked.

## Parallel tasks (user)
- Send the Nothing SDK permission email (`docs/outreach/2026-10-11-nothing-sdk-permission-email.md`).
- Ask OpenCode about Go keys in non-coding apps (draft not written yet).

## Environment notes
- Phone (4a) Pro on USB, Android 16 / API 36. `android` CLI, adb, scrcpy, mobile-mcp, shellcheck and actionlint are installed.
- Non-interactive shells don't load `~/.zshrc`: use `$HOME/Android/Sdk/platform-tools/adb`. If adb sees no device, check USB debugging is on (the phone can fall back to MTP only).
- Visual companion: superpowers brainstorming `scripts/start-server.sh --project-dir <repo>`. Mockups live in `.superpowers/` (gitignored).

## Architecture
Module level is final. The internal architecture deep-dive comes before M1 (D46).
