# Dependency licences (M0)

**Audited:** 2026-10-13, M0 Task 7 Step 5 (spec §9 supply chain). **Project licence:** GPL-3.0-or-later plus a §7 additional permission for the Nothing Glyph Matrix SDK (D51, `LICENSE`).

**Method:** the `<licenses>` element of each artifact's POM in the Gradle cache, following `<parent>` when absent. Where a POM had no licence, the project's GitHub licence file. Compatibility follows the [FSF licence list](https://www.gnu.org/licenses/license-list.html): Apache-2.0 ([#apache2](https://www.gnu.org/licenses/license-list.html#apache2)), MIT ([#Expat](https://www.gnu.org/licenses/license-list.html#Expat)) and BSD-3-Clause ([#ModifiedBSD](https://www.gnu.org/licenses/license-list.html#ModifiedBSD)) are GPLv3-compatible; EPL-1.0/2.0 ([#EPL](https://www.gnu.org/licenses/license-list.html#EPL), [#EPL2](https://www.gnu.org/licenses/license-list.html#EPL2)) are not.

**Re-run** when dependencies change (Renovate PRs): `./gradlew :app:dependencies --configuration fullReleaseRuntimeClasspath`, then check each new group's POM licence.

## Shipped in the APK

The `:app` full release runtime classpath: 96 Maven artifacts, all Apache-2.0, plus the Nothing SDK.

| Dependency | Version(s) | Licence | GPL-3.0 compatible? |
|---|---|---|---|
| `androidx.*` (85 artifacts: activity, annotation, arch.core, autofill, collection, compose.* incl. material3, concurrent-futures, core, customview-poolingcontainer, emoji2, graphics-path, interpolator, lifecycle, navigationevent, profileinstaller, savedstate, startup-runtime, tracing, versionedparcelable, window) | Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0), activity 1.13.0, core 1.18.0, lifecycle 2.9.4 (transitive) | Apache-2.0 | Y |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.20 | Apache-2.0 | Y |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.11.0 | Apache-2.0 | Y |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` (transitive) | 1.7.3 | Apache-2.0 | Y |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 | Y |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 | Y |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 (parent POM `guava-parent` 26.0-android) | Y |
| `androidx.lifecycle:lifecycle-service` (`:spike` only, throwaway) | 2.11.0 | Apache-2.0 | Y |
| `com.nothing:glyph-matrix-sdk-2.0` (`full` only) | commit `a65d8e1` | Proprietary [Glyph SDK EULA](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md) | N on its own; linking allowed by the §7 additional permission, which grants no rights to the SDK itself. See below |

## Build and test tools (never distributed)

No compatibility requirement, since none of these reach the APK. Recorded so a move onto a runtime classpath is caught.

| Dependency | Version | Licence |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | Apache-2.0 |
| Kotlin Gradle plugin, Compose compiler plugin | 2.4.20 | Apache-2.0 |
| Kover | 0.9.11 | Apache-2.0 |
| detekt | 2.0.0-alpha.6 | Apache-2.0 |
| ktlint-gradle | 14.2.0 | MIT (from GitHub; POM has none) |
| ktlint | 1.8.0 | MIT |
| compose-rules (ktlint) | 0.6.7 | Apache-2.0 |
| JUnit 6 (bom, jupiter, platform-launcher) | 6.1.3 | **EPL-2.0** (GPL-incompatible: test-only) |
| JUnit 4 + hamcrest-core | 4.13.2 / 1.3 | **EPL-1.0** (GPL-incompatible: test-only) / BSD-3-Clause |
| Robolectric | 4.17 | MIT |
| kotlin-test, kotlinx-coroutines-test, Compose ui-test-junit4 / ui-test-manifest | 2.4.20 / 1.11.0 / 1.12.1 | Apache-2.0 |
| foojay-resolver-convention | 1.0.0 | Apache-2.0 (from GitHub; POM has none) |
| Gradle (wrapper jar committed in the repo) | 9.6.1 | Apache-2.0 |

Only the tools themselves were audited, not their own transitive classpaths (never distributed).

## The Nothing Glyph Matrix SDK

[EULA](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md), last changed in upstream commit `adeeb48` (2025-08-13):
- §1: use is licensed "solely for the purpose of integrating and using the Software's functionality within your applications".
- §2.1(a): don't remove Nothing's proprietary notices. The `.aar` currently contains none.
- §2.1(c): no redistribution "except as expressly authorized"; the EULA doesn't say whether an app bundling the `.aar` counts.
- §2.2: no commercial use without written permission.
- §3: the SDK "contains or relies on" AOSP (Apache-2.0) and OpenJDK code (GPL-2.0 with the Classpath Exception). The Classpath Exception allows linking under any terms, so no conflict is expected; what, if anything, is bundled is not stated.

**Handling:** the `.aar` is never committed (D18); it is resolved at build time, pinned by commit and SHA-256. CI builds the `full` flavour but attaches no artifact. No `full` APK is published until Nothing grants written permission (D26).

## Open items

- **Apache-2.0 §4(a), a copy of the licence for recipients:** the release APK keeps only 5 of the androidx `META-INF/androidx/**/LICENSE.txt` files (checked 2026-10-13), so the APK alone doesn't carry one per artifact. The planned About → "open-source notices" screen (atlas, UI31) should list every shipped dependency with its licence text. **M2.**
- **Apache-2.0 §4(d), NOTICE files:** no shipped binary contains a NOTICE file. The kotlinx.coroutines and kotlinx.serialization source repos publish NOTICE texts; whether that binds binary redistribution is a legal judgement [U]. Reproducing both texts in the notices screen is cheap and conservative. **M2.**
