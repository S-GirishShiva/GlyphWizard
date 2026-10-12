# GlyphWizard — Modern Kotlin & Android Patterns

**Status:** v1 draft for user review (supports D45). **Date:** 2026-10-12. Applies on top of `code-guidelines.md` and, for UI, `docs/design/frontend-guidelines.md`. Where this file disagrees with those, they win and this file gets fixed.

**Why this file exists:** AI agents (and Stack Overflow) have mostly seen Java-era Android: Fragments, XML, `LiveData`, Gson, `!!`, util classes, builders. This file is the "write this, not that" list so new code starts modern.

Tags: **[V]** verified against the linked primary source on 2026-10-12 · **[U]** unverified or from memory, check before relying on it · **[D]** needs a user decision.

---

## 1. Version baseline

### 1.1 Toolchain

| Item | Version | Notes | Source |
|---|---|---|---|
| JDK | **25** daemon + toolchains; `:core`/`:agent` `-Xjdk-release=17` (they run on ART) (D54) [V] | Gradle 9.6 runs on JDK 17–26; JDK 27 needs Gradle ≥ 9.8 [V] | [Gradle compatibility](https://docs.gradle.org/9.6.1/userguide/compatibility.html), [Java versions in Android builds](https://developer.android.com/build/jdks) |
| Kotlin | **2.4.21** (8 Oct 2026, bug-fix of 2.4.20) [V] | K2 is the only compiler now; K1 and `-language-version=1.9` are gone [V] | [Releases](https://kotlinlang.org/docs/releases.html), [What's new 2.4.0](https://kotlinlang.org/docs/whatsnew24.html) |
| Language / API level | **2.4** (the default for 2.4 compiler; don't set `languageVersion`) [U: default not stated on page] | Kotlin 2.5.0 is planned for Dec 2026; 2.5.0-Beta1 exists [U] | [JetBrains blog](https://blog.jetbrains.com/kotlin/2026/05/the-road-to-name-based-destructuring/) |
| AGP | **9.4.1** (repo pin; 9.4.0 notes) [V]; needs Gradle ≥ 9.6.0, JDK ≥ 17 (a minimum; we run 25, D54), max API 37 [V] | Built-in Kotlin (AGP 9.0+): **don't apply `org.jetbrains.kotlin.android`**; it's a config error on AGP 9 [V]. Use `kotlin { compilerOptions { } }`, not `android.kotlinOptions` [V] | [AGP notes](https://developer.android.com/build/releases/gradle-plugin), [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) |
| SDK levels | min 35 / target 36 / compile 37 (spec §2.2) | Target 36 ⇒ edge-to-edge can't be opted out; predictive back is on by default; `onBackPressed` is not called [V] | [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16) |
| Compose BOM | **2026.09.00** → ui 1.12.1, material3 1.4.0 [U: mapping read by row position] | Strong skipping is on by default since Kotlin 2.0.20 [V] | [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Strong skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) |
| Navigation 3 | **1.2.0** (23 Sep 2026) [V] | `NavDisplay` takes `List<SceneStrategy>` since 1.1 [V] | [Nav3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3) |
| kotlinx.coroutines | **1.11.0** [V] | | [Releases](https://github.com/Kotlin/kotlinx.coroutines/releases) |
| kotlinx.serialization | **1.11.0** (built on Kotlin 2.3.20); 1.12.0-RC on 2.4.10 [V] | Use 1.12.0 stable once out [U] | [Releases](https://github.com/Kotlin/kotlinx.serialization/releases) |
| Ktor client | **3.6.0** (16 Sep 2026) [V] | | [Releases](https://github.com/ktorio/ktor/releases) |
| JUnit | **6.1.3** latest; 5.14.x still gets patches [V] | JUnit 6 Jupiter on JVM modules; Android modules use plain JUnit 4 + Robolectric with no Vintage engine (deprecated in JUnit 6) (D49 as amended) | [Releases](https://github.com/junit-team/junit-framework/releases) |
| Turbine | **1.2.1** [V] | | [Releases](https://github.com/cashapp/turbine/releases) |
| detekt | stable **1.23.8 is built on Kotlin 2.0.21**; **2.0.0-alpha.6** is built on Kotlin 2.4.10 [V] | ⚠ detekt stable cannot be trusted on Kotlin 2.4 sources. Either pin 2.0.0-alpha.6 or rely on ktlint + Lint + compose-rules(ktlint) until detekt 2 is stable [D] | [Releases](https://github.com/detekt/detekt/releases) |
| compose-rules | 44 rules, ktlint and detekt variants [V] | Use the **ktlint** variant if detekt stays blocked | [Rules](https://mrmans0n.github.io/compose-rules/latest/rules/) |
| kotlinx-collections-immutable | **Alpha** [V] | Frontend R9 depends on it. Acceptable (small, widely used), but it's a conscious pre-stable dependency [D] | [Stability](https://kotlinlang.org/docs/components-stability.html) |

### 1.2 Language features: allowed vs not

Rule: **Stable only.** A pre-stable feature is a *candidate* and needs a logged decision before anyone turns on its flag.

| Feature | Status | Our rule |
|---|---|---|
| `sealed interface`, `data object`, value classes (`@JvmInline value class`), `enum.entries`, `..<` | Stable (1.5–1.9) [U: from memory, long stable] | **Allowed, preferred** |
| Guard conditions in `when` (`is X if cond ->`) | Stable 2.2.0 [V] | **Allowed** |
| Non-local `break`/`continue` in inline lambdas | Stable 2.2.0 [V] | **Allowed** |
| Multi-dollar interpolation (`$$"""…"""`) | Stable 2.2.0 [V] | **Allowed** (good for JSON schemas in `:agent`) |
| `Base64`, `HexFormat` | Stable 2.2.0 [V] | **Allowed** |
| Nested type aliases | Stable 2.3.0 [V] | Allowed, sparingly |
| Data-flow-based `when` exhaustiveness | Stable 2.3.0 [V] | Allowed (automatic) |
| `kotlin.time.Instant` / `Clock` | Stable 2.3.0 [V] | **Allowed, preferred** over `java.time.Instant` in `:core` |
| `kotlin.uuid.Uuid` | Stable 2.4.0, **except** `generateV4()`/`generateV7()` (experimental) [V] | **Allowed**; use `Uuid.random()` [U: assumed stable as it predates the generators] |
| Explicit backing fields (`field = MutableStateFlow(...)`) | Stable 2.4.0 [V] | **Allowed** once ktlint/detekt parse it in M0 [U] |
| Context parameters | Stable 2.4.0 (explicit context *arguments* are experimental) [V] | **Not for DI.** Constructor injection only (D45). Allowed only for scoped DSL vocabulary after review [D] |
| Annotation use-site targets / `@all` | Stable 2.4.0 [V] | Allowed |
| Name-based destructuring | Experimental since 2.3.20; planned Stable in 2.5.0 (only-syntax mode) [V/U] | **Candidate.** Until then avoid positional destructuring of data classes with > 2 fields |
| Unused return-value checker (`-Xreturn-value-checker`) | Experimental 2.3.0 [V] | **Candidate** (fits errors-as-values well; revisit when stable) |
| Collection literals `[a, b]` | Experimental 2.4.0 [V] | **Not allowed** |
| Context-sensitive resolution | Experimental [V] | **Not allowed** |
| Rich errors (`T | Error`) | Exploration only, not shipped [V] | **Not allowed**; we use `Outcome<T, E>` |
| `@OptIn(Experimental*Api::class)` anywhere | — | **Not allowed** without a decision entry |

Sources: [What's new 2.2](https://kotlinlang.org/docs/whatsnew22.html), [2.3](https://kotlinlang.org/docs/whatsnew23.html), [2.4](https://kotlinlang.org/docs/whatsnew24.html), [2.4.20](https://kotlinlang.org/docs/whatsnew2420.html), [Features and proposals](https://kotlinlang.org/docs/kotlin-language-features-and-proposals.html).

---

## 2. Write this, not that

Each pair: ❌ the old habit, ✅ what we write.

### 2.1 Data modelling
```kotlin
// ❌ Java bean + magic constants
class Frame { private int index; public int getIndex() {...} ... equals/hashCode/toString }
const val TRIGGER_CALL = 1
// ✅ data class, closed sets as sealed interface, singletons as data object
data class Frame(val index: Int, val pixels: PixelGrid)
sealed interface Trigger {
    data object IncomingCall : Trigger
    data class Notification(val packageName: String) : Trigger
}
```
```kotlin
// ❌ primitive soup: fun setBrightness(led: Int, value: Int)
// ✅ value class: type safety, zero allocation on the JVM in most cases
@JvmInline value class Brightness(val level: Int) {
    init { require(level in 0..255) { "brightness out of range: $level" } }
}
```
```kotlin
// ❌ Mode.values()          ✅ Mode.entries   (immutable list, no array copy)
```
Rule: `enum` for a fixed set with no per-case data; `sealed interface` when cases carry different data.

### 2.2 Nullability
```kotlin
// ❌ val name = user!!.name
// ✅ handle the absent case explicitly
val name = user?.name ?: return Outcome.Err(DocError.MissingAuthor)
val doc = requireNotNull(cache[id]) { "doc $id must be loaded before edit" } // programmer error
check(isOpen) { "player used after close" }
```
No `!!` anywhere. No `lateinit` outside test fixtures. Platform types from Java APIs get an explicit Kotlin type at the boundary.

### 2.3 Collections
```kotlin
// ❌ val out = ArrayList<Frame>(); for (f in frames) if (f.isKey) out.add(f); return out
// ✅
val keyFrames: List<Frame> = frames.filter { it.isKey }
val layers = buildList { add(base); if (hasOverlay) add(overlay) }
val byId = buildMap { docs.forEach { put(it.id, it) } } // or docs.associateBy { it.id }
```
- Expose `List`/`Map`/`Set`, never `MutableList` or `ArrayList`. In UI state use `ImmutableList` (R9).
- `Sequence` only for long chains on large inputs or when you stop early (`first`, `take`). Default to `List`; measure in hot paths.
- Use `firstOrNull`, `getOrElse`, `orEmpty()`, `isNullOrEmpty()`, `any`/`none` instead of index loops and `find != null`.

### 2.4 Scope functions
| Function | Use for | Returns |
|---|---|---|
| `let` | null-guarded transform: `x?.let(::parse)` | lambda result |
| `apply` | configuring a freshly built object | the receiver |
| `also` | side effects in a chain (log, assert) | the receiver |
| `run` / `with` | a few calls on one receiver to compute a value | lambda result |

Never nest scope functions and never chain more than one. If `it` becomes ambiguous, name the parameter or use a plain `val`. `if (x != null)` is fine and often clearer than `?.let`.

### 2.5 Extension functions vs util classes
```kotlin
// ❌ object FrameUtils { fun isBlank(f: Frame): Boolean }   // also a static holder
// ✅ top-level function (Go package func), or an extension if it reads as the type's own vocabulary
fun Frame.isBlank(): Boolean = pixels.all { it == Brightness(0) }
```
Extensions sparingly (D45 §3). No `*Utils`/`*Helper` classes.

### 2.6 Strings
```kotlin
// ❌ "Frame " + index + " of " + total     ❌ String.format("%d/%d", a, b) for plain joining
// ✅ "Frame $index of $total"
// ✅ JSON with literal $: multi-dollar raw string
val schema = $$"""{"$schema": "https://json-schema.org/draft/2020-12/schema", "title": "$$name"}"""
```
User-visible text lives in string resources (R3), not templates.

### 2.7 `when`, guards, ranges
```kotlin
// ❌ if (e instanceof Call && e.isMissed()) ... else if ...
// ✅ exhaustive when over a sealed type, guard for the extra condition, no `else` on sealed types
fun describe(e: TriggerEvent): Int = when (e) {
    is TriggerEvent.Call if e.isMissed -> R.string.missed_call
    is TriggerEvent.Call -> R.string.call
    is TriggerEvent.Notification -> R.string.notification
}
// ❌ for (i in 0 until n)   ❌ for (i in 0..n - 1)
// ✅ for (i in 0..<n)   or   frames.indices   or   repeat(n) { }
```
No `else ->` branch on a sealed `when`: adding a case must break the build.

### 2.8 Properties, default and named args
```kotlin
// ❌ getName()/setName(), overload ladders, Builder classes
// ✅ properties; computed val; defaults + named args; copy() for "withers"
class Playback(val fps: Int = 30, val loop: Boolean = true)
val isEmpty: Boolean get() = frames.isEmpty()
val next = state.copy(selectedFrame = index)
GwButton(text = stringResource(R.string.play), onClick = onPlay, isEnabled = canPlay)
```
Use named arguments for every boolean and for any call with ≥ 3 arguments.

### 2.9 `object` and `companion object`
```kotlin
// ❌ object Repository { val cache = mutableMapOf<...>() }   // hidden mutable singleton
// ❌ companion object { fun getInstance() }                  // service locator
// ✅ stateless objects only: sealed-case data objects, constants, Nav keys, serializer modules.
// ✅ companion only for factory functions (`fun from(...)`) and `const val`s that belong to the type.
```

### 2.10 Delegation
```kotlin
// ✅ lazy for expensive, rarely-needed values; thread-safe by default
private val palette by lazy { buildPalette(curve) }
// ✅ interface delegation instead of inheritance
class LoggingStore(private val inner: DocumentStore, ...) : DocumentStore by inner { override suspend fun save(...) }
// ✅ Compose state
var query by rememberSaveable { mutableStateOf("") }
```
No `Delegates.notNull()` (that's `lateinit` in disguise).

### 2.11 Errors as values
```kotlin
// ❌ throw for expected failures; ❌ catch (e: Exception) {} ; ❌ Result<T> with a Throwable for domain errors
// ✅ :core / :agent
sealed interface Outcome<out T, out E> {
    data class Ok<T>(val value: T) : Outcome<T, Nothing>
    data class Err<E>(val error: E) : Outcome<Nothing, E>
}
sealed interface ImportError { data object NotGlyphFile : ImportError; data class BadVersion(val found: Int) : ImportError }
```
- Exceptions only for programmer errors (`require`/`check`/`error()`).
- At I/O boundaries catch the **specific** exception (`IOException`, `SerializationException`, `ResponseException`) and map it to the domain error.
- **`runCatching` is banned in suspend code.** It catches `CancellationException` and breaks cancellation. If you must catch broadly in a coroutine, rethrow cancellation first:
```kotlin
try { client.send(req) } catch (e: CancellationException) { throw e } catch (e: IOException) { Outcome.Err(NetError.Offline) }
```
Enforced by detekt `SuspendFunSwallowedCancellation` [V].

### 2.12 Coroutines and Flow
```kotlin
// ❌ GlobalScope.launch { }   ❌ runBlocking in prod   ❌ withContext(Dispatchers.IO) hard-coded   ❌ Thread.sleep
// ✅ dispatchers and scopes are injected (constructor DI)
class DocumentRepository(private val io: CoroutineDispatcher, private val files: FileStore) {
    suspend fun load(id: DocId): Outcome<Doc, LoadError> = withContext(io) { ... }
}
```
- **Structured concurrency:** every coroutine has an owner (`viewModelScope`, a scope the container creates and cancels, `coroutineScope { }` inside a function). App-wide work uses an injected `applicationScope`, never `GlobalScope`.
- **Suspend vs Flow:** one-shot = `suspend fun`; a stream = a non-suspend `fun …(): Flow<T>` (detekt `SuspendFunWithFlowReturnType`).
- **UI state:** `StateFlow`, not `LiveData`:
```kotlin
class EditorViewModel(repo: DocumentRepository, docId: DocId) : ViewModel() {
    val uiState: StateFlow<EditorUiState> = repo.observe(docId)
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState.Loading)
}
```
  For state the ViewModel writes itself, use an explicit backing field (stable in 2.4) instead of the `_state`/`state` pair:
```kotlin
val uiState: StateFlow<EditorUiState>
    field = MutableStateFlow(EditorUiState.Loading)
```
- **Callback APIs → Flow:** `callbackFlow { register(cb); awaitClose { unregister(cb) } }` (missing `awaitClose` = leak).
- **One-shot callback → suspend:** `suspendCancellableCoroutine { cont -> …; cont.invokeOnCancellation { cancel() } }`, never `suspendCoroutine`.
- **One-off UI events:** model them as state (e.g. `pendingMessage: Message?` cleared by an event), not `SharedFlow`/`Channel` that can drop events [U: Android architecture guidance, not re-fetched].
- **`finally` that suspends:** wrap in `withContext(NonCancellable)` (detekt `SuspendFunInFinallySection`).

### 2.13 Serialization
```kotlin
// ❌ Gson / Moshi-reflection / org.json / manual JSONObject parsing
// ✅ kotlinx.serialization, compile-time, no reflection
@Serializable data class GlyphDoc(val schemaVersion: Int, val frames: List<Frame>)
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true } // one configured instance, injected
```
Sealed hierarchies get `@SerialName` on every case so renames don't break files. Ktor uses `ContentNegotiation { json(json) }`.

### 2.14 Time
```kotlin
// ❌ Date(), Calendar, SimpleDateFormat, System.currentTimeMillis(), Long millis everywhere
// ✅ inject a Clock; Duration for spans; kotlin.time in :core
class Recorder(private val clock: Clock) { fun stamp(): Instant = clock.now() }
val frameTime: Duration = 33.milliseconds
```
`java.time` is fine at Android boundaries (formatting with `DateTimeFormatter` and locale). Monotonic timing uses `TimeSource.Monotonic`, not wall time.

### 2.15 Android
| ❌ Not that | ✅ This |
|---|---|
| `AppCompatActivity`, Fragments, XML layouts, `findViewById`, ViewBinding, DataBinding | one `ComponentActivity` + `setContent { }` |
| no inset handling, `windowOptOutEdgeToEdgeEnforcement` | `enableEdgeToEdge()` before `setContent`; consume `WindowInsets` in `:designsystem` scaffolding |
| `onBackPressed()`, `KEYCODE_BACK` | Navigation 3 back stack; `BackHandler`/`PredictiveBackHandler` for custom back (predictive back is on by default at target 36) [V] |
| Jetpack Navigation (`NavController`, XML nav graphs, string routes) | Navigation 3: back stack is a list of `@Serializable` `NavKey`s you own |
| `AsyncTask`, `Handler`/`Looper.post`, `Executors`, `Thread` | coroutines with injected dispatchers |
| `LiveData`, RxJava | `StateFlow` / `Flow` |
| `SharedPreferences` | DataStore |
| `android.support.*`, `kapt`, Dagger/Hilt/Koin | AndroidX; no annotation processing (KSP only if a library requires it); manual DI container |
| `AndroidViewModel` / `Context` in ViewModels | inject the narrow interface the ViewModel needs |
| `ViewModelProvider` boilerplate | `viewModel { EditorViewModel(container.repo, key.id) }` initializer factory |

Navigation 3 shape:
```kotlin
@Serializable data object Library : NavKey
@Serializable data class Editor(val docId: String) : NavKey

val backStack = rememberNavBackStack(Library)
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Library> { LibraryScreen(onOpen = { backStack.add(Editor(it)) }) }
        entry<Editor> { key -> EditorScreen(docId = key.docId) }
    },
)
```
Per-entry ViewModel scoping uses the lifecycle ViewModel nav-entry decorator [U: exact artifact/function name not verified; check the Nav3 docs in M0].

Build: R8 is in full mode by default since AGP 8.0 [U]; keep `isMinifyEnabled = true` for release and test the minified build (spec §9). Ship Baseline Profiles via the `androidx.baselineprofile` plugin and a Macrobenchmark module.

### 2.16 Compose
```kotlin
// ❌ ViewModel passed down; state owned deep in the tree; side effects in composition
@Composable fun FrameStrip(vm: EditorViewModel) { vm.load(); val s = vm.state.value ... }
// ✅ stateful screen at the top, stateless content below (R6–R8)
@Composable fun EditorScreen(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditorContent(state = state, onEvent = viewModel::onEvent)
}
@Composable fun EditorContent(state: EditorUiState, onEvent: (EditorEvent) -> Unit, modifier: Modifier = Modifier)
```
- **Collect with `collectAsStateWithLifecycle()`**, never `collectAsState()` or `.value` in composition (Lint `StateFlowValueCalledInComposition`) [V].
- **`remember` keys:** `remember(docId) { … }` whenever the computation depends on an input. Missing keys = stale values.
- **`rememberSaveable`** for UI-only state that must survive process death (text field, selected tab). Screen data comes from the ViewModel.
- **`derivedStateOf`** only when a fast-changing state produces a slower-changing result (`showFab = listState.firstVisibleItemIndex > 0`). Not for plain mapping.
- **Effects:** `LaunchedEffect(key)` with real keys (`Unit` only for truly one-time work). Use `rememberUpdatedState` for lambdas captured by long-lived effects (compose-rules `LambdaParameterInRestartableEffect`). `LaunchedEffect` runs off-screen too; for "user can see it" logic use `LifecycleEventEffect` [V].
- **No side effects in composition:** no `launch`, I/O, logging or state writes in the body (Lint `CoroutineCreationDuringComposition`).
- **Custom modifiers:** chain existing modifiers first; for custom draw/layout/input use `Modifier.Node` + `ModifierNodeElement` (a `data class`). **`Modifier.composed {}` is no longer recommended** [V].
- **Stability under strong skipping:** unstable params are compared by `===`, lambdas are auto-remembered [V]. So: keep UI state `data class` + `val` + `ImmutableList`; add `@Immutable`/`@Stable` only when a class really can't be inferred stable and you've checked the compiler report. Don't sprinkle them.
- **Frequently changing values** (playhead): pass `() -> T` and read late, in layout/draw (frontend §4).
- Lazy lists always pass `key = { it.id }`.

### 2.17 Testing
```kotlin
// ❌ JUnit 4 @Before/@RunWith in JVM modules, Mockito everywhere, Thread.sleep, runBlocking
// ✅ kotlin.test assertions on JUnit Jupiter, fakes, runTest, Turbine
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Test fun `adding a frame selects it`() = runTest(dispatcher) {
        val vm = EditorViewModel(FakeDocumentRepository(sampleDoc), dispatcher)
        vm.uiState.test {
            assertEquals(EditorUiState.Loading, awaitItem())
            vm.onEvent(EditorEvent.AddFrame)
            assertEquals(1, (awaitItem() as EditorUiState.Ready).selectedFrame)
        }
    }
}
```
- Inject `TestDispatcher` (or `Dispatchers.setMain` for `viewModelScope` [U: `viewModelScope` uses `Dispatchers.Main.immediate`]).
- Fakes implement the consumer-defined interface; mocks only for things you can't fake.
- Ktor: `MockEngine` with recorded streams (spec).
- Time: `runTest`'s virtual time and `testScheduler.timeSource`, never real delays.
- Back-ticked behaviour names in JVM tests.

---

## 3. Banned legacy list and enforcement

Legend: **D** = detekt (needs detekt 2.x on Kotlin 2.4, see §1.1 [D]) · **L** = Android Lint · **CR** = compose-rules (ktlint id / detekt id) · **K** = ktlint · **C** = custom check needed.

| # | Banned | Use instead | Enforced by |
|---|---|---|---|
| 1 | `!!` | `?:`, `requireNotNull`, early return | D `UnsafeCallOnNullableType`, `MapGetWithNotNullAssertionOperator` |
| 2 | `lateinit` in prod code | constructor params, `by lazy` | D `LateinitUsage` (exclude tests) |
| 3 | `as` unchecked casts | `as?` + handling, sealed `when` | D `UnsafeCast`, `CastNullableToNonNullableType` |
| 4 | `GlobalScope` | injected scope | D `GlobalCoroutineUsage` (inactive by default, turn on) |
| 5 | Hard-coded `Dispatchers.IO/Default` | injected dispatcher | D `InjectDispatcher` |
| 6 | `runBlocking` in prod | `suspend` / scope | D `ForbiddenMethodCall` (`kotlinx.coroutines.runBlocking`) |
| 7 | `Thread.sleep` | `delay` | D `SleepInsteadOfDelay` + `ForbiddenMethodCall` |
| 8 | `runCatching` around suspend calls | typed `try/catch`, rethrow cancellation | D `SuspendFunSwallowedCancellation` |
| 9 | `suspend fun …(): Flow` | plain `fun …(): Flow` | D `SuspendFunWithFlowReturnType` |
| 10 | Suspending in `finally` without `NonCancellable` | `withContext(NonCancellable)` | D `SuspendFunInFinallySection` |
| 11 | `LiveData`, `MutableLiveData` | `StateFlow` | D `ForbiddenImport` (`androidx.lifecycle.LiveData`, `androidx.lifecycle.MutableLiveData`) |
| 12 | RxJava | coroutines/Flow | D `ForbiddenImport` (`io.reactivex.*`) + C: Gradle dependency ban |
| 13 | Gson / Moshi / `org.json` | kotlinx.serialization | D `ForbiddenImport` (`com.google.gson.*`, `com.squareup.moshi.*`, `org.json.*`) |
| 14 | `java.util.Date`, `Calendar`, `SimpleDateFormat`, `System.currentTimeMillis` | `Clock`, `Instant`, `Duration` | D `ForbiddenImport` + `ForbiddenMethodCall` |
| 15 | `java.util.UUID` | `kotlin.uuid.Uuid` | D `ForbiddenImport` |
| 16 | Fragments, `AppCompatActivity`, `findViewById`, ViewBinding | `ComponentActivity` + Compose | D `ForbiddenImport` (`androidx.fragment.*`, `androidx.appcompat.*`) + `ForbiddenMethodCall` (`findViewById`) |
| 17 | XML layouts | Compose | C: Gradle check that `src/*/res/layout*` is empty |
| 18 | `AsyncTask`, `Handler`, `Executors`, raw `Thread` | coroutines | D `ForbiddenImport` (`android.os.AsyncTask`, `android.os.Handler`, `java.util.concurrent.Executors`) |
| 19 | `android.support.*` | AndroidX | D `ForbiddenImport`; L (Jetifier off) |
| 20 | `kapt`, Hilt, Dagger, Koin | manual DI; KSP only if forced | C: Gradle plugin/dependency ban (`kotlin-kapt`, `com.android.legacy-kapt`, `com.google.dagger.*`, `io.insert-koin.*`) |
| 21 | `SharedPreferences` | DataStore | D `ForbiddenImport` (`android.content.SharedPreferences`) |
| 22 | `onBackPressed`, `KEYCODE_BACK` | Nav3 / `BackHandler` | D `ForbiddenMethodCall`; target 36 makes it dead anyway |
| 23 | `Enum.values()` | `entries` | K/IDE inspection; C if needed [U: no confirmed detekt rule] |
| 24 | `0 until n` / `0..n-1` | `0..<n` | D `RangeUntilInsteadOfRangeTo` covers `..n-1` only; `until` via review [U] |
| 25 | `else` branch on sealed `when` | exhaustive branches | C (detekt custom rule) [U: no built-in] |
| 26 | `object` with mutable state / `getInstance()` | DI container | review + C |
| 27 | `*Utils` / `*Helper` classes | top-level or extension fns | D `UtilityClassWithPublicConstructor` (partial) + review |
| 28 | Mutable public collections, `var` in data classes | `val`, read-only types | D `DataClassShouldBeImmutable`, `DoubleMutabilityForCollection`, `VarCouldBeVal` |
| 29 | `@OptIn` / `-X` experimental flags without a decision | stable APIs | D `ForbiddenAnnotation` (`kotlin.OptIn`) with per-file suppression via decision + C: review of `freeCompilerArgs` |
| 30 | `collectAsState()`, `.value` in composition | `collectAsStateWithLifecycle()` | L `StateFlowValueCalledInComposition`; D `ForbiddenMethodCall` for `collectAsState` |
| 31 | Coroutines/side effects in composition | `LaunchedEffect`, events | L `CoroutineCreationDuringComposition`, `FlowOperatorInvokedInComposition`, `RememberReturnType`, `UnrememberedMutableState` |
| 32 | `mutableStateOf<Int>` boxing | `mutableIntStateOf` | L `AutoboxingStateCreation`; CR `compose:mutable-state-autoboxing` / `MutableStateAutoboxing` |
| 33 | `Modifier.composed {}` | `Modifier.Node` | CR `compose:modifier-composed-check` / `ModifierComposed` |
| 34 | ViewModel passed to children / created in children | hoisted state | CR `compose:vm-forwarding-check` / `ViewModelForwarding`; `compose:vm-injection-check` / `ViewModelInjection` |
| 35 | Mutable types or `MutableState` as params | values + lambdas | CR `compose:mutable-params-check` / `MutableParams`; `compose:mutable-state-param-check` / `MutableStateParam` |
| 36 | `List`/`Set`/`Map` in UI state | `ImmutableList` | CR `compose:unstable-collections` / `UnstableCollections` (opt-in) + compiler stability report (R9) |
| 37 | Missing/misplaced `Modifier` param | first optional param, root only | CR `ModifierMissing`, `ModifierNotUsedAtRoot`, `ModifierReused`, `ModifierWithoutDefault` |
| 38 | Lambdas captured by effects without keys | `rememberUpdatedState` | CR `compose:lambda-param-in-effect` / `LambdaParameterInRestartableEffect` |
| 39 | `remember` missing around state | `remember { mutableStateOf() }` | CR `compose:remember-missing-check` / `RememberMissing` |
| 40 | Material 2 | M3 inside `:designsystem` | CR `compose:material-two` / `Material2` (opt-in) |
| 41 | Wildcard imports, formatting drift | official style | K `standard:no-wildcard-imports` and the standard ruleset [U: rule id from memory] |
| 42 | `SDK_INT` checks below 35 | none needed | L `ObsoleteSdkInt` |
| 43 | Mockito/MockK as default | fakes | review (C optional: dependency ban) |

**Note for the frontend guidelines:** R6/R8/R10 cite `ComposeViewModelForwarding`, `ComposeMutableParameters`, `ComposeViewModelInjection`, `ComposeModifierMissing`, `ComposeModifierReused`. The current compose-rules ids are `ViewModelForwarding`, `MutableParams`, `ViewModelInjection`, `ModifierMissing`, `ModifierReused` (detekt) or `compose:*-check` (ktlint) [V]. Fix that file when it's next touched.

Sources: [detekt potential-bugs](https://detekt.dev/docs/rules/potential-bugs), [coroutines](https://detekt.dev/docs/rules/coroutines), [style](https://detekt.dev/docs/rules/style) (docs are for 2.0.0-alpha.6) [V]; [compose-rules](https://mrmans0n.github.io/compose-rules/latest/rules/) [V]; [Lint check index](https://googlesamples.github.io/android-custom-lint-rules/checks/index.md.html) [V].

---

## 4. Go → Kotlin mapping (D45)

| Go | Kotlin in GlyphWizard |
|---|---|
| `func F() (T, error)` | `fun f(): Outcome<T, E>` with a `sealed interface` E |
| `if err != nil { return err }` | `when (val r = f()) { is Err -> return r; is Ok -> r.value }` (or a small `Outcome` helper; no exceptions) |
| `errors.Is` / typed errors | `when (error) { is ImportError.BadVersion -> … }` exhaustive |
| `panic` | `require` / `check` / `error()` (programmer errors only) |
| struct | `data class` with `val`s |
| iota consts | `enum class` + `entries` |
| tagged union by convention | `sealed interface` + `data class`/`data object` |
| small interface at the consumer | `fun interface` / small `interface` declared next to its user |
| embedding | interface delegation `by` |
| package-level func | top-level `fun` (`internal` by default) |
| constructor `NewX(deps)` | primary constructor; wiring in the manual container |
| `context.Context` cancellation | `CoroutineScope` / `Job`; cancellation is cooperative |
| goroutine | `scope.launch { }` (always in an owned scope) |
| `sync.WaitGroup` / errgroup | `coroutineScope { a = async{}; b = async{} }` |
| channel | `Flow` (cold stream); `Channel` only for real hand-off between coroutines |
| `select` | `select { }` (rare) or `merge`/`combine` of Flows |
| `time.Duration`, `time.Now()` | `kotlin.time.Duration`, injected `Clock.now()` |
| `defer` | `try/finally`, `use { }` for `Closeable` |
| `encoding/json` struct tags | `@Serializable` + `@SerialName` |
| `go vet` / `golangci-lint` | detekt + Android Lint + ktlint + compose-rules |
| table-driven tests | parameterised JUnit tests or a `listOf(case…).forEach` with named cases |
| `httptest.Server` | Ktor `MockEngine` |

Where Go and Kotlin idioms conflict, Kotlin wins: use `?.`/`?:` rather than sentinel zero values, expressions (`val x = when …`) rather than mutable locals, and `copy()` rather than mutating structs.

---

## 5. Agent self-check (run before finishing any code task)

1. Did I read `CLAUDE.md`, `code-guidelines.md`, this file and (for UI) the frontend guidelines, and state reuse/refactor/flows?
2. Zero `!!`, `lateinit` (outside tests), unchecked `as`, `runCatching` in suspend code, `else` on sealed `when`?
3. Expected failures returned as `Outcome`; exceptions only via `require`/`check`/`error`; no swallowed errors; `CancellationException` never caught without rethrow?
4. Every coroutine in an owned scope; dispatchers and `Clock` injected; no `GlobalScope`/`runBlocking`/`Thread.sleep`?
5. All dependencies via constructor; nothing new in a mutable `object` or companion?
6. Models are `data class`/`sealed interface`/`value class`; public types expose read-only collections; UI state uses `ImmutableList`?
7. No banned import from §3 (Gson, `Date`, `UUID`, `LiveData`, Fragment, `Handler`, `SharedPreferences`, support libs)?
8. Compose: state hoisted, `collectAsStateWithLifecycle`, effects keyed, no side effects in composition, `Modifier` first optional param at root, `Gw*` + tokens only, strings in resources?
9. No experimental `@OptIn` or `-X` flag without a decision entry?
10. Scope functions not nested; named args on booleans; no `*Utils`; comments are why-only?
11. Tests written after: `runTest` + test dispatcher, Turbine for Flows, fakes, coverage targets met?
12. `ktlint`, Lint and detekt clean; release (minified) test run passes?
13. New Kotlin/Android concept used? Learning aside added (D23).

---

## Learning aside
**Why "modern" matters here:** Kotlin and Android changed fast between 2019 and 2026. The K2 compiler, Compose, coroutines and Flow replaced most of what older tutorials teach, and target SDK 36 removes old escape hatches (edge-to-edge opt-out, `onBackPressed`). Think of this file as the project's `golangci-lint` config written in prose: the tools enforce what they can, and this list covers the rest.
- Read: kotlinlang.org → "Idioms" and "Coding conventions"
- Read: developer.android.com → "Guide to app architecture", "State and Jetpack Compose", "Side-effects in Compose"
