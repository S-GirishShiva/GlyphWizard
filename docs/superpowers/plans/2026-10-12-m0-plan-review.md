# Adversarial review: M0 Foundations & Hardware Spike plan

**Target:** `docs/superpowers/plans/2026-10-12-m0-foundations-spike.md` · **Reviewed:** 2026-10-12 · **Reviewer:** Claude (adversarial plan review)
**Read first:** CLAUDE.md, code-guidelines, frontend-guidelines, spec §2/§7/§8/§9/§10/§11, decisions D18/D26/D28/D31/D44–D46, `docs/research/2026-10-11-dev-tooling-visual-mcp.md` §3.1, and the current build files (AGP 9.4.1, Gradle 9.6.0, daemon JVM 25, `.gitignore` already ignores `*.aar`).

## Verdict

**Not executable as written: revise, then re-review.** The structure, task order and Review Focus intent are good. The device facts (13×13, 137 LEDs, row widths, `DEVICE_25111p`, permission name) check out against the SDK. But:
- two Review Focus items have tests that cannot fail (Task 3);
- one step would publish the SDK in a public release, which breaks D26;
- several build snippets fail on AGP 9 / Gradle 9 as written;
- the adb hooks cannot reach the receiver the plan registers;
- three spec §10 items cannot be measured with the spike as designed.

**Counts:** 2 CRITICAL · 12 HIGH · 19 MEDIUM · 10 LOW.

### What I verified directly
I downloaded the SDK to scratch, pinned to commit `a65d8e1`, and inspected it with `javap`:
- **Asset name:** the repo's only `.aar` is `glyph-matrix-sdk-2.0.aar`.
- **SHA-256:** `be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29`.
- **Package:** `com.nothing.ketchum`.
- **`GlyphMatrixManager` methods:**
  - `static getInstance(Context)`
  - `init(Callback)`, `unInit()`
  - `boolean register(String)`
  - `setAppMatrixFrame(int[]) throws GlyphException`
  - `closeAppMatrix() throws GlyphException`
  - `setGlyphMatrixTimeout(boolean) throws GlyphException`
  - `turnOff()`
- **`Callback`:** `onServiceConnected(ComponentName)` and `onServiceDisconnected(ComponentName)`.
- **Device fields:** `Glyph.DEVICE_25111p` and `Glyph.DEVICE_25111p_MATRIX_LENGTH` exist, and so does `Common.getDeviceMatrixLength()`.
- **Binding:** `init` calls `bindService` to `com.nothing.thirdparty/.GlyphService`.
- **Swallowed errors:** `register` catches and logs `RemoteException`, and `setAppMatrixFrame` does the same.
- **`NothingKey`:** the manifest meta-data is read only by the legacy `GlyphManager`, not by `GlyphMatrixManager`.
- **AAR manifest:** `minSdk 33`, with no `<queries>` element.

Also on this machine:
- `gh` is **not installed**.
- Two adb binaries exist: `/usr/bin/adb` (37.0.0) and the SDK's adb (37.0.1).
- The repo `S-GirishShiva/GlyphWizard` is **public**.
- Branches are `design/v1-spec` (current) and `master`.

---

## CRITICAL

### C1. Task 3 Step 3: the fetch tests for Review Focus 1 and 2 cannot fail
**Issue:**
- **Assertion 2 (tamper) is self-defeating.** Step 2 says the task re-downloads when "the hash differs". A tampered local file is therefore silently healed and the build succeeds, so `! … grep -q 'BUILD SUCCESSFUL'` is false.
- **Bash ignores that failure anyway.** `set -e` does not exit on a pipeline negated with `!`, so the line is a no-op whatever happens.
- **The next line asserts nothing** because it ends in `|| true`.
- **The tamper case never tests the threat.** The real threat is the *remote* changing, or a MITM, which this case never exercises.
- **Assertion 3 (`--offline`) is invalid.** `--offline` only affects Gradle dependency resolution. A custom task doing `URL.openStream()` still downloads, so "Could not download" never appears.
- **The note at the end of the step admits it is unresolved** ("Pick the behaviour…"), which makes it a placeholder.

**Evidence:** plan lines 211, 217–229. Bash manual: "`set -e` … the shell does not exit if … the pipeline's return value is being inverted with `!`".

**Fix:**
1. Make the task's inputs overridable Gradle properties (`glyphSdk.url`, `glyphSdk.sha256`), defaulting to the catalog values.
2. Have the task check `gradle.startParameter.isOffline` and fail early with the download message.
3. Replace the script with:
```bash
#!/usr/bin/env bash
set -euo pipefail
fail() { echo "FAIL: $*" >&2; exit 1; }
rm -rf glyph-sdk/libs
./gradlew -q :glyph-sdk:fetchGlyphSdk || fail "clean fetch"
test -f glyph-sdk/libs/glyph-matrix-sdk.aar || fail "aar missing after fetch"
# 2. wrong pinned hash (simulates an upstream change / MITM) must fail and leave no file behind
rm -rf glyph-sdk/libs
out=$(./gradlew :glyph-sdk:fetchGlyphSdk -PglyphSdk.sha256=0000000000000000000000000000000000000000000000000000000000000000 2>&1) && fail "bad hash passed"
grep -q 'Glyph SDK SHA-256 mismatch' <<<"$out" || fail "mismatch message"
test ! -e glyph-sdk/libs/glyph-matrix-sdk.aar || fail "bad file kept"
# 3. unreachable URL must fail with the download message
out=$(./gradlew :glyph-sdk:fetchGlyphSdk -PglyphSdk.url=https://127.0.0.1:9/none.aar 2>&1) && fail "bad url passed"
grep -q 'Could not download the Glyph SDK' <<<"$out" || fail "download message"
# 4. sdkFree needs no SDK and no network
rm -rf glyph-sdk/libs
./gradlew --offline -q :glyph-sdk:assembleSdkFreeDebug || fail "sdkFree offline"
test ! -e glyph-sdk/libs || fail "sdkFree triggered fetch"
echo OK
```
The tamper case (local file edited) is then defined as "re-download and verify", and the existing check covers it.

### C2. Task 6 Step 7.1: publishing the `full` spike APK in a public GitHub release redistributes the Nothing SDK
**Issue:**
- **What happens:** Obtainium installs from release assets. The spike's meaningful build is `full`, which contains `com.nothing.*` classes.
- **Why it breaks D26:** a release on the public repo ships the SDK before Nothing's written permission. That breaks D26 and the EULA terms recorded in D18. The repo is public (`"visibility": "public"`).
- **The fallback is invalid too:** the plan falls back to `adb install`, which does not exercise restricted settings, so it can't stand in for the Obtainium path.

**Fix:** replace Step 7.1 with:
> Publish a **prerelease** containing only `spike-sdkFree-debug.apk`. Restricted settings depend on the installer and not on the SDK, so the sdkFree spike is a valid probe. Tag it `spike-m0-test`. Delete it after the measurement. Install it with Obtainium (track prereleases). Never attach a `full` APK to any release (D26).

This also needs a NotificationListenerService in the spike (see H10). Pushing a tag needs the user's explicit OK (CLAUDE.md push rule), so add "ask the user before pushing the tag".

---

## HIGH

### H1. Task 3 Step 2: the `preBuild` wiring inside `onVariants` will most likely throw `UnknownTaskException`, and it is the wrong mechanism anyway
**Issue:**
- **Timing:** AGP calls `onVariants` before it registers that variant's tasks, so `tasks.named("preFullDebugBuild")` fails at configuration.
- **Lint path:** `preBuild` also does not guard consumers such as lint analysis, which resolve the file dependency.
- **`libs` is unavailable:** a script applied with `apply(from = "gradle/fetch-glyph-sdk.gradle.kts")` gets no type-safe `libs` accessor. Reading `glyphSdkUrl`/`glyphSdkSha256` from the catalog there won't compile.
- **Configuration cache:** it is enabled in `gradle.properties`, so the task must be compatible with it.

**Evidence:** [AGP "extend AGP" docs: variant API runs before task creation](https://developer.android.com/build/extend-agp); [gist: AGP tasks not defined during onVariants](https://gist.github.com/dscoppelletti/d4ca0e1a19ed4ba4aaea99cf48ae930a). Not verified by a build; verify in Step 2.

**Fix:** delete the `onVariants` snippet and the separate script file. In `glyph-sdk/build.gradle.kts`:
```kotlin
abstract class FetchGlyphSdk : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val offline: Property<Boolean>
    @get:OutputFile abstract val aar: RegularFileProperty
    @TaskAction fun fetch() { /* download if missing or hash differs; verify; delete + fail on mismatch */ }
}
val fetchGlyphSdk = tasks.register<FetchGlyphSdk>("fetchGlyphSdk") {
    url = providers.gradleProperty("glyphSdk.url").orElse(libs.versions.glyphSdkUrl)
    sha256 = providers.gradleProperty("glyphSdk.sha256").orElse(libs.versions.glyphSdkSha256)
    offline = gradle.startParameter.isOffline
    aar = layout.projectDirectory.file("libs/glyph-matrix-sdk.aar")
}
dependencies { "fullImplementation"(files(fetchGlyphSdk.flatMap { it.aar }).builtBy(fetchGlyphSdk)) }
```
`builtBy` propagates the task dependency to every consumer: compile, lint and the consuming `:app`. Add a learning aside on lazy task wiring.

### H2. Task 3 Step 1: the URL is a placeholder, uses `gh` (not installed), and points at a mutable branch
**Fix:** replace the step's commands with these real values:
```bash
curl -fsSL https://api.github.com/repos/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/contents | grep '"name"'
curl -fsSL "https://api.github.com/repos/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/commits?path=glyph-matrix-sdk-2.0.aar&per_page=1" | grep -m1 '"sha"'
```
Then set:
- `glyphSdkUrl = "https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/a65d8e1d13e796919894e54ce43f93d939cdb34c/glyph-matrix-sdk-2.0.aar"`
- `glyphSdkSha256 = "be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29"`

Re-run `sha256sum` to confirm (verified 2026-10-12). Pinning a commit means an upstream update won't break builds; upgrading becomes a deliberate change.

### H3. Task 2: `:app` never depends on `:glyph-sdk` (or `:core`/`:designsystem`)
**Issue:** two Task 7 jobs become vacuous:
- The sdkFree "no `com/nothing`" check passes trivially.
- `full-build` (`:app:assembleFullDebug`) never runs the fetch.

**Evidence:** Task 2 Steps 5–6 list no `dependencies` for `:app`. Task 7 Step 1 states that the job "exercises the fetch".

**Fix:** in Task 2 Step 5, add for `:app`:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))
    implementation(project(":glyph-sdk"))
}
```
Add the same `:core`/`:glyph-sdk` dependencies for `:spike`. In Task 4, bind `GlyphDisplays.create(...)` inside `AppContainer`, so the sdkFree/full split is exercised by the real app.

### H4. Task 2 Step 4: JUnit 5 on Gradle 9 fails without the platform launcher
**Issue:** Gradle 9 removed the automatic loading of test-framework runtime dependencies, so `useJUnitPlatform()` without `junit-platform-launcher` fails with "Failed to load JUnit Platform".

**Fix:**
```kotlin
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test)
}
```
Add the catalog entries `junit-bom`, `junit-jupiter` (no version, comes from the BOM) and `junit-platform-launcher` (no version).

### H5. Task 2 Step 2: the catalog is under-specified and the "stable" query is wrong
**Issue:**
- **Undefined aliases:** snippets use `libs.plugins.kotlin.jvm`, `libs.plugins.kover`, `libs.junit.jupiter` and `libs.kotlin.test`, but no step defines them.
- **Missing pins:** no step pins the Kotlin version, `com.android.library`, the Compose compiler plugin, the ktlint plugin or compose-rules.
- **Compose plugin still needed:** with AGP 9 built-in Kotlin, Compose still needs `org.jetbrains.kotlin.plugin.compose`, applied per Compose module. Without it, `buildFeatures.compose = true` fails.
- **KGP floor:** AGP 9 needs KGP ≥ 2.2.10, and the root `kotlin.jvm` declaration sets KGP for the whole build.
- **`<release>` is not "stable":** Maven Central's `<release>` is simply the latest deployment and can be a milestone or RC. Nothing in the loop enforces "≥ 2 weeks old".
- **Unused pins:** the loop pins things M0 doesn't use (Navigation 3, DataStore, Ktor, serialization, immutable collections). That inflates verification metadata and the licence audit.

**Evidence:** [AGP 9.0 release notes: KGP 2.2.10 runtime dependency](https://developer.android.com/build/releases/agp-9-0-0-release-notes); [JetBrains AGP 9 blog](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/).

**Fix:** replace Step 2 with an explicit table to fill in: alias | coordinates | version | release date | source URL. Rows:
- `kotlin` (≥ 2.2.10)
- `android-library`
- `kotlin-jvm`
- `kotlin-compose`
- `kover`
- `detekt` (see H6)
- `ktlint-gradle` (`org.jlleitschuh.gradle.ktlint`)
- `compose-rules-ktlint` (`io.nlopez.compose.rules:ktlint`)
- `compose-bom`
- `activity-compose`
- `lifecycle-runtime-ktx`
- `coroutines`
- `junit-bom`
- `kotlin-test`

Use only what M0 consumes. For each row, take the newest version without `-alpha|-beta|-rc|-M` in `<versions>` whose upload date on `search.maven.org` / `plugins.gradle.org` is ≥ 14 days old. Record any deliberate pre-release as an exception (H6).

### H6. Task 2 Step 2/7: detekt 1.x (`io.gitlab.arturbosch.detekt`) does not support AGP 9 built-in Kotlin
**Issue:**
- Support landed in detekt **2.0.0-alpha.3** (`dev.detekt` plugin ID); the latest found is alpha.6.
- That conflicts with the plan's "stable only" rule.
- One issue remains open: type resolution can't see `R`/`BuildConfig` in AGP 9 library modules.

**Evidence:** [detekt #8320](https://github.com/detekt/detekt/issues/8320), [#8908](https://github.com/detekt/detekt/issues/8908), [#9402](https://github.com/detekt/detekt/issues/9402), [releases](https://github.com/detekt/detekt/releases).

**Fix:** add a blocking decision to Task 2: *"detekt: use `dev.detekt` 2.0.0-alpha.N (pre-release, the only AGP 9 compatible line) or defer detekt until 2.0 stable. Ask the user; log as D47."* If adopted, run plain `detekt` (no type resolution) so #9402 doesn't apply, and note the exception in the catalog comment. Likewise, require Kover ≥ 0.9.11, the first version reported to work with AGP 9 built-in Kotlin [unverified: confirm in the Kover changelog].

### H7. Task 4 Step 4: `connect()` can hang forever, so Review Focus 3 isn't met
**Issue:**
- **Hang:** on a non-Nothing phone, or when the service is missing, `bindService` returns `false` and the callback never fires, so `suspendCancellableCoroutine` waits indefinitely.
- **Wrong exception:** the plan maps `SecurityException`, but the SDK throws the checked `GlyphException`.
- **Ignored failure:** `register` returns `boolean` and swallows errors, and the plan ignores it.
- **Disconnects:** `onServiceDisconnected` isn't handled.
- **Cancellation:** cancellation doesn't `unInit`.

**Evidence:** the `javap` output above. [GDK README API table](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit#glyphmatrixmanager).

**Fix:** specify the following:
- `connect()` = `withTimeoutOrNull(connectTimeout) { suspendCancellableCoroutine { … } } ?: Err(ServiceUnavailable)`.
  - `connectTimeout` is a constructor parameter (DI) with a default of 3 s, tagged [P].
  - `invokeOnCancellation { manager.unInit() }`.
- `onServiceConnected` → `if (manager.register(Glyph.DEVICE_25111p)) Ok(Unit) else Err(Rejected("register returned false"))`.
- `onServiceDisconnected` → mark disconnected, so `show()` returns `Err(ServiceUnavailable)`.
- Catch `GlyphException` → `Rejected(e.message)` in `show`/`release`.
- Obtain `GlyphMatrixManager.getInstance(context.applicationContext)`.

### H8. Review Focus 3 and 5: no owning automated tests
**Issue:**
- **Review Focus 3:** the only Task 4 test is `NoGlyphDisplayTest`. The `ServiceUnavailable`/`Rejected` mapping, the very thing Review Focus 3 is about, has no test.
- **Review Focus 5:** it names "Task 5 test", but Task 5's tests cover only `Patterns` and `MirrorLog`.

"No SDK mocks" is right, but the fix is a seam, not no tests.

**Fix:**
- **Seam:** in `full`, put the SDK behind a tiny internal consumer interface. `NothingGlyphDisplay(service: MatrixService, encoder, timeout)` gets a 10-line `SdkMatrixService` adapter, the only code touching `com.nothing.*`, which is excluded from coverage as a thin adapter.
- **Fake tests in `src/testFull/`:**
  - callback never fires → `ServiceUnavailable` after the timeout (`runTest` virtual time)
  - `register` false → `Rejected`
  - throwing `show` → `Rejected`
  - `release()` twice → one `closeAppMatrix` and one `unInit`
- **Review Focus 5:** extract the spike's lifecycle wiring into a small `GlyphSession(display, scope)` with `start()`/`stop()`, and test that `stop()` cancels the frame loop and calls `release()`.
- **Kill case:** add to Task 6 a manual step, `adb shell am force-stop …spike` while `all(255)` plays, recording whether the panel clears. A killed process can't call `closeAppMatrix`, so this is a [HW] fact, not app logic.

### H9. Task 5 Step 3: adb cannot deliver to a `RECEIVER_NOT_EXPORTED` receiver
**Issue:** a non-exported dynamic receiver only accepts its own UID or system/root. The shell (UID 2000) is denied, so every `SHOW`/`DUMP` hook in Task 6 is dead. `-p` doesn't change that.

**Evidence:** `BroadcastQueue` skips non-exported receivers when the caller lacks a component permission. The project's own research already chose the DUMP-permission pattern (`docs/research/2026-10-11-dev-tooling-visual-mcp.md:148`).

**Fix:**
```kotlin
ContextCompat.registerReceiver(this, receiver, filter, Manifest.permission.DUMP, null, ContextCompat.RECEIVER_EXPORTED)
```
Only holders of DUMP (the adb shell) can send it [HW: confirm on Nothing OS B4.1 with one broadcast]. Update the learning aside to cover exported vs non-exported vs permission-guarded receivers.

### H10. Task 6: spec §10 "Process and permissions" items are missing or unmeasurable
**Issue:**
- **Restricted settings can't be tested:** Step 7 checks "Notification access for the spike", but the spike declares no `NotificationListenerService`, so it never appears in that list.
- **Not covered at all:**
  - "listener process state, and recovery after a forced crash"
  - "`shortService` interplay with the listener process"

**Evidence:** spec §10 lines 498–501 ("Process and permissions" block).

**Fix:**
- Add `SpikeListener : NotificationListenerService`. It logs `onListenerConnected` / `onNotificationPosted` with an elapsed-realtime timestamp, which also gives a first event→LED latency sample for §9.
- Add a debug `shortService` that sleeps 60 s.
- Add these Task 6 steps:
  - (a) grant access, background, `adb shell dumpsys activity processes | grep -A3 spike`;
  - (b) crash the listener (`adb shell am crash io.github.sgirishshiva.glyphwizard.spike`) and time the rebind via `onListenerConnected` logs, with and without `requestRebind`;
  - (c) start the `shortService` while the listener is bound and record the process state and any timeout or ANR.
- Record "not reproducible" explicitly where that's the case.

### H11. Task 7 Step 2: dependency verification generated with `help` is incomplete, and CI will fail
**Issue:** `--write-verification-metadata sha256 help` resolves only the configurations `help` needs: no compile, test, lint, Kover or detekt classpaths.

**Fix:** generate it after the plugin set is final, using the exact CI tasks:
```bash
./gradlew --write-verification-metadata sha256 ktlintCheck detekt lintSdkFreeDebug :core:test :agent:test :evals:test :glyph-sdk:testSdkFreeDebugUnitTest :spike:testSdkFreeDebugUnitTest koverVerify :app:assembleSdkFreeRelease :app:assembleFullDebug
```
Then commit the result. Document the regeneration command in `README.md` (every dependency bump needs it), and add a learning aside.

### H12. Coverage gates aren't concrete for Android modules, and `:app` has no tests at all
**Issue:**
- **No config:** CLAUDE.md requires ≥ 80% on `:app`/`:designsystem` (CI-enforced), but only `:core` gets a rule, and no Kover configuration is given for Android variants.
- **No `:app` tests:** `:app` gets none (its example tests are deleted).
- **Vacuous gates:** `:agent`/`:evals` "smoke tests" make the 100% gate meaningless.
- **Wrong scope:** a root `koverVerify` with no variant config verifies only the JVM modules.

**Fix:** add to Task 2 Step 7 (exact DSL):
```kotlin
// core/agent: build.gradle.kts
kover { reports { verify { rule { minBound(100) } } } }
// glyph-sdk, designsystem, app: build.gradle.kts
kover { reports { variant("sdkFreeDebug") { verify { rule { minBound(80) } } } } }   // designsystem: variant("debug")
```
Then:
- **`:app`:** a Robolectric test that launches `MainActivity` and asserts the text, plus a test that `AppContainer` provides a `GlyphDisplay`.
- **`:spike`:** state explicitly that it is excluded from coverage gates, as throwaway code, and get the user to agree.
- **CI:** run `koverVerify` per module (`:glyph-sdk:koverVerifySdkFreeDebug`, and so on).

---

## MEDIUM

### M1. Spec §8 "debug glyph mirror" is treated as throwaway
**Issue:** the spec's M0 scope and the research design (§3.1) describe a durable `FrameSink` seam with a debug `MirrorSink`: text logs plus PNG contact-sheet dumps plus an on-screen mirror. The agent workflow depends on it. The plan builds it only in `:spike`, which is deleted at M1.

**Fix:**
- Put `MirrorLog` (pure text codec) in `:core`. It's the spec's "text-grid codec", with 100% coverage.
- Put a `MirroringGlyphDisplay(delegate, sink)` decorator in `:glyph-sdk` and wire it in the `:app` debug build.
- Have the spike consume the same pieces.
- Defer PNG dumps to M2 explicitly, recorded in the plan.

### M2. Task 4: `NoGlyphDisplayTest` in `src/test` breaks `testFullDebugUnitTest`
**Issue:** `NoGlyphDisplay` exists only in the sdkFree source set, so any `./gradlew test` fails to compile.

**Fix:** place the test in `glyph-sdk/src/testSdkFree/kotlin/…`, and full-only tests (H8) in `src/testFull/`. Shared encoder tests stay in `src/test/`.

### M3. Task 2 Step 5 and Task 4: names and packages are inconsistent
- **Invalid namespace:** "`io.github.sgirishshiva.glyphwizard.<module>`" gives `…glyph-sdk`, which is invalid, while Task 4 paths use `…/glyph/`. **Fix:** list the namespaces explicitly:
  - `…glyphwizard.core`
  - `.agent`
  - `.evals`
  - `.designsystem`
  - `.glyph`
  - `.spike`
  - `:app` = `io.github.sgirishshiva.glyphwizard`
- **Phantom type:** the File structure (line 56) mentions `MatrixSpec`, which nothing defines. **Fix:** delete it.
- **Contradictory defaults:** Task 6 Step 9 says "set the encoder defaults in `GlyphDisplays.create`", but `create` *receives* the encoder. **Fix:** set the measured defaults where `AppContainer` constructs `MatrixFrameEncoder`, or add `MatrixFrameEncoder.Companion.forPhone4aPro()`. Test whichever you choose.
- **No transform step:** Task 6 Step 1 may discover a required transform (mirror, rotation, ordering), but no step implements it. **Fix:** add a Task 6 Step 9 sub-step: "if a transform is needed, add `Layout`/transform to `MatrixFrameEncoder`, with tests".
- **Hard-coded device ID:** `register(Glyph.DEVICE_25111p)` ignores `DeviceProfile.id` (D3). **Fix:** map `profile.id` → SDK constant in one `when`.

### M4. Task 2 Step 10 / Task 7: `lint` runs the default variant, which is `fullDebug` because "full" sorts before "sdkFree"
**Issue:** the `checks` job then fetches the SDK and needs the network.

**Fix:** `create("sdkFree") { dimension = "sdk"; isDefault = true }`. In CI, use `lintSdkFreeDebug` in `checks` and `lintFullDebug` in `full-build`.

### M5. Task 7 Step 1: the sdkFree dex check is weak
**Issue:**
- **R8 can hide classes:** R8 on `assembleSdkFreeRelease` can repackage or rename classes, so a grep for `Lcom/nothing/` can give a false negative.
- **No positive control:** nothing proves the check can fail.

**Fix:** use three checks:
- (a) `./gradlew :app:dependencies --configuration sdkFreeReleaseRuntimeClasspath | grep -q glyph-matrix-sdk && exit 1`;
- (b) the dex grep on **`assembleSdkFreeDebug`**, which isn't minified;
- (c) a positive control in `full-build`: the same grep on `app-full-debug.apk` **must** find `Lcom/nothing/ketchum/`.

Also grep the merged sdkFree manifest for `com.nothing.ketchum.permission.ENABLE`, which must be absent.

### M6. Task 3 Step 1 / Task 7 Step 4: `gh` is not installed on this machine
**Fix:**
- Task 3 is replaced by `curl` (H2).
- In Task 7, use `curl -fsSL https://api.github.com/repos/S-GirishShiva/GlyphWizard/actions/runs?branch=m0-foundations`, or add the step "install `github-cli` (`sudo pacman -S github-cli`) and `gh auth login`, with the user's OK".

### M7. Task 7 Step 4: the step pushes without asking
**Issue:** CLAUDE.md says "Push only when asked."

**Fix:** "Ask the user for permission to push `m0-foundations`; then push and watch the run."

### M8. Task 1 / Task 8 Step 4: the branch base and merge target are ambiguous
**Issue:** `design/v1-spec` isn't merged to `master`, and Task 8 leaves the target open.

**Fix:** Task 1 Step 1 asks: "Merge `design/v1-spec` into `master` first and branch `m0-foundations` from `master`?" Task 8 then merges to the agreed target only.

### M9. Task 1 Step 1: the licence question hides a conflict
**Issue:** D18 says "non-commercial" and "licence TBD (must reconcile)". GPL-3.0 permits third-party commercial use, so the question as worded doesn't surface that.

**Fix:** add to the question: *"GPL allows others to sell or modify the app; only the name and logo are reserved via NOTICE. Non-commercial use applies to the Nothing SDK, not our code. OK?"* Log the answer as a decision, and update D18's "TBD".

### M10. Task 5 Step 1: the `Patterns` tick model is underspecified
**Issue:**
- `(profile, tick)` has no tick duration, while the fps slider varies the frame period.
- "`flash(10)` toggles every 50 ms at 20 ms per tick" isn't representable, because 50 isn't a multiple of 20.
- No adb extras exist for `flash` hz or `single` x/y.

**Fix:**
- Make patterns `(profile, elapsedMs: Long) -> IntArray`, with `flash(hz)` on = `(elapsedMs * hz * 2 / 1000) % 2 == 0`.
- Test it with a table at elapsedMs 0, 49, 50, 99, 100.
- Add the extras `--ei hz N --ei x N --ei y N`.

### M11. Task 6 Step 4: the "achieved fps" is measured app-side only
**Issue:** it counts binder calls, not panel refreshes. The research doc warns that the mirror shows what was *sent*.

**Fix:** add "film `rowsSweep` at 240 fps slow-mo for each rate; count distinct row steps per second; record both numbers". Only the panel number feeds D10.

### M12. Task 6: further §10 display items are missing
**Issue:** rise time, glow, and pitch are absent.

**Fix:** add these sub-steps:
- **Rise time:** from the 240 fps `flash` footage, count the frames from off to full.
- **Glow:** photograph a `single(6,6)` at 255 and record halo bleed into the neighbouring cells.
- **Pitch:** measure the centre-to-centre distance across 13 LEDs and divide by 12.
- **Auto-timeout:** test `setGlyphMatrixTimeout(true/false)`, which exists in the SDK. Record whether the app matrix auto-clears.

### M13. Task 4: binder calls and threading
**Issue:** `setAppMatrixFrame` is a synchronous AIDL call, but the frame loop's dispatcher isn't specified. The guidelines also say "Dispatchers are injected".

**Fix:**
- `SpikeActivity`/`GlyphSession` takes a `CoroutineDispatcher` (default `Dispatchers.Default`).
- The loop runs off the main thread.
- Note that the SDK logs and swallows `RemoteException`, so `show()` can't detect a dead binder except via `onServiceDisconnected`.

### M14. Task 4 Step 4: package visibility for `bindService` is unverified [HW]
**Issue:** the SDK manifest declares no `<queries>`.

**Fix:** if `connect()` times out on the 4a Pro, add `<queries><package android:name="com.nothing.thirdparty"/></queries>` to the `full` manifest and record the result in the spike doc.

### M15. Tasks 3, 5 and 7: learning asides are missing
**Issue:** D23 requires them, and these concepts get none:
- Gradle tasks and lazy providers / `builtBy`
- SHA pinning
- BroadcastReceivers (exported vs permission)
- the Activity lifecycle (`onStart`/`onStop`)
- `lifecycleScope`
- NotificationListenerService / `shortService`
- GitHub Actions
- Gradle dependency verification
- Kover, detekt and ktlint

**Fix:** add a "Learning asides" step to each of those tasks, listing these topics.

### M16. Tasks 3–7: no Discover / understand steps
**Issue:** code-guidelines §1 requires them, but only Task 2 has one.

**Fix:** add a Step 0 to each: "Discover: read X, Y; state reuse/refactor/flows touched", naming the concrete files. For example, Task 5 reads Task 4's `GlyphDisplay` and the research mirror design.

### M17. Task 5 Step 3: the spike's exemption from UI rules needs sign-off
**Issue:** "use no `:designsystem`" overrides a CLAUDE.md non-negotiable. detekt `maxIssues: 0`, compose-rules and Lint (hard-coded strings) will also fail on it.

**Fix:** ask the user and log it as a decision. Add `:spike` exclusions in `config/detekt/detekt.yml` and `lint { disable += "HardcodedText" }` for `:spike` only, or simply apply the rules.

### M18. Spec §9 supply-chain gates are missing at M0
**Issue:** "every milestone" includes a licence audit of dependencies and Renovate.

**Fix:** add these to Task 7:
- `renovate.json` (`config:recommended`, with Gradle and GitHub Actions managers, and `glyphSdk*` excluded);
- a `docs/engineering/licences.md` table of every M0 dependency and its licence, with a GPL-3.0 compatibility column.

### M19. Task 7 Step 3: gaps in the threat model
**Fix:** add these entries:
- **Supply chain:** the SDK fetch (pinned commit and SHA), Gradle plugins, and verification metadata.
- **Debug surfaces:** the spike's receiver (DUMP-guarded), and `run-as` in debug only.
- **CI:** no secrets in M0, and actions pinned by SHA.
- **The Glyph permission's scope.**

---

## LOW

### L1. Task 5 Step 2: the `MirrorLog` alphabet collides with the mask
**Issue:** ` .:-=+*#%@` begins with a space, which is also "outside the mask".

**Fix:** use `·` for 0 and `.:-=+*#%@` (9 buckets) for 1–255. Test bucket edges 1, 28, 29 and 255.

### L2. Task 2 Step 6: `val container = AppContainer(this)` in a field initialiser
**Issue:** it runs before `attachBaseContext`. It's harmless only while the container is empty.

**Fix:** `lateinit var container: AppContainer; override fun onCreate() { super.onCreate(); container = AppContainer(this) }`.

### L3. Task 2 Step 6: theme replacement is vague
**Fix:** "set `Theme.GlyphWizard` parent to `android:Theme.Material.NoActionBar`; delete `values-night/themes.xml`".

### L4. Task 2 Step 5: `compileSdk = 37` doesn't match the repo's new-DSL form
**Fix:** keep `compileSdk { version = release(37) }`, as in the current `app/build.gradle.kts:7-9`. Change `compileOptions` from `VERSION_11` to `VERSION_17`.

### L5. Two adb binaries
**Issue:** `/usr/bin/adb` (37.0.0) and the SDK's adb (37.0.1) are both installed, and the plan mixes bare `adb` and the full path.

**Fix:** define `ADB=$HOME/Android/Sdk/platform-tools/adb` once and use `$ADB` everywhere. Use `logcat -d -s GlyphMirror:V`, never streaming, in agent steps.

### L6. Task 6 Step 9: "spec §10 checkboxes" don't exist
**Fix:** "tag each §10 bullet `[measured YYYY-MM-DD → hardware-spike-results.md#…]`".

### L7. Tech stack line 17: Robolectric is listed but not used in M0
**Issue:** H12 now uses it for `:app`.

**Fix:** state `testOptions.unitTests.isIncludeAndroidResources = true`, and pin a Robolectric version that supports SDK 36. With compileSdk 37, set `@Config(sdk = [36])` if Robolectric lacks 37 [verify].

### L8. Task 2 Step 4: `DeviceProfile` in `:core` pre-empts the D46 deep-dive
**Fix:** mark it "provisional; D46 may move or rename it". Keep it minimal, which it is.

### L9. Task 2 Step 4: `evals` uses the `application` plugin without a `mainClass`
**Fix:** `application { mainClass = "io.github.sgirishshiva.glyphwizard.evals.MainKt" }` plus a `main()` that prints its usage. That's also a real smoke test.

### L10. File structure: `.editorconfig`, `scripts/` and `renovate.json` are not listed
**Fix:** add them to the tree.

---

## Spec coverage matrix (M0)

| Spec item | Plan | Status |
|---|---|---|
| §8 Licence | T1 | OK (but see M9) |
| §8 App ID | T1, T2 S11 | OK |
| §8 Scaffold migration, modules | T2 | Partial (H3, H5) |
| §8 Flavours | T2/T3 | Partial (H1, M4) |
| §8 CI skeleton | T7 | Partial (H11, H12, M5) |
| §8 Debug glyph mirror | T5 | Throwaway only (M1) |
| §10 Layout and ordering | T6 S1 | OK |
| §10 Brightness range and curve | T6 S2–3 | OK |
| §10 Minimum visible level | T6 S3 | OK |
| §10 Max sustained fps | T6 S4 | Partial (M11) |
| §10 PWM | T6 S5 | OK |
| §10 Rise time, glow, pitch | — | Missing (M12) |
| §10 Diameter, luminance | T6 S5 | OK |
| §10 Locked / face-down / pocket | T6 S6 | OK |
| §10 Essential Notifications / AOD priority | T6 S6 | OK |
| §10 Setting path | T6 S6 | OK |
| §10 Listener process state and crash recovery | — | Missing (H10) |
| §10 Restricted settings (Obtainium) | T6 S7 | Unmeasurable and breaks D26 (C2, H10) |
| §10 `shortService` interplay | — | Missing (H10) |
| §9 Threat model | T7 S3 | Partial (M19) |
| §9 Dependency verification | T7 S2 | Broken (H11) |
| §9 sdkFree has no SDK | T7 S1 | Vacuous (H3, M5) |
| §9 Licence audit, Renovate | — | Missing (M18) |
| §9 Release-build tests | `assembleSdkFreeRelease` only | Partial: add `testSdkFreeReleaseUnitTest` to `checks` |
| §9 Learning asides | T2, T4 | Partial (M15) |
| §9 `/code-review` | T8 | OK |

## Review Focus quality

| # | Item | Owning test | Gap and fix |
|---|---|---|---|
| 1 | Missing SDK / offline | None that can fail | C1 |
| 2 | SHA mismatch | None that can fail | C1 |
| 3 | Service unavailable | None, and `connect()` can hang | H7, H8 |
| 4 | 137 vs 169 frame length | Encoder tests | OK. The test arithmetic is correct: (6,0) is input index 2, (8,12) is the last LED, 128 maps to 1027, the corner count is 32, and `cornerProbe` lights 26 LEDs |
| 5 | Releasing the matrix on leave or kill | None | H8 (`GlyphSession` test + force-stop step) |

Add a sixth item: **adb hook reachability** (H9), owned by a one-line Task 5 manual check: `$ADB shell am broadcast … DUMP` produces a `GlyphMirror` line.
