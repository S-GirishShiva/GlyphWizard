# Adversarial re-review (round 2): M0 Foundations & Hardware Spike plan (rev 2)

**Target:** `docs/superpowers/plans/2026-10-12-m0-foundations-spike.md` (rev 2) · **Reviewed:** 2026-10-12 · **Reviewer:** Claude (adversarial plan review, round 2)
**Read:** CLAUDE.md, round-1 review, the rev 2 plan, `code-guidelines.md`, `modern-kotlin-patterns.md` (enforcement table §, rules 1–30), D47–D49.

## Verdict

**Needs another (small) revision before execution.** Rev 2 closes both CRITICALs and most HIGHs. But it adds new build-breaking problems:
- `:glyph-sdk:assembleFullDebug` fails on AGP's local-AAR-in-library rule;
- the `FetchGlyphSdk` task in the build script is likely uninstantiable and not configuration-cache safe;
- the detekt canary cannot pass as specified;
- the Android modules' Vintage dependency has no version;
- the negative APK checks can pass silently under `pipefail`.

All of these have mechanical fixes (below). One more revision is enough. No third full review is needed, just a spot check of the fixed snippets.

**Counts (new):** 0 CRITICAL · 6 HIGH · 11 MEDIUM · 6 LOW.
**Round-1 closure:** 34 CLOSED · 6 PARTIAL · 0 OPEN · 1 REGRESSED (of 43). The two not counted (H1, H6) are closed in intent but carry new HIGHs.

---

## 1. Round-1 closure table

| ID | Status | Note |
|---|---|---|
| C1 | CLOSED | `check-glyph-sdk-fetch.sh` cases can now fail (bad hash, bad URL, offline, sdkFree-offline). |
| C2 | CLOSED | Task 6 Step 9: sdkFree-only prerelease, ask first, delete after, never a `full` APK. |
| H1 | CLOSED → see N1/N2 | `builtBy` + `files(provider)` replaces `preBuild` wiring, but the dependency shape breaks the AAR build (N1), and the task class has script-capture problems (N2). |
| H2 | CLOSED | Commit-pinned raw URL + SHA, `curl` instead of `gh`. |
| H3 | CLOSED | `:app`/`:spike` depend on `:core`/`:designsystem`/`:glyph-sdk`. |
| H4 | PARTIAL | JVM modules fixed (BOM + launcher). Android modules: see N4 (Vintage engine has no version, no `useJUnitPlatform`). |
| H5 | CLOSED | Catalog table + "stable, ≥ 14 days, record URL/date" rule. |
| H6 | CLOSED → see N3 | `dev.detekt` 2.0 alpha per D47, but the canary/config design can't work (N3). |
| H7 | PARTIAL | Timeout + `Rejected` + disconnect are in. Missing: `connected = true` on success, double-resume guard, a `GlyphException` catch in `release()`, thread-safe flag (N7). |
| H8 | CLOSED | `MatrixService` seam + `NothingGlyphDisplayTest` + `GlyphSessionTest`. |
| H9 | CLOSED | DUMP-guarded `RECEIVER_EXPORTED`; the `ContextCompat.registerReceiver(ctx, r, filter, permission, handler, flags)` overload exists. |
| H10 | PARTIAL | Listener and shortService added, but the shortService "sleep 60 s then log `onTimeout`" can never observe `onTimeout`, and `SHORT` is unreachable once backgrounded (N8). |
| H11 | CLOSED | Verification metadata generated from the CI task set (small gap: N15). |
| H12 | CLOSED | Per-module Kover DSL + `:app` Robolectric tests. Caveat: full-flavour code is never gated (N12). |
| M1 | CLOSED | `TextGrid` + `MirroringGlyphDisplay` durable; PNG deferred to M2. |
| M2 | CLOSED | `NoGlyphDisplayTest` moved to `src/testSdkFree`. |
| M3 | CLOSED | Names and packages consistent (minor duplication: L4). |
| M4 | PARTIAL | `lintSdkFreeDebug` + `lintFullDebug` in CI, but `:designsystem` (no flavours) is never linted (N13). |
| M5 | PARTIAL | Positive control added, but the `pipefail` + `grep -q` and binary-manifest problems make the negative checks vacuous (N5, N11). |
| M6 | CLOSED | `curl` everywhere (Task 7 Step 0 SHA lookup is wrong: L2). |
| M7 | CLOSED | Push only after asking. |
| M8 | CLOSED | Task 1 Q3. |
| M9 | CLOSED | Licence question reworded; D18 update. |
| M10 | CLOSED | Tick model specified (`(DeviceProfile, elapsedMs) -> IntArray`). |
| M11 | CLOSED | Panel fps from 240 fps footage. |
| M12 | CLOSED | Rise, glow, pitch, auto-timeout added. |
| M13 | CLOSED | Threading step + injected dispatcher in `GlyphSession`. |
| M14 | CLOSED | `<queries>` fallback, recorded. |
| M15 | CLOSED | Learning asides in every task. |
| M16 | CLOSED | Step 0 Discover in every task. |
| M17 | CLOSED | Task 1 Q4. |
| M18 | CLOSED | `renovate.json` + `licences.md`. |
| M19 | CLOSED | Threat-model entries added. |
| L1 | CLOSED | `·` + 9 buckets; bucket-edge tests. |
| L2 | **REGRESSED** | Rev 2 applied round 1's `lateinit var container` fix, but `modern-kotlin-patterns.md` rule #2 bans `lateinit` in prod code (detekt `LateinitUsage`). Round 1's fix was wrong. See N9. |
| L3 | CLOSED | |
| L4 | CLOSED | |
| L5 | CLOSED | `$ADB`, `logcat -d`. |
| L6 | CLOSED | |
| L7 | CLOSED | `isIncludeAndroidResources`, `@Config(sdk = [36])`. |
| L8 | CLOSED | Marked provisional. |
| L9 | CLOSED | |
| L10 | CLOSED | |

---

## 2. New findings

### HIGH

#### N1. Task 3: a local `.aar` in a *library* module breaks `:glyph-sdk:assembleFullDebug`
- **The bug:** `"fullImplementation"(files(…aar))` in `:glyph-sdk` (an `android-library`) triggers AGP's hard error *"Direct local .aar file dependencies are not supported when building an AAR"* in `bundleFullDebugAar`. `assembleFullDebug` includes that task.
- **What it breaks:**
  - Task 3 Step 2's "run `:glyph-sdk:assembleFullDebug` twice" fails;
  - so does any CI step that assembles the library's full AAR.
  - `:app:assembleFullDebug` may still work, because it consumes the library's intermediates, but that masks the problem.
- **Evidence:** the AGP error text; the documented workaround in [react-native-community/cli#1759](https://github.com/react-native-community/cli/issues/1759) and [react-native#32919](https://github.com/facebook/react-native/issues/32919).

**Fix:**
- **In `:glyph-sdk`:** `"fullCompileOnly"(sdk)` and `"testFullImplementation"(sdk)`, because `NothingGlyphDisplayTest` constructs `GlyphException`.
- **In `:app` and `:spike`:** `"fullImplementation"(sdk)`. Here `sdk` is `files(project(":glyph-sdk").layout…)`, wired to `:glyph-sdk:fetchGlyphSdk`.
- **Sharing the file across projects:** cleanest is a `glyphSdk` consumable configuration in `:glyph-sdk`, with an outgoing artifact `builtBy` the fetch task. `:app`/`:spike` then use `"fullRuntimeOnly"(project(":glyph-sdk", configuration = "glyphSdk"))`. This is isolated-projects friendly; avoid `project(":glyph-sdk").tasks`.
- **Task 3 Step 2:** replace `:glyph-sdk:assembleFullDebug` with `:app:assembleFullDebug` for the UP-TO-DATE check.
- **Learning aside:** compileOnly vs implementation.

#### N2. Task 3 Step 1: `FetchGlyphSdk` declared in the build script captures the script object
- **The bug:** the task's `fetch()` calls the script-level `private fun ByteArray.sha256()` / `File.sha256()`. In the Kotlin DSL, a class that references script members compiles as an inner class of `Build_gradle`.
- **What breaks:** Gradle then either refuses to instantiate it (non-static inner class), or configuration-cache serialization fails on the `Build_gradle` reference. Either way, the "configuration-cache safe" claim in the task title is unverified.
- **Also uncertain:** `ByteArray.toHexString()` is `@ExperimentalStdlibApi` in older Kotlin, and build scripts compile with Gradle's embedded Kotlin (not 2.4.21), so stability depends on the Gradle 9.6 embedded version [verify].

**Fix:**
- Make the helpers private **members** of the task class (or a `companion object`).
- Use `java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))`: JDK 17, no opt-in.
- Better still, move the task into `build-logic/` (an included build), the idiomatic home for task classes, and add it to the file structure.
- Add an explicit check to Step 2: `./gradlew --configuration-cache :glyph-sdk:fetchGlyphSdk` twice; the second run prints "Reusing configuration cache".

#### N3. Task 2 Step 7: the detekt canary cannot pass, and one ID is a placeholder
- **Type resolution:** `UnsafeCallOnNullableType` and `ForbiddenMethodCall` (the patterns doc's rule for `runBlocking`, #6) **require type resolution**. The plan says "plain `detekt`, no type resolution", so those rules never run and the canary fails.
- **Default config:** `GlobalCoroutineUsage` is **inactive by default** (patterns doc #4: "turn on"), but `detekt.yml` is "built-in defaults".
- **Placeholder:** for a mutable `object`, the patterns doc (#26) says "review + C": there is **no detekt rule ID** to copy. "Copy the exact IDs from that doc" is therefore an unresolvable placeholder.
- **Undefined CLI:** "runs the detekt CLI": which artifact, how is it fetched and pinned, and is it covered by verification metadata? Undefined.
- **Missing config:** `detekt.yml` "defaults" also ignores the enforcement table (ForbiddenImport lists for LiveData/Gson/`java.util.Date`, `InjectDispatcher`, `LateinitUsage` …).
- **Evidence:** [detekt type resolution docs](https://detekt.dev/docs/gettingstarted/type-resolution/) ("rules that require type resolution will not run"); [detekt PR #1886](https://github.com/detekt/detekt/pull/1886).

**Fix:**
- **`detekt.yml`:** `buildUponDefaultConfig = true`, then explicitly activate and configure every D-row of the enforcement table that has a rule. Write the YAML in the plan.
- **Canary:** make it run the **Gradle** type-resolved tasks (detekt 2.0: `detektMainSourceSet`/variant tasks [verify names on the pinned alpha]) against a tiny `:detekt-canary` JVM module excluded from `include()` in normal builds, *or* via `-PwithCanary`.
- **Expected IDs:** `UnsafeCallOnNullableType`, `GlobalCoroutineUsage`, `ForbiddenMethodCall`.
- **Mutable `object`:** drop it from the canary. It's covered by review + `guard-banned.sh`, so add a grep for `^\s*object .*\{` followed by `var` there, or state it's review-only.

#### N4. Task 2 Step 5: the Android modules' Vintage dependency has no version, and JUnit Platform is never enabled
- **No version:** `testRuntimeOnly(libs.junit.vintage.engine)` has no version ("take no version (BOM)"), but the Android modules don't add `testImplementation(platform(libs.junit.bom))`, so resolution fails.
- **Platform never enabled:** no `testOptions { unitTests.all { it.useJUnitPlatform() } }` either. AGP then runs the JUnit 4 runner directly, and the Vintage engine is dead weight.
- **Deprecated:** JUnit 6.0 **deprecated** the Vintage engine; it reports an INFO discovery issue for JUnit 4 classes ([JUnit 6 release notes](https://docs.junit.org/6.0.1/release-notes.html)).

**Fix (pick one and record it in D49):**
- **(a)** Android modules use plain JUnit 4 (`junit4` + Robolectric, no Vintage, no Platform). This is simplest and matches how Robolectric and Compose UI tests run.
- **(b)** Keep Vintage: add `platform(libs.junit.bom)`, `testRuntimeOnly(libs.junit.platform.launcher)`, `unitTests.all { it.useJUnitPlatform() }`, and `junit.vintage.discovery.issue.reporting.enabled=false`.

Recommend (a): D49 already says "to verify in M0".

#### N5. Task 7 Step 1: the negative checks pass silently under `set -o pipefail`
- **The bug:** `for f in …; do strings "$f"; done | grep -q 'Lcom/nothing/' && fail …`. When `grep -q` matches, it exits early, `strings` dies with SIGPIPE (141), and with `pipefail` the pipeline is non-zero, so `&& fail` is skipped. The "SDK in sdkFree" check passes exactly when it should fail.
- **Same bug elsewhere:** the `:app:dependencies | grep -q` line.
- **The positive control** (`… | grep -q … || fail`) fails *spuriously* for the same reason.

**Fix:** write to a file first:
```bash
strings "$d"/classes*.dex > "$d/strings.txt"
grep -q 'Lcom/nothing/' "$d/strings.txt" && fail "SDK classes in sdkFree"
```
Do the same for every `| grep -q` in the scripts.

#### N6. Task 2 Step 7 / Task 7: the detekt exit-code split cannot work through Gradle
**The bug:** CI runs `./gradlew detekt`. Gradle exits **1** for any task failure (findings *or* a crash) and never exits 2. The detekt-CLI convention (1 = error, 2 = max issues) only applies to the CLI. So "exit 1 = tool error, exit 2 = findings" misclassifies every findings run as a crash.

**Fix:** decide by output, not exit code:
- after `./gradlew detekt` fails, check whether `build/reports/detekt/*.sarif` / `*.xml` exist and contain results;
- findings → "detekt findings";
- no report → "detekt crashed: tool error, not findings".

Encode this in a `scripts/run-detekt.sh` and test it with the canary (findings path) and a broken config (crash path).

### MEDIUM

#### N7. Task 4 Step 4: `connect()` state handling is incomplete
- **Never connected:** the snippet resumes `Ok` but never sets `connected = true`, so as written `show()` would always return `ServiceUnavailable`.
- **Double resume:** `onServiceConnected` can fire again after a rebind, and a second `cont.resume` throws `IllegalStateException: Already resumed`. Guard with `if (cont.isActive)`, or route post-connect callbacks to the flag only.
- **Thread-safe flag:** `connected` is written on the main thread (binder callback) and read from `show()` on the session dispatcher. Use `AtomicBoolean` or `@Volatile`.
- **`release()` can throw:** it calls `close()` (`closeAppMatrix` throws `GlyphException`) without a catch. Wrap it, and still `unbind()`.
- **Permanent idempotency:** `release()` is "idempotent" across the whole object lifetime. But `SpikeActivity` does `onStop → release`, then `onStart → connect` on the same instance, so the second `release` must work. Reset the released state in `connect()`.
- **Register-false leak:** `register == false` should also `unbind()`.

**Tests to add:**
- re-connect after release unbinds again;
- a second `onConnected` doesn't crash;
- `close()` throwing still unbinds.

#### N8. Task 5 Step 5 / Task 6 Step 8: the shortService probe can't measure what it targets
- **Timeout never reached:** the `shortService` timeout is ~3 min, so "sleeps 60 s, logs `onTimeout`, then stops" never reaches `onTimeout`.
- **Banned `Thread.sleep`:** "sleeps" implies `Thread.sleep`, banned by rule #7 (and `guard-banned.sh` scans `spike/src/main`).
- **Unreachable when backgrounded:** `SpikeReceiver` is unregistered in `onStop`, so `SHORT` can't be fired once the app is backgrounded (step 8.4's scenario).
- **Missing manifest detail:** `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` (runtime, target 36).

**Fix:**
- Run until `onTimeout(startId, fgsType)` (API 35+), then `stopSelf`. Use a coroutine `delay` in a service-owned scope.
- Fire `SHORT` from the foreground, then background the app and observe.
- Spell out the manifest: `<uses-permission FOREGROUND_SERVICE/>`, `<uses-permission POST_NOTIFICATIONS/>`, and `<service android:name=".SpikeShortService" android:exported="false" android:foregroundServiceType="shortService"/>`.
- Call `startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)`.

#### N9. Task 2 Step 6: `lateinit` in prod code (L2 regression)
**Fix:** `class GlyphWizardApplication : Application() { val container: AppContainer by lazy { AppContainer(this) } }`. `Application` is attached before any component touches it, so lazy init is safe. Delete the round-1 note.

#### N10. Task 5 Step 5: the `NotificationListenerService` manifest entry is under-specified
**The gap:** binding needs `android:exported="true"`, `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, and an `<intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter>`. The plan names only the permission.

**Fix:** give the full XML in the plan.

#### N11. Task 7 Step 1: `strings` on the binary `AndroidManifest.xml` is unreliable, and the classpath check is vacuous
- **Binary manifest:** the APK manifest is binary XML (AXML). Its string pool may be UTF-16, which `strings` (8-bit) misses, and there is no positive control for the permission check.
- **Vacuous classpath check:** a `files(…)` dependency does not appear in the `:app:dependencies` report, so the `glyph-matrix-sdk` grep can never match.

**Fix:**
- Use `$ANDROID_HOME/build-tools/<v>/aapt2 dump permissions <apk>` (or `apkanalyzer manifest permissions`), with a positive control on the full APK.
- Replace the dependencies grep with a check that `glyph-sdk/libs/` is absent after `assembleSdkFreeRelease` from clean, or drop it.

#### N12. Coverage: `NothingGlyphDisplay` (full-only) is never coverage-gated
**The gap:** gates run only `koverVerifySdkFreeDebug`, so the most failure-prone logic, `NothingGlyphDisplay`, has tests but no coverage floor.

**Fix:** add `variant("fullDebug") { verify { rule { minBound(80) } } }` in `:glyph-sdk`, with `SdkMatrixService` excluded via `kover { reports { filters { excludes { classes("*.SdkMatrixService") } } } }`, and run `:glyph-sdk:koverVerifyFullDebug` in the `full-build` CI job.

#### N13. `:designsystem` is never linted
**The gap:** CI runs `lintSdkFreeDebug` / `lintFullDebug`, and `:designsystem` has only `lintDebug`.

**Fix:** add `:designsystem:lintDebug` to Task 2 Step 10, the CI `checks` job and the verification-metadata command.

#### N14. Task 4 Step 6: `BuildConfig.DEBUG` doesn't exist by default on AGP 9
**The bug:** `buildFeatures.buildConfig` defaults to false (AGP 8+), so `:app` won't compile.

**Fix (pick one):**
- add `buildFeatures { buildConfig = true }` to `:app`;
- **preferred, no flag:** put the mirror wiring in `src/debug/kotlin/.../DebugWrap.kt` vs `src/release/...` (`fun wrapForBuild(d: GlyphDisplay, profile): GlyphDisplay`).

Update `AppContainerTest` accordingly (`testSdkFreeReleaseUnitTest` asserts *not* mirroring).

#### N15. Verification-metadata command misses artifacts CI resolves
**Missing:** the detekt canary path (N3) and `:spike:assembleSdkFreeDebug`. With N12/N13 also `:glyph-sdk:koverVerifyFullDebug` and `:designsystem:lintDebug`.

**Fix:** generate it with exactly the union of the CI commands. Put them in one `scripts/ci-tasks.txt` that both `ci.yml` and the README command read, so they cannot drift.

#### N16. Task 5 Step 2: `GlyphSession` is missing `profile`, and its clock type breaks the guidelines
- **Missing parameter:** `start` calls `pattern(profile, …)`, but `profile` is not a constructor parameter.
- **Clock type:** `clock: () -> Long` departs from the patterns doc ("inject a Clock / `kotlin.time`").
- **Untested and drifting:** `achievedFps` is untested, and `1000/fps` ms integer division drifts (120 fps → 8 ms → 125 Hz).

**Fix:**
- `GlyphSession(display, profile, scope, dispatcher, timeSource: TimeSource.WithComparableMarks)`, using `TestTimeSource`/`testScheduler.timeSource` in tests.
- Schedule by absolute deadlines (`period = 1.seconds / fps`).
- Test `achievedFps ≈ fps`.

#### N17. Task 5 Step 4: the spike receiver lives only while the Activity is started
**The gap:** the adb hooks (`SHOW`/`DUMP`) are unusable in Task 6 Step 7 "locked / pocketed" states, because the screen-off → `onStop` path unregisters the receiver and stops the session. Steps 7.2 and 8.2 can't be performed as written.

**Fix:**
- Decide explicitly: run patterns from the `SpikeShortService` (or a `specialUse` debug FGS) for the locked/pocketed/background measurements; or
- accept that the matrix stops on `onStop` and record "app matrix requires foreground" as the measured behaviour, which is itself a §10 fact.

### LOW

- **L1 (Task 4 Step 4):** the `SdkMatrixService { /* GlyphMatrixManager.getInstance(context.applicationContext) … */ }` body is a placeholder. Give its ~12 lines:
  - `init(Callback)` mapped to `bind`;
  - `register(Glyph.DEVICE_25111p)` via the `when`;
  - `setAppMatrixFrame`, `closeAppMatrix`, `unInit`;
  - plus the Task 6 Step 6 `setGlyphMatrixTimeout` spike extension, which is currently also undefined ("spike-only extension": how, if `SdkMatrixService` is `internal` in another module?). Make it a `MatrixService` method or drop the step.
- **L2 (Task 7 Step 0):** `releases/latest` returns a tag name, not a commit SHA. Use `curl …/git/ref/tags/<tag>` and dereference annotated tags (`…/git/tags/<sha>`).
- **L3 (Task 2 Step 2):** `subprojects { apply(plugin = …) }` is cross-project configuration (incompatible with isolated projects). Also, type-safe `ktlintRuleset(...)` accessors don't exist inside that block, so it must be `"ktlintRuleset"(libs.compose.rules.ktlint)`. Prefer `alias(libs.plugins.ktlint.gradle)` in each module's `plugins {}`.
- **L4 (Task 4 Step 4):** `NothingGlyphDisplay(…, sdkDeviceId = "25111p")` duplicates `encoder.profile.id`. Derive it from `encoder.profile.id` (no duplication rule).
- **L5 (Task 3 Step 0):** URL and SHA in the catalog's `[versions]` misuses the catalog (they are not versions). Move them to `gradle.properties` (`glyphSdk.url`/`glyphSdk.sha256`, already the override names). Then `providers.gradleProperty(...)` alone suffices, and Renovate needs no `ignoreDeps`.
- **L6 (Task 4 Step 7):** `NothingGlyphDisplayTest` constructs `GlyphException`. Confirm its public constructor signature with `javap` in Step 0 [verify]; if there is none, the fake throws via a test-only subclass or the test must change.

---

## 3. Minimum to make rev 3 executable
1. N1 (compileOnly + app/spike runtime wiring).
2. N2 (task class helpers + `HexFormat` / build-logic).
3. N3 + N6 (concrete `detekt.yml`, type-resolved canary, report-based classification).
4. N4 (pick JUnit 4 for Android).
5. N5 + N11 (script fixes).
6. N7 (connect/release state).
7. N8 + N17 (spike service design for background measurements).
8. N9, N14.

The remaining MEDIUM/LOW items are quick text edits.
