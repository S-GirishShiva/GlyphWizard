# M0 — Foundations & Hardware Spike Implementation Plan (rev 4, final)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans, **inline in the main session** (user choice, 2026-10-12). Reviews, QA and research run in subagents. Steps use checkbox (`- [ ]`) syntax.
>
> **Rev 4 is the last plan revision** (user, 2026-10-12). Anything found later becomes a `[verify]` resolved during execution, recorded in the task's commit, not a rev 5.
>
> **Project override (D45):** this project does **not** use TDD. Each task runs: **Step 0 Discover** (read the named files, then state what you reuse, refactor and touch) → implement → write comprehensive tests → run → commit. Mandatory reading: `CLAUDE.md`, `docs/engineering/code-guidelines.md`, `docs/engineering/modern-kotlin-patterns.md`.
>
> **Revision note:** rev 2 applied `2026-10-12-m0-plan-review.md` (C1–C2, H1–H12, M1–M19, L1–L10), plus D47 (detekt alpha + mitigations) and D48 (immutable collections). Rev 3 applies `2026-10-12-m0-plan-review-r2.md` (N1–N17, L1–L6) and D49 as amended (plain JUnit 4 on Android modules). Rev 4 applies `2026-10-12-m0-plan-review-r3.md` (R-H1–R-H3, R-M1–R-M9, R-L1–R-L8), the user's direction to use modern Java, and two research reports (JDK/bytecode; verification of r3 claims), summarised under "Review responses (rev 3 → rev 4)".

**Goal:**
- Turn the template into the real multi-module GlyphWizard project: identity, licence, flavours, CI, Glyph SDK bridge, and a durable debug glyph mirror.
- Build a throwaway spike app (patterns plus a NotificationListener) to **measure** every [HW] fact in spec §10.

**Architecture:**
- **Modules:** spec §2.1 modules plus a debug-only `:spike`.
- **SDK isolation:** the Nothing SDK sits behind the consumer-defined `GlyphDisplay` interface in `:glyph-sdk`. A tiny `MatrixService` adapter is the only code that touches `com.nothing.*`. `sdkFree` binds a no-op implementation.
- **Mirror:** `TextGrid` (in `:core`) and `MirroringGlyphDisplay` (in `:glyph-sdk`) are durable.
- **Spike lifetime:** `:spike` is deleted at the end of M1.
- **Scope:** the internal `:core` architecture is *not* designed here (D46). `DeviceProfile` and `TextGrid` are provisional.

**Tech stack:**
- Kotlin 2.4.21; AGP 9.4.1 with built-in Kotlin; Gradle 9.6.x (wrapper present: 9.6.0; take the newest 9.6 patch that passes the pin rule).
- **Java: JDK 25 (LTS) everywhere we control.** See "Java versions" in Global Constraints (user direction 2026-10-12, D5x).
- Compose, with the `org.jetbrains.kotlin.plugin.compose` plugin per Compose module.
- JUnit 6 Jupiter on JVM modules; plain JUnit 4 + Robolectric 4.17 on Android modules, no Vintage engine and no JUnit Platform (D49 as amended per review r2 N4).
- Kover 0.9.11 (not 0.9.10: it reports 0% with the configuration cache, kover #831); ktlint-gradle ≥ 14.1.0 (built-in Kotlin support, #1016; latest 14.2.0) running ktlint 1.8.0, plus compose-rules; detekt `dev.detekt` 2.0.0-alpha.6 pinned (D47); GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md` (§2, §7, §8 M0, §9, §10).

**Shell conventions:**
- `ADB=$HOME/Android/Sdk/platform-tools/adb` (never bare `adb`).
- The machine's default JDK is 27, which Gradle 9.6 cannot run ("JVM 27 and later are not yet supported"; Gradle ≥ 9.8 adds it). The daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`, `toolchainVersion=25`) pick JDK 25 and take precedence over `JAVA_HOME`. If the wrapper *client* itself refuses JDK 27, export `JAVA_HOME` to a JDK 25 (Gradle's provisioned one under `~/.gradle/jdks/`) and record that in `docs/learning/m0.md`.
- `SCRATCH=$(mktemp -d)` for downloads.
- Read logcat with `-d` (dump), never by streaming.

## Global Constraints
- minSdk **35**, targetSdk **36**, compileSdk **37** (DSL `compileSdk { version = release(37) }`).
- **Java versions** (research 2026-10-12. Why 17 existed: the foundations research read AGP's *minimum* JDK as the target):

  | What | Value | Why |
  |---|---|---|
  | Daemon JVM (runs Gradle, AGP, kotlinc, detekt) | **25** (`gradle-daemon-jvm.properties`, unchanged) | Gradle 9.6 runs on 17–26; 25 is LTS; detekt alpha.6 is tested on 25; ktlint-gradle ≥ 14.0.1 supports 25 |
  | JVM toolchain, every JVM module | **25** (`kotlin { jvmToolchain(25) }`) | Gradle ≥ 9.1 supports a toolchain of 25. JUnit 6 and tests run on 25 |
  | `:core`, `:agent` (also shipped inside the APK) | toolchain 25, **API and bytecode level 17**: `compilerOptions { jvmTarget = JvmTarget.JVM_17; freeCompilerArgs.add("-Xjdk-release=17") }` + `java { targetCompatibility = JavaVersion.VERSION_17 }` | Their code runs on ART. Without `-Xjdk-release`, a call to a Java 21+ API compiles, dexes, then crashes on the phone with `NoSuchMethodError`. Google documents Java 17 as the Android 14+ API level. A canary proves the guard (Task 2 Step 9) |
  | `:evals` (never shipped) | toolchain 25, target 25 | no device constraint |
  | Android modules | `compileOptions` **`VERSION_25`**, `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_25 } }`; **no toolchain** (unit tests run on the daemon JDK 25) | AGP 9.4.1's D8/R8 (9.4.24) accepts class files up to v27 and desugars records and indy. The device API is governed by minSdk + Lint `NewApi`, not by this number. **Gate (Task 2 Step 10):** R8 release assemble, a Robolectric test, `koverXmlReport` and `lint` all pass at 25; otherwise drop to 21 and record why |
  | Robolectric | runs on JDK 25 (SDK 36 needs ≥ 21) | add the `--add-opens` set from robolectric.org "getting started" to `testOptions.unitTests.all { it.jvmArgs(…) }` only if a test fails without it [verify] |
  | CI | `actions/setup-java` (v6, SHA-pinned), Temurin **25** | satisfies the daemon criteria without a download; foojay provisions anything else |

  `-Xjdk-release` is a stable compiler option, but the patterns doc (#29) requires a decision for any `-X` flag, so it is logged in D5x together with the JDK choice.
- **Application ID** `io.github.sgirishshiva.glyphwizard`: proposed, **confirmed in Task 1** (permanent).
- **Namespaces:**

  | Module | Namespace |
  |---|---|
  | `:core` | `io.github.sgirishshiva.glyphwizard.core` |
  | `:agent` | `…agent` |
  | `:evals` | `…evals` |
  | `:designsystem` | `…designsystem` |
  | `:glyph-sdk` | `…glyph` |
  | `:spike` | `…spike` |
  | `:app` | `io.github.sgirishshiva.glyphwizard` |
- **Flavour dimension `sdk`:** `sdkFree` (`isDefault = true`) and `full`, in `:glyph-sdk`, `:app` and `:spike`.
- **The Nothing SDK:**
  - never committed; fetched only for `full`
  - pinned in `gradle.properties` (`glyphSdk.url`, `glyphSdk.sha256`) to `https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/a65d8e1d13e796919894e54ce43f93d939cdb34c/glyph-matrix-sdk-2.0.aar`, SHA-256 `be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29`
  - **No APK containing it is ever attached to any release** (D26).
- **SDK facts (verified with javap):**
  - package `com.nothing.ketchum`
  - `GlyphMatrixManager.getInstance(Context)`, `init(Callback)`, `unInit()`
  - `register(String): Boolean`
  - `setAppMatrixFrame(IntArray)` and `closeAppMatrix()` throw `GlyphException`
  - `setGlyphMatrixTimeout(Boolean)`
  - `Glyph.DEVICE_25111p`
  - permission `com.nothing.ketchum.permission.ENABLE`
- **Device:** 13×13 grid, 137 LEDs, rows `[5,9,11,11,13,13,13,13,13,11,11,9,5]`.
- **Coverage (Kover):** `:core`/`:agent` **100%** line; `:glyph-sdk` (sdkFreeDebug **and** fullDebug, `SdkMatrixService` excluded), `:designsystem` (debug) and `:app` (sdkFreeDebug) **≥ 80%**. `:spike` is excluded from the gates (throwaway; user confirms in Task 1).
- **Commits:** `<slug> [Siddanth, Claude] - message`, blank line, details, `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. **Push or tag only after asking the user.**
- **Modern Kotlin only** (`modern-kotlin-patterns.md`): constructor DI, errors as sealed values, immutable state, minimal *why*-only comments.

## Review Focus
1. **Unreachable SDK URL** (offline, firewalled, the URL moved): the `full` fetch fails with `Could not download the Glyph SDK from <url>…`; `sdkFree` builds offline and never fetches. *Owner: Task 3, `scripts/check-glyph-sdk-fetch.sh` cases 3–4.*
2. **SDK changed upstream or tampered:** the build fails with `Glyph SDK SHA-256 mismatch` and leaves no file. *Owner: Task 3 script case 2.*
3. **Glyph service missing** (non-Nothing phone, binding never answers, `register` false, a `GlyphException`): `connect()`/`show()` return `Err` within the timeout. No hang, no crash. *Owner: Task 4 `NothingGlyphDisplayTest` (fake `MatrixService`, virtual time).*
4. **Frame length 137 vs 169**, plus any transform: the encoder handles both layouts, and the default comes from measurement. *Owner: Task 4 `MatrixFrameEncoderTest`; Task 6 Step 9 default test.*
5. **Leaving or killing the app mid-pattern:** `stop()` cancels the loop and releases the matrix; a force-stop is recorded as a [HW] fact. *Owner: Task 5 `GlyphSessionTest`; Task 6 Step 8.*
6. **adb hooks reachable:** a DUMP-guarded receiver accepts shell broadcasts. *Owner: Task 5 Step 7 manual check (one broadcast → one `GlyphMirror` line), `SpikeCommandTest`.*
7. **Measuring with the screen off or the app backgrounded:** the session and receiver live in the `specialUse` `SpikePatternService`, not the Activity. *Owner: Task 5 Step 4; Task 6 Step 7.*
8. **detekt alpha silently stops enforcing:** the canary must report `UnsafeCallOnNullableType`, `GlobalCoroutineUsage` and `ForbiddenMethodCall`, and `run-detekt.sh` must tell findings from crashes. *Owner: Task 2 Step 7 `scripts/detekt-canary.sh`.*
9. **ktlint or Kover silently checks nothing** (review r3 R-H3): ktlint-gradle < 14.1 registers no Android source-set tasks under built-in Kotlin, and Kover's verifier skips a bound when there are 0 lines (`minBound(80)` passes on nothing). *Owner: Task 2 Step 7 `scripts/check-tooling-active.sh`.*
10. **A JDK API that Android lacks** sneaks into `:core`/`:agent`: `-Xjdk-release=17` makes it a compile error. *Owner: Task 2 Step 9 JDK-API canary.*

## File structure (end of M0)
```
.editorconfig  renovate.json  LICENSE  NOTICE  README.md
settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  gradle/verification-metadata.xml
config/detekt/detekt.yml
build-logic/  settings.gradle.kts  build.gradle.kts
  src/main/kotlin/.../buildlogic/FetchGlyphSdk.kt    pinned, hash-checked SDK download task
  src/main/kotlin/.../buildlogic/GlyphSdkFetchPlugin.kt  plugin glyphwizard.glyph-sdk-fetch (task + glyphSdkAar + glyphSdk configuration)
detekt-canary/  build.gradle.kts  src/main/kotlin/Canary.kt   JVM module, only with -PwithCanary; violations detekt MUST report (D47)
scripts/check-glyph-sdk-fetch.sh  scripts/guard-banned.sh  scripts/check-sdkfree-apk.sh
scripts/run-detekt.sh  scripts/detekt-canary.sh  scripts/check-tooling-active.sh  scripts/ci.sh  scripts/ci-tasks.txt
core/   DeviceProfile.kt (provisional)  TextGrid.kt (provisional, text-grid codec) + tests
agent/  AgentModule.kt (placeholder entry: `object AgentModule { const val NAME = "agent" }`) + test
evals/  Main.kt (prints usage) + test
designsystem/  GlyphTheme.kt (minimal, empty token set) + Robolectric test
glyph-sdk/
  build.gradle.kts                                   flavours; applies glyphwizard.glyph-sdk-fetch; fullCompileOnly SDK
  src/main/kotlin/.../glyph/GlyphDisplay.kt          GlyphDisplay, GlyphResult, GlyphError
  src/main/kotlin/.../glyph/MatrixFrameEncoder.kt
  src/main/kotlin/.../glyph/MirroringGlyphDisplay.kt
  src/sdkFree/kotlin/.../glyph/GlyphDisplays.kt      -> NoGlyphDisplay
  src/full/kotlin/.../glyph/GlyphDisplays.kt         -> NothingGlyphDisplay(SdkMatrixService)
  src/full/kotlin/.../glyph/MatrixService.kt         consumer interface + SdkMatrixService (only com.nothing.* user)
  src/full/kotlin/.../glyph/NothingGlyphDisplay.kt
  src/full/AndroidManifest.xml                       ENABLE permission
  src/test/  src/testSdkFree/  src/testFull/
app/    GlyphWizardApplication.kt  AppContainer.kt  MainActivity.kt + Robolectric tests
        src/debug/.../DebugWrap.kt (mirror)  src/release/.../DebugWrap.kt (identity)
spike/  Patterns.kt  GlyphSession.kt  SpikeCommand.kt  SpikePatternService.kt (specialUse FGS + DUMP-guarded receiver)
        SpikeActivity.kt  SpikeListener.kt  SpikeShortService.kt + tests
.github/workflows/ci.yml
docs/research/hardware-spike-results.md  docs/engineering/threat-model.md  docs/engineering/licences.md  docs/learning/m0.md
```

---
### Task 1: Decisions, identity, licence, branch

**Files:** Create `LICENSE`, `NOTICE`, `README.md`; modify `docs/research/decisions.md`.

- [ ] **Step 0: Ask the user (blocking).** Ask these one at a time and log the answers as D50+:
  1. **Application ID:** `io.github.sgirishshiva.glyphwizard`? (Permanent.)
  2. **Licence:** GPL-3.0-or-later plus a Nothing SDK linking exception. *"GPL lets others sell or modify our code; only the name and logo are reserved through NOTICE. 'Non-commercial' applies to the Nothing SDK, not our code. OK?"* Update D18's "licence TBD".
  3. **Branching:** merge `design/v1-spec` into `master` first, then branch `m0-foundations` from `master`?
  4. **`:spike` exemptions:** exempt the throwaway `:spike` from the UI rules (`:designsystem`), the coverage gates and the hard-coded-text lint? It's deleted at the end of M1.

  If the ID changes, replace it throughout this plan before continuing.
- [ ] **Step 1: Branch** per answer 3, e.g. `git switch master && git merge --ff-only design/v1-spec && git switch -c m0-foundations`.
- [ ] **Step 2: `LICENSE`.** Run `curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE`, then append this section verbatim:
  ```
  ADDITIONAL PERMISSION UNDER GNU GPL VERSION 3 SECTION 7

  If you modify this Program, or any covered work, by linking or combining it
  with the Nothing Glyph Matrix SDK (or a modified version of that library),
  containing parts covered by the terms of the Glyph SDK End User License
  Agreement, the licensors of this Program grant you additional permission to
  convey the resulting work. This permission does not grant any rights to the
  Glyph SDK itself, whose use remains subject to Nothing's EULA, including its
  prohibition on commercial use.
  ```
- [ ] **Step 3: `NOTICE`:**
  ```
  GlyphWizard
  Copyright (C) 2026 Siddanth and contributors

  The name "GlyphWizard" and its logo are not licensed under the GPL and may
  not be used for derived works without permission.

  GlyphWizard is an independent, non-commercial project and is not affiliated
  with or endorsed by Nothing Technology Ltd. "Nothing" and "Glyph" are
  trademarks of their owner.
  ```
- [ ] **Step 4: `README.md`.** Cover:
  - what the app is, plus the not-affiliated line
  - building: `./gradlew :app:assembleSdkFreeDebug` (offline-capable); `full` fetches the SDK (needs network)
  - regenerating dependency verification: `bash scripts/ci.sh verification-metadata` (Task 7)
  - a link to the spec, and the licence
- [ ] **Step 5: Commit.** Slug: `m0-identity`.

### Task 2: Multi-module scaffold, toolchain and quality wiring

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`
- Delete: `app/src/main/res/values-night/themes.xml`, the example tests, `app/src/main/res/drawable*`, and the template's `appcompat`/`material` usages
- Move: `app/src/main/java/com/example/glyphwizard` → `app/src/main/kotlin/io/github/sgirishshiva/glyphwizard`
- Create: the module dirs, `.editorconfig`, `config/detekt/detekt.yml`, `detekt-canary/` (module), `scripts/guard-banned.sh`, `scripts/run-detekt.sh`, `scripts/detekt-canary.sh`, `scripts/check-tooling-active.sh`, `docs/learning/m0.md`
- Modify (docs, separate commit first, boy-scout): `docs/research/decisions.md` (D47 wording: SARIF classification, not exit codes; new D5x Java versions + `-Xjdk-release`), `docs/engineering/modern-kotlin-patterns.md` §1.1 (AGP 9.4.1; JUnit row: no Vintage per D49; JDK row), `docs/superpowers/specs/…-design.md` §2 Build row ("JDK 17 toolchain" → "JDK 25; see D5x") (review r3 R-L7, R-L8)

**Interfaces (produces):**
- module paths `:core :agent :evals :designsystem :glyph-sdk :app :spike`
- catalog aliases listed in Step 2
- `DeviceProfile` (provisional)
- `TextGrid.encode(profile, brightness): String` (provisional)
- `AppContainer`

- [ ] **Step 0: Discover.**
  - **Read:** `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` (configuration cache on), `gradle/gradle-daemon-jvm.properties`.
  - **First command:** `./gradlew --version`. It must show the daemon on JDK 25 (see Shell conventions if the client refuses JDK 27).
  - **Note:** AGP 9.4.1 and the new `compileSdk { version = release(37) }` DSL. Keep that DSL. **Keep** the template's `buildTypes.release { optimization { enable = true; packageScope = … } }` block (R8 on; spec §9 tests the minified release, and `check-sdkfree-apk.sh` inspects it). Change the template's `targetSdk = 37` to 36 (D31) (review r3 R-L4).
  - **AGP 9 default:** `android.onlyEnableUnitTestForTheTestedBuildType=true`, so release unit-test tasks don't exist. Add `android.onlyEnableUnitTestForTheTestedBuildType=false` to `gradle.properties`, because `:app:testSdkFreeReleaseUnitTest` proves the release has no mirror (Task 4). [verify] that 9.4.1 still accepts the flag: `./gradlew :app:tasks --all > "$SCRATCH/t.txt"; grep -c testSdkFreeReleaseUnitTest "$SCRATCH/t.txt"` ≥ 1. If not, drop the `testRelease` test, record it, and rely on `check-sdkfree-apk.sh` + review for the release wiring. Only `:app` runs release unit tests in CI; Compose rule tests elsewhere run in debug, where `ui-test-manifest` is present.
  - **Docs to read:** `android docs search "AGP 9 built-in Kotlin"` and `android docs search "Compose compiler Gradle plugin"`.
  - **State:** what you reuse (the plugin block and the repositories).
- [ ] **Step 1: Pin versions** (stable only, at least 14 days old). For each row, take the newest version in Maven `<versions>` with no `-alpha|-beta|-rc|-M|-dev` suffix whose upload date on search.maven.org / maven.google.com / plugins.gradle.org is at least 14 days old. Record the source URL and date in a comment above each entry. **Exceptions:** detekt `dev.detekt` 2.0.0-alpha.6 (D47) and `kotlinx-collections-immutable` (D48, not used in M0, so not pinned yet). **Kover 0.9.11** was published 2026-09-29, so it passes the rule from 2026-10-13. If pinning earlier, log the exception in D5x: the only older candidate, 0.9.10, reports 0% under the configuration cache (#831). Log the Java choice as **D5x** now: the "Java versions" table + `-Xjdk-release=17` for `:core`/`:agent` (patterns #29 needs a decision for `-X` flags).

  | alias | coordinates | constraint |
  |---|---|---|
  | `agp` / `android-application`, `android-library` | `com.android.application` / `com.android.library` | keep 9.4.1 unless a newer stable passes the rule |
  | `kotlin` → `kotlin-jvm`, `kotlin-compose` | `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.compose` | **2.4.21** (≥ 2.2.10 required by AGP 9) |
  | `kover` | `org.jetbrains.kotlinx.kover` | **0.9.11** (AGP 9 built-in Kotlin since 0.9.5, #787; 0.9.10 broken under CC, #831; watch #815) |
  | `detekt` | `dev.detekt` | **2.0.0-alpha.6**, exact (D47) |
  | `ktlint-gradle` | `org.jlleitschuh.gradle.ktlint` | stable, **≥ 14.1.0** (built-in Kotlin, #1016) |
  | `ktlint` | `com.pinterest.ktlint` (engine, set via `ktlint { version = libs.versions.ktlint }`) | **1.8.0** (the plugin's default, 1.5.0, is stale) |
  | `compose-rules-ktlint` | `io.nlopez.compose.rules:ktlint` | stable |
  | `compose-bom` | `androidx.compose:compose-bom` | stable (verify: the pattern doc marked it [U]) |
  | `activity-compose`, `lifecycle-runtime-ktx`, `lifecycle-service` | androidx | stable (`lifecycle-*` share one version) |
  | `coroutines-core`, `coroutines-test`, `coroutines-android` | `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.11.0 |
  | `junit-bom`, `junit-jupiter`, `junit-platform-launcher` | `org.junit:*` | JUnit **6.x** stable (D49); jupiter and launcher take no version (BOM). JVM modules only |
  | `junit4` | `junit:junit` | 4.13.2 |
  | `robolectric` | `org.robolectric:robolectric` | **4.17** or newer stable (SDK 36/37; needs JDK ≥ 21 for SDK 36) |
  | `compose-ui-test-junit4`, `compose-ui-test-manifest` | via BOM | — (manifest: `debugImplementation`, needed by `createComposeRule()`; review r3 R-H2) |
  | `kotlin-test` | `org.jetbrains.kotlin:kotlin-test` | `version.ref = kotlin` |
- [ ] **Step 2: Root `build.gradle.kts`.** Apply `false` for `android-application`, `android-library`, `kotlin-jvm`, `kotlin-compose`, `kover`, `detekt` and `ktlint-gradle`. No `subprojects {}`/`allprojects {}` (cross-project configuration breaks isolated projects; review r2 L3): every module applies `alias(libs.plugins.ktlint.gradle)` in its own `plugins {}` plus `ktlint { version = libs.versions.ktlint.get() }`, and Compose modules add `ktlintRuleset(libs.compose.rules.ktlint)` to their `dependencies {}` (the type-safe accessor exists there because the plugin is applied in that script). `.editorconfig`:
  ```
  root = true
  [*.{kt,kts}]
  max_line_length = 120
  ktlint_code_style = ktlint_official
  ```
- [ ] **Step 3: `settings.gradle.kts`.** Add:
  ```kotlin
  include(":core", ":agent", ":evals", ":designsystem", ":glyph-sdk", ":app", ":spike")
  if (providers.gradleProperty("withCanary").isPresent) include(":detekt-canary")
  ```
  The canary holds deliberate violations, so normal builds never see it (Step 7).
- [ ] **Step 4: JVM modules** `core`, `agent`, `evals`:
  ```kotlin
  plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kover); alias(libs.plugins.detekt); alias(libs.plugins.ktlint.gradle) }
  kotlin { jvmToolchain(25) }
  detekt { buildUponDefaultConfig = true }
  ktlint { version = libs.versions.ktlint.get() }
  dependencies {
      testImplementation(platform(libs.junit.bom))
      testImplementation(libs.junit.jupiter)
      testRuntimeOnly(libs.junit.platform.launcher)
      testImplementation(libs.kotlin.test)
  }
  tasks.test { useJUnitPlatform() }
  kover { reports { verify { rule { minBound(100) } } } }   // core, agent only
  ```
  `:core` and `:agent` also get the Android API guard (Global Constraints, Java versions):
  ```kotlin
  import org.jetbrains.kotlin.gradle.dsl.JvmTarget   // top of the script; not a Kotlin DSL default import

  java { targetCompatibility = JavaVersion.VERSION_17 }
  tasks.withType<JavaCompile>().configureEach { options.release = 17 }   // also caps any future Java sources
  kotlin {
      compilerOptions {
          jvmTarget = JvmTarget.JVM_17
          // runs on ART: only Java 17-level JDK APIs exist there (D5x)
          freeCompilerArgs.add("-Xjdk-release=17")
      }
  }
  ```
  [verify] that Kotlin accepts `jvmTarget` together with `-Xjdk-release` with no "inconsistent JVM target" error, and that `:app:assembleSdkFreeDebug` resolves `:core` (the `org.gradle.jvm.version` attribute is 17 ≤ the consumer's). If Kotlin rejects the pair, drop the explicit `jvmTarget` (the flag implies it) and record that.
  - `agent` depends on `:core`.
  - `evals` adds the `application` plugin with `application { mainClass = "io.github.sgirishshiva.glyphwizard.evals.MainKt" }`, and `fun main() = println("glyphwizard-evals: no suites yet (M5)")`.
  - **`core`** gets `DeviceProfile` (provisional, D46):
  ```kotlin
  package io.github.sgirishshiva.glyphwizard.core

  data class DeviceProfile(val id: String, val gridSize: Int, val rowWidths: List<Int>) {
      val ledCount: Int = rowWidths.sum()

      fun isLed(x: Int, y: Int): Boolean {
          val width = rowWidths.getOrNull(y) ?: return false
          val inset = (gridSize - width) / 2
          return x in inset..<inset + width
      }

      fun ledPositions(): List<Pair<Int, Int>> =
          buildList { for (y in 0..<gridSize) for (x in 0..<gridSize) if (isLed(x, y)) add(x to y) }

      companion object {
          val Phone4aPro = DeviceProfile("25111p", 13, listOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5))
      }
  }
  ```
  - **`core`** also gets `TextGrid` (provisional): the spec's text-grid codec, also used by the mirror. Its glyph set is `·` for 0, then 9 buckets `.:-=+*#%@` for 1–255, with a space outside the mask.
  ```kotlin
  object TextGrid {
      private const val LEVELS = ".:-=+*#%@"
      fun encode(profile: DeviceProfile, brightness: IntArray): String {
          require(brightness.size == profile.ledCount) { "expected ${profile.ledCount} values, got ${brightness.size}" }
          val values = brightness.iterator()
          return (0..<profile.gridSize).joinToString("\n") { y ->
              (0..<profile.gridSize).joinToString("") { x -> if (profile.isLed(x, y)) glyph(values.next()) else " " }
          }
      }
      private fun glyph(value: Int): String = when (val v = value.coerceIn(0, 255)) {
          0 -> "·"
          else -> LEVELS[(v - 1) * LEVELS.length / 255].toString()   // 1..255 → 0..8, never past lastIndex
      }
  }
  ```
- [ ] **Step 5: Android modules.** `designsystem`, `glyph-sdk`, `app` and `spike` each use `alias(libs.plugins.android.library)` (or `application`) plus `kover`, `ktlint-gradle` and `detekt` (with `detekt { buildUponDefaultConfig = true }`), and Compose modules add `alias(libs.plugins.kotlin.compose)`. Set the namespace from the Global Constraints, the `compileSdk { version = release(37) }` DSL, `minSdk = 35`, `compileOptions` with `JavaVersion.VERSION_25` (source and target), `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_25 } }` (with `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` at the top of the script), **no toolchain** (Robolectric runs on the daemon JDK 25; review r3 R-H1), `ktlint { version = libs.versions.ktlint.get() }`, and `testOptions.unitTests.isIncludeAndroidResources = true`. The plain `detekt` task only scans `src/{main,test}/{java,kotlin}`, so flavour and build-type sets are added explicitly (the `src/<set>/kotlin` dirs that exist in the module, e.g. `detekt { source.from("src/sdkFree/kotlin", "src/full/kotlin", "src/debug/kotlin", "src/release/kotlin", "src/testSdkFree/kotlin", "src/testFull/kotlin") }`). Flavours, in `glyph-sdk`, `app` and `spike`:
  ```kotlin
  flavorDimensions += "sdk"
  productFlavors {
      create("sdkFree") { dimension = "sdk"; isDefault = true }
      create("full") { dimension = "sdk" }
  }
  ```
  Dependencies:
  - `glyph-sdk` → `api(project(":core"))`
  - `app` → `implementation(project(":core"))`, `implementation(project(":designsystem"))`, `implementation(project(":glyph-sdk"))`
  - `spike` → `implementation(project(":core"))`, `implementation(project(":glyph-sdk"))`
  - Compose: `implementation(platform(libs.compose.bom))` plus ui, material3 and `activity-compose`
  - tests: `testImplementation(libs.junit4)`, `testImplementation(libs.robolectric)`, `testImplementation(libs.kotlin.test)`, `testImplementation(libs.compose.ui.test.junit4)` + `debugImplementation(libs.compose.ui.test.manifest)` (Compose modules whose tests use `createComposeRule()`: declares the `ComponentActivity` it launches), `testImplementation(libs.coroutines.test)` (`:glyph-sdk`, `:spike`). AGP runs these with its JUnit 4 runner directly: no `useJUnitPlatform()`, no Vintage (JUnit 6 deprecated it), no JUnit BOM on Android modules (D49).

  Kover, per Android module:
  ```kotlin
  kover { reports { variant("sdkFreeDebug") { verify { rule { minBound(80) } } } } }   // designsystem: variant("debug")
  ```
  `:glyph-sdk` also gates the `full` code, where the most failure-prone logic (`NothingGlyphDisplay`) lives (review r2 N12):
  ```kotlin
  kover {
      reports {
          filters { excludes { classes("*.SdkMatrixService") } }   // thin SDK adapter, needs a real device
          variant("sdkFreeDebug") { verify { rule { minBound(80) } } }
          variant("fullDebug") { verify { rule { minBound(80) } } }
      }
  }
  ```
  `:spike` gets no Kover rule (Task 1 answer 4).
  **Known repetition:** each module repeats about 4 lines of quality wiring (plugins, `detekt {}`, `ktlint {}`, `kover {}`). A convention plugin in `build-logic/` would remove it, but it pulls AGP/KGP/detekt/ktlint onto `build-logic`'s classpath. Not in M0: it's an explicit agenda item for the D46 architecture deep-dive, so the duplication is recorded, not ignored. `applicationId`: `:app` = `io.github.sgirishshiva.glyphwizard`; `:spike` = `io.github.sgirishshiva.glyphwizard.spike`.
- [ ] **Step 6: `:app` shell** (replaces Views; constructor DI root, D44):
  ```kotlin
  class GlyphWizardApplication : Application() {
      val container: AppContainer by lazy { AppContainer(this) }
  }

  class AppContainer(context: Context) {
      val glyphDisplay: GlyphDisplay = wrapForBuild(
          GlyphDisplays.create(context.applicationContext, MatrixFrameEncoder.forPhone4aPro()),
          DeviceProfile.Phone4aPro,
      )
  }

  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          enableEdgeToEdge()
          super.onCreate(savedInstanceState)
          setContent { GlyphTheme { Text(stringResource(R.string.app_name)) } }
      }
  }
  ```
  - `by lazy`, not `lateinit` (patterns #2, detekt `LateinitUsage`): Android attaches the `Application` before any component can reach `container`, so the first access always sees a ready context.
  - `GlyphDisplays`/`MatrixFrameEncoder`/`wrapForBuild` come from Task 4. Until Task 4 lands, `AppContainer` is an empty class and the `glyphDisplay` property is added in Task 4 Step 6. Set the theme parent `Theme.GlyphWizard` → `android:Theme.Material.NoActionBar`. In `designsystem`, add `@Composable fun GlyphTheme(content: @Composable () -> Unit) = MaterialTheme(content = content)`, a minimal placeholder that M2 fills from the design-log tokens.
- [ ] **Step 7: detekt and guards (D47).**
  - **Wiring:** every module (JVM and Android) applies `alias(libs.plugins.detekt)` and sets `detekt { buildUponDefaultConfig = true }`. The plugin reads `config/detekt/detekt.yml` from the root project by default [verify: `./gradlew :core:detekt --info | grep -m1 detekt.yml`; if absent, add `config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))`]. Every rule's default severity is `error`, so any finding fails the task (the canary proves this).
  - **Two kinds of task** (detekt docs: rules that need type resolution "will not run" without it):
    - `detekt`: plain, every module, no type resolution.
    - `detektMain`: type-resolved, on the JVM modules (`:core`, `:agent`, `:evals`, `:detekt-canary`). This is where `UnsafeCallOnNullableType` and `ForbiddenMethodCall` actually run.
    - Android variant tasks: alpha.6 registers type-resolved `detekt<Variant>` tasks under AGP ≥ 9 built-in Kotlin (`detektSdkFreeDebug`, `detektFullDebug`, `detektDebug`; verified in `DetektPlugin.kt`). They run in CI (Task 7 `detekt` line). detekt #9402 (open) means type resolution may not see generated `R`/`BuildConfig`, which causes false positives. [verify] `./gradlew :glyph-sdk:detektSdkFreeDebug :app:detektSdkFreeDebug --info > "$SCRATCH/d.txt" 2>&1; grep -ci 'compiler errors' "$SCRATCH/d.txt"`. If a variant task reports #9402 false positives, remove *that* task from `ci-tasks.txt`, record it in the commit and `decisions.md` D47, and rely on `guard-banned.sh`. Never use baselines to hide them.
  - **`config/detekt/detekt.yml`** (activates every detekt row of the `modern-kotlin-patterns.md` §3 table; `config.validation` turns a misplaced key into a crash):
  ```yaml
  config:
    validation: true

  coroutines:
    GlobalCoroutineUsage:
      active: true
    InjectDispatcher:
      active: true
      dispatcherNames: ['IO', 'Default', 'Unconfined']
    SleepInsteadOfDelay:
      active: true
    SuspendFunSwallowedCancellation:
      active: true
    SuspendFunWithFlowReturnType:
      active: true
    SuspendFunInFinallySection:
      active: true

  potential-bugs:
    UnsafeCallOnNullableType:
      active: true
    MapGetWithNotNullAssertionOperator:
      active: true
    UnsafeCast:
      active: true
    CastNullableToNonNullableType:
      active: true
    DoubleMutabilityForCollection:
      active: true
    LateinitUsage:
      active: true
      excludes: ['**/test/**', '**/testSdkFree/**', '**/testFull/**', '**/androidTest/**']

  style:
    ForbiddenMethodCall:
      active: true
      methods:
        - value: 'kotlinx.coroutines.runBlocking'
          reason: 'suspend or use an owned scope (patterns #6)'
        - value: 'java.lang.Thread.sleep'
          reason: 'use delay (patterns #7)'
        - value: 'java.lang.System.currentTimeMillis'
          reason: 'inject a Clock or TimeSource (patterns #14)'
        - value: 'android.app.Activity.findViewById'
          reason: 'Compose only (patterns #16)'
        - value: 'androidx.activity.ComponentActivity.onBackPressed'
          reason: 'BackHandler / Nav3 (patterns #22)'
        - value: 'androidx.compose.runtime.collectAsState'
          reason: 'use collectAsStateWithLifecycle (patterns #30)'
    ForbiddenImport:
      active: true
      forbiddenImports:
        - value: 'androidx.lifecycle.LiveData'
          reason: 'StateFlow (patterns #11)'
        - value: 'androidx.lifecycle.MutableLiveData'
          reason: 'StateFlow (patterns #11)'
        - value: 'io.reactivex.*'
          reason: 'coroutines/Flow (patterns #12)'
        - value: 'com.google.gson.*'
          reason: 'kotlinx.serialization (patterns #13)'
        - value: 'com.squareup.moshi.*'
          reason: 'kotlinx.serialization (patterns #13)'
        - value: 'org.json.*'
          reason: 'kotlinx.serialization (patterns #13)'
        - value: 'java.util.Date'
          reason: 'kotlin.time / Clock (patterns #14)'
        - value: 'java.util.Calendar'
          reason: 'kotlin.time / Clock (patterns #14)'
        - value: 'java.text.SimpleDateFormat'
          reason: 'kotlin.time / Clock (patterns #14)'
        - value: 'java.util.UUID'
          reason: 'kotlin.uuid.Uuid (patterns #15)'
        - value: 'androidx.fragment.*'
          reason: 'Compose (patterns #16)'
        - value: 'androidx.appcompat.*'
          reason: 'ComponentActivity + Compose (patterns #16)'
        - value: 'android.os.AsyncTask'
          reason: 'coroutines (patterns #18)'
        - value: 'android.os.Handler'
          reason: 'coroutines (patterns #18)'
        - value: 'java.util.concurrent.Executors'
          reason: 'coroutines (patterns #18)'
        - value: 'android.support.*'
          reason: 'AndroidX (patterns #19)'
        - value: 'android.content.SharedPreferences'
          reason: 'DataStore (patterns #21)'
    ForbiddenAnnotation:
      active: true
      annotations:
        - value: 'kotlin.OptIn'
          reason: 'needs a decision entry; suppress per file citing it (patterns #29)'
    DataClassShouldBeImmutable:
      active: true
    VarCouldBeVal:
      active: true
    UtilityClassWithPublicConstructor:
      active: true
    RangeUntilInsteadOfRangeTo:
      active: true
  ```
  [verify] the rule-set placement against the pinned alpha's `default-detekt-config.yml` (`unzip -p ~/.gradle/caches/modules-2/files-2.1/dev.detekt/detekt-core/<v>/*/detekt-core-<v>.jar default-detekt-config.yml | grep -n -e LateinitUsage -e ForbiddenMethodCall`); with `validation: true` a wrong placement makes detekt crash, which `run-detekt.sh` reports as a crash.
  - **`:detekt-canary`** (a JVM module, included only with `-PwithCanary`; see Step 3):
    - `detekt-canary/build.gradle.kts`:
    ```kotlin
    plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.detekt) }
    kotlin { jvmToolchain(25) }
    dependencies { implementation(libs.coroutines.core) }
    detekt {
        buildUponDefaultConfig = true
        ignoreFailures = providers.gradleProperty("canary.ignoreFailures").isPresent
    }
    ```
    - `detekt-canary/src/main/kotlin/Canary.kt`, exactly one violation per expected ID:
    ```kotlin
    package canary

    import kotlinx.coroutines.GlobalScope
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.runBlocking

    fun unsafe(s: String?): Int = s!!.length                 // UnsafeCallOnNullableType
    fun global(): Job = GlobalScope.launch { }               // GlobalCoroutineUsage
    fun blocking(): Unit = runBlocking { }                   // ForbiddenMethodCall
    ```
    These are the only comments of their kind allowed: they name what each line must trigger.
    - The mutable `object` ban (patterns #26) has no detekt rule, so it is not in the canary. `guard-banned.sh` checks it (below), and review remains the backstop.
  - **`scripts/run-detekt.sh`** (review r2 N6: Gradle exits 1 for findings and crashes alike, so classify by reports, not exit code):
  ```bash
  #!/usr/bin/env bash
  # Gradle exits 1 for detekt findings and for crashes alike, so classify by the SARIF reports.
  set -euo pipefail
  find . -path ./build-logic -prune -o -path '*/build/reports/detekt' -type d -prune -exec rm -rf {} +
  if ./gradlew "$@"; then echo "detekt: clean"; exit 0; fi
  mapfile -t reports < <(find . -path '*/build/reports/detekt/*.sarif' -type f)
  if ((${#reports[@]})) && grep -q '"ruleId"' "${reports[@]}"; then
    echo "detekt findings: ${reports[*]}" >&2
    exit 1
  fi
  echo "detekt crashed: tool error, not findings" >&2
  exit 2
  ```
  SARIF is on by default [verify: `ls core/build/reports/detekt/` after `./gradlew :core:detekt` lists a `.sarif`; if not, enable `reports { sarif.required = true }` on the `Detekt` tasks of each module].
  - **`scripts/detekt-canary.sh`** (tests both `run-detekt.sh` paths, then the IDs):
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  fail() { echo "FAIL: $*" >&2; exit 1; }
  rc=0; bash scripts/run-detekt.sh :core:detektNoSuchTask >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 2 ] || fail "crash path: expected exit 2, got $rc"
  rc=0; bash scripts/ci.sh canary || rc=$?
  [ "$rc" -eq 1 ] || fail "findings path: expected exit 1, got $rc"
  mapfile -t reports < <(find detekt-canary/build/reports/detekt -name '*.sarif' -type f)
  ((${#reports[@]})) || fail "no canary SARIF report"
  for id in UnsafeCallOnNullableType GlobalCoroutineUsage ForbiddenMethodCall; do
    grep -q "$id" "${reports[@]}" || fail "detekt no longer reports $id"
  done
  echo OK
  ```
  (`scripts/ci.sh` and `scripts/ci-tasks.txt` are defined in Task 7 Step 2; until then run `bash scripts/run-detekt.sh -PwithCanary :detekt-canary:detektMain` in its place.)
  - **`scripts/guard-banned.sh`** (production sources only: every `src/<set>` except test sets; `detekt-canary/` and `build-logic/` excluded). Every check writes to a file before `grep`, so `pipefail` can't hide a match:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  hits=$(mktemp)
  mapfile -t files < <(find . \( -path ./detekt-canary -o -path ./build-logic -o -path '*/build' -o -path '*/src/test*' -o -path '*/src/androidTest*' \) -prune -o -path '*/src/*' -name '*.kt' -print)
  ((${#files[@]})) || { echo "FAIL: no sources found" >&2; exit 1; }
  grep -nHE '!!|\bGlobalScope\b|\brunBlocking\b|\bLiveData\b|\bAsyncTask\b|\bfindViewById\b|\bThread\.sleep\b' "${files[@]}" >>"$hits" || true
  mapfile -t builds < <(find . \( -path ./build-logic -o -path '*/build' \) -prune -o -name '*.gradle.kts' -print)
  grep -nH 'kapt' "${builds[@]}" >>"$hits" || true
  # mutable state in an `object` / `companion object` body (patterns #26); heuristic, review is the backstop
  awk '
    /^[[:space:]]*([a-z]+[[:space:]]+)*object([[:space:]]|\{)/ && /\{/ { inobj = 1; depth = 0 }
    inobj {
      if (depth == 1 && /(^|[[:space:]])var[[:space:]]/) print FILENAME ":" FNR ": var in object"
      depth += gsub(/\{/, "{") - gsub(/\}/, "}")
      if (depth <= 0) inobj = 0
    }' "${files[@]}" >>"$hits"
  if [ -s "$hits" ]; then cat "$hits" >&2; echo "FAIL: banned constructs" >&2; exit 1; fi
  echo OK
  ```
  Plus the remaining custom (**C**) rows of the patterns enforcement table (review r3 R-M8), appended before the `if [ -s "$hits" ]` line:
  ```bash
  # #17 no XML layouts
  find . \( -path ./build-logic -o -path '*/build' \) -prune -o -type d -path '*/src/*/res/layout*' -print >>"$hits"
  # #12, #20 banned plugins and dependencies (kapt is above)
  grep -nHE 'legacy-kapt|com\.google\.dagger|io\.insert-koin|io\.reactivex' "${builds[@]}" gradle/libs.versions.toml >>"$hits" || true
  ```
  #25 (`else` on a sealed `when`) needs a custom detekt rule: deferred to M1, with review as the backstop.
  Test it (after writing it), one case at a time, deleting each afterwards:
  - a temporary `core/src/main/kotlin/…/Tmp.kt` with `object X {\n    var n = 0\n}` and a `runBlocking {}` → fails listing both lines
  - `mkdir -p app/src/main/res/layout` → fails
  - a `// io.reactivex` line in `core/build.gradle.kts` → fails
  - all removed → `OK`
  - **Duplicate bans in Lint/ktlint** where the patterns doc lists equivalents (L `ObsoleteSdkInt`, the compose-rules ktlint set).
  - **`scripts/check-tooling-active.sh`** (Review Focus 9; review r3 R-H3). ktlint and Kover have no canary module, so this proves they *see* code:
  ```bash
  #!/usr/bin/env bash
  # ktlint-gradle < 14.1 registers no Android tasks under built-in Kotlin, and Kover skips a bound at 0 lines,
  # so both can pass while checking nothing.
  set -euo pipefail
  fail() { echo "FAIL: $*" >&2; exit 1; }
  out=$(mktemp -d)
  for m in designsystem glyph-sdk app spike; do
    ./gradlew -q ":$m:tasks" --all > "$out/$m.txt"
    grep -q 'ktlintMainSourceSetCheck' "$out/$m.txt" || fail ":$m has no ktlint main source-set task"
  done
  ./gradlew -q :core:koverXmlReport :agent:koverXmlReport :designsystem:koverXmlReportDebug \
    :app:koverXmlReportSdkFreeDebug
  for r in core/build/reports/kover/report.xml agent/build/reports/kover/report.xml \
           designsystem/build/reports/kover/reportDebug.xml app/build/reports/kover/reportSdkFreeDebug.xml; do
    test -f "$r" || fail "missing $r"
    # the report-level LINE counter is the last one in the file
    grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$r" > "$out/lines.txt" || fail "no LINE counter in $r"
    tail -n1 "$out/lines.txt" > "$out/last.txt"
    if grep -q 'missed="0" covered="0"' "$out/last.txt"; then fail "$r counts zero lines"; fi
  done
  echo OK
  ```
  `:glyph-sdk` has no code until Task 4, so Task 4 Step 9 adds `:glyph-sdk:koverXmlReportSdkFreeDebug` and its report to this script, and to the `scripts:` line of `ci-tasks.txt` in the same commit.
  [verify] the report file names and the ktlint task name against the pinned versions on the first run, and fix the script, not the check, if they differ. In the full-flavour CI job, also check `glyph-sdk/build/reports/kover/reportFullDebug.xml` the same way.
- [ ] **Step 8: Learning asides** in `docs/learning/m0.md`, 2–4 sentences each plus a link:
  - Gradle modules (like Go modules / packages in one repo)
  - the version catalog
  - product flavours (like build tags)
  - `Application` vs `Activity` (process entry vs screen)
  - the manual DI container (like wiring in `main.go`)
  - Kover, ktlint and detekt, and why some detekt rules need type resolution (they ask the compiler what a call resolves to, like `go vet` vs `grep`)
  - the four Java versions: daemon JVM (runs Gradle, like the interpreter running a build script), toolchain (the JDK we compile and test with, like `GOROOT`), bytecode target (the class-file format; D8 rewrites it to DEX anyway), and on-device API level (minSdk: which `java.*` classes exist on the phone, the one that crashes at runtime); why `:core` uses `-Xjdk-release=17`
- [ ] **Step 9: Tests (after the code).**
  - **`DeviceProfileTest`:**
    - 137 LEDs
    - corners (0,0) and (12,12) are not LEDs
    - the top row covers columns 4..8 and not 3
    - row 6 is full
    - out-of-range coordinates return false
    - `ledPositions().size == 137`
    - the first position is (4,0) and the last is (8,12)
  - **`TextGridTest`:**
    - 13 lines of 13 chars each
    - all 0 → `·` at every LED and spaces at the 32 non-LED cells
    - bucket edges: 1→`.`, 29→`.`, 30→`:`, 255→`@` (`(v-1)*9/255`; review r3 R-M1)
    - -5 clamps to `·` and 300 to `@`
    - a wrong size throws with the message above
  - **`:agent`:** `AgentModuleTest` asserts `NAME`.
  - **`:evals`:** `MainTest` captures stdout of `main()`.
  - **`:designsystem`:** a Robolectric `GlyphThemeTest` renders `GlyphTheme { Text("x") }` and asserts that the node exists.
  - **`:app`:** a Robolectric `MainActivityTest` (`@Config(sdk = [36])`, using `createAndroidComposeRule<MainActivity>()`, not `createComposeRule()`, because it also runs under `testSdkFreeReleaseUnitTest`, where `ui-test-manifest` is absent) launches it and asserts that the app name is displayed. `AppContainerTest` asserts that `glyphDisplay` is a `GlyphDisplay` (added in Task 4).
  - **Canaries, run once and recorded in the commit message (not kept as files):**
    - JDK API guard (Review Focus 10): a temporary `core/src/main/kotlin/…/Tmp.kt` calling `Thread.ofVirtual()` (Java 21, no Kotlin equivalent) → `:core:compileKotlin` **fails**; delete it.
    - Explicit backing fields (patterns §1.2 "[U] once ktlint/detekt parse it in M0"; review r3 R-M7): a temporary `core/src/main/kotlin/…/Tmp.kt` with `val s: StateFlow<Int>` + `field = MutableStateFlow(0)` (add `coroutines-core` to `:core` only for this check) → `./gradlew :core:ktlintCheck` and `bash scripts/run-detekt.sh :core:detekt :core:detektMain` are clean; delete it. Record the result in patterns §1.2 (drop the [U]). If either tool fails, Task 5 uses the `_x`/`x` pair and §1.2 records why.
- [ ] **Step 10: Verify.**
  ```bash
  ./gradlew ktlintCheck lintSdkFreeDebug :designsystem:lintDebug :core:test :agent:test :evals:test :designsystem:testDebugUnitTest :app:testSdkFreeDebugUnitTest :core:koverVerify :agent:koverVerify :app:assembleSdkFreeDebug :spike:assembleSdkFreeDebug
  bash scripts/run-detekt.sh detekt :core:detektMain :agent:detektMain :evals:detektMain :designsystem:detektDebug :glyph-sdk:detektSdkFreeDebug :app:detektSdkFreeDebug :spike:detektSdkFreeDebug
  bash scripts/detekt-canary.sh && bash scripts/guard-banned.sh && bash scripts/check-tooling-active.sh
  ./gradlew :app:assembleSdkFreeRelease :app:lintSdkFreeDebug   # Java 25 gate: R8 + Lint at target 25
  ```
  Expected: all pass. **Java 25 gate:** if R8, Lint, Robolectric or Kover fails *because of* class-file version 69 (the error names the version), set the Android modules to 21, record the error in D5x, and re-run.
- [ ] **Step 11: Install** with `$ADB install -r app/build/outputs/apk/sdkFree/debug/app-sdkFree-debug.apk`, then `$ADB shell pm list packages | grep glyphwizard` → `package:io.github.sgirishshiva.glyphwizard`.
- [ ] **Step 12: Commit.** Slug: `m0-scaffold`.

### Task 3: Glyph SDK fetch (full only, pinned, configuration-cache safe)

**Files:** Modify `settings.gradle.kts`, `glyph-sdk/build.gradle.kts`, `app/build.gradle.kts`, `spike/build.gradle.kts`, `gradle.properties` (`glyphSdk.url`, `glyphSdk.sha256`); create `build-logic/` (`settings.gradle.kts`, `build.gradle.kts`, `FetchGlyphSdk.kt`, `GlyphSdkFetchPlugin.kt`) and `scripts/check-glyph-sdk-fetch.sh`.

**Interfaces (produces):** plugin `glyphwizard.glyph-sdk-fetch`; task `:glyph-sdk:fetchGlyphSdk`; extension `glyphSdkAar: FileCollection` (built by the task); consumable configuration `:glyph-sdk` → `glyphSdk`. `:glyph-sdk` compiles against the SDK only in `full`; `:app`/`:spike` package it in `full`.

- [ ] **Step 0: Discover.** Read `glyph-sdk/build.gradle.kts` from Task 2 and the Gradle docs on lazy properties and configuration cache (`android docs search "Gradle lazy configuration"` or docs.gradle.org). Then confirm the pin:
  ```bash
  curl -fsSL "https://api.github.com/repos/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/commits?path=glyph-matrix-sdk-2.0.aar&per_page=1" | grep -m1 '"sha"'
  curl -fsSL -o "$SCRATCH/g.aar" "https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/a65d8e1d13e796919894e54ce43f93d939cdb34c/glyph-matrix-sdk-2.0.aar" && sha256sum "$SCRATCH/g.aar"
  ```
  Expect `be00ee9c…bf29`. Write both into `gradle.properties` (they are not versions, so not the catalog; review r2 L5):
  ```properties
  glyphSdk.url=https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/a65d8e1d13e796919894e54ce43f93d939cdb34c/glyph-matrix-sdk-2.0.aar
  glyphSdk.sha256=be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29
  ```
  A `-PglyphSdk.url=…` / `-PglyphSdk.sha256=…` on the command line overrides them (the fetch tests use this).
- [ ] **Step 1: Implement** the fetch as a plugin in the included build `build-logic/` (review r2 N2: a task class declared inside a `build.gradle.kts` compiles as an inner class of the script, which Gradle can't instantiate or configuration-cache).
  - `settings.gradle.kts` (root): add `pluginManagement { includeBuild("build-logic") }` above the existing `pluginManagement` content (merge into the same block).
  - `build-logic/settings.gradle.kts`:
  ```kotlin
  dependencyResolutionManagement { repositories { mavenCentral(); gradlePluginPortal() } }
  rootProject.name = "build-logic"
  ```
  - `build-logic/build.gradle.kts`:
  ```kotlin
  plugins { `kotlin-dsl` }
  tasks.validatePlugins {
      enableStricterValidation = true
      failOnWarning = true
  }
  gradlePlugin {
      plugins {
          register("glyphSdkFetch") {
              id = "glyphwizard.glyph-sdk-fetch"
              implementationClass = "io.github.sgirishshiva.glyphwizard.buildlogic.GlyphSdkFetchPlugin"
          }
      }
  }
  ```
  - `build-logic/src/main/kotlin/io/github/sgirishshiva/glyphwizard/buildlogic/FetchGlyphSdk.kt`:
  ```kotlin
  package io.github.sgirishshiva.glyphwizard.buildlogic

  @DisableCachingByDefault(because = "downloads one pinned file; the output already lives in the project")
  abstract class FetchGlyphSdk : DefaultTask() {
      @get:Input abstract val url: Property<String>
      @get:Input abstract val sha256: Property<String>
      @get:Input abstract val offline: Property<Boolean>
      @get:OutputFile abstract val aar: RegularFileProperty

      @TaskAction
      fun fetch() {
          val target = aar.get().asFile
          if (target.exists() && sha256Of(target.readBytes()) == sha256.get()) return
          if (offline.get()) throw GradleException(downloadError())
          val bytes = runCatching { download() }.getOrElse { throw GradleException(downloadError(), it) }
          val actual = sha256Of(bytes)
          if (actual != sha256.get()) {
              target.delete()
              throw GradleException("Glyph SDK SHA-256 mismatch: expected ${sha256.get()}, got $actual. URL: ${url.get()}")
          }
          target.parentFile.mkdirs()
          target.writeBytes(bytes)
      }

      private fun download(): ByteArray =
          URI(url.get()).toURL().openConnection()
              .apply {
                  connectTimeout = CONNECT_TIMEOUT_MS
                  readTimeout = READ_TIMEOUT_MS
              }
              .getInputStream()
              .use { it.readBytes() }

      private fun sha256Of(bytes: ByteArray): String =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

      private fun downloadError() =
          "Could not download the Glyph SDK from ${url.get()} (needed for the 'full' flavour; build 'sdkFree' to skip)."

      private companion object {
          // the JDK defaults are "OS decides" (connect) and "forever" (read): a firewalled host would hang the build
          const val CONNECT_TIMEOUT_MS = 30_000
          const val READ_TIMEOUT_MS = 60_000
      }
  }
  ```
  - `build-logic/src/main/kotlin/io/github/sgirishshiva/glyphwizard/buildlogic/GlyphSdkFetchPlugin.kt`:
  ```kotlin
  package io.github.sgirishshiva.glyphwizard.buildlogic

  class GlyphSdkFetchPlugin : Plugin<Project> {
      override fun apply(project: Project) {
          val fetch = project.tasks.register<FetchGlyphSdk>("fetchGlyphSdk") {
              url = project.providers.gradleProperty("glyphSdk.url")
              sha256 = project.providers.gradleProperty("glyphSdk.sha256")
              offline = project.gradle.startParameter.isOffline
              aar = project.layout.projectDirectory.file("libs/glyph-matrix-sdk.aar")
          }
          val aarFile = fetch.flatMap { it.aar }
          project.extensions.add(FileCollection::class.java, "glyphSdkAar", project.files(aarFile).builtBy(fetch))
          val consumable = project.configurations.consumable(GLYPH_SDK_CONFIGURATION)
          project.artifacts.add(consumable.name, aarFile)
      }

      companion object {
          const val GLYPH_SDK_CONFIGURATION = "glyphSdk"
      }
  }
  ```
  - `glyph-sdk/build.gradle.kts`: add `id("glyphwizard.glyph-sdk-fetch")` to `plugins {}`, then
  ```kotlin
  dependencies {
      "fullCompileOnly"(glyphSdkAar)
      "testFullImplementation"(glyphSdkAar)
  }
  ```
  The plugin only exposes the file; the `full*` configurations are wired in the module script because AGP creates them only once the `productFlavors` block has run.
  In `app/build.gradle.kts` and `spike/build.gradle.kts`:
  ```kotlin
  dependencies { "fullRuntimeOnly"(project(":glyph-sdk", configuration = "glyphSdk")) }
  ```
  Why this shape (review r2 N1):
  - AGP refuses a direct local `.aar` dependency when it builds an AAR (`bundleFullDebugAar` fails with *"Direct local .aar file dependencies are not supported when building an AAR"*). So the library only **compiles against** the SDK (`fullCompileOnly`); its unit tests get it on their runtime classpath (`testFullImplementation`, because loading the full `GlyphDisplays` pulls in `SdkMatrixService`).
  - The applications package it (`fullRuntimeOnly`). They reach the file through the `glyphSdk` consumable configuration, whose artifact carries the fetch task dependency, instead of reaching into `project(":glyph-sdk").tasks` (keeps projects isolated).
  - [verify] that AGP accepts the raw AAR from a project configuration: `./gradlew :app:assembleFullDebug && unzip -l app/build/outputs/apk/full/debug/app-full-debug.apk | grep -c classes` and the Task 7 positive control (`Lcom/nothing/ketchum/` in the full dex). If AGP rejects it, fall back to `"fullRuntimeOnly"(files(isolated.rootProject.projectDirectory.file("glyph-sdk/libs/glyph-matrix-sdk.aar")))` plus `tasks.named("preFullDebugBuild") { dependsOn(":glyph-sdk:fetchGlyphSdk") }`, and record the reason.

  Notes:
  - Imports for the two `build-logic` files: `org.gradle.api.{DefaultTask, GradleException, Plugin, Project}`, `org.gradle.api.file.{FileCollection, RegularFileProperty}`, `org.gradle.api.provider.Property`, `org.gradle.api.tasks.{Input, OutputFile, TaskAction}`, `org.gradle.work.DisableCachingByDefault`, `org.gradle.kotlin.dsl.register`, `java.net.URI`, `java.security.MessageDigest`, `java.util.HexFormat`.
  - The SHA helper is a private **member** and uses `java.util.HexFormat` (JDK 17, no opt-in). `ByteArray.toHexString()` is avoided because `build-logic` compiles with Gradle's embedded Kotlin, where it may still be `@ExperimentalStdlibApi`.
  - `build-logic` is outside the module set, so ktlint/detekt don't scan it; it's build tooling, reviewed by hand. `validatePlugins` (strict, warnings fail) runs in CI as `:build-logic:validatePlugins` (Task 7).
  - Lazy `=` assignment on `Property` works in `build-logic`'s plain `.kt` files: `kotlin-dsl` applies the assignment compiler plugin to every `KotlinCompile` there (verified, Gradle 9.6.1 `KotlinDslCompilerPlugins.kt`; review r3 R-L1 refuted).
  - `runCatching` is fine here: this is not suspend code.
  - A local file whose hash doesn't match is re-downloaded and re-verified. The remote is the trust boundary, and the pinned hash guards it.
  - `.gitignore` already ignores `*.aar`; also add `glyph-sdk/libs/`.
- [ ] **Step 2: Tests (after).** `scripts/check-glyph-sdk-fetch.sh`:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  fail() { echo "FAIL: $*" >&2; exit 1; }
  rm -rf glyph-sdk/libs
  ./gradlew -q :glyph-sdk:fetchGlyphSdk || fail "clean fetch"
  test -f glyph-sdk/libs/glyph-matrix-sdk.aar || fail "aar missing after fetch"
  rm -rf glyph-sdk/libs
  out=$(./gradlew :glyph-sdk:fetchGlyphSdk -PglyphSdk.sha256=0000000000000000000000000000000000000000000000000000000000000000 2>&1) && fail "bad hash passed"
  grep -q 'Glyph SDK SHA-256 mismatch' <<<"$out" || fail "mismatch message"
  test ! -e glyph-sdk/libs/glyph-matrix-sdk.aar || fail "bad file kept"
  out=$(./gradlew :glyph-sdk:fetchGlyphSdk -PglyphSdk.url=https://127.0.0.1:9/none.aar 2>&1) && fail "bad url passed"
  grep -q 'Could not download the Glyph SDK' <<<"$out" || fail "download message"
  out=$(./gradlew --offline :glyph-sdk:fetchGlyphSdk 2>&1) && fail "offline fetch passed"
  grep -q 'Could not download the Glyph SDK' <<<"$out" || fail "offline message"
  rm -rf glyph-sdk/libs
  ./gradlew --offline -q :glyph-sdk:assembleSdkFreeDebug || fail "sdkFree offline"
  test ! -e glyph-sdk/libs || fail "sdkFree triggered fetch"
  echo OK
  ```
  Run it and expect `OK`. Then:
  - `./gradlew :app:assembleFullDebug` twice: the second run reports `:glyph-sdk:fetchGlyphSdk UP-TO-DATE`.
  - `./gradlew --configuration-cache :glyph-sdk:fetchGlyphSdk` twice: the second run prints `Reusing configuration cache.` and neither run reports a configuration-cache problem.
- [ ] **Step 3: Learning asides:**
  - Gradle tasks with lazy `Property`/`Provider` and `builtBy`, the wiring that runs on demand
  - `compileOnly` vs `implementation` vs `runtimeOnly` (like a Go build tag that compiles against an interface but links the implementation elsewhere), and why a library must not bundle a local AAR
  - SHA pinning: why a commit-pinned URL plus a hash makes builds reproducible
- [ ] **Step 4: Commit.** Slug: `m0-sdk-fetch`.

### Task 4: `GlyphDisplay` bridge + durable mirror

**Files:** under `glyph-sdk/src/` per the File structure; modify `app/.../AppContainer.kt`; create `app/src/debug/.../DebugWrap.kt`, `app/src/release/.../DebugWrap.kt`, `app/src/testDebug/.../AppContainerTest.kt`, `app/src/testRelease/.../AppContainerTest.kt`.

**Interfaces (produces):**
```kotlin
package io.github.sgirishshiva.glyphwizard.glyph

sealed interface GlyphError {
    data object Unsupported : GlyphError
    data object ServiceUnavailable : GlyphError
    data class Rejected(val reason: String) : GlyphError
}
sealed interface GlyphResult<out T> {
    data class Ok<out T>(val value: T) : GlyphResult<T>
    data class Err(val error: GlyphError) : GlyphResult<Nothing>
}
interface GlyphDisplay {
    suspend fun connect(): GlyphResult<Unit>
    fun show(brightness: IntArray): GlyphResult<Unit>   // one 0..255 value per LED, in DeviceProfile.ledPositions() order
    fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit>   // Nothing's app-matrix auto-clear; measured in Task 6 Step 6
    fun release()
}
class MatrixFrameEncoder(val profile: DeviceProfile, val layout: Layout, val maxValue: Int) {
    enum class Layout { LedsOnly, FullSquare }
    fun encode(brightness: IntArray): IntArray
    companion object { fun forPhone4aPro(): MatrixFrameEncoder }   // measured defaults (Task 6 Step 9); provisional: FullSquare, 255
}
class MirroringGlyphDisplay(private val delegate: GlyphDisplay, private val profile: DeviceProfile, private val sink: (String) -> Unit) : GlyphDisplay
object GlyphDisplays { fun create(context: Context, encoder: MatrixFrameEncoder): GlyphDisplay }   // per flavour
```
`GlyphResult` and `GlyphError` are local to `:glyph-sdk`. The shared result type is decided in the D46 deep-dive.

- [ ] **Step 0: Discover.** Read Task 2's `DeviceProfile`/`TextGrid` and Task 3's build file. Then inspect the AAR to confirm the class and method names:
  ```bash
  unzip -o glyph-sdk/libs/glyph-matrix-sdk.aar classes.jar -d "$SCRATCH/aar"
  javap -cp "$SCRATCH/aar/classes.jar" com.nothing.ketchum.GlyphMatrixManager com.nothing.ketchum.GlyphMatrixManager\$Callback com.nothing.ketchum.GlyphException com.nothing.ketchum.Glyph
  ```
  Tests never construct `GlyphException` (review r2 L6): `SdkMatrixService` maps it to `GlyphResult`, so the `MatrixService` fake returns `Err` directly. The `javap` output above is only used to confirm Step 4a's signatures.
  Also read research §3.1 (`docs/research/2026-10-11-dev-tooling-visual-mcp.md`).
- [ ] **Step 1: `MatrixFrameEncoder`.**
  - Require the size to equal `profile.ledCount` (otherwise `IllegalArgumentException("expected … got …")`).
  - Clamp to 0..255 and scale to `maxValue` (`v * maxValue / 255`).
  - `LedsOnly` returns the scaled array.
  - `FullSquare` writes values into `gridSize²` at the `ledPositions()` indices (`y * gridSize + x`) and leaves 0 elsewhere.
- [ ] **Step 2: `MirroringGlyphDisplay`.** It delegates every call. On `show`, it also calls `sink(TextGrid.encode(profile, brightness))` with `profile` taken from the encoder passed to the factory. Constructor: `MirroringGlyphDisplay(delegate, profile, sink)`.
- [ ] **Step 3: sdkFree.** `GlyphDisplays.create` returns `NoGlyphDisplay` (internal): `connect`/`show`/`setAutoTimeout` → `Err(Unsupported)`; `release` → no-op.
- [ ] **Step 4: full.**
  - **Seam:** a consumer interface. `SdkMatrixService` is the only code that uses `com.nothing.*`: it maps `GlyphException` to `GlyphResult`, so nothing else (tests included) touches SDK types. It is excluded from Kover as a thin adapter.
  ```kotlin
  internal interface MatrixService {
      fun bind(onConnected: () -> Unit, onDisconnected: () -> Unit)
      fun register(deviceId: String): Boolean
      fun setFrame(frame: IntArray): GlyphResult<Unit>
      fun setAutoTimeout(enabled: Boolean)
      fun close(): GlyphResult<Unit>
      fun unbind()
  }
  ```
  - **`NothingGlyphDisplay`** (review r2 N7: connected flag set, single resume, thread-safe flags, re-connectable, release never throws past `unbind`):
  ```kotlin
  internal class NothingGlyphDisplay(
      private val service: MatrixService,
      private val encoder: MatrixFrameEncoder,
      private val connectTimeout: Duration = 3.seconds, // [P] until Task 6 measures binding time
  ) : GlyphDisplay {
      // written on the main thread by binder callbacks, read from the session dispatcher
      private val bound = AtomicBoolean(false)
      private val connected = AtomicBoolean(false)

      override suspend fun connect(): GlyphResult<Unit> {
          check(bound.compareAndSet(false, true)) { "connect() called twice without release()" }
          return withTimeoutOrNull(connectTimeout) {
              suspendCancellableCoroutine { cont ->
                  cont.invokeOnCancellation { unbindOnce() }
                  service.bind(
                      onConnected = {
                          val registered = bound.get() && service.register(encoder.profile.id)
                          connected.set(registered)
                          if (!registered) unbindOnce()
                          if (cont.isActive) {
                              cont.resume(
                                  if (registered) GlyphResult.Ok(Unit)
                                  else GlyphResult.Err(GlyphError.Rejected("register returned false")),
                              )
                          }
                      },
                      onDisconnected = { connected.set(false) },
                  )
              }
          } ?: GlyphResult.Err(GlyphError.ServiceUnavailable)
      }

      override fun show(brightness: IntArray): GlyphResult<Unit> =
          if (connected.get()) service.setFrame(encoder.encode(brightness))
          else GlyphResult.Err(GlyphError.ServiceUnavailable)

      override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> =
          if (connected.get()) GlyphResult.Ok(service.setAutoTimeout(enabled))
          else GlyphResult.Err(GlyphError.ServiceUnavailable)

      override fun release() {
          if (!bound.get()) return
          // the session ends with unbind whatever close reports, so its result is not surfaced
          try { service.close() } finally { unbindOnce() }
      }

      private fun unbindOnce() {
          connected.set(false)
          if (bound.compareAndSet(true, false)) service.unbind()
      }
  }
  ```
  - A repeat `onConnected` (after a rebind) re-registers and updates `connected` but never resumes twice (`cont.isActive`). A late `onConnected` after `release()` registers nothing (`bound` is false).
  - `register == false` unbinds, so a rejected session leaks no binding.
  - `release()` → `connect()` → `release()` on the same instance works (`SpikePatternService` and `:app` may cycle it); a second `release()` is a no-op.
  - **Device ID:** `register` receives `encoder.profile.id` (no separate `sdkDeviceId` parameter); `SdkMatrixService` maps it to `Glyph.DEVICE_25111p` in one `when` (Step 4a).
  - **Manifest:** `src/full/AndroidManifest.xml` declares the ENABLE permission. If Task 6 shows `connect()` timing out, add `<queries><package android:name="com.nothing.thirdparty"/></queries>` and record it.
- [ ] **Step 4a: `SdkMatrixService`** (full only; the only `com.nothing.*` user; review r2 L1):
  ```kotlin
  internal class SdkMatrixService(context: Context) : MatrixService {
      private val manager = GlyphMatrixManager.getInstance(context.applicationContext)

      override fun bind(onConnected: () -> Unit, onDisconnected: () -> Unit) {
          manager.init(object : GlyphMatrixManager.Callback {
              override fun onServiceConnected(name: ComponentName) = onConnected()
              override fun onServiceDisconnected(name: ComponentName) = onDisconnected()
          })
      }

      override fun register(deviceId: String): Boolean = sdkDevice(deviceId)?.let(manager::register) ?: false
      override fun setFrame(frame: IntArray): GlyphResult<Unit> = glyphCall { manager.setAppMatrixFrame(frame) }
      override fun setAutoTimeout(enabled: Boolean) = manager.setGlyphMatrixTimeout(enabled)
      override fun close(): GlyphResult<Unit> = glyphCall { manager.closeAppMatrix() }
      override fun unbind() = manager.unInit()

      private fun sdkDevice(id: String): String? = when (id) {
          DeviceProfile.Phone4aPro.id -> Glyph.DEVICE_25111p
          else -> null
      }

      private inline fun glyphCall(block: () -> Unit): GlyphResult<Unit> =
          try {
              block()
              GlyphResult.Ok(Unit)
          } catch (e: GlyphException) {
              GlyphResult.Err(GlyphError.Rejected(e.message ?: "glyph exception"))
          }
  }
  ```
  [verify] in Step 0's `javap` output: `Callback` is an interface with exactly `onServiceConnected(ComponentName)` / `onServiceDisconnected(ComponentName)`; `init` takes that `Callback`; `Glyph.DEVICE_25111p` is a `String`; `GlyphException` extends `Exception` (so `e.message` is its detail). If any differs, adjust only this class: the `MatrixService` seam stays.
- [ ] **Step 5: Threading.** `show()` performs a synchronous binder call, so callers must invoke it off the main thread (documented as a one-line KDoc on `show`). The SDK swallows `RemoteException`, so a dead binder is only visible through `onDisconnected`.
- [ ] **Step 6: Wire `:app`.** `AppContainer` gains `glyphDisplay` exactly as in Task 2 Step 6. The mirror is chosen by **build-type source set**, not `BuildConfig` (AGP 9 has `buildFeatures.buildConfig` off by default; review r2 N14):
  ```kotlin
  // app/src/debug/kotlin/io/github/sgirishshiva/glyphwizard/DebugWrap.kt
  internal fun wrapForBuild(display: GlyphDisplay, profile: DeviceProfile): GlyphDisplay =
      MirroringGlyphDisplay(display, profile) { Log.i("GlyphMirror", it) }

  // app/src/release/kotlin/io/github/sgirishshiva/glyphwizard/DebugWrap.kt
  internal fun wrapForBuild(display: GlyphDisplay, profile: DeviceProfile): GlyphDisplay = display
  ```
  PNG dumps are deferred to M2 (recorded here).
- [ ] **Step 7: Tests (after).**
  - **`src/test`, `MatrixFrameEncoderTest`:**
    - `LedsOnly` gives size 137
    - `FullSquare` gives size 169, with indices 0, 12, 156 and 168 equal to 0
    - the value at index `0 * 13 + 4` comes from input index 0; input index 2 lands at (6,0); the last input lands at (8,12)
    - clamping: -5 → 0, 300 → 255
    - `maxValue = 2047`: 255 → 2047, 128 → 1027
    - a wrong size throws
    - `forPhone4aPro()` returns the documented provisional values
  - **`src/test`, `MirroringGlyphDisplayTest`:** with a fake delegate, `show` forwards and the sink receives the `TextGrid` output; `connect`, `setAutoTimeout` and `release` are forwarded.
  - **`src/testSdkFree`, `NoGlyphDisplayTest`:** every call returns `Unsupported`; `release()` twice is safe.
  - **`src/testFull`, `NothingGlyphDisplayTest`** (fake `MatrixService`, `runTest` with virtual time):
    - the callback never fires → `Err(ServiceUnavailable)` after 3 s of virtual time, and `unbind` is called once
    - `onConnected` + `register` true → `Ok`, and `show` then reaches `setFrame` with the encoded frame
    - `register` false → `Rejected`, and `unbind` is called
    - `setFrame` returns `Err(Rejected("x"))` → `show` returns it unchanged
    - `show` / `setAutoTimeout` before `connect` → `ServiceUnavailable`; after `connect` → forwarded
    - `onDisconnected`, then `show` → `ServiceUnavailable`
    - a second `onConnected` (rebind) doesn't throw, and `connect` returned once
    - `release()` twice → one `close` and one `unbind`
    - `connect` → `release` → `connect` → `release` → two `unbind`s
    - `close()` throwing (fake throws `IllegalStateException`) still calls `unbind`, **and** `release()` rethrows it (`assertFailsWith`; `try/finally` propagates by design, review r3 R-L6)
    - `connect()` twice without `release()` → `IllegalStateException`
    The fake is a plain class implementing `MatrixService`; no SDK type is constructed in tests.
  - **`:app`, `AppContainerTest`** (Robolectric): in `src/testDebug`, `glyphDisplay` is a `MirroringGlyphDisplay`; in `src/testRelease`, it is **not** (run by `:app:testSdkFreeReleaseUnitTest`).
- [ ] **Step 8: Learning asides:**
  - flavour source sets (`src/full`, `src/testFull`)
  - `suspendCancellableCoroutine`, which turns a callback into a suspend call (like a callback feeding a Go channel)
  - interfaces defined by the consumer
  - binder calls and threads
- [ ] **Step 9: Run and commit.**
  ```bash
  ./gradlew :glyph-sdk:testSdkFreeDebugUnitTest :glyph-sdk:testFullDebugUnitTest :glyph-sdk:koverVerifySdkFreeDebug :glyph-sdk:koverVerifyFullDebug :app:testSdkFreeDebugUnitTest :app:testSdkFreeReleaseUnitTest
  ```
  Now that `:glyph-sdk` has code, add `:glyph-sdk:koverXmlReportSdkFreeDebug` + `glyph-sdk/build/reports/kover/reportSdkFreeDebug.xml` to `scripts/check-tooling-active.sh`, then run it → `OK`. (Task 7 adds the `scripts:` line in `ci-tasks.txt`.)
  Slug: `m0-glyph-bridge`.

### Task 5: Spike app (throwaway): patterns, session, pattern service, listener, adb hooks

**Files:** `spike/src/main/kotlin/io/github/sgirishshiva/glyphwizard/spike/`:
- `Patterns.kt`
- `GlyphSession.kt`
- `SpikeCommand.kt`
- `SpikePatternService.kt`
- `SpikeActivity.kt`
- `SpikeListener.kt`
- `SpikeShortService.kt`

Plus `spike/src/main/AndroidManifest.xml` and tests under `spike/src/test/`. Catalog: add `lifecycle-service` (`androidx.lifecycle:lifecycle-service`, same version as `lifecycle-runtime-ktx`).

**Design decision (review r2 N17):** patterns run in **`SpikePatternService`, a `specialUse` foreground service**, not in the Activity. The Activity stops on screen-off, so an Activity-owned session and receiver could not serve the locked, pocketed and background measurements (Task 6 Steps 7–8). The service owns the session and the DUMP-guarded receiver for its whole life; it runs until `STOP`. Whether the Nothing service keeps showing an app matrix while the app has no visible Activity is itself recorded as a §10 fact (Task 6 Step 7). `SpikeShortService` is kept only for the §10 `shortService` interplay probe.

**Interfaces:**
- **Consumes:** `GlyphDisplays.create`, `MatrixFrameEncoder`, `MirroringGlyphDisplay`, `DeviceProfile.Phone4aPro`, `GlyphDisplay.setAutoTimeout`.
- **Produces these adb hooks** for Task 6, where `P=io.github.sgirishshiva.glyphwizard.spike` (the service must be running; start it from the Activity's "Start" button):
  - `$ADB shell am broadcast -p $P -a $P.SHOW --es pattern <name> [--ei level N] [--ei hz N] [--ei x N] [--ei y N] [--ei fps N] [--es layout leds|square] [--ei max 255|2047]`
  - `$ADB shell am broadcast -p $P -a $P.TIMEOUT --ez enabled true|false` calls `setAutoTimeout`
  - `$ADB shell am broadcast -p $P -a $P.DUMP` logs the last frame under `GlyphMirror`, and `lastResult` + `achievedFps` under `SpikeStatus`
  - `$ADB shell am broadcast -p $P -a $P.STOP` stops the session and the service
  - `$ADB shell am broadcast -p $P -a $P.SHORT` starts `SpikeShortService` (fire it while the Activity is visible: a background app may not start a foreground service)

- [ ] **Step 0: Discover.** Read Task 4's `GlyphDisplay` and `MirroringGlyphDisplay`, research §3.1, and Android docs on `NotificationListenerService`, foreground service types and `specialUse` (`android docs search "foreground service types specialUse"`, `android docs search "shortService foreground service type"`). Each file starts with the single allowed header comment: `// Throwaway M0 spike (spec §8). Deleted at end of M1.`
- [ ] **Step 1: `Patterns`.** `typealias Pattern = (DeviceProfile, elapsedMs: Long) -> IntArray`. Pure functions, each returning `ledCount` values:
  - `checker`
  - `all(level)`
  - `ramp`: 8 bands across x
  - `rowsSweep(framePeriodMs)`: row = `(elapsedMs / framePeriodMs) % 13`
  - `single(x, y)`
  - `flash(hz)`: on when `(elapsedMs * hz * 2 / 1000) % 2 == 0L`
  - `cornerProbe`: first and last LED of each row
- [ ] **Step 2: `GlyphSession`** (review r2 N16: `profile` injected, `kotlin.time` source injected, absolute deadlines):
  ```kotlin
  class GlyphSession(
      private val display: GlyphDisplay,
      private val profile: DeviceProfile,
      private val scope: CoroutineScope,
      private val dispatcher: CoroutineDispatcher,
      private val timeSource: TimeSource.WithComparableMarks,
  ) {
      val achievedFps: StateFlow<Double>
          field = MutableStateFlow(0.0)
      val lastResult: StateFlow<GlyphResult<Unit>?>
          field = MutableStateFlow<GlyphResult<Unit>?>(null)
      private var loop: Job? = null

      fun start(pattern: Pattern, fps: Int) {
          require(fps in 1..240) { "fps must be in 1..240, got $fps" }
          loop?.cancel()
          loop = scope.launch(dispatcher) {
              val period = 1.seconds / fps
              val t0 = timeSource.markNow()
              var frames = 0L
              while (isActive) {
                  delay((t0 + period * frames.toDouble()) - timeSource.markNow())   // absolute deadline: no drift
                  lastResult.value = display.show(pattern(profile, t0.elapsedNow().inWholeMilliseconds))
                  frames++
                  val elapsed = t0.elapsedNow()
                  if (elapsed.isPositive()) achievedFps.value = frames / elapsed.toDouble(DurationUnit.SECONDS)
              }
          }
      }

      fun stop(): Job {
          val running = loop
          loop = null
          // show() is a blocking binder call: release only after the in-flight frame finishes (review r3 R-M3)
          return scope.launch(dispatcher) {
              running?.cancelAndJoin()
              display.release()
          }
      }
  }
  ```
  `start`/`stop` are called on the main thread only (service callbacks), so `loop` needs no synchronisation. Explicit backing fields (Kotlin 2.4 stable, patterns §2.12) replace the `_x`/`x` pair, provided Task 2 Step 9's canary passed; otherwise use the pair and cite the canary result.
- [ ] **Step 3: `SpikeCommand`.** One parser shared by the Activity and the receiver, so both paths behave the same:
  ```kotlin
  sealed interface SpikeCommand {
      data class Show(val pattern: String, val fps: Int, val layout: MatrixFrameEncoder.Layout, val maxValue: Int, val level: Int, val hz: Int, val x: Int, val y: Int) : SpikeCommand
      data class AutoTimeout(val enabled: Boolean) : SpikeCommand
      data object Dump : SpikeCommand
      data object Stop : SpikeCommand
      data object Short : SpikeCommand
      companion object { fun from(action: String?, extras: Bundle?): SpikeCommand? }
  }
  ```
  Defaults for missing extras: `fps = 30`, `layout = FullSquare`, `maxValue = 255`, `level = 255`, `hz = 10`, `x = 6`, `y = 6`. An unknown action or pattern name returns `null` (logged under `SpikeStatus`, ignored). `fun patternFor(show: Show): Pattern?` maps the name to Step 1.
- [ ] **Step 4: `SpikePatternService : LifecycleService`** (`specialUse` FGS).
  - `onCreate`: `startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`; register `SpikeReceiver` (an inner `BroadcastReceiver` that parses with `SpikeCommand.from` and calls `handle`) via `ContextCompat.registerReceiver(this, receiver, filter, Manifest.permission.DUMP, null, ContextCompat.RECEIVER_EXPORTED)`. Only callers holding DUMP (the adb shell) can reach it.
  - `onStartCommand`: the Activity's commands arrive as intents → `SpikeCommand.from(intent.action, intent.extras)` → `handle`.
  - **Serialised commands** (review r3 R-M5): the receiver and `onStartCommand` only `trySend` into a `Channel<SpikeCommand>(Channel.UNLIMITED)`. One coroutine in `lifecycleScope` consumes it, so two quick `SHOW`s can never race two `connect()`s.
  - `handle(Show)`: if `(layout, maxValue)` differs from the current session's, `stop()` it and build a new one: `MirroringGlyphDisplay(GlyphDisplays.create(this, MatrixFrameEncoder(profile, layout, maxValue)), profile) { Log.i("GlyphMirror", it) }`, then `connect()`. The old session's `stop().join()` completes first, so its `closeAppMatrix` can't blank the new display. **Only on `Ok`** call `session.start(pattern, fps)`; on `Err`, log it under `SpikeStatus` and play nothing. `Stop` → `session.stop()`, `stopSelf()`. `Dump` → logs. `AutoTimeout` → `display.setAutoTimeout`. `Short` → `startForegroundService(Intent(this, SpikeShortService::class.java))`.
  - Wiring lives in a small `SpikeContainer(context, dispatcher: CoroutineDispatcher = Dispatchers.Default, timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic)`, the spike's composition root (default parameter values are where detekt `InjectDispatcher` allows a named dispatcher).
  - The container also owns `appScope = CoroutineScope(SupervisorJob() + dispatcher)` (process lifetime, the patterns doc's injected `applicationScope`). `GlyphSession` receives `appScope`, **not** `lifecycleScope`: `onDestroy` cancels `lifecycleScope`, which would cancel `stop()`'s pending `release()` and leave the matrix lit.
  - `onDestroy`: unregister the receiver, `session.stop()`.
- [ ] **Step 5: `SpikeActivity` (Compose).** A thin control panel: pattern buttons, an fps slider (5–120), layout and max toggles, Start/Stop, "grant notification access" (opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`) and "allow notifications" (requests `POST_NOTIFICATIONS`). Every button sends its `SpikeCommand` as an intent through `ContextCompat.startForegroundService`. Status is read from `DUMP` logs, not shown in the UI (throwaway).
- [ ] **Step 6: `SpikeListener` and `SpikeShortService`.**
  - **`SpikeListener : NotificationListenerService`** logs `onListenerConnected`, `onListenerDisconnected` and `onNotificationPosted` with `SystemClock.elapsedRealtime()` under `SpikeListener`.
  - **`SpikeShortService : LifecycleService`** (review r2 N8): `onStartCommand` calls `startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)` and launches a heartbeat in `lifecycleScope` (`while (isActive) { Log.i("SpikeShort", "alive ${SystemClock.elapsedRealtime()}"); delay(10.seconds) }`). It does **not** stop itself: it runs until the system calls `onTimeout(startId, fgsType)` (API 35+, about 3 minutes), which logs the time and calls `stopSelf(startId)`. No `Thread.sleep`.
  - **Manifest** (`spike/src/main/AndroidManifest.xml`), in full:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

  <service
      android:name=".SpikePatternService"
      android:exported="false"
      android:foregroundServiceType="specialUse">
      <property
          android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
          android:value="Debug-only hardware measurement of the Glyph Matrix" />
  </service>
  <service
      android:name=".SpikeShortService"
      android:exported="false"
      android:foregroundServiceType="shortService" />
  <service
      android:name=".SpikeListener"
      android:exported="true"
      android:label="GlyphWizard spike"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter>
          <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
  </service>
  ```
  `shortService` needs no type-specific permission. `POST_NOTIFICATIONS` is a runtime permission on target 36: grant it with the Activity button or `$ADB shell pm grant $P android.permission.POST_NOTIFICATIONS`. Without it the services still run; only their notifications are hidden.
- [ ] **Step 7: Manual check (Review Focus 6).**
  ```bash
  ./gradlew :spike:installFullDebug && $ADB shell am start -n io.github.sgirishshiva.glyphwizard.spike/.SpikeActivity
  ```
  Then tap Start, and:
  1. `$ADB shell am broadcast -p … -a ….SHOW --es pattern checker` → flip the phone: a checkerboard.
  2. `….DUMP` → `$ADB logcat -d -s GlyphMirror:V SpikeStatus:V` shows one 13-line grid and one status line.
  3. Press Home → record whether the matrix keeps playing (input to Task 6 Step 7).
  4. `….STOP` → the matrix turns off within about 1 s and the notification disappears.
  5. On the `sdkFree` spike build: `SHOW` logs `Err(Unsupported)` under `SpikeStatus` and plays nothing.
- [ ] **Step 8: Tests (after).**
  - **`PatternsTest`:**
    - every pattern returns 137 values within 0..255
    - `checker` alternates between LED-order neighbours in the same row
    - `ramp` is non-decreasing by band
    - `flash(10)` is on/off/on at elapsedMs 0 / 50 / 100, and on at 49
    - `cornerProbe` lights exactly 26 LEDs
    - `single(6,6)` lights exactly one LED
  - **`GlyphSessionTest`** (`runTest`, fake `GlyphDisplay`, `StandardTestDispatcher(testScheduler)`, `testScheduler.timeSource`, `backgroundScope`):
    - `start(…, 50)` → 50 ± 1 `show` calls per virtual second, and `achievedFps` within 1 of 50
    - `start(…, 120)` → 120 ± 1 calls per virtual second (no drift from the 8.33 ms period)
    - `stop()`, then `testScheduler.runCurrent()` (the release is queued on the test dispatcher) → no further `show` calls, and `release` is called once, **after** the last `show` (the fake records the call order); the returned `Job` is completed
    - a second `start` cancels the first loop
    - an `Err` from `show` lands in `lastResult`
    - `fps = 0` throws
    - only stable `kotlinx-coroutines-test` APIs: `testScheduler.timeSource`, `testScheduler.advanceTimeBy(Duration)`, `testScheduler.runCurrent()`, `backgroundScope`. The `TestScope.*` shortcuts and `advanceTimeBy(Long)` are `@ExperimentalCoroutinesApi` in 1.11.0, so they are not used, and no `@OptIn` is needed (review r3 R-M6, verified). If one ever becomes necessary, log a decision entry first (user decision, 2026-10-12; patterns #29)
  - **`SpikeCommandTest`** (Robolectric for `Bundle`): each action parses with its defaults; every extra overrides its default; an unknown action or pattern → `null`.
  - **Lint/detekt exemptions (only if the user agreed in Task 1):** `:spike` uses `lint { disable += "HardcodedText" }` and a detekt `excludes` entry. Otherwise, apply the full rules.
- [ ] **Step 9: Learning asides:**
  - the Activity lifecycle (`onStart`/`onStop`) vs a foreground service's lifetime, and why measurements need the service
  - `LifecycleService` and `lifecycleScope` (an owned scope, like a `context.Context` cancelled on shutdown)
  - BroadcastReceivers: exported vs non-exported vs permission-guarded
  - `NotificationListenerService`
  - foreground service types: `specialUse` vs `shortService`, and the background-start restriction
  - `kotlin.time.TimeSource` and absolute-deadline scheduling (like a Go `time.Ticker` vs repeated `time.Sleep`)
- [ ] **Step 10: Commit.** Slug: `m0-spike`.

### Task 6: Hardware measurements (human + agent) → results doc

**Files:** Create `docs/research/hardware-spike-results.md` (plus small JPEGs in `docs/research/spike-photos/`).

**Interfaces (produces):** measured `frameLayout` + transform, `maxValue`, `brightnessCurve`, `minVisibleLevel`, panel `maxSustainedFps` (D10), flash inputs (D37), rise time, glow, pitch, auto-timeout behaviour, priority, settings path, restricted settings, listener/process facts.

- [ ] **Step 0: Discover.** Read spec §10, D6/D10/D28/D33/D37 and the Task 5 hooks. Every step records its value as `[measured YYYY-MM-DD]`. Use `$ADB logcat -d` (dump, never stream). Record the phone's Nothing OS build and Glyph system version (`$ADB shell getprop ro.build.display.id`, plus Settings → About). The SDK README says `setAppMatrixFrame`/`closeAppMatrix` need system version ≥ 20250801 (review r3 R-L3).
- [ ] **Step 1: Layout and ordering.** `cornerProbe` with `layout=leds`, then `layout=square`. Photograph each: which lights exactly the row ends? Is there any mirror, rotation or ordering transform?
- [ ] **Step 2: Brightness range.** `all` at 64/128/255 with `max=255`, then `max=2047`. Record the true maximum. The SDK README says values above 255 are capped at 255 (GlyphMatrix-Developer-Kit README), so expect `maxValue = 255`. This step confirms it on hardware.
- [ ] **Step 3: Curve.** `all(level)` at 8, 16, 32, 64, 96, 128, 192, 255. Use a light meter, or a camera with manual exposure (fixed ISO and shutter, RAW). Record relative luminance, fit the curve, and find the minimum level visibly distinct from off.
- [ ] **Step 4: Frame rate.** `rowsSweep` at 30/50/60/90/120 fps for 60 s. Record the app-side achieved fps **and** the panel rate, measured by filming at 240 fps slow-motion and counting distinct row steps per second. Only the panel number feeds D10.
- [ ] **Step 5: Flash, rise, glow, pitch.**
  - From 240 fps footage of `flash(10)` and `flash(15)`: on/off timing, and rise time (frames from off to full).
  - Physical diameter, and luminance at 255.
  - PWM visible in slow motion?
  - Glow: `single(6,6)` at 255, photographed for halo bleed into neighbouring cells.
  - Pitch: centre-to-centre distance across 13 LEDs, divided by 12.
- [ ] **Step 6: Auto-timeout.** While `all(128)` plays, send `$P.TIMEOUT --ez enabled true`, then `false` (`GlyphDisplay.setAutoTimeout` → `SdkMatrixService` → `setGlyphMatrixTimeout`). Does the app matrix auto-clear, and after how long?
- [ ] **Step 7: Priority and states.** Patterns run from `SpikePatternService` (Task 5), so they survive the Activity stopping. While `all(128)` plays:
  1. Receive a WhatsApp message from a contact with a Nothing Essential Notification → which wins?
  2. Press Home (no visible Activity, service still foreground) → does the app matrix keep showing? Record it: "app matrix requires a visible Activity" is itself a §10 fact. `DUMP` confirms the session is still running.
  3. Repeat 1 locked, face-down, face-up and pocketed (hooks keep working: the receiver lives in the service).
  4. Repeat with an AOD Glyph Toy enabled.
  5. Find and record the **exact settings path** for Nothing's per-app Glyph notification setting.
- [ ] **Step 8: Kill and process.**
  1. `$ADB shell am force-stop io.github.sgirishshiva.glyphwizard.spike` while `all(255)` plays → does the panel clear? (Review Focus 5 [HW].)
  2. Grant notification access, background the app, then run `$ADB shell dumpsys activity processes | grep -A3 spike` → record the state (cached? frozen?).
  3. `$ADB shell am crash io.github.sgirishshiva.glyphwizard.spike` → time the rebind from the `SpikeListener` logs, with and without `requestRebind`.
  4. With the listener bound and the Activity visible, fire `SHORT`, then press Home. Over about 4 minutes, record the `SpikeShort` heartbeats, the `onTimeout` time, the process state (`dumpsys activity processes`) and any ANR. Documented: about 3 min from `startForeground` (AOSP `DEFAULT_SHORT_FGS_TIMEOUT_DURATION`), with an ANR if the service doesn't stop after `onTimeout`. This measurement feeds **D33** (agent turn as `shortService`, "5 min total / ≤ 150 s background (P)").
  5. Record anything not reproducible as such.
- [ ] **Step 9: Restricted settings via Obtainium** (D26-safe).
  1. **Ask the user** before tagging or pushing.
  2. Build `./gradlew :spike:assembleSdkFreeDebug`.
  3. Create a GitHub **prerelease** tagged `spike-m0-test` with **only** `spike-sdkFree-debug.apk` attached (no SDK inside), through the GitHub web UI or the REST API with the user's token.
  4. Install it via Obtainium (track prereleases), then open Settings → Notification access → spike: is "Restricted setting" shown? Walk through and record the unlock path.
  5. Delete the prerelease and tag afterwards. **Never attach any `full` APK.**
- [ ] **Step 10: Write the results doc and apply it.**
  - Fill `hardware-spike-results.md`: one table per step, with photos referenced.
  - Set `MatrixFrameEncoder.forPhone4aPro()` to the measured layout and max. If a transform is needed, add it to `MatrixFrameEncoder` with tests.
  - Update `MatrixFrameEncoderTest` for the defaults.
  - Amend **D6, D10, D28** (process state, listener rebind time; Step 8.2–8.3), **D33** (shortService timing; Step 8.4) and **D37** in `decisions.md` (review r3 R-M9), and tag each spec §10 bullet `[measured YYYY-MM-DD → hardware-spike-results.md#…]`.
  - If `connect()` needed `<queries>`, record that.
- [ ] **Step 11: Commit.** Slug: `m0-measurements`.

### Task 7: CI, supply chain, threat model

**Files:** Create `.github/workflows/ci.yml`, `scripts/ci-tasks.txt`, `scripts/ci.sh`, `scripts/check-sdkfree-apk.sh`, `renovate.json`, `docs/engineering/threat-model.md`, `docs/engineering/licences.md`; modify `gradle/verification-metadata.xml` and `README.md`.

- [ ] **Step 0: Discover.** Read every script from Tasks 2–3 and the README. Look up the commit SHAs of `actions/checkout`, `actions/setup-java` and `gradle/actions/setup-gradle` with `curl` (no `gh`). `releases/latest` gives a tag name, not a SHA (review r2 L2), so resolve it:
  ```bash
  repo=actions/checkout   # then actions/setup-java, gradle/actions
  tag=$(curl -fsSL "https://api.github.com/repos/$repo/releases/latest" | grep -m1 '"tag_name"' | cut -d'"' -f4)
  curl -fsSL "https://api.github.com/repos/$repo/git/ref/tags/$tag"   # note object.type and object.sha
  # object.type "commit" → object.sha is the pin; "tag" (annotated) → dereference once more:
  curl -fsSL "https://api.github.com/repos/$repo/git/tags/<object.sha>"   # its object.sha is the commit
  ```
  Pin as `uses: <repo>@<commit-sha> # <tag>`.
- [ ] **Step 1: `scripts/check-sdkfree-apk.sh`.** Every `grep -q` reads a file, never a pipe: under `pipefail`, an early `grep -q` match kills the producer with SIGPIPE and the pipeline's non-zero status would skip `&& fail` (review r2 N5). Permissions come from `aapt2`, because the APK manifest is binary XML that `strings` can miss (N11).
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  fail() { echo "FAIL: $*" >&2; exit 1; }
  sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
  bt=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
  aapt2="$bt/aapt2"
  test -x "$aapt2" || fail "aapt2 not found under $sdk/build-tools"
  one_apk() { mapfile -t apks < <(find "$1" -name '*.apk' -type f); [ "${#apks[@]}" -eq 1 ] || fail "expected one APK in $1, got ${#apks[@]}"; echo "${apks[0]}"; }
  inspect() { # <apk> <out-dir>: dex strings and permissions into files
    unzip -qo "$1" 'classes*.dex' -d "$2"
    strings "$2"/classes*.dex > "$2/strings.txt"
    "$aapt2" dump permissions "$1" > "$2/permissions.txt"
  }

  rm -rf glyph-sdk/libs
  ./gradlew -q :app:assembleSdkFreeRelease
  test ! -e glyph-sdk/libs || fail "sdkFree build fetched the SDK"
  apk=$(one_apk app/build/outputs/apk/sdkFree/release)   # a plain assignment, so set -e sees one_apk fail
  d=$(mktemp -d); inspect "$apk" "$d"
  grep -q 'Lcom/nothing/' "$d/strings.txt" && fail "SDK classes in sdkFree"
  grep -q 'com.nothing.ketchum.permission.ENABLE' "$d/permissions.txt" && fail "ENABLE permission in sdkFree"

  # positive control: the full build MUST contain both, or the checks above prove nothing
  ./gradlew -q :app:assembleFullDebug
  apk=$(one_apk app/build/outputs/apk/full/debug)
  e=$(mktemp -d); inspect "$apk" "$e"
  grep -q 'Lcom/nothing/ketchum/' "$e/strings.txt" || fail "control: full lacks SDK classes"
  grep -q 'com.nothing.ketchum.permission.ENABLE' "$e/permissions.txt" || fail "control: full lacks ENABLE permission"
  echo OK
  ```
  - The old `:app:dependencies | grep glyph-matrix-sdk` check is gone: a `files(…)` dependency never appears in that report, so it could not fail. "No `glyph-sdk/libs/` after a clean sdkFree release build" replaces it.
  - `strings` stays for the dex files: dex string pools are MUTF-8, so ASCII type descriptors like `Lcom/nothing/…` are visible to it.
  - Apply the same rule to every script in this plan: `grep -q` reads a file or a here-string (`<<<"$out"`), never a pipe.
- [ ] **Step 2: One task list, then `ci.yml`** (review r2 N15: CI and the verification-metadata command read the same file, so they can't drift).
  - **`scripts/ci-tasks.txt`:**
  ```
  # <job>: <gradle args>. Read by scripts/ci.sh (CI jobs, README, verification metadata).
  checks: ktlintCheck lintSdkFreeDebug :designsystem:lintDebug :core:test :agent:test :evals:test :designsystem:testDebugUnitTest :glyph-sdk:testSdkFreeDebugUnitTest :app:testSdkFreeDebugUnitTest :app:testSdkFreeReleaseUnitTest :spike:testSdkFreeDebugUnitTest :core:koverVerify :agent:koverVerify :glyph-sdk:koverVerifySdkFreeDebug :designsystem:koverVerifyDebug :app:koverVerifySdkFreeDebug :app:assembleSdkFreeDebug :spike:assembleSdkFreeDebug :build-logic:validatePlugins
  detekt: detekt :core:detektMain :agent:detektMain :evals:detektMain :designsystem:detektDebug :glyph-sdk:detektSdkFreeDebug :app:detektSdkFreeDebug :spike:detektSdkFreeDebug
  canary: -PwithCanary :detekt-canary:detektMain
  full: :glyph-sdk:testFullDebugUnitTest :glyph-sdk:koverVerifyFullDebug :glyph-sdk:koverXmlReportFullDebug lintFullDebug
  detekt-full: :glyph-sdk:detektFullDebug
  # tasks the check-*.sh scripts run; listed for verification metadata only
  scripts: :glyph-sdk:fetchGlyphSdk :glyph-sdk:assembleSdkFreeDebug :app:assembleSdkFreeRelease :app:assembleFullDebug :core:koverXmlReport :agent:koverXmlReport :designsystem:koverXmlReportDebug :glyph-sdk:koverXmlReportSdkFreeDebug :app:koverXmlReportSdkFreeDebug   (the :glyph-sdk entry is added in Task 4)
  ```
  - **`scripts/ci.sh`:**
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  cd "$(dirname "$0")/.."
  job=${1:?usage: ci.sh <checks|detekt|detekt-full|canary|full|verification-metadata>}
  if [ "$job" = verification-metadata ]; then
    mapfile -t all < <(grep -Ev '^(#|$)' scripts/ci-tasks.txt | sed 's/^[a-z-]*: //' | tr ' ' '\n' | grep -v '^$' | sort -u)
    exec ./gradlew --write-verification-metadata sha256 -Pcanary.ignoreFailures "${all[@]}"
  fi
  read -ra tasks <<<"$(sed -n "s/^$job: //p" scripts/ci-tasks.txt)"
  ((${#tasks[@]})) || { echo "unknown job: $job" >&2; exit 1; }
  case "$job" in
    detekt|detekt-full|canary) exec bash scripts/run-detekt.sh "${tasks[@]}" ;;
    *) exec ./gradlew "${tasks[@]}" ;;
  esac
  ```
  `-Pcanary.ignoreFailures` lets the metadata run resolve the canary's detekt classpath without failing on its deliberate findings. When a `check-*.sh` script gains a Gradle task, add it to the `scripts:` line in the same commit.
  - **`ci.yml`.** Triggers on push and PR. Uses `ubuntu-latest` and `actions/setup-java` v6 with Temurin **25** (the daemon JDK; see Global Constraints). Actions are pinned by full SHA. `permissions: contents: read`. Jobs:
    - **`checks`:** `bash scripts/ci.sh checks && bash scripts/guard-banned.sh && bash scripts/check-tooling-active.sh`
    - **`detekt`:** `bash scripts/ci.sh detekt && bash scripts/detekt-canary.sh`. `run-detekt.sh` (Task 2 Step 7) decides from the SARIF reports: exit 1 + "detekt findings: …" or exit 2 + "detekt crashed: tool error, not findings". Gradle's own exit code is never interpreted.
    - **`full-build`:** `bash scripts/check-glyph-sdk-fetch.sh && bash scripts/check-sdkfree-apk.sh && bash scripts/ci.sh full && bash scripts/ci.sh detekt-full`, then the same zero-lines check on `glyph-sdk/build/reports/kover/reportFullDebug.xml` (`:glyph-sdk:detektFullDebug` needs the fetched SDK, so it lives here)
- [ ] **Step 3: Dependency verification** (after the plugin set is final): `bash scripts/ci.sh verification-metadata`. Commit `gradle/verification-metadata.xml`, and put the same command in the README "Updating dependencies" section. [verify] that the included build `build-logic` is covered by the root file: after the run, `grep -c 'kotlin-dsl\|gradle-kotlin-dsl' gradle/verification-metadata.xml` is non-zero, and `./gradlew --refresh-dependencies :glyph-sdk:fetchGlyphSdk` passes verification; if not, record what Gradle requires for included builds and follow it.
- [ ] **Step 4: `renovate.json`.** `{"extends":["config:recommended"],"enabledManagers":["gradle","github-actions"],"packageRules":[{"matchPackageNames":["dev.detekt*"],"automerge":false}]}`
- [ ] **Step 5: `licences.md`.** A table of every M0 dependency: name, version, licence, GPL-3.0 compatible? (Y/N + note). The Nothing SDK row: EULA, linked via the §7 exception, never redistributed until permitted.
- [ ] **Step 6: `threat-model.md`.**
  - **Assets:** API keys (future), animations, notification content, the device panel.
  - **Entry points:**
    - imported files (future)
    - notification text
    - LLM and LAN endpoints (future)
    - the SDK fetch (pinned commit and SHA)
    - Gradle plugins (verification metadata)
    - the spike's DUMP-guarded receiver (debug only)
    - adb and `run-as` (debug only)
    - CI (no secrets in M0; actions pinned by SHA; read-only token)
    - Robolectric's runtime download of `android-all-instrumented` jars, which bypasses Gradle dependency verification (open risk; candidate fix in M1: pre-resolve via a Gradle configuration + `robolectric.offline=true`; review r3 R-L5)
    - the Glyph ENABLE permission's scope
  - **Mitigations:** mapped to spec §3.5, §5.5 and §9.
  - **Open risks.**
- [ ] **Step 7: Run CI.** Ask the user for permission to push `m0-foundations`, then push. Watch the run with `curl -fsSL "https://api.github.com/repos/S-GirishShiva/GlyphWizard/actions/runs?branch=m0-foundations&per_page=1"`, or install `github-cli` with the user's OK. All three jobs must be green.
- [ ] **Step 8: Learning asides:**
  - GitHub Actions (like a CI pipeline file)
  - Gradle dependency verification (like a `go.sum`)
  - Renovate
  - why a positive control proves that a negative check works
- [ ] **Step 9: Commit.** Slug: `m0-ci`.

### Task 8: M0 exit review
- [ ] **Step 1: Check spec §8 M0 exit criteria.**
  - the spike-results doc is complete
  - D6, D10, D28, D33 and D37 are confirmed or amended from measurements
  - D5x (Java versions) reflects the Task 2 gate's outcome (25 or 21 on Android modules)
  - CI is green
  - the app installs with the real ID
  - the mirror works, proven by `MirroringGlyphDisplayTest` (the sink receives the `TextGrid`), `AppContainerTest` (debug wires a `MirroringGlyphDisplay`, release doesn't) and the on-device spike `DUMP` (Task 5 Step 7.2, same class). `:app` has no glyph-playing UI in M0 (review r3 R-M2)
- [ ] **Step 2:** Run `/code-review` on `m0-foundations`. Resolve findings, with refactors in separate commits.
- [ ] **Step 3: Update** `docs/HANDOFF.md` (next: the D46 architecture deep-dive, then the M1 plan) and the `project-state` memory.
- [ ] **Step 4:** Merge into the branch agreed in Task 1, after the user's OK. Push only when asked.

## Review responses (rev 1 → rev 2)
- **All CRITICAL and HIGH findings are applied as specified.**
- **M1:** `TextGrid` and `MirroringGlyphDisplay` are durable. PNG dumps are deferred to M2 (recorded in Task 4 Step 6).
- **M17:** the spike's exemptions are now a user decision (Task 1 answer 4) instead of an assumption.
- **L8:** `DeviceProfile`/`TextGrid` are marked provisional for D46.
- **Rejected: none.**

## Review responses (rev 2 → rev 3, review r2)
- **N1:** `:glyph-sdk` uses `fullCompileOnly` + `testFullImplementation`; `:app`/`:spike` use `fullRuntimeOnly` via the `glyphSdk` consumable configuration (Task 3 Step 1). AGP's acceptance of that shape is marked [verify] with a fallback.
- **N2:** `FetchGlyphSdk` lives in the included build `build-logic/` as plugin `glyphwizard.glyph-sdk-fetch`; helpers are members using `java.util.HexFormat`; a configuration-cache reuse check is in Task 3 Step 2.
- **N3/N6:** concrete `detekt.yml`; type-resolved `detektMain` tasks; `:detekt-canary` JVM module behind `-PwithCanary` expecting `UnsafeCallOnNullableType`, `GlobalCoroutineUsage`, `ForbiddenMethodCall`; mutable `object` checked by `guard-banned.sh`; `run-detekt.sh` classifies by SARIF and is tested on both paths. Android type resolution stays [verify] (detekt #9402).
- **N4:** option (a), D49 amended: plain JUnit 4 + Robolectric on Android modules.
- **N5/N11:** file-based `grep`, `aapt2 dump permissions`, positive controls for classes and permission, vacuous classpath check replaced.
- **N7:** `NothingGlyphDisplay` given in full with atomic flags, single resume, re-connectable `release`, unbind on rejection and on `close` failure. `GlyphException` mapping moved into `SdkMatrixService` so only the adapter touches `com.nothing.*`.
- **N8/N17:** patterns run in a `specialUse` debug FGS (`SpikePatternService`) that also owns the receiver; `SpikeShortService` runs until `onTimeout` and is only the §10 interplay probe; whether the app matrix survives without a visible Activity is a recorded measurement.
- **N9, N10, N12–N16:** as specified (`by lazy`; full listener XML; `fullDebug` Kover gate; `:designsystem:lintDebug`; `src/debug`/`src/release` `wrapForBuild`; `scripts/ci-tasks.txt` + `scripts/ci.sh`; `GlyphSession` with `profile`, `TimeSource.WithComparableMarks` and absolute deadlines).
- **L1–L6:** `SdkMatrixService` given (Step 4a); `setGlyphMatrixTimeout` exposed as `GlyphDisplay.setAutoTimeout` instead of a spike-only extension; tag → commit SHA lookup; per-module ktlint plugin; device ID from `encoder.profile.id`; SDK URL/SHA in `gradle.properties`; tests no longer construct `GlyphException`.
- **Rejected: none.** Deviation: L1's auto-timeout became a `GlyphDisplay` method (durable) rather than a spike-only hook, since §10 needs the behaviour and the spike can only reach `:glyph-sdk` through `GlyphDisplay`.

## Review responses (rev 3 → rev 4, review r3 + research)
- **User direction:** modern Java. Daemon and toolchain on JDK 25 (LTS; Gradle 9.6 can't run 27). Android modules target 25 behind a Task 2 gate (fallback 21). `:core`/`:agent` use `-Xjdk-release=17` because their code runs on ART. Logged as D5x. The origin of "17" is recorded in Global Constraints.
- **R-H1:** superseded by the Java table; Android modules never set a toolchain; CI uses Temurin 25.
- **R-H2:** `compose-ui-test-manifest` as `debugImplementation` (confirmed: `testImplementation` doesn't merge it).
- **R-H3:** ktlint-gradle ≥ 14.1.0 + ktlint 1.8.0; `scripts/check-tooling-active.sh` (ktlint tasks exist; Kover XML counts > 0 lines; vacuous Kover pass confirmed in the intellij-coverage `Verifier`); Review Focus 9.
- **R-M1:** bucket edges 29→`.`, 30→`:`; dead `coerceAtMost` removed.
- **R-M2:** the mirror exit criterion is proven by tests + the spike `DUMP`.
- **R-M3:** `stop()` cancels and joins, then releases on the container's process-lifetime scope (not `lifecycleScope`, which `onDestroy` cancels).
- **R-M4:** 30 s connect / 60 s read timeouts; `@DisableCachingByDefault`; strict `validatePlugins` in CI.
- **R-M5:** commands serialised through one `Channel`; the session starts only on `connect()` `Ok`.
- **R-M6:** refuted. The `testScheduler.*` APIs used are stable in 1.11.0, so no `@OptIn`. User rule: any future opt-in needs a decision entry.
- **R-M7:** explicit backing fields in `GlyphSession`, after a Task 2 parse canary.
- **R-M8:** `guard-banned.sh` gains #17 layouts and the #12/#20 dependency bans; #25 deferred to M1.
- **R-M9:** Task 6 amends D28 and D33 too; Task 8 checks them.
- **R-L1:** refuted (`kotlin-dsl` applies assignment to plain `.kt`). **R-L2–R-L8:** applied as written.
- **Research extras:**
  - `ForbiddenImport` key is `forbiddenImports` (`imports:` would crash under `validation: true`).
  - AGP 9 creates no release unit-test tasks by default → `android.onlyEnableUnitTestForTheTestedBuildType=false` [verify].
  - detekt Android variant tasks exist in alpha.6; #9402 is a type-resolution false-positive risk, not missing tasks. They run in CI, and a task is dropped only on proven false positives.
  - Plain `detekt` skips flavour/build-type source sets → added explicitly.
  - Kover pinned to 0.9.11 (0.9.10 is broken under the configuration cache).
  - Robolectric 4.17; detekt alpha.6.
  - No repeat `startForeground` is needed for an already-foreground service (AOSP `ActiveServices`).
- **Recorded, not fixed:** about 4 lines of quality wiring repeated per module. Convention plugin → D46 deep-dive agenda.
- **Rejected: none.**
- **Spot check (subagent, after rev 4):** fixed 3 blockers in place:
  - `:glyph-sdk` Kover check moved to Task 4;
  - `JvmTarget` import;
  - `runCurrent()` after `stop()`.

  Also adopted:
  - `stop(): Job` joined before a new display connects;
  - `createAndroidComposeRule` in `MainActivityTest`;
  - `JavaCompile.options.release = 17` on `:core`/`:agent`.
