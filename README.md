# GlyphWizard

Design, animate and trigger patterns on the Nothing Phone's Glyph Matrix: a fully local, open-source studio for the 13×13 matrix on the Phone (4a) Pro, with an optional bring-your-own-key AI agent.

GlyphWizard is an independent, non-commercial project and is not affiliated with or endorsed by Nothing Technology Ltd.

> **Status:** early development (milestone M0: foundations and hardware spike). Not usable yet.

## Building

Requirements: the Android SDK. Gradle provisions the JDKs it needs: the daemon runs on JDK 25, per `gradle/gradle-daemon-jvm.properties`.

| Flavour | Command | Notes |
|---|---|---|
| `sdkFree` (default) | `./gradlew :app:assembleSdkFreeDebug` | Works offline. No Nothing SDK, so the app can't drive the matrix; everything else works. |
| `full` | `./gradlew :app:assembleFullDebug` | Downloads the Nothing Glyph Matrix SDK at build time from a commit-pinned URL and checks its SHA-256 (needs network). The SDK is never committed, and `full` APKs are not distributed. |

## Updating dependencies

Dependency verification (`gradle/verification-metadata.xml`) is regenerated from the exact task set CI runs:

```bash
bash scripts/ci.sh verification-metadata
```

## Docs

- Design spec: [`docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md`](docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md)
- Decisions: [`docs/research/decisions.md`](docs/research/decisions.md)

## Licence

GPL-3.0-or-later, with an additional permission to link with the Nothing Glyph Matrix SDK (see [`LICENSE`](LICENSE)). The GlyphWizard name and logo are reserved (see [`NOTICE`](NOTICE)).
