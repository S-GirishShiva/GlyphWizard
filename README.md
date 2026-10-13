# GlyphWizard

Design, animate and trigger patterns on the Nothing Phone's Glyph Matrix: a fully local, open-source studio for the 13×13 matrix on the Phone (4a) Pro, with an optional bring-your-own-key AI agent.

GlyphWizard is an independent, non-commercial project and is not affiliated with or endorsed by Nothing Technology Ltd.

> **Status:** early development (milestone M0: foundations and hardware spike). Not usable yet.

## Building

Requirements: the Android SDK. Gradle provisions the JDKs it needs: the daemon runs on JDK 25, per `gradle/gradle-daemon-jvm.properties`.

| Flavour | Command | Notes |
|---|---|---|
| `sdkFree` (default) | `./gradlew :app:assembleSdkFreeDebug` | Works offline. No Nothing SDK, so the app can't drive the matrix; everything else works. |
| `full` | `./gradlew :app:assembleFullDebug` | Resolves the Nothing Glyph Matrix SDK through Gradle from a commit-pinned GitHub URL, checked by dependency verification (needs network the first time). The SDK is never committed, and `full` APKs are not distributed. |

## Checks

CI (`.github/workflows/ci.yml`) runs three jobs, and each one runs locally with the same command:

| Job | Command |
|---|---|
| checks: ktlint, Lint, tests, coverage gates, assembles | `bash scripts/ci.sh checks && bash scripts/guard-banned.sh && bash scripts/check-tooling-active.sh` |
| detekt, plus its canary | `bash scripts/ci.sh detekt && bash scripts/detekt-canary.sh` |
| full build: SDK fetch, sdkFree APK has no SDK, full-flavour tests | `bash scripts/check-glyph-sdk-fetch.sh && bash scripts/check-sdkfree-apk.sh && bash scripts/ci.sh full && bash scripts/ci.sh detekt-full && bash scripts/check-tooling-active.sh full` |

The Gradle tasks behind each job are listed once, in `scripts/ci-tasks.txt`.

## Updating dependencies

Renovate opens update PRs once a release is at least 14 days old. Dependency verification (`gradle/verification-metadata.xml`) is regenerated from an empty Gradle home, using the exact task set CI runs:

```bash
bash scripts/ci.sh verification-metadata
```

Generation only ever adds entries and keeps hand-written ones, so review the diff. Licences are tracked in [`docs/engineering/licences.md`](docs/engineering/licences.md).

## Docs

- Design spec: [`docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md`](docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md)
- Decisions: [`docs/research/decisions.md`](docs/research/decisions.md)

## Licence

GPL-3.0-or-later, with an additional permission to link with the Nothing Glyph Matrix SDK (see [`LICENSE`](LICENSE)). The GlyphWizard name and logo are reserved (see [`NOTICE`](NOTICE)).
