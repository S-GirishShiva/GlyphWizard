# App Foundations Research: Stack, Architecture, Build, Testing, CI, Licence

2026-10-11. Written for a developer with Go backend/auth and Python AI experience who has never built an Android app. Each section covers what we pick, why, what we rejected, and a **Learning aside** (D23).

Inputs: `agent-research-brief.md`, `decisions.md` (D1–D23), `2026-10-11-targeted-android-platform.md` (minSdk 35 / targetSdk 36 / compileSdk 37; GitHub Releases + Obtainium), `2026-10-11-agent-architecture-draft.md` §2 (modules), and the current scaffold.

Tags (same as the platform doc):
- **[V]** verified against a primary source this session
- **[S]** secondary source (blog, issue tracker, search summary)
- **[I]** my inference from verified facts
- **[U]** unverified; check before relying on it

---

## TL;DR

| Area | Pick |
|---|---|
| UI | **Jetpack Compose** plus Material 3 *components*, with a **custom monochrome theme** and a hand-drawn `Canvas` matrix. Google calls Views "maintenance mode" [V]. |
| Architecture | Google's UI layer / data layer split with UDF. **`:core` is the domain layer.** The ViewModel holds a `StateFlow<UiState>` and calls `dispatch(command)`. |
| Navigation | **Navigation 3** (stable since Nov 2025, 1.2.0 on 23 Sep 2026 [V]). The back stack is a list you own. |
| DI | **Manual DI** (one `AppContainer`). No Hilt or Koin in v1. `:core` and `:agent` use plain constructor injection, as in Go. |
| Storage | **Files, not Room.** `.glyphwiz` JSON and a `.history.gz` sidecar live in `filesDir`. Settings use DataStore. Triggers use a typed JSON DataStore. API keys sit in `noBackupFilesDir`, wrapped by Keystore. Import/export uses SAF. |
| Concurrency | Coroutines + Flow. Bakes run on `Dispatchers.Default`, and a new edit cancels a stale bake (`collectLatest`). The playback tick is a deadline loop, not `scheduleAtFixedRate`. |
| Build | **Keep the scaffold and migrate it by hand.** Changes: Views → Compose deps, `com.example` → real ID, minSdk 24 → 35, **targetSdk 37 → 36**, Java 11 → 17. No KSP is needed: no Hilt, no Room. No convention plugins yet. |
| Tests | JVM modules: JUnit Jupiter + `kotlin.test`, text-grid golden files, Turbine. App: JUnit 4 + Compose tests on Robolectric. Roborazzi screenshots come later. Prefer fakes over mocks. |
| CI | GitHub Actions: `check` + `lint` on PRs; a tag builds the signed `sdkFree` APK and publishes a GitHub Release. Dependabot. ktlint + Android Lint; **skip detekt** (2.0 is still alpha [S]). Reproducible builds are deferred. |
| Licence | **Recommended: GPL-3.0-or-later plus a §7 additional permission for the Nothing Glyph SDK**, plus a name/logo reservation. "Non-commercial" can't be both a licence term and OSI open source [V]. The user decides (Q1). |
| Repo | Fix `.gitignore` before the first commit (there are no commits yet). Add `.kotlin/`, `**/build/`, keystores, the SDK `.aar`, and most of `.idea/`. Use Conventional Commits. |

---

## 1. UI toolkit

### 1.1 Compose vs Views

**Evidence**
- Google's "Android is Compose-first" page (last updated 2026-06-22) says the View toolkit is "in **maintenance mode** — this means that it will only receive highly critical fixes" [V]. Fragment, RecyclerView, ConstraintLayout, Navigation (the Views/Fragment version) and Material Components for Views are also in maintenance mode [V].
- The same page says: "Any new Android Studio UI tools will be built for Jetpack Compose only" [V]. Docs, codelabs and samples now focus on Compose [V].
- The May 2026 Android Developers Blog post ("Android UI Development is Compose First") says to build all new features in Compose [S, via search summary].
- **The scaffold is Views-based.** It uses `appcompat` 1.6.1 and `material` 1.10.0 (Material Components for *Views*). It was likely generated from Android Studio's "Empty **Views** Activity" template, or with no activity at all [I].

**Choice: Compose.** Views would mean learning a toolkit in maintenance mode, plus XML layouts, Fragments and RecyclerView adapters.

**Why Compose suits this app**
- The core of GlyphWizard is a custom-drawn grid of 137 or 489 LEDs, a timeline, and a chat panel.
- In Compose, a `Canvas { drawCircle(...) }` inside a composable is ordinary code. In Views you'd subclass `View` and override `onDraw`.
- Compose is declarative: UI = f(state). That fits the immutable document plus command model (D5).

**Mental model for a Go/Python dev**
- A composable is a function from state to UI, like a React component or a Jinja template that re-runs.
- Compose *recomposes*: it re-runs only the functions whose inputs changed.
- You never mutate widgets. You change state, and the UI follows.

**Rejected alternatives**
- **Views + XML:** maintenance mode [V].
- **Compose Multiplatform (desktop + Android):** D14 already keeps the engine in pure-Kotlin modules, and nothing in v1 needs a desktop UI. You can still adopt it later, because the engine has no Android dependencies [I].

> **Learning aside: Jetpack Compose.** Compose builds UI from Kotlin functions marked `@Composable` that describe the screen for the current state. When state changes, Compose re-runs only the affected functions ("recomposition"). Think of it like React, or like a Python function that returns the page for a given dict of state. The two ideas to learn first are where state lives (state hoisting) and why composables must be side-effect free.
> - [Android Basics with Compose (official course)](https://developer.android.com/courses/android-basics-compose/course)
> - [Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
> - [State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)

### 1.2 Material 3 vs a custom design (Nothing aesthetic)

**The tension:** Nothing's look is monochrome, red-accented and dot-matrix. Material 3 (M3) is colourful by default and offers dynamic colour.

**Recommendation: use M3 *components*, but take control of the *theme*.**
- **Use M3** for `Scaffold`, `TopAppBar`, `ModalBottomSheet`, `Slider`, dialogs and text fields. They come with accessibility, touch targets, predictive-back animations (M3 ≥ 1.3.0 [V]) and inset handling, and you'd otherwise rebuild all of that yourself.
- **Override the theme:**
  - Turn off dynamic colour.
  - Use a black/white/grey `ColorScheme` with one accent, and a `Typography` built on a dot-matrix display font for headings.
  - Use square or small corner `Shapes`.
- **Custom-draw only the domain widgets:** the matrix canvas, the timeline, and the LED preview.
- **Font licence caveat** (already flagged in context research K9): Nothing's **Ndot 57/55 are proprietary**. Use an OFL font (NDOT 47 by Interactivate, with its author's similarity caveat) or draw a 5×7 font yourself [S].
- **Dark-first:** the LED preview should look like the real matrix, i.e. dark. Still support the light theme so system settings behave; that's cheap with M3 tokens [I].

**Rejected alternatives**
- **A fully custom design system** (Google documents how [V-link]). That means writing every component: too much for a solo first app.
- **Stock M3 with dynamic colour.** It looks nothing like the target aesthetic.

> **Learning aside: theming in Compose.** `MaterialTheme(colorScheme, typography, shapes)` wraps your UI, and every M3 component reads its colours and fonts from it. You get a consistent monochrome look by changing ~30 colour tokens, not by restyling each widget. A custom font is a resource file plus a `FontFamily`.
> - [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
> - [Custom design systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom)
> - [M3 theme builder](https://material-foundation.github.io/material-theme-builder/)

### 1.3 Edge-to-edge and predictive back (forced by targetSdk 36)

**Edge-to-edge**
- "Edge-to-edge is enforced on Android 15 and higher once your app targets SDK 35" [V].
- At target 36 the opt-out is gone [V, platform doc].
- So the app draws behind the status bar and the gesture bar, and **you** must pad content using `WindowInsets`.
- M3 `Scaffold` passes `innerPadding` to its content, which handles most of it [V].
- Call `enableEdgeToEdge()` in `MainActivity.onCreate` for correct bar contrast [S, general knowledge; the API exists in androidx.activity].
- The matrix canvas and timeline need explicit `Modifier.windowInsetsPadding(...)` or `safeDrawingPadding()` [I].

**Predictive back**
- System back animations are on by default on Android 15+ for apps using the supported back APIs [V].
- At target 36 on Android 16, `onBackPressed` isn't called [V, platform doc].
- Rules for us:
  - Never override `onBackPressed`.
  - Use `BackHandler { }` (Compose) only for in-screen state, e.g. "back exits paint mode before leaving the editor".
  - Let the navigation library handle screen pops.
- Intercepting back at the root activity disables the back-to-home animation [V]. Keep `BackHandler` conditional (`enabled = inPaintMode`).

> **Learning aside: insets and back.** "Insets" are the areas the system covers (status bar, gesture bar, camera cutout). Since Android 15 your app draws under them, so you add padding where text or controls would be hidden. Back is no longer a callback you override. You *register* handlers, so the system can preview where back leads before the user lifts their finger.
> - [About window insets (Compose)](https://developer.android.com/develop/ui/compose/system/insets)
> - [Predictive back in Compose](https://developer.android.com/develop/ui/compose/system/predictive-back)
> - [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)

---

## 2. Architecture

### 2.1 Google's recommended architecture, in Go/Python terms

What Google says [V, developer.android.com/topic/architecture]:
- **At least two layers:** a **UI layer** ("displays application data") and a **data layer** ("contains the business logic … and exposes application data").
- **An optional domain layer:** "Use it only when needed — for example, to handle complexity or favor reusability."
- **Unidirectional data flow (UDF):** "state flows in only one direction … The events that modify the data flow in the opposite direction."
- **Single source of truth:** "only the SSOT can modify or mutate it."
- **Separation of concerns:** "The most important principle." Also: "Reduce dependencies on Android classes", and "Make each part of the app testable in isolation."

**How it maps for a Go developer**
- It's hexagonal / ports-and-adapters with Android names.
- The ViewModel plays the role of an HTTP handler that holds per-screen state and survives a "reconnect" (screen rotation).
- Repositories are your `store` package.
- The domain layer is your service package.
- UDF works like Redux/Elm, or a Go service with one goroutine owning the state and a channel of commands.

### 2.2 Mapping onto our modules

```
             ┌──────────────── :app (Android) ───────────────────────────┐
  UI layer   │ Compose screens ──events──▶ ViewModel ──dispatch(cmd)──┐   │
             │       ▲                        │ StateFlow<UiState>    │   │
             │       └──────────state─────────┘                       │   │
  data layer │ DocumentRepository (files) · SettingsRepository         │   │
             │ (DataStore) · KeyVault (Keystore) · TriggerStore         │   │
             │ AgentTurnService (FGS) · GlyphOutput impl ──▶ :glyph-sdk │   │
             └──────────────────────────────────────────────────────┼───┘
  domain     :core  (pure Kotlin/JVM): Document model, Command +    ◀┘
                     validate/apply (a reducer), History (JSON Patch),
                     Renderer/Baker, analysis metrics, ports (interfaces)
             :agent (pure Kotlin/JVM): agent loop, provider clients (Ktor),
                     tool registry → calls :core commands
  device     :glyph-sdk (Android lib): implements core's `GlyphOutput` port
                     using the Nothing .aar; absent in the sdkFree flavour
  harness    :evals (JVM CLI): drives :agent + :core headless (no Android)
```

**Key points**

1. **`:core` is the domain layer.** It's richer than Google's typical "use case" classes. Commands are values. `apply(doc, cmd) → Result<Doc, ValidationError>` is a pure function, and history is a log of patches. The UI and the agent both call it (D2). That *is* the UDF event path, and it's why D14/D20 keep it Android-free [I].
2. **The ViewModel stays thin.**
   - It holds an `EditorSession` from `:core` (the current doc, selection, and the undo stack).
   - It exposes `val uiState: StateFlow<EditorUiState>`.
   - It has one entry point, `fun onEvent(e: EditorEvent)`, which turns UI gestures into `Command`s.
   - Persistence is a side effect: debounce, then `DocumentRepository.save`.
3. **`:agent` is a domain-level service, not part of the UI.**
   - It depends on `:core` and on a few *ports* (interfaces) it declares itself: `HttpEngine`, `Clock`, `SecretProvider`, `Logger`.
   - `:app` supplies the Android implementations. `:evals` supplies JVM ones.
   - The agent emits `Flow<AgentEvent>`, which the agent-panel ViewModel collects.
4. **`:glyph-sdk` is an adapter.** `:core` declares `interface GlyphOutput { fun show(frame: Frame); fun clear() }`, and `:glyph-sdk` implements it (D18). The `sdkFree` flavour binds a no-op or "preview only" implementation [I].
5. **Module rule:** `:core` → nothing. `:agent` → `:core`. `:app` → `:core`, `:agent`, (`:glyph-sdk` in `full`). `:evals` → `:core`, `:agent`. Gradle enforces this, because a module can't import what it doesn't declare. Go gets the same guarantee from packages without import cycles.

**Rejected alternatives**
- **A separate `:domain` / `:data` / `:feature-*` module per layer** (the "Now in Android" style, ~30 modules). That's built for teams. For a solo app it adds Gradle overhead with no benefit. Keep `:app` as one module with packages per feature [I].
- **MVI frameworks** (Orbit, Mavericks) and Redux libraries. The command layer already gives you the reducer.
- **KMP:** the draft already rejects it [TR].

> **Learning aside: UI layer, ViewModel and StateFlow.** A `ViewModel` is an object Android keeps alive across screen rotations. It holds a screen's state and logic, so the composables can be thrown away and rebuilt. It exposes one immutable `UiState` through `StateFlow`, an observable value that always has a current value, a bit like a Go `atomic.Value` that also notifies watchers. The UI collects it with `collectAsStateWithLifecycle()` and sends events back by calling ViewModel functions.
> - [Guide to app architecture](https://developer.android.com/topic/architecture)
> - [UI layer and state holders](https://developer.android.com/topic/architecture/ui-layer)
> - [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)

> **Learning aside: domain layer.** Google's domain layer is where reusable business logic lives, separate from the UI and from storage. Ours is the `:core` module. Because it has no Android imports, it's tested with plain JUnit on your laptop in milliseconds, like a Go package with `go test`.
> - [Domain layer](https://developer.android.com/topic/architecture/domain-layer)
> - [Data layer](https://developer.android.com/topic/architecture/data-layer)

### 2.3 Navigation

**Evidence**
- **Navigation 3** 1.0 went stable on 19 Nov 2025 [V]. 1.2.0 is the current stable, released 23 Sep 2026 [V].
- It's "designed to work with Compose. … you have full control over your back stack, and you navigate … by adding and removing items from a list" [V].
- Google calls the older library "Nav2" [S, blog via search].
- Navigation Compose (Nav2) supports predictive back from 2.8.0 [V].

**Choice: Navigation 3.** The back stack is a `SnapshotStateList<Route>` you own: push a route to navigate, pop it to go back. It's easy to reason about and test, like a slice you append to and pop from. Routes are `@Serializable` data objects or classes.

**Screens (v1)**
- Library
- Editor (with the agent panel as a sheet or pane)
- Triggers
- Settings / Providers
- Import preview

That's few enough that a single `NavDisplay` covers it.

**Rejected alternatives**
- **Nav2 / Navigation Compose:** more Stack Overflow answers, but you'd learn a graph DSL and `NavController` indirection that Google is moving away from.
- **No library** (a `when(screen)` switch): this works, but you'd re-implement predictive-back animations and saved state. Nav3 is the cheap version of exactly that.
- **Fragment Navigation:** maintenance mode [V].

> **Learning aside: Navigation 3.** In Nav3, the app's history is just a list of route objects, and the `NavDisplay` composable shows the top one. Navigating is `backStack.add(Editor(docId))`, and going back is `removeLastOrNull()`. Predictive back and screen transitions come built in.
> - [Navigation 3 guide](https://developer.android.com/guide/navigation/navigation-3)
> - [Nav3 recipes (official samples)](https://github.com/android/nav3-recipes)
> - [Nav3 stable announcement](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html)

---

## 3. Dependency injection

**What Google says:** "Use the dependency injection pattern and the Hilt library in Android apps" [V]. Google also documents manual DI as a valid approach [V-link].

**Evidence specific to us**
- **Hilt has had AGP 9 friction.**
  - Hilt 2.59 was the first release compatible with AGP 9, and early 2.59 builds were broken.
  - `kotlin-kapt` doesn't work with AGP 9's built-in Kotlin.
  - One project reports Dagger's metadata reader rejecting Kotlin 2.4.
  - Latest: 2.60.1 (Jul 2026) [S: Dagger issues #5099/#4756, Maven Central, third-party trackers].
- **Hilt needs KSP**, which is versioned in lockstep with Kotlin. That's another moving part to keep aligned [V: AGP 9 notes, KSP auto-upgrade].
- **Our object graph is small.** In `:app`:
  - Five-ish singletons: `DocumentRepository`, `SettingsRepository`, `KeyVault`, `TriggerStore`, `AgentSessionHost`.
  - Five-ish ViewModels.
  - One `Service`.

**Choice: manual DI.**
- Write `class AppContainer(context: Context)` that builds the singletons lazily. Create it in `GlyphWizardApp : Application`.
- ViewModels get their dependencies through the `viewModelFactory { initializer { EditorViewModel(container.docs, ...) } }` helper [V-link].
- **`:core` and `:agent` stay DI-framework-free.** Use constructor parameters and interfaces only, exactly as you'd wire structs in Go's `main()`.
- That keeps `:evals` wiring trivial, and an external-agent host could reuse them later (D14).

**Go analogy:** Hilt is roughly Google's `wire`, a compile-time code generator. Manual DI is what most Go services do anyway: build things in `main` and pass them down.

**When to revisit:** Hilt pays off with many screens, WorkManager workers, many services, or a team. If the container grows past about 20–30 bindings, or wiring the Service and ViewModels starts to hurt, migrate to Hilt [I]. The migration is mechanical because constructors already take their dependencies.

**Rejected alternatives**
- **Hilt now:** codegen, KSP lockstep, recent AGP 9 breakage, and annotations to learn before you can draw a pixel.
- **Koin:** a runtime service locator with a nice DSL and no codegen. Missing bindings fail **at runtime**, which is the failure mode we're trying to avoid. It's also one more library to learn when a 40-line class does the job [I].

> **Learning aside: dependency injection on Android.** Android creates your `Activity`, `Service` and `ViewModel` objects for you, so you can't call their constructors with arguments. That's why DI frameworks exist on Android. With manual DI, you put a container on the `Application` object and hand dependencies to ViewModels through a small factory. The habits you already have from Go (constructor injection, interfaces at boundaries) carry over directly.
> - [Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)
> - [ViewModel factories / CreationExtras](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
> - [Hilt (for later)](https://developer.android.com/training/dependency-injection/hilt-android)

---

## 4. Storage

### 4.1 Documents: files vs Room

**Evidence**
- App-specific internal storage (`filesDir`):
  - needs no permissions [V]
  - other apps can't read it [V]
  - is encrypted on Android 10+ [V]
  - is removed on uninstall [V]
- DataStore docs: "If you need … large or complex datasets, partial updates, or referential integrity, consider using Room" [V].

**Choice: one file per document.**
- Path: `filesDir/docs/<docId>.glyphwiz`, as JSON via **kotlinx.serialization**.
- **Why:** D11 already makes `.glyphwiz` JSON the interchange format, and D9 makes the document the source of truth. So the on-disk format *is* the export format, with no second schema to migrate.
- Documents are small: 137–489 LEDs × frames, typically KB–low MB [I].
- **Library list:** scan `docs/` and read a small header, or keep `docs/index.json` as a rebuildable cache. With fewer than a few hundred documents, a scan is fine [I].
- **Atomic saves:** write to a temp file, then rename. `androidx.core.util.AtomicFile` does this. It works like Go's "write tmp + `os.Rename`".
- **Schema versioning:** add a top-level `"format": 1` field. Migrations are plain Kotlin functions in `:core` (`migrate(json) → json`), tested with golden fixtures.
- **kotlinx.serialization lives in `:core`.** It's pure Kotlin (a compiler plugin plus a runtime library), so `:core` stays Android-free, and the agent's tool schemas use the same `@Serializable` classes (draft §3).

**Rejected: Room (SQLite ORM).**
- It needs KSP.
- It needs a second schema that mirrors the JSON.
- It adds DB migrations on top of format migrations.
- It solves problems we don't have: queries, partial updates, relations.
- Revisit only if we need search or tag queries across hundreds of documents.

### 4.2 History sidecar (D9)

- **Path:** `filesDir/docs/<docId>.history.gz`. It's a gzip'd JSON-lines log of `{author, label, group, forward, inverse}` patches, capped at ~200 steps / 256 KB (D9).
- **Writing:** append-then-compact, or rewrite the whole capped file on each save. At 256 KB, a full rewrite is fine and simpler [I].
- **Deletion:** delete it with the document.
- **Format:** it's an implementation detail, not interchange. Don't include it in export (D11).
- **Backup:** fine to include. A 256 KB cap × documents stays well under the 25 MB Auto Backup quota for a typical library [V quota; I estimate].

### 4.3 Settings, triggers, secrets

| Data | Store | Why |
|---|---|---|
| UI prefs (theme, tick default, last provider) | **Preferences DataStore** | Key-value, async, Flow-based. Replaces `SharedPreferences` [V]. Current version 1.2.1 [V] |
| Provider configs (endpoint, model, tier; not keys) | **Typed DataStore with a kotlinx.serialization JSON `Serializer`**, documented officially [V] | One `@Serializable data class` holds them. Atomic writes come for free |
| Triggers/rules (D21) | **Typed JSON DataStore** (`triggers.json`) | Small list, read whole, written whole. The trigger receiver (`NotificationListenerService`) reads it as a Flow. If it ever runs in a separate process, use `MultiProcessDataStoreFactory` [V] |
| API keys | **AES-GCM ciphertext in `noBackupFilesDir/keys/`, key in Android Keystore** (draft §9; Jetpack security-crypto is deprecated [PM]) | `noBackupFilesDir` and `cache` are excluded from Auto Backup [V: "fully enabled for all content except for `no-backup` and `cache` directories"]. Keystore keys don't survive restore on another device anyway [I] |

**Backup rules**
- Fill in the scaffold's `data_extraction_rules.xml` (and `backup_rules.xml`, which is ignored on API 31+ and so irrelevant at minSdk 35 [V: page says it's for ≤ API 30]).
- Under `cloud-backup` and `device-transfer`:
  - Include the domains `file` (docs) and DataStore files.
  - Explicitly `<exclude domain="file" path="keys/"/>` as belt-and-braces, even though the keys live in no-backup.
- Set `disableIfNoEncryptionCapabilities="true"` on cloud backup [V].
- **[U]** The exact domain name for `noBackupFilesDir` files in rules: they're excluded automatically, so no rule is needed.
- The scaffold manifest points `fullBackupContent` at a sample file. You can drop it, since minSdk is 35 [I].

### 4.4 Import/export, share sheet, "Open with"

**Export / import with the Storage Access Framework (SAF)**
- Export: `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))`.
- Import: `OpenDocument(arrayOf("application/json", "application/octet-stream", "*/*"))`.
- **No storage permission** is needed [V-link]. The user picks a location in the system picker (Downloads, Drive, and so on).
- **Go analogy:** you receive an `io.Reader` / `io.Writer` (a `content://` URI), never a path.

**Share**
- Write a temp export to `cacheDir/share/` and expose it through a `FileProvider`.
- Fire `Intent.ACTION_SEND` with `EXTRA_STREAM` and `FLAG_GRANT_READ_URI_PERMISSION` [V-link].

**Open with GlyphWizard** (`.glyphwiz`, or a Glyph Museum JSON from a file manager or chat app)
- Add an `<intent-filter>` for `ACTION_VIEW` (and `ACTION_SEND` for "Share to GlyphWizard"), `scheme="content"`, with mimeTypes `application/json` and `application/octet-stream`.
- **Matching on a custom file extension is unreliable.** `pathPattern` matching works poorly with `content://` URIs, and most apps send unknown extensions as `application/octet-stream` [S, long-standing community knowledge; U for current Android 16 Files app behaviour].
- So: accept broadly, **sniff the content** (does it parse as `.glyphwiz` or GM JSON?), then show an import preview screen. Don't trust the MIME type or the extension.
- Treat imported files as untrusted input:
  - enforce a size cap
  - validate through `:core`'s validator
  - never `eval` anything
- That's the same posture as handling an HTTP upload.

> **Learning aside: Android storage.** Each app gets a private folder (`filesDir`) that needs no permissions and other apps can't read. Use it like a server's data dir. To touch the user's files, you ask the system picker (SAF) for one document and get back a `content://` URI to stream from. For sharing, you lend a URI to another app through `FileProvider`. Small settings go in DataStore, a transactional key-value or JSON store with a `Flow` API.
> - [App-specific storage](https://developer.android.com/training/data-storage/app-specific)
> - [Storage Access Framework: documents and files](https://developer.android.com/training/data-storage/shared/documents-files)
> - [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)

> **Learning aside: kotlinx.serialization.** Annotate a data class with `@Serializable`, and a compiler plugin generates the encoder at build time. There's no reflection, which makes it closer to Go struct tags with generated code than to Python's `json.dumps`. `Json { ignoreUnknownKeys = true }` gives forward compatibility. Sealed classes give you tagged unions (`"type": "ring_pulse@1"`).
> - [Serialization guide](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md)
> - [Kotlin docs: serialization](https://kotlinlang.org/docs/serialization.html)

> **Learning aside: Auto Backup.** Android backs up app files to the user's Google account (up to 25 MB) and copies them during phone migration, unless you opt files out. `noBackupFilesDir` and `cacheDir` are never backed up, which is why encrypted API keys go there.
> - [Auto Backup](https://developer.android.com/identity/data/autobackup)
> - [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

---

## 5. Concurrency

### 5.1 Coroutines vs goroutines

| Go / Python | Kotlin |
|---|---|
| `go f()` | `scope.launch { f() }`. It must live in a **scope**, so there are no orphan goroutines |
| `context.Context` cancel | `Job.cancel()`. Cancellation propagates down the scope tree automatically |
| `errgroup.Group` | `coroutineScope { launch{}; launch{} }`. It waits for all children, and one failure cancels the siblings |
| channel | `Channel<T>` exists, but you mostly use **`Flow<T>`**: a cold, lazy stream like a Python async generator |
| `atomic.Value` + broadcast | **`StateFlow<T>`**: always has a value, conflates, multicasts |
| Python `asyncio` `async def` / `await` | `suspend fun`. You call it directly, with no `await` keyword |
| GOMAXPROCS threads | **Dispatchers**: `Main` (the UI thread, which must never block), `Default` (CPU-bound, ~#cores), `IO` (blocking I/O, larger pool) |

**Structured concurrency** means every coroutine belongs to a scope with a lifetime:
- `viewModelScope` dies when the screen is gone for good.
- `lifecycleScope` dies with the Activity.
- An app-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` lives in `AppContainer` for work that outlives screens: the agent turn, which is hosted by the FGS (platform doc §R3).

Google's guidance [V-link best practices]:
- Inject dispatchers. Don't hardcode them.
- Make suspend functions main-safe. In Google's words, "types are responsible for their concurrency policy, and should be main-safe" [V].
- Don't use `GlobalScope`.

### 5.2 Where the render/bake loop runs

- **`:core`'s render and bake functions are plain, pure, non-suspending functions:** `bake(doc, profile): FrameSeq`.
  - They're deterministic and easy to golden-test.
  - `:core` doesn't even need coroutines for this [I].
  - `:core` may still depend on `kotlinx-coroutines-core` (pure JVM) if `EditorSession` exposes Flows. That's a judgement call; keep it minimal.
- **Live editor preview:** the ViewModel maps `docState` with `mapLatest { withContext(Dispatchers.Default) { bake(it) } }`.
  - A new edit cancels an in-flight stale bake. That's the "latest wins" behaviour you'd hand-roll in Go with a context per request.
  - Cancellation is *cooperative*, so a long bake loop should call `ensureActive()` every N frames.
- **On-screen playback:** drive the frame index from Compose's frame clock (`LaunchedEffect` + `withFrameNanos`), so it's vsync-aligned.
- **On-device LED playback (D10 tick 16–100 ms):** run a coroutine on `Default` that computes `nextDeadline += tick` and `delay(nextDeadline - now)` using a monotonic clock, then calls `GlyphOutput.show(frame)`.
  - The platform doc notes that at target 36 `scheduleAtFixedRate` replays at most one missed run, so use coroutines [V].
  - Real jitter at 20 ms is a hardware-spike question [U].
- **Agent loop (`:agent`):** suspend functions over Ktor, in the app-scoped scope tied to `AgentTurnService`. It emits `Flow<AgentEvent>`. Each tool call dispatches `:core` commands through the same `EditorSession`, serialised on one owner (a `Mutex`, or a single-consumer `Channel`), so user and agent edits can't interleave mid-command.
  - This is the "one goroutine owns the state" pattern [I].

> **Learning aside: coroutines and Flow.** A coroutine is a lightweight task like a goroutine, but it's always launched inside a *scope* that cancels its children when it ends. That's structured concurrency, roughly `errgroup` plus `context` built into the language. `Flow` is an async stream (a Python async generator). `StateFlow` is a Flow that always holds the latest value, which is perfect for UI state.
> - [Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
> - [Coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
> - [Kotlin flows on Android](https://developer.android.com/kotlin/flow)

---

## 6. Build

### 6.1 What we have (scaffold audit)

| Item | Scaffold | Needed | Note |
|---|---|---|---|
| Gradle wrapper | 9.6.0 (SHA pinned) | 9.6.0 | AGP 9.4 needs Gradle ≥ 9.6.0 [V] ✓ |
| AGP | 9.4.1 | 9.4.x | The release page shows 9.4.0 (Sep 2026) as latest [V]. 9.4.1 is presumably a patch [U]. Note the open android-junit5 issue on 9.4.1 [S], which doesn't affect us (§7) |
| Kotlin | none (no plugin, no sources) | built-in Kotlin (AGP 9) | AGP 9 compiles Kotlin with **no `kotlin-android` plugin** [V]. It bundles KGP ≥ 2.2.10 [V] |
| Compose | none | Compose BOM `2026.09.00` (ui 1.12.1, material3 1.4.0) [V, inferred row alignment] + `org.jetbrains.kotlin.plugin.compose` | Compose 1.12 *requires* compileSdk 37 and AGP 9 [V]. The Compose compiler page's example uses Kotlin **2.4.10** [V] |
| UI deps | appcompat 1.6.1, material 1.10.0 (Views) | remove both. Add `activity-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `navigation3-runtime/ui`, `core-ktx` (bump from 1.10.1) | Old versions from a stale template catalog [I] |
| namespace / appId | `com.example.glyphwizard` | e.g. `io.github.<user>.glyphwizard` | **The applicationId is permanent once users install it.** Obtainium and Android updates key on it. Change it *before* the first release |
| minSdk | 24 | **35** | Platform doc |
| targetSdk | **37** | **36** | Target 37 forces the runtime `ACCESS_LOCAL_NETWORK` permission, which breaks LAN Ollama without a prompt [V, platform doc] |
| compileSdk | `release(37)` | 37 ✓ | |
| Java | `VERSION_11` | **17** via toolchain | AGP needs JDK 17 [V]. JUnit 6 needs 17 [S] |
| R8 | `optimization { enable = true; packageScope = … }`, `src/main/keepRules/` | keep | New AGP 9.x R8 DSL [U: not verified this session]. kotlinx.serialization needs keep rules only for reflective lookups. Prefer generated `serializer()` calls [I] |
| gradle.properties | configuration cache on | add `org.gradle.parallel=true`, `org.gradle.caching=true` | Safe for a decoupled multi-module build [V-link in the file's own comment] |
| foojay resolver | present | keep | Lets Gradle auto-download JDK 17 for toolchains |

**Verdict: keep and hand-migrate. Don't regenerate.**
- The repo has no commits, so either is cheap.
- The wrapper, AGP 9.4 and the catalog are already modern. Only the *template content* (Views deps, minSdk 24) is wrong.
- **A practical trick:** in a *scratch* directory, generate a new project with Android Studio's **"Empty Activity"** template (that's the Compose one; "Empty Views Activity" is the Views one [S]). Copy its `MainActivity.kt`, `ui/theme/*` and the Compose catalog entries across, then delete the scratch project.
- You see what the official template wires up without losing the current files.

### 6.2 Multi-module setup

- Five modules, in `settings.gradle.kts`: `include(":app", ":core", ":agent", ":glyph-sdk", ":evals")`.
- **JVM modules** (`:core`, `:agent`, `:evals`) apply `org.jetbrains.kotlin.jvm`, plus `org.jetbrains.kotlin.plugin.serialization` where needed, plus `application` for `:evals`. Built-in Kotlin covers only *Android* modules [V: it replaces `kotlin-android`; KMP still needs its own plugins].
- **[U] Version alignment:** declare one `kotlin` version in the catalog and use it for `kotlin-jvm`, `compose`, and `serialization`. AGP 9 auto-upgrades KGP to its minimum [V], so pin a Kotlin version ≥ AGP's bundled one, e.g. 2.4.10 as in the Compose docs [V]. Confirm the root build doesn't end up with two KGP versions on the classpath. That's the most likely day-one Gradle error.
- **`:glyph-sdk`** is an Android library (`com.android.library`). It contains a `fetchGlyphSdk` task (raw GitHub URL + pinned SHA-256, per the platform doc) and `implementation(files("libs/glyph-matrix-sdk-2.0.aar"))` only in the `full` flavour.
- **`:app` flavours:** `productFlavors { create("sdkFree"); create("full") }`. `fullImplementation(project(":glyph-sdk"))`.
- **JVM toolchain:** `kotlin { jvmToolchain(17) }` in every module, or `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`. This avoids "works on my JDK" problems, like pinning a Go version in `go.mod`.

**Convention plugins: not yet.**
- They exist to dedupe build logic across many modules ("Now in Android" has ~30).
- We'd have two Android modules and three JVM modules, sharing about 6 lines each.
- Add a `build-logic/` included build when copy-paste exceeds ~20 lines or a third Android module appears [I].

**KSP: not needed** with manual DI, no Room, and no Moshi. That removes a whole class of AGP/Kotlin/KSP version-lockstep problems [V: AGP auto-upgrades KSP to match KGP].

> **Learning aside: Gradle for Go/Python devs.** Gradle is the build tool, closer to Bazel-lite than to `go build`. `settings.gradle.kts` lists modules, like a `go.work`. Each module's `build.gradle.kts` declares plugins and dependencies, like a `go.mod` plus a Makefile. `gradle/libs.versions.toml` is the single version catalog, like a Python lock of chosen versions. Always run `./gradlew` (the pinned wrapper), never a system `gradle`.
> - [Configure your build (Android)](https://developer.android.com/build)
> - [Migrate to version catalogs](https://developer.android.com/build/migrate-to-catalogs)
> - [Gradle multi-project builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)

> **Learning aside: AGP 9.** The Android Gradle Plugin (AGP) is the Gradle plugin that knows how to build APKs. Version 9 compiles Kotlin itself ("built-in Kotlin"), so tutorials that apply `org.jetbrains.kotlin.android` or use `kotlinOptions {}` are out of date. It also only supports the new DSL (no `applicationVariants`). Expect older blog posts to be wrong. Prefer developer.android.com.
> - [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
> - [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
> - [Compose compiler Gradle plugin](https://developer.android.com/develop/ui/compose/compiler)

---

## 7. Testing

| Layer | Framework | Why |
|---|---|---|
| `:core`, `:agent`, `:evals` (JVM) | **JUnit Jupiter** (5.x, or JUnit 6 on JDK 17) + **`kotlin.test`** assertions (`kotlin-test-junit5`), `useJUnitPlatform()` | Native on plain JVM Gradle, with no Android plugin involved. Parameterised tests suit command validation tables. `kotlin.test` reads like Go's `testing` + testify |
| `:core` renderer | **Golden files**: a text grid (the same format as the agent's grid view) under `src/test/resources/golden/`, plus a tiny helper (`assertGolden(name, actual)`, regenerated with `-PupdateGoldens`) | Pure JVM, diffable in git review, no image tooling. Optional PNG goldens rendered with `java.awt.image` for the visual report, also pure JVM [I] |
| Flows (`EditorSession`, `AgentEvent`) | **Turbine** + `kotlinx-coroutines-test` (`runTest`, virtual time) | Turbine turns a Flow into "await next item" assertions. Virtual time makes the 20 ms tick and timeouts instant |
| `:app` unit (ViewModels, repositories) | **JUnit 4** + Robolectric where Android classes are needed | androidx.test, Robolectric and the Compose test rule are JUnit 4 based. JUnit 5 on Android needs the third-party mannodermaus plugin, which has an open AGP 9.4.1 issue [S]. Not worth it |
| Compose UI | `createComposeRule()` on **Robolectric** (JVM, fast, CI-friendly), plus a few device tests on the 4a Pro | [V-link Compose testing] |
| Screenshots | **Roborazzi** (Robolectric-based, JVM; generates tests from `@Preview` [V]) **later, not v1-day-one** | Alternatives: **Paparazzi** (Cash App, layoutlib; fast but sometimes lags new Compose/AGP [U]), and Google's **Compose Preview Screenshot Testing** (AGP-integrated; maturity [U]). Pixel-level Compose screenshots are lower value than the `:core` goldens, because the LED render is tested in pure JVM |
| Test doubles | **Fakes** (`FakeDocumentRepository`, `FakeLlmProvider` replaying recorded SSE) | Google: "Fakes don't require a mocking framework and are lightweight. They are **preferred**" [V]. MockK only where a fake is impractical; probably never in `:core` |

Notes
- Recorded provider transcripts as fakes in `:agent` double as `:evals` fixtures.
- Property-based tests (kotest-property, or hand-rolled with a seeded `Random`) suit `apply(cmd)` ∘ `inverse` = identity for the D9 history. It's optional, but a very high-value invariant [I].

> **Learning aside: testing pyramid on Android.** "Local tests" run on your laptop's JVM, are fast, and should be most of the suite. "Instrumented tests" run on a device or emulator and are slow. Because `:core` and `:agent` are pure Kotlin, almost all logic is testable like a Go package. Robolectric fakes enough of Android to run Compose UI tests on the JVM too.
> - [Fundamentals of testing Android apps](https://developer.android.com/training/testing/fundamentals)
> - [Test doubles (fakes vs mocks)](https://developer.android.com/training/testing/fundamentals/test-doubles)
> - [Testing Kotlin coroutines](https://developer.android.com/kotlin/coroutines/test)

> **Learning aside: Compose UI tests and screenshots.** A Compose test finds nodes by text or test tag (`onNodeWithText("Play").performClick()`) and asserts on the semantics tree, similar to querying a DOM in Playwright. Screenshot tests (Roborazzi) render a composable to a PNG and diff it against a committed golden.
> - [Testing your Compose layout](https://developer.android.com/develop/ui/compose/testing)
> - [Roborazzi](https://github.com/takahirom/roborazzi)
> - [Turbine](https://github.com/cashapp/turbine)

---

## 8. CI/CD

### 8.1 GitHub Actions

**PR workflow** (`ci.yml`)
1. `actions/checkout`
2. `actions/setup-java` (Temurin 17)
3. `gradle/actions/setup-gradle`, which caches and validates the wrapper [S]
4. `./gradlew check lintSdkFreeDebug assembleSdkFreeDebug`

**CI never fetches the Nothing `.aar`** unless and until Nothing permits redistribution (D18), so CI builds `sdkFree` only.

**Release workflow** (`release.yml`, on tag `v*`)
- Decode the keystore from a base64 secret into `$RUNNER_TEMP`.
- `signingConfigs.release` reads the path and passwords from env vars.
- Run `./gradlew assembleSdkFreeRelease`.
- Run `gh release create $TAG app-sdkFree-release.apk` (or `softprops/action-gh-release`).
- Derive `versionName` from the tag and `versionCode` from it too (e.g. `major*10000+minor*100+patch`), so they always increase.

**Obtainium compatibility** [S/I]
- Obtainium reads GitHub Releases and downloads an APK asset. It can filter assets by regex and pick up the version from the tag [S: README/wiki; details not verified this session].
- So:
  - attach **plain `.apk` files** with stable names (`glyphwizard-sdkFree-v1.2.0.apk`)
  - use semver tags
  - mark betas as GitHub *pre-releases*
- **Signing continuity is the real constraint.** Android refuses an update signed with a different key [general platform fact].
  - Back up the keystore offline in two places.
  - Losing it means every user must uninstall, which deletes their documents unless they exported them.
  - **Developer verification (2027 global, platform doc) registers this key's SHA-256** [V, platform doc].

**Reproducible builds:** **defer.** They're only needed for F-Droid to ship *your* signature [V, platform doc]. Cheap habits now: a pinned JDK toolchain, a pinned wrapper SHA (already done), no timestamps in `BuildConfig`.

**Dependency updates**
- **Dependabot:** built into GitHub. It understands Gradle version catalogs [S]. Use weekly schedules with `groups:` so the Compose BOM, AGP and Kotlin each arrive as one PR.
- **Renovate** is more powerful (shared presets, auto-merge rules), but it's another config dialect. Not needed solo [I].
- **AGP / Kotlin / Compose-compiler bumps should be manual and deliberate.** They're coupled (§6).

**Static analysis**
- **Android Lint:** built in, catches Android-specific bugs (missing permissions, API levels, accessibility). Run it in CI. Create `lint-baseline.xml` only if needed.
- **ktlint** via Spotless or `org.jlleitschuh.gradle.ktlint`, with `.editorconfig`. Formatting only; it's the `gofmt` equivalent.
- **detekt: skip for now.** The 2.0 line is still alpha (2.0.0-alpha.6, Aug 2026, built against Kotlin 2.4.10). Users report pinning Kotlin to match it [S]. Stable 1.23.8 (Feb 2025) predates Kotlin 2.4 [S]. Revisit when 2.0 is stable.

> **Learning aside: signing and releasing an APK.** Every APK is signed, and Android only installs an update if it's signed with the same key as the installed app. Your release keystore is therefore like a root CA private key: never commit it, back it up, and inject it into CI as a secret. A GitHub Release with the APK attached is all Obtainium needs to offer updates.
> - [Sign your app](https://developer.android.com/studio/publish/app-signing)
> - [gradle/actions (setup-gradle)](https://github.com/gradle/actions)
> - [Obtainium](https://github.com/ImranR98/Obtainium)

> **Learning aside: lint and formatting.** Android Lint is a static analyser that understands manifests, resources and API levels, like `go vet` for Android. ktlint is the formatter and style checker, like `gofmt` / `ruff format`.
> - [Improve your code with lint checks](https://developer.android.com/studio/write/lint)
> - [ktlint](https://pinterest.github.io/ktlint/)
> - [Dependabot version updates](https://docs.github.com/en/code-security/dependabot/dependabot-version-updates)

---

## 9. Licence (D18: "non-commercial but open")

*Not legal advice. These are facts plus inference to inform the user's choice.*

### 9.1 The core conflict

- The Open Source Definition forbids discriminating "against fields of endeavor", and commercial use is a field of endeavour [OSD #6; V-link].
- PolyForm Noncommercial is not OSI-approved [S, licence databases].
- **A licence can't be both "non-commercial" and "open source" in the OSI sense** [I from the above].
- F-Droid requires FLOSS app code. It accepts non-commercial licences only for **non-functional assets** (artwork, fonts) [V].

### 9.2 Options

| Licence | OSI / FSF | F-Droid (sdkFree flavour) | Blocks commercial resale? | Combining with the closed SDK `.aar` |
|---|---|---|---|---|
| **GPL-3.0** | Yes / yes | Yes | No, but any distributed fork must stay GPL and ship source, which kills closed commercial forks | **Needs an additional permission.** The SDK is bundled in the APK, so the system-library exception doesn't apply (the FAQ says shipping the library with the program loses eligibility) [V]. The FAQ gives a GPLv3 §7 "additional permission" template. Only the copyright holder can grant it, and contributions must come under the same terms [V] |
| **AGPL-3.0** | Yes / yes | Yes | Same as GPL | Same as GPL. The network clause is irrelevant for a local app. **GlyphMaps uses AGPL-3.0 plus a paid commercial licence, and reserves brand assets in a NOTICE** [V] |
| **MPL-2.0** | Yes / yes | Yes | No | File-level copyleft. Designed to combine with proprietary code, so no exception is needed [I, per MPL FAQ] |
| **Apache-2.0 / MIT** | Yes / yes | Yes | No. Anyone can sell builds | Fine. Review r1 flagged the "sell builds embedding a no-commercial SDK" risk, but that risk lands on the seller under Nothing's EULA, not on us [I] |
| **PolyForm Noncommercial 1.0** | **No** | **No** [I from policy] | **Yes** (legally) | Fine. "Source-available", not open source |
| **CC BY-NC(-SA)** | No | No | Yes | Creative Commons itself advises against using CC licences for software [V-link CC FAQ] |

**Precedent in this niche** [V/S]
- **GlyphMatrixEditor** (pauwma): GPL-3.0 [V].
- **GlyphMaps** (capad-xyz): AGPL-3.0 + commercial licence + "all rights reserved" brand assets [V].
- **glyph-matrix-lab**, **hi-glyph**: GPL-3.0 [S].
- **GlyphHub**: MIT [S].
- Nothing's example project: MIT [S].
- None of them commits the `.aar` [S].

### 9.3 Recommendation

**GPL-3.0-or-later, plus a §7 additional permission, plus a trademark/brand reservation.**
- **The additional permission** allows "linking and distributing with the Nothing Glyph Matrix SDK (any version) under its own licence terms".
- **The brand reservation:** the GlyphWizard name and icon are "all rights reserved" in a `NOTICE`, the GlyphMaps pattern [V]. That stops look-alike commercial clones on name and branding.

**Why this delivers "non-commercial in practice"** [I]
1. The only *useful* build, the one that drives LEDs, contains the SDK. Nothing's EULA §2.2 already bans commercial use of the SDK without Nothing's permission (verified in review r1). So nobody can lawfully sell the full flavour anyway, whatever *our* licence says.
2. GPL forces any fork to publish its source under GPL, which removes the closed-source "repackage and sell" incentive.
3. It stays OSI/FSF open source, F-Droid-eligible (sdkFree), and matches the niche's main precedent.

**Downsides**
- GPL doesn't *legally* stop someone from charging for the sdkFree build (they'd have to ship source).
- Contributors must accept the exception, which is automatic if you state "contributions are licensed under the project licence including the SDK exception" in CONTRIBUTING (inbound = outbound).
- **[U]** Have someone check the exact §7 wording, e.g. adapt the FSF template from the GPL FAQ verbatim.

**If the user insists on a *legal* non-commercial bar:** use PolyForm Noncommercial 1.0.0. Then:
- describe the project as "source-available", not "open source"
- accept F-Droid ineligibility
- expect fewer contributors

That's a values call → **Q1**.

**Separate licences for content:**
- Exemplar documents / KB text: CC BY 4.0 or CC BY-SA 4.0.
- Fonts: OFL (context research K9).
- List each in a `LICENSES/` folder or a REUSE-style `.reuse/dep5` [I].

> **Learning aside: open-source licensing.** "Open source" has a formal definition (OSI's OSD), and one of its rules is "no discrimination against fields of endeavour". That's why "non-commercial" licences aren't open source. Copyleft licences (GPL) make forks stay open. Permissive ones (MIT/Apache) don't. Linking GPL code with a closed library requires an explicit exception from the copyright holder, which you are.
> - [Open Source Definition](https://opensource.org/osd)
> - [GNU GPL FAQ: GPL-incompatible libraries](https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs)
> - [choosealicense.com](https://choosealicense.com/)

---

## 10. Repo hygiene

There are no commits yet (`git log`: "does not have any commits yet"), so fix this **before the first commit**.

**`.gitignore` changes**
- Use `build/` instead of only `/build`. Every module has one.
- Add:
  - `.kotlin/` (Kotlin 2.x project-local caches [S])
  - `*.jks`, `*.keystore`, `keystore.properties`
  - `glyph-sdk/libs/*.aar` (D18)
  - `/captures`
  - `*.hprof`
  - `.cxx`
- **`.idea/`:** ignore it all except shareable settings: `!.idea/codeStyles/`, plus optionally `!.idea/runConfigurations/`. JetBrains' guidance shares project-level files and excludes `workspace.xml`, but for a solo repo the simplest robust rule is "ignore all but code style" [S, JetBrains KB].
  - The current `.idea/` contains `workspace.xml`, `deploymentTargetSelector.xml`, `caches`, and so on, which shouldn't be committed.
- Keep `local.properties` ignored (it holds the SDK path).
- Commit `gradle/wrapper/gradle-wrapper.jar` and `gradlew`.

**Files to add**
- `LICENSE` and `NOTICE` (brand reservation)
- `README.md`: what it is, supported devices, the sdkFree vs full explanation, how to build, Obtainium link
- `CONTRIBUTING.md`: inbound = outbound licence incl. the SDK exception; DCO sign-off optional (no CLA); how to run tests
- `.editorconfig` (ktlint)
- `SECURITY.md` (one paragraph: API keys, how to report)
- `.github/dependabot.yml` and the workflows

**Commits:** **Conventional Commits** (`feat(core): …`, `fix(app): …`, `docs:`, `build:`). Use module names as scopes. It's cheap, makes release notes easy to generate later, and needs no tooling on day one.

**Branching:** trunk-based, with short feature branches and PRs to yourself so CI runs (already the process per D22).

> **Learning aside: what to commit in an Android Studio project.** Commit source, Gradle files, the wrapper and the version catalog. Never commit `build/`, `local.properties`, keystores, or per-user IDE state (`.idea/workspace.xml`). GitHub's Android `.gitignore` template is a good baseline.
> - [GitHub Android.gitignore](https://github.com/github/gitignore/blob/main/Android.gitignore)
> - [JetBrains: which .idea files to share](https://intellij-support.jetbrains.com/hc/en-us/articles/206544839)
> - [Conventional Commits](https://www.conventionalcommits.org/)

---

## 11. Recommended stack table

| Concern | Choice | Why | Alternative rejected | Learning link |
|---|---|---|---|---|
| UI | Jetpack Compose (BOM 2026.09.00) | Views are in maintenance mode [V]. Custom-drawn matrix is natural in Compose | Views/XML | [Compose course](https://developer.android.com/courses/android-basics-compose/course) |
| Design | M3 components + custom mono theme + OFL dot font | Free a11y, insets and predictive back. Nothing look via tokens | Full custom design system; stock dynamic colour | [M3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) |
| Architecture | UI layer (ViewModel + `StateFlow<UiState>`) → `:core` domain → repositories | Google's guide [V]. Command layer = reducer = UDF | Per-layer modules; MVI libraries | [App architecture](https://developer.android.com/topic/architecture) |
| Navigation | Navigation 3 (1.2.0) | Stable [V]; back stack is a list you own | Nav2 (Navigation Compose); hand-rolled | [Nav3 guide](https://developer.android.com/guide/navigation/navigation-3) |
| DI | Manual `AppContainer`; frameworks-free `:core`/`:agent` | Small graph; no KSP; avoids Hilt/AGP 9 churn [S] | Hilt (later if it grows), Koin | [Manual DI](https://developer.android.com/training/dependency-injection/manual) |
| Documents | JSON files in `filesDir` via kotlinx.serialization; `AtomicFile` | Disk format = export format (D11); pure Kotlin | Room | [App-specific storage](https://developer.android.com/training/data-storage/app-specific) |
| History | `<id>.history.gz` sidecar next to the doc | D9; capped, simple rewrite | Room table | — |
| Settings / triggers | Preferences DataStore / typed JSON DataStore | Official, Flow-based, atomic [V] | SharedPreferences; Room | [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| Secrets | Keystore AES-GCM, ciphertext in `noBackupFilesDir` | Never backed up [V] | security-crypto (deprecated) | [Keystore](https://developer.android.com/privacy-and-security/keystore) |
| Import/export | SAF `CreateDocument`/`OpenDocument`; `FileProvider` share; broad intent filter + content sniffing | No permissions; extension matching unreliable [S] | Storage permissions | [SAF](https://developer.android.com/training/data-storage/shared/documents-files) |
| Concurrency | Coroutines/Flow; bake on `Default` with `mapLatest`; deadline-loop ticker | Structured cancellation; main-safety [V] | Executors / `scheduleAtFixedRate` | [Coroutines on Android](https://developer.android.com/kotlin/coroutines) |
| HTTP (agent) | Ktor client (from the draft) | Pure-Kotlin, so it runs in `:agent` and `:evals` | Retrofit (Android-centric) | [Ktor client](https://ktor.io/docs/client-create-new-application.html) |
| Build | AGP 9.4 built-in Kotlin, Gradle 9.6, catalog, JDK 17 toolchain, no KSP, no convention plugins yet | Modern; minimal moving parts | Regenerating; build-logic now | [AGP 9 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| JVM tests | JUnit Jupiter + kotlin.test + text goldens + Turbine | Fast, pure JVM, diffable | Kotest (fine, but more DSL) | [kotlin.test](https://kotlinlang.org/api/core/kotlin-test/) |
| App tests | JUnit 4 + Robolectric + Compose rule; Roborazzi later | Ecosystem default; JUnit 5 on Android is third-party [S] | android-junit5; Paparazzi | [Compose testing](https://developer.android.com/develop/ui/compose/testing) |
| Doubles | Fakes | Google: "preferred" [V] | MockK by default | [Test doubles](https://developer.android.com/training/testing/fundamentals/test-doubles) |
| CI | GitHub Actions + setup-gradle; tag → signed sdkFree APK release | Matches GitHub + Obtainium | Fastlane, Play pipeline | [setup-gradle](https://github.com/gradle/actions) |
| Quality | Android Lint + ktlint; Dependabot (grouped) | Built-in, stable | detekt (2.0 alpha) [S]; Renovate | [Lint](https://developer.android.com/studio/write/lint) |
| Licence | GPL-3.0-or-later + §7 SDK permission + NOTICE brand reservation (**user decides, Q1**) | OSI/F-Droid-compatible; SDK EULA already bars commercial use | PolyForm NC / CC BY-NC (not OSS); MIT/Apache | [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs) |

---

## 12. Proposed module and package layout

```
GlyphWizard/
├─ settings.gradle.kts            include(":app", ":core", ":agent", ":glyph-sdk", ":evals")
├─ gradle/libs.versions.toml      single catalog (kotlin, agp, compose-bom, nav3, ktor, serialization, …)
├─ core/   (kotlin-jvm + serialization)
│   └─ src/main/kotlin/<pkg>/core/
│       model/        Document, Layer, Clip (Raster | Generator), Keyframe, DeviceProfile, Frame
│       command/      Command (sealed), validate(), apply() → Result, CommandError
│       history/      JsonPatch, HistoryLog, UndoGroup, Author
│       render/       Renderer (render(doc,t,inputs)), Baker, generators/ (ring_pulse@1 …)
│       analysis/     symmetry, centering, flicker, smoothness metrics
│       io/           GlyphwizCodec (format versioning + migrations), GlyphMuseumCodec (import/export)
│       session/      EditorSession (owner of doc + history; serialised dispatch)
│       port/         GlyphOutput, Clock (interfaces implemented elsewhere)
│   └─ src/test/…     unit tests + resources/golden/*.txt
├─ agent/  (kotlin-jvm + serialization; deps: :core, ktor-client-core)
│   └─ <pkg>/agent/   loop/, provider/ (openai-compatible, anthropic, …), tools/ (registry, schemas),
│                     context/ (KB, skills, glossary, tiering), perception/ (grid view, renders), port/
├─ glyph-sdk/ (android-library; full flavour only)
│   └─ <pkg>/glyphsdk/ NothingGlyphOutput : GlyphOutput   (+ fetchGlyphSdk task, libs/ git-ignored)
├─ evals/  (kotlin-jvm + application; deps: :core, :agent)
│   └─ <pkg>/evals/   cli/, tasks/, scoring/, report/
└─ app/    (android-application; flavours sdkFree | full)
    └─ src/main/kotlin/<pkg>/
        GlyphWizardApp.kt         Application; creates AppContainer
        AppContainer.kt           manual DI
        MainActivity.kt           enableEdgeToEdge(); setContent { GlyphWizardTheme { AppNav() } }
        nav/                      routes (@Serializable), AppNav (NavDisplay)
        ui/theme/                 Color, Type (dot font), Shape, Theme
        ui/components/            MatrixCanvas, Timeline, LedPreview
        feature/library/          LibraryScreen, LibraryViewModel
        feature/editor/           EditorScreen, EditorViewModel, EditorUiState
        feature/agent/            AgentPanel, AgentViewModel, AgentTurnService (shortService FGS)
        feature/triggers/         TriggersScreen, TriggerViewModel, NotificationTriggerListener
        feature/settings/         SettingsScreen, ProvidersScreen
        feature/importexport/     ImportPreviewScreen, SAF launchers, share
        data/                     DocumentRepository (files), SettingsRepository, TriggerStore, KeyVault
        platform/                 AndroidClock, Ktor engine binding, logging
    └─ src/sdkFree/…  PreviewOnlyGlyphOutput     src/full/…  binds :glyph-sdk
```

Package name: pick the final reverse-DNS ID now (e.g. `io.github.<user>.glyphwizard`). Use it as the root package across modules: `…glyphwizard.core`, `…glyphwizard.agent`, and so on.

---

## 13. Suggested learning order, aligned to milestones

| # | Milestone (what you build) | Learn first (asides above) | Done when |
|---|---|---|---|
| M0 | **Repo + build foundation.** Fix `.gitignore`, licence, add `:core` as a JVM module, migrate `:app` to Compose (minSdk 35 / target 36 / JDK 17), CI on PRs | Gradle + catalog (§6), AGP 9 notes, repo hygiene (§10), GitHub Actions (§8) | `./gradlew check` is green locally and in CI. A "Hello" Compose screen runs on the 4a Pro |
| M1 | **`:core` model + commands + bake, no UI.** Golden text grids | Kotlin language basics ([Kotlin tour](https://kotlinlang.org/docs/kotlin-tour-welcome.html)), kotlinx.serialization, JUnit/kotlin.test, golden files (§7) | Round-trip `.glyphwiz`. `apply`/`inverse` property test. Ring-pulse golden |
| M2 | **First editor screen:** matrix canvas, paint/erase, undo, file save | Compose basics + state (§1.1), ViewModel/StateFlow/UDF (§2), coroutines + `mapLatest` (§5), app-specific storage (§4.1), manual DI (§3) | Draw, undo, kill the app, reopen, and the drawing is still there |
| M3 | **Navigation + library + settings;** edge-to-edge and predictive back correct | Nav3 (§2.3), insets/back (§1.3), DataStore (§4.3), theming (§1.2) | Library → Editor → back animates. No content under the bars |
| M4 | **Device output:** `:glyph-sdk`, flavours, playback ticker (hardware spike) | Android library modules + flavours (§6.2), deadline loop (§5.2) | An animation plays on the LEDs at 20 ms. Jitter measured |
| M5 | **Import/export + share + Open-with;** GM JSON | SAF, FileProvider, intent filters (§4.4) | Export → import round-trip through Files and a chat app |
| M6 | **`:agent` + `:evals`** (headless first), then the agent panel + FGS | Flow/Turbine (§7), Ktor, Keystore (§4.3), services (platform doc R3) | The eval suite runs from the CLI. The in-app turn survives 2 min in the background |
| M7 | **Triggers** (D21) | NotificationListenerService (separate research), typed DataStore | A notification fires an animation |
| M8 | **Release:** signing, tag workflow, Obtainium, README | App signing (§8), licence files (§9) | Obtainium installs and updates from GitHub Releases |

**Rationale:** M1 before any UI plays to your strengths. You learn Kotlin itself on pure logic with fast tests, the way you'd start a Go service from its domain package. Android-specific concepts arrive one per milestone after that.

---

## 14. Open questions for the user

1. **Q1 Licence:** GPL-3.0-or-later + SDK exception + brand NOTICE (recommended, open source), or PolyForm Noncommercial (legally non-commercial, *not* open source, not on F-Droid)?
2. **Q2 Application ID:** `io.github.<github-user>.glyphwizard`, or a domain you own? This is permanent after the first release.
3. **Q3 Theme:** dark-only or dark + light? Is red accent use OK, given it's Nothing's signature?
4. **Q4 Font:** NDOT 47 (OFL, with its author's similarity caveat) or a self-drawn 5×7 font for UI headings?

## Verification gaps to close during M0

- **[U]** KGP alignment between AGP 9.4's built-in Kotlin and `org.jetbrains.kotlin.jvm` / `plugin.compose` / `plugin.serialization` declared in the root. Expect to iterate once.
- **[U]** The AGP 9.4 `optimization {}` / `keepRules` DSL in the scaffold (not verified this session).
- **[U]** Obtainium's asset-regex and version-from-tag specifics (README/wiki not read in full).
- **[U]** Which MIME type Nothing's Files app and common chat apps send for `.glyphwiz`. Test on the device.
- **[S]** Hilt + Kotlin 2.4 incompatibility reports. Irrelevant unless we adopt Hilt.

## Sources

UI / platform
- Android is Compose-first: https://developer.android.com/develop/ui/compose/first
- Compose-first blog (May 2026) [S]: https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- Compose documentation: https://developer.android.com/develop/ui/compose/documentation
- Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Compose compiler plugin: https://developer.android.com/develop/ui/compose/compiler
- Insets / edge-to-edge: https://developer.android.com/develop/ui/compose/system/insets
- Predictive back: https://developer.android.com/develop/ui/compose/system/predictive-back · setup: https://developer.android.com/develop/ui/compose/system/predictive-back-setup

Architecture / navigation / DI
- Guide to app architecture: https://developer.android.com/topic/architecture
- Navigation 3: https://developer.android.com/guide/navigation/navigation-3 · releases: https://developer.android.com/jetpack/androidx/releases/navigation3 · stable blog: https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html
- Manual DI: https://developer.android.com/training/dependency-injection/manual
- Hilt Gradle setup: https://dagger.dev/hilt/gradle-setup
- Hilt/AGP 9 issues [S]: https://github.com/google/dagger/issues/5099 · https://github.com/google/dagger/issues/4756 · https://mvnrepository.com/artifact/com.google.dagger/hilt-android-gradle-plugin

Storage
- App-specific storage: https://developer.android.com/training/data-storage/app-specific
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Auto Backup: https://developer.android.com/identity/data/autobackup
- SAF: https://developer.android.com/training/data-storage/shared/documents-files
- Sharing: https://developer.android.com/training/sharing/send

Build
- AGP 9.0 release notes: https://developer.android.com/build/releases/agp-9-0-0-release-notes
- AGP current (9.4) release notes: https://developer.android.com/build/releases/gradle-plugin
- Migrate to built-in Kotlin: https://developer.android.com/build/migrate-to-built-in-kotlin
- Gradle convention plugins sample: https://docs.gradle.org/current/samples/sample_convention_plugins.html

Testing / CI
- Test doubles: https://developer.android.com/training/testing/fundamentals/test-doubles
- Roborazzi: https://github.com/takahirom/roborazzi
- android-junit5 AGP 9.4.1 issue [S]: https://github.com/mannodermaus/android-junit-framework/issues/417
- detekt releases [S]: https://github.com/detekt/detekt/releases
- Obtainium: https://github.com/ImranR98/Obtainium

Licence
- F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- GPL FAQ: https://www.gnu.org/licenses/gpl-faq.html
- Open Source Definition: https://opensource.org/osd
- PolyForm Noncommercial: https://polyformproject.org/licenses/noncommercial/1.0.0/
- CC on software: https://creativecommons.org/faq/#can-i-apply-a-creative-commons-license-to-software
- MPL 2.0 FAQ: https://www.mozilla.org/en-US/MPL/2.0/FAQ/
- GlyphMatrixEditor (GPL-3.0): https://github.com/pauwma/GlyphMatrixEditor
- GlyphMaps (AGPL-3.0 + commercial): https://github.com/capad-xyz/GlyphMaps
- glyph-matrix-lab [S]: https://github.com/alex-1121/glyph-matrix-lab · hi-glyph [S]: https://github.com/alexmicuplusfour/hi-glyph · GlyphHub [S]: https://github.com/Esperosa/GlyphHub
