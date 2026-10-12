# M0 — Foundations & Hardware Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Project override (D45):** this project does **not** use TDD. For each task: implement, then write the comprehensive test suite, run it, then commit. Every agent first follows `CLAUDE.md` → `docs/engineering/code-guidelines.md` §1 (discover → understand every touched flow → state the plan → code).

**Goal:** turn the Android Studio template into the real multi-module GlyphWizard project (identity, licence, flavours, CI, Glyph SDK bridge), then build a throwaway spike app that drives the 4a Pro Glyph Matrix so we can **measure** the hardware facts the spec marks [HW].

**Architecture:**
- **Modules:** six empty-but-wired modules per spec §2.1 (`:core`, `:agent`, `:designsystem`, `:glyph-sdk`, `:app`, `:evals`) plus one debug-only `:spike` app.
- **SDK isolation:** the Nothing SDK sits behind a consumer-defined `GlyphDisplay` interface in `:glyph-sdk`. The `sdkFree` flavour binds a no-op implementation; `full` binds the real one.
- **Spike lifetime:** `:spike` is deleted at the end of M1. Its only durable output is `docs/research/hardware-spike-results.md`.
- **Scope:** the internal `:core` architecture is deliberately *not* designed here (D46).

**Scratch dir:** `SCRATCH=$(mktemp -d)` before any step that uses `$SCRATCH`.

**Tech stack:** Kotlin (AGP 9 built-in Kotlin), Gradle KTS + version catalog, Jetpack Compose (`:app`/`:spike`), JUnit 5 + kotlin.test on JVM modules, JUnit 4 + Robolectric on Android modules, Kover, ktlint, detekt, Android Lint, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-12-glyphwizard-v1-design.md` (§2, §7, §8 M0 row, §9, §10). Also read `CLAUDE.md`, `docs/engineering/code-guidelines.md` and `docs/research/decisions.md` (D18, D26, D28, D31, D44–D46).

## Global Constraints
- minSdk **35**, targetSdk **36**, compileSdk **37**; JDK **17** toolchain.
- Application ID **`io.github.sgirishshiva.glyphwizard`** (proposed from the GitHub handle). **Confirm with the user in Task 1 before writing it anywhere.** It is permanent.
- Licence: **GPL-3.0-or-later** plus an additional permission for linking the Nothing Glyph SDK; a NOTICE reserves the name and icon. Confirm with the user in Task 1.
- The Nothing SDK `.aar` is **never committed**. It is fetched at build time, pinned by SHA-256, and only for the `full` flavour (D18).
- `sdkFree` and `full` product flavours; the `sdkFree` APK must contain **no** `com.nothing.*` classes (spec §9).
- Commit format: `<slug> [Siddanth, Claude] - message`, blank line, details, `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Branch **`m0-foundations`** from `design/v1-spec`.
- Device facts (GDK, verified):
  - 4a Pro = `Glyph.DEVICE_25111p`, 13×13 grid, 137 LEDs, rows `[5,9,11,11,13,13,13,13,13,11,11,9,5]`
  - app-driven display = `setAppMatrixFrame(int[])` / `closeAppMatrix()`
  - permission `com.nothing.ketchum.permission.ENABLE`
- Every new Android/Kotlin concept gets a **Learning aside** in the task's commit details or in `docs/learning/m0.md` (D23).

## Review Focus
1. **Missing SDK file / offline build:** `full` must fail with a clear message naming the URL and the expected SHA; `sdkFree` must build with no network. (Task 3 test.)
2. **SHA mismatch:** a tampered or updated `.aar` must fail the build, never silently pass. (Task 3 test.)
3. **Glyph service not available** (non-Nothing phone, SDK service missing, permission denied): `GlyphDisplay.connect()` returns an error value; the spike shows it and nothing crashes. (Task 4 test.)
4. **Frame array length:** pushing a 137-value array vs a 169-value full-square array must be handled deliberately. The bridge pads/masks to the length the device accepts, and that length is a *measured* setting. (Task 4 test + Task 6 measurement.)
5. **Leaving the app mid-pattern:** the matrix must be released (`closeAppMatrix`) when the spike goes to the background or is killed, so the panel never stays stuck lit. (Task 5 test + manual check.)

---

## File structure (end of M0)
```
settings.gradle.kts                      modules + repositories
build.gradle.kts                         root: plugins apply false, ktlint/detekt/kover wiring
gradle/libs.versions.toml                every version pinned here only
build-logic/                             (not in M0: convention plugins were judged not worth it yet, per app-foundations report)
LICENSE  NOTICE  README.md
core/build.gradle.kts                    kotlin("jvm"), empty src + one smoke test
agent/build.gradle.kts                   kotlin("jvm"), depends on :core, empty + smoke test
evals/build.gradle.kts                   kotlin("jvm") application, depends on :core :agent, empty + smoke test
designsystem/build.gradle.kts            android library + Compose, empty + smoke test
glyph-sdk/
  build.gradle.kts                       android library; flavours; fetch task for full
  src/main/kotlin/.../glyph/GlyphDisplay.kt        interface + MatrixSpec + GlyphError
  src/main/kotlin/.../glyph/MatrixFrameEncoder.kt  137-LED values -> device int[]
  src/sdkFree/kotlin/.../glyph/GlyphDisplays.kt    factory returning NoGlyphDisplay
  src/full/kotlin/.../glyph/GlyphDisplays.kt       factory returning NothingGlyphDisplay
  src/full/kotlin/.../glyph/NothingGlyphDisplay.kt real SDK bridge
  src/test/...                                      encoder + no-op tests
  gradle/fetch-glyph-sdk.gradle.kts                download + SHA-256 verify
app/                                     Compose shell: GlyphWizardApplication + AppContainer + MainActivity (placeholder screen)
spike/                                   debug-only app: pattern buttons, mirror, measurement helpers
.github/workflows/ci.yml                 build, tests, ktlint, detekt, lint, kover, sdkFree-no-SDK check
config/detekt/detekt.yml
docs/research/hardware-spike-results.md  filled in Task 6
docs/engineering/threat-model.md         Task 7
docs/learning/m0.md                      learning asides collected
```

---

### Task 1: Identity, licence, branch

**Files:** Create `LICENSE`, `NOTICE`, `README.md`; modify `.gitignore`.

**Interfaces:** Produces the application ID and namespace root `io.github.sgirishshiva.glyphwizard`, used by every later task.

- [ ] **Step 1: Confirm with the user** (blocking). Ask: *"Permanent application ID `io.github.sgirishshiva.glyphwizard`? Licence GPL-3.0-or-later plus a Nothing SDK linking exception?"* Use their answers verbatim everywhere below. If the ID changes, replace the string throughout this plan before continuing.
- [ ] **Step 2: Create the branch.**
  ```bash
  git switch design/v1-spec && git pull && git switch -c m0-foundations
  ```
- [ ] **Step 3: `LICENSE`.** The full GPL-3.0 text (`curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE`), followed by this exact appended section:
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
- [ ] **Step 4: `NOTICE`.**
  ```
  GlyphWizard
  Copyright (C) 2026 Siddanth and contributors

  The name "GlyphWizard" and its logo are not licensed under the GPL and may
  not be used for derived works without permission.

  GlyphWizard is an independent, non-commercial project and is not affiliated
  with or endorsed by Nothing Technology Ltd. "Nothing" and "Glyph" are
  trademarks of their owner.
  ```
- [ ] **Step 5: `README.md`.** Cover: what the app is, "not affiliated with Nothing", build instructions (`./gradlew :app:assembleSdkFreeDebug`; the `full` flavour fetches the SDK and needs network), a link to the spec, and the licence line.
- [ ] **Step 6: Commit.** Slug: `m0-identity`.

### Task 2: Multi-module scaffold & build migration

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Move: `app/src/*/java/com/example/glyphwizard/*` → `app/src/*/kotlin/io/github/sgirishshiva/glyphwizard/`
- Create: the module dirs listed under File structure, `config/detekt/detekt.yml`, `docs/learning/m0.md`

**Interfaces:**
- Produces Gradle paths `:core :agent :evals :designsystem :glyph-sdk :app :spike`.
- Produces flavour dimension `sdk` with flavours `sdkFree` and `full`, declared identically in `:glyph-sdk`, `:app` and `:spike`.

- [ ] **Step 1: Discover.** Read the current `build.gradle.kts`, `app/build.gradle.kts` and `libs.versions.toml`. Note that AGP 9.4.1 is present. Read the AGP 9 built-in Kotlin notes: `android docs search "AGP 9 built-in Kotlin"` (the `android` CLI).
- [ ] **Step 2: Pin versions.** Get each latest **stable** version, choosing ones at least 2 weeks old:
  ```bash
  for a in androidx/compose/compose-bom androidx/activity/activity-compose androidx/lifecycle/lifecycle-runtime-ktx androidx/navigation3/navigation3-runtime androidx/datastore/datastore-preferences; do
    echo "$a: $(curl -fsSL https://dl.google.com/android/maven2/$a/maven-metadata.xml | grep -o '<release>[^<]*' | cut -d'>' -f2)"; done
  for a in org/jetbrains/kotlinx/kotlinx-serialization-json org/jetbrains/kotlinx/kotlinx-coroutines-core org/jetbrains/kotlinx/kotlinx-collections-immutable io/ktor/ktor-client-core org/junit/jupiter/junit-jupiter org/robolectric/robolectric io/gitlab/arturbosch/detekt/detekt-gradle-plugin org/jetbrains/kotlinx/kover-gradle-plugin; do
    echo "$a: $(curl -fsSL https://repo1.maven.org/maven2/$a/maven-metadata.xml | grep -o '<release>[^<]*' | cut -d'>' -f2)"; done
  ```
  Write every version into `gradle/libs.versions.toml` only (R1 spirit: one place). Record the date pinned in a comment at the top.
- [ ] **Step 3: `settings.gradle.kts`.** Set `rootProject.name = "GlyphWizard"` and include all seven modules. Keep the existing repositories block.
- [ ] **Step 4: JVM modules** (`core`, `agent`, `evals`). Each `build.gradle.kts`:
  ```kotlin
  plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kover) }
  kotlin { jvmToolchain(17) }
  dependencies {
      testImplementation(libs.junit.jupiter)
      testImplementation(libs.kotlin.test)
  }
  tasks.test { useJUnitPlatform() }
  ```
  Dependencies: `agent` → `implementation(project(":core"))`; `evals` adds the `application` plugin and depends on `:core :agent`. In `core/src/main/kotlin/io/github/sgirishshiva/glyphwizard/core/` add the one fact M0 needs:
  ```kotlin
  data class DeviceProfile(val id: String, val gridSize: Int, val rowWidths: List<Int>) {
      val ledCount: Int get() = rowWidths.sum()
      fun isLed(x: Int, y: Int): Boolean {
          if (y !in rowWidths.indices || x !in 0 until gridSize) return false
          val inset = (gridSize - rowWidths[y]) / 2
          return x in inset until inset + rowWidths[y]
      }
      companion object {
          val PHONE_4A_PRO = DeviceProfile("25111p", 13, listOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5))
      }
  }
  ```
- [ ] **Step 5: Android modules.** In `designsystem`, `glyph-sdk`, `app` and `spike`: set namespaces under `io.github.sgirishshiva.glyphwizard.<module>`, `compileSdk = 37`, `minSdk = 35`, `targetSdk = 36` (apps only), JDK 17 toolchain, and Compose enabled in `designsystem`/`app`/`spike`. Product flavours in `glyph-sdk`, `app` and `spike`:
  ```kotlin
  flavorDimensions += "sdk"
  productFlavors { create("sdkFree") { dimension = "sdk" }; create("full") { dimension = "sdk" } }
  ```
  - `:app`: `applicationId = "io.github.sgirishshiva.glyphwizard"`.
  - `:spike`: `applicationId = "io.github.sgirishshiva.glyphwizard.spike"`. It's debug-only by convention: never released, and excluded from release CI.
- [ ] **Step 6: Replace the Views template in `:app`.** Remove the appcompat/material dependencies and the `Theme.*` XML parents. Add:
  - `GlyphWizardApplication : Application` with `val container = AppContainer(this)`
  - `AppContainer(context: Context)`: the manual DI root (D44), empty for now
  - `MainActivity : ComponentActivity` with `setContent { Text("GlyphWizard") }` and edge-to-edge

  Delete `ExampleUnitTest` and `ExampleInstrumentedTest`.
- [ ] **Step 7: Root lint wiring.** Apply ktlint (official style, `max_line_length = 120` in `.editorconfig`), detekt (config `config/detekt/detekt.yml`, built-in defaults plus `maxIssues: 0`) and Kover. Add the compose-rules ktlint ruleset to the Compose modules.
- [ ] **Step 8: Learning asides** in `docs/learning/m0.md`, 2–4 sentences each plus a link:
  - Gradle modules (like Go modules / packages in one repo)
  - version catalogs
  - product flavours (like build tags)
  - `Application` vs `Activity` (process entry vs screen)
  - manual DI container (like wiring in `main.go`)
- [ ] **Step 9: Tests (after the code).** In `core/src/test/.../DeviceProfileTest.kt`:
  ```kotlin
  class DeviceProfileTest {
      private val p = DeviceProfile.PHONE_4A_PRO
      @Test fun `4a Pro has 137 LEDs`() = assertEquals(137, p.ledCount)
      @Test fun `corners are not LEDs`() { assertFalse(p.isLed(0, 0)); assertFalse(p.isLed(12, 12)) }
      @Test fun `top row spans columns 4 to 8`() { assertTrue((4..8).all { p.isLed(it, 0) }); assertFalse(p.isLed(3, 0)) }
      @Test fun `middle row is full width`() = assertTrue((0..12).all { p.isLed(it, 6) })
      @Test fun `out of range is false`() { assertFalse(p.isLed(-1, 6)); assertFalse(p.isLed(6, 13)) }
      @Test fun `mask matches ledCount`() = assertEquals(137, (0..12).sumOf { y -> (0..12).count { x -> p.isLed(x, y) } })
  }
  ```
  Smoke tests in `agent`/`evals`/`designsystem` assert the module compiles: one trivial test each, which keeps CI meaningful.
- [ ] **Step 10: Verify.**
  ```bash
  ./gradlew :core:test :agent:test :evals:test :app:assembleSdkFreeDebug :spike:assembleSdkFreeDebug ktlintCheck detekt lint
  ```
  Expected: BUILD SUCCESSFUL. `:core` coverage is 100% (`./gradlew :core:koverVerify` with a 100% line rule configured in `core/build.gradle.kts`).
- [ ] **Step 11: Install and check the real ID.** Run `$HOME/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/sdkFree/debug/app-sdkFree-debug.apk`, then confirm with `adb shell pm list packages | grep glyphwizard`.
- [ ] **Step 12: Commit.** Slug: `m0-scaffold`.

### Task 3: Glyph SDK fetch (full flavour only, SHA-pinned)

**Files:** Create `glyph-sdk/gradle/fetch-glyph-sdk.gradle.kts`; modify `glyph-sdk/build.gradle.kts`, `.gitignore` (`glyph-sdk/libs/`).

**Interfaces:** Produces the Gradle task `:glyph-sdk:fetchGlyphSdk`, plus `fullImplementation(files("libs/glyph-matrix-sdk.aar"))` resolved only for `full`.

- [ ] **Step 1: Find the canonical URL and record the SHA.**
  ```bash
  gh api repos/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/contents --jq '.[].name'
  curl -fsSL -o $SCRATCH/glyph.aar "https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/<exact .aar name from listing>"
  sha256sum $SCRATCH/glyph.aar
  ```
  Put the URL and hash into `libs.versions.toml` under `[versions]` as `glyphSdkUrl` / `glyphSdkSha256`.
- [ ] **Step 2: Implement the fetch task.** It downloads to `glyph-sdk/libs/glyph-matrix-sdk.aar` only if the file is missing or its hash differs, verifies SHA-256, and on mismatch deletes the file and fails with `"Glyph SDK SHA-256 mismatch: expected <x>, got <y>. URL: <url>"`. On network failure it fails with `"Could not download the Glyph SDK from <url> (needed for the 'full' flavour; build 'sdkFree' to skip)."`. Wire `preBuild` for **full** variants only to depend on it:
  ```kotlin
  androidComponents.onVariants(selector().withFlavor("sdk" to "full")) { v ->
      tasks.named("pre${v.name.replaceFirstChar(Char::uppercase)}Build") { dependsOn("fetchGlyphSdk") }
  }
  ```
- [ ] **Step 3: Tests (after).** Add `scripts/check-glyph-sdk-fetch.sh` (run locally and in the CI `full-build` job). It asserts these four behaviours with exit codes and grepped messages:
  ```bash
  set -euo pipefail
  rm -rf glyph-sdk/libs
  ./gradlew :glyph-sdk:fetchGlyphSdk                        # 1. clean fetch passes
  printf 'x' >> glyph-sdk/libs/glyph-matrix-sdk.aar         # 2. tampered file fails
  ! ./gradlew :glyph-sdk:fetchGlyphSdk 2>&1 | tee /dev/stderr | grep -q 'BUILD SUCCESSFUL'
  ./gradlew :glyph-sdk:fetchGlyphSdk 2>&1 | grep -q 'SHA-256 mismatch' || true
  rm -rf glyph-sdk/libs                                     # 3. offline full fails with the download message
  ./gradlew --offline :glyph-sdk:fetchGlyphSdk 2>&1 | grep -q 'Could not download the Glyph SDK'
  ./gradlew --offline :glyph-sdk:assembleSdkFreeDebug       # 4. sdkFree never needs the SDK
  ```
  Note: assertion 2 checks that the tampered file is re-fetched or rejected. Pick the behaviour from Step 2 (delete and re-download, then verify), and make the script assert exactly that.
- [ ] **Step 4: Commit.** Slug: `m0-sdk-fetch`.

### Task 4: `GlyphDisplay` bridge (sdkFree no-op + full Nothing implementation)

**Files:**
- `glyph-sdk/src/main/kotlin/io/github/sgirishshiva/glyphwizard/glyph/GlyphDisplay.kt`
- `.../MatrixFrameEncoder.kt`
- `glyph-sdk/src/sdkFree/kotlin/.../GlyphDisplays.kt`
- `glyph-sdk/src/full/kotlin/.../GlyphDisplays.kt`
- `.../NothingGlyphDisplay.kt`
- `glyph-sdk/src/full/AndroidManifest.xml`
- tests under `glyph-sdk/src/test/`

**Interfaces (produced, used by Task 5 and later milestones):**
```kotlin
sealed interface GlyphError {
    data object Unsupported : GlyphError                 // sdkFree build or non-Nothing device
    data object ServiceUnavailable : GlyphError
    data class Rejected(val message: String) : GlyphError
}
sealed interface GlyphResult<out T> { data class Ok<T>(val value: T) : GlyphResult<T>; data class Err(val error: GlyphError) : GlyphResult<Nothing> }

interface GlyphDisplay {
    suspend fun connect(): GlyphResult<Unit>
    fun show(brightness: IntArray): GlyphResult<Unit>    // one value 0..255 per LED of the profile, row-major over LEDs
    fun release()
}
object GlyphDisplays { fun create(context: Context, encoder: MatrixFrameEncoder): GlyphDisplay }

class MatrixFrameEncoder(private val profile: DeviceProfile, private val layout: Layout, private val maxValue: Int) {
    enum class Layout { LedsOnly, FullSquare }          // 137 values vs 169; measured in Task 6
    fun encode(brightness: IntArray): IntArray
}
```
- `GlyphResult` and `GlyphError` are local to `:glyph-sdk` on purpose: the shared result type is designed in the D46 deep-dive.
- `:glyph-sdk` depends on `:core` for `DeviceProfile`.

- [ ] **Step 1: Discover the real SDK API.** Unzip the fetched `.aar`, list its classes, and confirm the exact package and class names, then read the GDK README API section:
  ```bash
  unzip -o glyph-sdk/libs/glyph-matrix-sdk.aar classes.jar -d $SCRATCH/aar && unzip -l $SCRATCH/aar/classes.jar | grep -i -E 'glyph|matrix'
  ```
  Use the names found (expected: `com.nothing.ketchum.GlyphMatrixManager`, `com.nothing.ketchum.Glyph`). Update this task's code to match before writing it.
- [ ] **Step 2: Implement `MatrixFrameEncoder`.**
  - `require(brightness.size == profile.ledCount)` → otherwise throw `IllegalArgumentException("expected ${profile.ledCount} values, got ${brightness.size}")`. A wrong size is a programmer error.
  - Clamp to 0..255, then scale to `maxValue` (255 or 2047, measured): `v * maxValue / 255`.
  - `LedsOnly` returns the scaled array.
  - `FullSquare` writes LED values into a `gridSize²` array at their (x, y) positions, with 0 elsewhere.
- [ ] **Step 3: Implement the sdkFree factory.** `GlyphDisplays.create` returns `NoGlyphDisplay`: `connect()` → `Err(Unsupported)`, `show()` → `Err(Unsupported)`, `release()` → no-op.
- [ ] **Step 4: Implement `NothingGlyphDisplay`.**
  - Add `<uses-permission android:name="com.nothing.ketchum.permission.ENABLE"/>` to the `full` manifest.
  - `connect()` suspends until `GlyphMatrixManager.init(callback)` reports connected (`suspendCancellableCoroutine`), then calls `register(Glyph.DEVICE_25111p)`. A missing service or a `SecurityException` maps to `Err(ServiceUnavailable)` / `Err(Rejected(msg))`.
  - `show()` calls `setAppMatrixFrame(encoder.encode(brightness))`.
  - `release()` calls `closeAppMatrix()` then `unInit()`, and is idempotent.
- [ ] **Step 5: Tests (after).** In `MatrixFrameEncoderTest`:
  - `LedsOnly` returns 137 values
  - `FullSquare` returns 169 values, with corners 0, the LED at (6,0) taking input index 2, and the last LED at (8,12)
  - clamping: -5→0, 300→255
  - scaling to 2047: 255→2047, 128→1027
  - a wrong input size throws with the message above

  In `NoGlyphDisplayTest`: every call returns `Unsupported`, and `release()` is safe to call twice. `NothingGlyphDisplay` is exercised on the device in Task 5 (no SDK mocks; per the guidelines we prefer real behaviour over mock-pinned tests).
- [ ] **Step 6: Coverage.** Kover ≥ 80% for `:glyph-sdk` sdkFree (Android module target).
- [ ] **Step 7: Learning asides** (to `docs/learning/m0.md`):
  - source sets per flavour
  - `suspendCancellableCoroutine`, which turns a callback into a suspend call (like wrapping a callback in a Go channel)
  - why an interface is defined at the consumer
- [ ] **Step 8: Commit.** Slug: `m0-glyph-bridge`.

### Task 5: Spike app (throwaway) — patterns + debug glyph mirror

**Files:**
- `spike/src/main/kotlin/io/github/sgirishshiva/glyphwizard/spike/SpikeActivity.kt`
- `.../Patterns.kt`
- `.../MirrorLog.kt`
- `.../MirrorReceiver.kt`
- `spike/src/main/AndroidManifest.xml`
- `spike/src/test/.../PatternsTest.kt`, `MirrorLogTest.kt`

**Interfaces:**
- Consumes `GlyphDisplays.create`, `MatrixFrameEncoder`, `DeviceProfile.PHONE_4A_PRO`.
- Produces adb hooks for Task 6:
  - `adb shell am broadcast -a io.github.sgirishshiva.glyphwizard.spike.SHOW --es pattern <name> [--ei level N] [--ei fps N] [--es layout leds|square] [--ei max 255|2047]`
  - `... .DUMP`, which writes the last frame as a text grid to logcat tag `GlyphMirror`

- [ ] **Step 1: Implement `Patterns`.** Pure functions `(profile, tick) -> IntArray`:
  - `checker`
  - `all(level)`
  - `ramp`: 8 bands, 0..255 left to right
  - `rowsSweep`: one row lit per tick
  - `single(x, y)`
  - `flash(hz)`: alternates all 255 / all 0 at the given rate
  - `cornerProbe`: lights only the first and last LED of each row, to detect ordering
- [ ] **Step 2: Implement `MirrorLog`.** `toTextGrid(profile, brightness): String`:
  - 13 lines, one char per cell, using levels ` .:-=+*#%@`
  - a space outside the mask, `·` for an unlit LED
  - logged with `Log.i("GlyphMirror", …)` on every `show` and on `DUMP`
- [ ] **Step 3: Implement `SpikeActivity`.**
  - **Layout:** a Compose list of pattern buttons; a frame-rate slider (5–120 fps); layout (`LedsOnly`/`FullSquare`) and maxValue (255/2047) toggles; a status line showing the `GlyphResult`.
  - **Lifecycle:** `connect()` in `onStart`, `release()` in `onStop` (Review Focus 5). A coroutine loop pushes frames at the chosen fps and records the actual achieved fps (frames shown per second, measured with `SystemClock.elapsedRealtimeNanos`).
  - **Receivers:** `MirrorReceiver` (not exported; registered in `onStart` with `RECEIVER_NOT_EXPORTED`) handles `SHOW`/`DUMP` from adb via `adb shell am broadcast -p io.github.sgirishshiva.glyphwizard.spike …`.
  - **Debug-only:** use no `:designsystem` (UI rules don't apply to throwaway debug code). It's stated in the file header as the only allowed comment: `// Throwaway M0 spike (D46/spec §8). Deleted at end of M1.`
- [ ] **Step 4: Tests (after).**
  - **`PatternsTest`:**
    - each pattern returns 137 values in 0..255
    - `checker` alternates
    - `ramp` is monotonic by band
    - `flash(10)` toggles every 50 ms of ticks at 20 ms per tick (tick→state table)
    - `cornerProbe` lights exactly 26 LEDs
  - **`MirrorLogTest`:** the grid has 13 lines of 13 chars; all-255 has `@` at every LED and spaces at the 32 corners; all-0 shows `·`.
- [ ] **Step 5: Manual device check.**
  ```bash
  ./gradlew :spike:installFullDebug && $HOME/Android/Sdk/platform-tools/adb shell am start -n io.github.sgirishshiva.glyphwizard.spike/.SpikeActivity
  ```
  Tap `checker` and flip the phone: a checkerboard should show. Press home: the matrix must turn off within about 1 s. `adb logcat -s GlyphMirror` shows the grid.
- [ ] **Step 6: Commit.** Slug: `m0-spike`.

### Task 6: Hardware measurements (human + agent, results doc)

**Files:** Create `docs/research/hardware-spike-results.md`.

**Interfaces:** Produces measured values that later tasks and milestones depend on:

| Value | Feeds |
|---|---|
| `frameLayout` | Task 4 encoder default |
| `maxValue` | Task 4 encoder default |
| `brightnessCurve` | spec UI11 |
| `minVisibleLevel` | — |
| `maxSustainedFps` | D10 tick ceiling |
| flash luminance inputs | D37 |
| priority behaviour | — |
| restricted-settings behaviour | — |
| process-state facts | — |

- [ ] **Step 1: Layout and ordering.**
  1. `cornerProbe` with `layout=leds`, then with `layout=square`.
  2. Note which shows lit LEDs exactly at row ends, and whether either is mirrored or rotated (photograph the back).
  3. Record the winning layout plus any transform needed.
- [ ] **Step 2: Brightness range.** `all` at levels 64/128/255 with `max=255`, then again with `max=2047`. Does 2047 look brighter than 255? Record the true max value.
- [ ] **Step 3: Response curve.**
  1. `ramp` and `all(level)` at 8, 16, 32, 64, 96, 128, 192, 255.
  2. Measure with a light meter or phone camera (manual exposure, fixed ISO/shutter, RAW if available) and record relative luminance.
  3. Fit a curve and record the table.
  4. Find the minimum level that is visibly distinguishable from off.
- [ ] **Step 4: Frame rate.** `rowsSweep` at 30/50/60/90/120 fps for 60 s each. Record the achieved fps (from the status line) and any visual tearing or dropping. The sustained max gives the D10 tick ceiling.
- [ ] **Step 5: Flash inputs.**
  1. `flash(10)` and `flash(15)`, filmed in 240 fps slow-motion: confirm on/off timing.
  2. Measure the matrix's physical diameter with calipers or a ruler, and its luminance at 255.
  3. Record PWM flicker visible in slow-motion, if any.
- [ ] **Step 6: Priority and states.**
  1. While `all(128)` plays, send yourself a WhatsApp message whose app has a Nothing Essential Notification set, and record which wins.
  2. Repeat with the phone locked, face-down, face-up, and in a pocket (screen-off proximity).
  3. Enable an AOD Glyph Toy and record the interaction.
  4. Find and record the **exact settings path** for Nothing's per-app Glyph notification setting.
- [ ] **Step 7: Install path and process.**
  1. Install the spike APK via **Obtainium** from a test GitHub release (or `adb install` as fallback). Open Settings → Notification access for the spike: is "Restricted setting" shown?
  2. Run `adb shell dumpsys activity processes | grep spike` while backgrounded (cached or frozen state).
- [ ] **Step 8: Write `hardware-spike-results.md`.** One table per step, each value tagged `[measured YYYY-MM-DD]`, with photos referenced as `docs/research/spike-photos/*` (commit only small JPEGs).
- [ ] **Step 9: Update spec/decisions.** Amend D6 if the max differs, D10's tick ceiling, the D37 luminance inputs and the spec §10 checkboxes. Then set the encoder defaults in `GlyphDisplays.create` to the measured layout and max, and update `MatrixFrameEncoderTest` to cover the defaults.
- [ ] **Step 10: Commit.** Slug: `m0-measurements`.

### Task 7: CI pipeline, threat model, release-safety check

**Files:** Create `.github/workflows/ci.yml` and `docs/engineering/threat-model.md`.

**Interfaces:** Produces a CI that every later milestone extends.

- [ ] **Step 1: Write `ci.yml`.** Runs on `push` and `pull_request`; `ubuntu-latest`; `actions/setup-java` (temurin 17); `gradle/actions/setup-gradle`, all actions pinned to a full commit SHA. Jobs:
  - `checks`: `./gradlew ktlintCheck detekt lint :core:test :agent:test :evals:test :glyph-sdk:testSdkFreeDebugUnitTest :spike:testSdkFreeDebugUnitTest koverVerify`
  - `sdkfree-apk`: `./gradlew :app:assembleSdkFreeRelease`, then fail if any `com/nothing/` class is in the dex:
    ```bash
    unzip -o app/build/outputs/apk/sdkFree/release/*.apk 'classes*.dex' -d dex && \
    ! (for f in dex/classes*.dex; do strings "$f"; done | grep -q 'Lcom/nothing/')
    ```
  - `full-build`: `./gradlew :app:assembleFullDebug` (exercises the fetch and SHA verification)
- [ ] **Step 2: Enable dependency verification.** Run `./gradlew --write-verification-metadata sha256 help` and commit `gradle/verification-metadata.xml` (spec §9 supply chain).
- [ ] **Step 3: Write `threat-model.md`.** Cover:
  - **Assets:** API keys, user animations, notification content, the device panel.
  - **Entry points:** imported files, notification text, the LLM endpoint, LAN endpoints, adb (debug only).
  - **Threats and mitigations,** mapped to spec §3.5 limits, §5.5 safety and §9.
  - **Open risks.**
- [ ] **Step 4: Verify.** Push the branch and confirm all three jobs are green (`gh run watch`).
- [ ] **Step 5: Commit.** Slug: `m0-ci`.

### Task 8: M0 exit review

- [ ] **Step 1: Check M0 exit criteria (spec §8).**
  - the spike-results doc exists
  - D6, D10 and D37 are confirmed or amended
  - CI is green
  - the app installs with the real ID
- [ ] **Step 2:** Run `/code-review` on the `m0-foundations` branch and resolve the findings (refactors in separate commits).
- [ ] **Step 3: Update `docs/HANDOFF.md`.** M0 done; next is the D46 architecture deep-dive, then the M1 plan.
- [ ] **Step 4:** Merge to `design/v1-spec` (or `master` if the user prefers) after the user's OK, and push when asked.
