# Adversarial re-review (round 3): M0 Foundations & Hardware Spike plan (rev 3)

**Target:** `docs/superpowers/plans/2026-10-12-m0-foundations-spike.md` (rev 3, `665b8b4`) · **Reviewed:** 2026-10-12 · **Reviewer:** Claude (adversarial plan review, round 3)
**Read:** CLAUDE.md, `code-guidelines.md`, review r2, the full rev 3 plan, the repo's current build files (`settings.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `libs.versions.toml`), spec §8 M0 row, and the GlyphMatrix Developer Kit README.

## Verdict

**One more small revision (rev 4), then execute.** Every r2 finding (N1–N17, L1–L6) is closed as specified; I found no regressions. Three new HIGHs would each fail a build or a test, or let a quality gate pass vacuously. All three have mechanical fixes. No round 4 review is needed, just a spot check of the edited lines.

**Counts (new):** 0 CRITICAL · 3 HIGH · 6 MEDIUM · 6 LOW.
**r2 closure:** 23 / 23 CLOSED.

---

## 1. r2 closure

| ID | Status | Note |
|---|---|---|
| N1 | CLOSED | `fullCompileOnly` + `testFullImplementation`; apps use `fullRuntimeOnly` through the `glyphSdk` consumable configuration. AGP's acceptance is [verify] with a fallback. |
| N2 | CLOSED | Task class in `build-logic/`, member helpers, `HexFormat`, CC reuse check (see R-L1, R-L2). |
| N3 / N6 | CLOSED | Concrete YAML, `detektMain`, `:detekt-canary`, SARIF-based `run-detekt.sh` tested on both paths. |
| N4 | CLOSED | JUnit 4 on Android, D49 amended (but see R-H1: the JDK). |
| N5 / N11 | CLOSED | File-based `grep`, `aapt2`, positive controls. |
| N7 | CLOSED | Traced every path: timeout, late callback, rebind, reject, release/reconnect. Correct. |
| N8 / N17 | CLOSED | `specialUse` service owns the session and receiver; shortService runs to `onTimeout`. |
| N9, N10, N12–N16 | CLOSED | As specified. |
| L1–L6 | CLOSED | |

---

## 2. New findings

### HIGH

#### R-H1. Robolectric `@Config(sdk = [36])` needs Java 21, but the plan says "JDK 17 toolchain" and "Temurin 17"
- **Evidence:** Robolectric 4.16 release notes say SDK 36 tests need JDK 21, because the Android 36 jars are compiled for Java 21. On Java 17 they fail with *"Android SDK 36 requires Java 21 (have Java 17)"* ([Robolectric releases](https://github.com/robolectric/robolectric/releases/), [discussion #10552](https://github.com/robolectric/robolectric/discussions/10552), [Mozilla bug 1985185](https://bugzilla.mozilla.org/show_bug.cgi?id=1985185)).
- **Why the build currently works anyway:** AGP's unit-test tasks run on the Gradle daemon JVM, and `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=25`.
- **The risk:** the plan never says this. An executor who reads "JDK 17 toolchain" and adds `kotlin { jvmToolchain(17) }` (or `java.toolchain`) to an Android module moves its tests onto JDK 17, and `MainActivityTest`, `GlyphThemeTest`, `AppContainerTest` and `SpikeCommandTest` all fail. CI's "Temurin 17" has the same mismatch.
- **Fix:** in the Tech stack and Global Constraints, write:
  - "**Bytecode target 17.** JVM modules compile and test on a JDK 17 toolchain (provisioned by foojay). Android modules never set a toolchain: their Robolectric tests run on the daemon JDK (25, `gradle-daemon-jvm.properties`), because SDK 36 needs ≥ 21."
  - CI: `setup-java` with **25** (the daemon), letting foojay provision 17.
  - Task 2 Step 1, Robolectric row: also record whether the pinned version needs `jvmFlags` on JDK ≥ 17 (4.17 release note) [verify].
  - Learning aside: daemon JVM vs toolchain vs bytecode target.

#### R-H2. `GlyphThemeTest` (`createComposeRule`) has no `ComponentActivity` to launch
- **The bug:** `createComposeRule()` launches `androidx.activity.ComponentActivity`, which must be declared in a manifest. Compose's docs ship that declaration in `androidx.compose.ui:ui-test-manifest`. `:designsystem` is a library with no activity, and the plan's catalog and dependency list don't include `ui-test-manifest`, so the test fails with *"Unable to resolve activity for Intent … ComponentActivity"*. `:app`'s `MainActivityTest` is unaffected, because it uses its own activity.
- **Evidence:** [Compose testing setup](https://developer.android.com/develop/ui/compose/testing#setup) (`debugImplementation("androidx.compose.ui:ui-test-manifest")`).
- **Fix:** add catalog alias `compose-ui-test-manifest` (via BOM), and `debugImplementation(libs.compose.ui.test.manifest)` in every module whose tests use `createComposeRule()` (`:designsystem` now; note it for M2). [verify] `:designsystem:testDebugUnitTest` passes.

#### R-H3. ktlint (and possibly Kover) can pass vacuously on Android modules under AGP 9 built-in Kotlin
- **The bug:** ktlint-gradle detects Kotlin by the standalone `kotlin-android` plugin. With built-in Kotlin it registers **no source-set tasks**, so `ktlintCheck` is green while checking nothing. Fixed in ktlint-gradle **14.1.0** (#1016). An AGP 9 project (stardroid) shipped with this unnoticed.
- **Evidence:** [ktlint-gradle #1008](https://github.com/JLLeitschuh/ktlint-gradle/issues/1008), [CHANGELOG 14.1.0](https://github.com/JLLeitschuh/ktlint-gradle/blob/main/CHANGELOG.md), [stardroid #1054](https://github.com/sky-map-team/stardroid/issues/1054). The same class of bug exists in kotlinter ([#506](https://github.com/jeremymailen/kotlinter-gradle/issues/506)).
- **Why it matters:** the plan guards detekt against exactly this failure (Review Focus 8), but not ktlint or Kover. Kover with no instrumented lines may also satisfy `minBound(80)` vacuously; the plan only says "check the changelog".
- **Fix:**
  - Task 2 Step 1: `ktlint-gradle` constraint "**≥ 14.1.0** (built-in Kotlin support, #1016)".
  - Add `scripts/check-tooling-active.sh` (CI `checks` job, file-based greps):
    - For each Android module, `./gradlew :<m>:tasks --all > out` contains `ktlintMainSourceSetCheck`, or whatever name the pinned version registers [verify].
    - After the Kover verify tasks, each gated module's `build/reports/kover/report*.xml` has a `LINE` counter with `covered + missed > 0`.
  - Add Review Focus 9: "a lint/coverage tool silently checks nothing".

### MEDIUM

#### R-M1. `TextGridTest` bucket edge is wrong
`glyph(v) = LEVELS[(v - 1) * 9 / 255]`. For v = 29: 28 × 9 = 252, and 252 / 255 = 0, which gives `.`, not `:`. The first `:` is at **v = 30** (29 × 9 = 261). The test as written fails against the code as written.
- **Fix:** expected edges `1→.`, `29→.`, `30→:`, `255→@`.
- **Also:** `.coerceAtMost(LEVELS.lastIndex)` is dead (the maximum is 254 × 9 / 255 = 8 = lastIndex). Remove it, because no dead code should hide in a 100%-line module.

#### R-M2. The M0 exit criterion "the mirror works (`GlyphMirror` from `:app` debug)" can't be checked
`:app` in M0 never calls `show()`: `MainActivity` renders only the app name. Nothing in the plan makes `:app` emit a `GlyphMirror` line. Adding a trigger to `:app` would be a UI change needing sign-off, which is out of M0 scope.
- **Fix (recommended):** reword the Task 8 criterion to say the mirror is proven by:
  1. `MirroringGlyphDisplayTest` (sink receives the `TextGrid`);
  2. `AppContainerTest` (debug wiring is a `MirroringGlyphDisplay`, release is not);
  3. the on-device spike `DUMP` (Task 5 Step 7.2), which uses the same class.

  This matches spec §8, whose M0 row lists "debug glyph mirror" in scope but not "from `:app`".

#### R-M3. `GlyphSession.stop()` releases while a frame may still be mid-call
`loop?.cancel()` is cooperative. `show()` is a blocking binder call on the session dispatcher, so `display.release()` (`closeAppMatrix` + `unInit`) on the main thread can run concurrently with an in-flight `setAppMatrixFrame`. This is exactly the Review Focus 5 path the spike exists to measure.
- **Fix:**
  ```kotlin
  fun stop() {
      val running = loop
      loop = null
      scope.launch { running?.cancelAndJoin(); display.release() }
  }
  ```
- **Test:** `release` is recorded after the last `show`, and exactly once.

#### R-M4. `FetchGlyphSdk` has no network timeouts
`URL.openStream()` has an infinite read timeout, and its connect timeout is left to the OS. If a firewall drops packets, the task hangs for minutes; a stalled server hangs it forever. Review Focus 1 claims the task fails with a message.
- **Fix:** `(URI(url.get()).toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 60_000 }).getInputStream().use { it.readBytes() }`, with the values as named constants carrying a *why* comment.

#### R-M5. `SpikePatternService.handle(Show)` doesn't say what happens when `connect()` fails or overlaps
- **Fix:**
  - Start the session only on `Ok`. On `Err`, log it under `SpikeStatus` and keep `lastResult`.
  - Serialise `handle` (one `Mutex`, or a `Channel<SpikeCommand>` consumed by one coroutine in `lifecycleScope`), so two quick `SHOW`s with different layouts can't race two `connect()`s.
- **Test:** `SpikeCommandTest` is pure parsing, so add nothing there. Record the expected behaviour in the Task 5 Step 7 manual check: "`SHOW` on a non-Nothing/sdkFree build logs `Err(Unsupported)` and plays nothing".

#### R-M6. `@OptIn` in tests vs the `ForbiddenAnnotation: kotlin.OptIn` ban
- **The conflict:** detekt's plain task scans test sources too. The config already excludes test paths for `LateinitUsage`, but not for `ForbiddenAnnotation`. `GlyphSessionTest` uses `testScheduler.timeSource`, which may be `@ExperimentalCoroutinesApi` in the pinned `kotlinx-coroutines-test` [verify with the pinned source]. If it is, the test needs `@OptIn`, and the `detekt` job fails.
- **Fix:** decide in the plan. Either:
  - (a) exclude test source sets from `ForbiddenAnnotation`, because test-only opt-ins carry no production risk; or
  - (b) keep the ban and log a decision entry (D5x) for `ExperimentalCoroutinesApi` in tests.

  Recommend (a).

### LOW

- **R-L1 (Task 3 Step 1):** `url = project.providers.gradleProperty(…)` relies on Kotlin's assignment plugin inside a plain `.kt` class in `build-logic`. Use `url.set(…)` (likewise for `sha256`, `offline`, `aar`), which is unambiguous in any compilation context.
- **R-L2 (Task 3 Step 1):** annotate `FetchGlyphSdk` with `@DisableCachingByDefault(because = "downloads a pinned artifact; the output is already local")`. It's idiomatic, and `validatePlugins` warns without it.
- **R-L3 (Task 6 Step 2 / Task 4):** the SDK README says *"Any value above 255 will be automatically capped at 255"*. Keep the `max=2047` probe as a confirmation, but cite the README in Task 6 and expect `maxValue = 255`. The README also says `setAppMatrixFrame`/`closeAppMatrix` need **system version ≥ 20250801**. Record the phone's Nothing OS/system version in Task 6 Step 0.
- **R-L4 (Task 2 Step 5):** the template's `app/build.gradle.kts` has `buildTypes.release { optimization { enable = true; packageScope = … } }` (R8 on) and `targetSdk = 37`. The plan sets targetSdk 36 but never mentions the R8 block. State "keep the release `optimization` block": spec §9 runs the suite against the minified release, and `check-sdkfree-apk.sh` inspects a minified APK.
- **R-L5 (Threat model):** Robolectric downloads `android-all-instrumented` jars at **test runtime**, outside Gradle dependency verification. Add this as an open risk in `threat-model.md` (or pre-resolve via a Gradle configuration with `robolectric.offline=true`; defer to M1).
- **R-L6 (Task 4 Step 7):** for "`close()` throwing still calls `unbind`", also assert that `release()` **rethrows** (the code uses `try/finally`, so the exception propagates). The test should pin that contract explicitly.

---

## 3. Minimum to make rev 4 executable
1. R-H1 (JDK wording + CI JDK 25).
2. R-H2 (`ui-test-manifest`).
3. R-H3 (ktlint ≥ 14.1.0 + `check-tooling-active.sh`).
4. R-M1, R-M3, R-M4 (code/test corrections in snippets).
5. R-M2, R-M5, R-M6 (one-line decisions).

The LOW items are text edits.
