# GlyphWizard — Front-end Code Guidelines (Compose)

**Status:** v1, part of the v1 spec (§6.4). Enforced by review and, where marked ⚙, by tooling.

**Goal:** every visual decision lives in **one place**, feature code only composes building blocks, and the UI stays consistent, testable and fast.

**Prior art:**
- Android "Custom design systems in Compose": tokens via `CompositionLocal`, wrapping Material where useful.
- Now in Android's `:core:designsystem` module.
- The compose-rules lint ruleset (mrmans0n/compose-rules).

---

## 1. One source of truth: the `:designsystem` module

```
:designsystem   (Android library; depends on Compose only, no feature code)
  theme/        GlyphTheme { } entry point; light/dark/system; CompositionLocals
  tokens/       Color, Type, Spacing, Shape, Elevation/Glass, Motion, Haptics, Icons, Strings-adjacent sizes
  components/   GwButton, GwPill, GwIconButton, GwToolTray, GwTool, GwSegmented, GwChip,
                GwToggle, GwSheet, GwCard, GwListRow, GwBadge, GwLedCanvas, GwFrameStrip,
                GwDotText, GwAmbientBackground, GwTopBar, GwPrompt, GwReplyCard ...
  preview/      @GwPreviews multi-preview (dark, light, font scale 2×, each body font)
```

- **Feature code** (`:app/feature/*`) depends on `:designsystem` and never on raw Material colours or typography.
- **Tokens are the API:** `GlyphTheme.colors.red`, `GlyphTheme.type.display`, `GlyphTheme.space.md`, `GlyphTheme.shape.tray`, `GlyphTheme.motion.push`, `GlyphTheme.haptics.tick`.
- **Material 3 is wrapped, not leaked.** Our components may use M3 internally (for accessibility and ripple behaviour), but screens only see `Gw*` components.
- **Token values come from the design log**, and each token has a comment citing its UI# entry:

  | Token | Value | Source |
  |---|---|---|
  | fg | `#EAE8E3` | UI34 |
  | red | `#FF5C4D` / `#9A281F` | UI14 / UI15 |
  | radii | 20/16/12/10/pill | |
  | grid | 8 dp | |
  | ring opacity | 12/20/28 % | UI16 |
  | motion durations | | UI17 / UI18 |
  | font families | | UI13 |

- **The LED canvas** (`GwLedCanvas`) is the one place that converts brightness to colour, through the perceived-brightness curve (UI11). Nothing else draws LEDs.

## 2. Hard rules
| # | Rule | How it's enforced |
|---|---|---|
| R1 | No hard-coded colours, dp/sp, radii, durations or font families outside `:designsystem` | ⚙ custom Lint/detekt check: ban `Color(0x…)`, raw `.dp` literals in `feature/*`, `FontFamily(…)`, `tween(<number>)` |
| R2 | Screens use only `Gw*` components and `GlyphTheme` tokens | review; ⚙ forbidden imports of `androidx.compose.material3.*` in `feature/*` (except `Scaffold` plumbing if needed) |
| R3 | All user-facing text in string resources; no string literals in composables | ⚙ Android Lint `HardcodedText` / custom check |
| R4 | Every interactive element has a content description or semantic label, and is ≥ 48 dp | ⚙ compose-rules + `Modifier.minimumInteractiveComponentSize()` built into components |
| R5 | Every `Gw*` component has `@GwPreviews` (dark, light, 2× font, RTL off for v1) and a Roborazzi screenshot test | ⚙ CI fails if a public component lacks a preview test |
| R6 | Composables are **stateless by default**: state is hoisted, values go in and events (lambdas) come out | review; ⚙ compose-rules `ComposeViewModelForwarding`, `ComposeMutableParameters` |
| R7 | **One-way data flow:** UI → `onEvent` → ViewModel → `:core` command → new immutable state → UI | review |
| R8 | ViewModels are never passed into child composables; only screen-level composables touch a ViewModel | ⚙ compose-rules `ComposeViewModelInjection` |
| R9 | UI state types are **immutable and stable** (`data class` with `val`, `ImmutableList` from kotlinx.collections.immutable); no `var` in state | ⚙ compose compiler stability report checked in CI |
| R10 | `Modifier` is the first optional parameter of every public composable, defaulting to `Modifier`, and is applied to the root only | ⚙ compose-rules `ComposeModifierMissing` / `ComposeModifierReused` |
| R11 | No business logic in composables: formatting, validation and math live in ViewModels or `:core` | review |
| R12 | Motion uses `GlyphTheme.motion` specs only, and respects reduced motion through a single `LocalReducedMotion` | ⚙ R1 check on raw `tween/spring` |
| R13 | Haptics go through `GlyphTheme.haptics` (honours the user toggle) and never call `performHapticFeedback` directly | ⚙ forbidden call outside `:designsystem` |
| R14 | Icons come from one `GwIcons` object (the duotone set, 24 dp); no ad-hoc vectors in features | review |

## 3. Structure & naming
- **Packages:** `feature/<area>/` holds `XxxScreen.kt` (stateful, owns the ViewModel), `XxxContent.kt` (stateless, previewable), `XxxViewModel.kt`, `XxxUiState.kt` and `XxxEvent.kt`.
- **Naming:**
  - components are `Gw<Noun>`
  - screen content is `<Area>Content`
  - events are sealed `XxxEvent`
  - state is `XxxUiState`
- **File size:** a file over ~300 lines or a composable over ~80 lines is a signal to split.
- **Previews** use realistic sample data from `preview/SampleData.kt`. Lorem ipsum is not allowed.

## 4. Performance
- Read state as late as possible: pass lambdas (`() -> T`) for frequently changing values such as the playhead.
- Use `derivedStateOf` for computed UI flags.
- Use `key` in lazy lists, and stable IDs from `:core`.
- `GwLedCanvas` draws in one `Canvas` pass. There is no composable per LED.
- The frame strip uses `LazyRow` with thumbnail bitmaps cached by frame hash.
- Measure, don't guess: macrobenchmark plus JankStats against the spec §9 budgets. Baseline Profiles ship in the release build.

## 5. Accessibility built into components
- **Semantics:** components set roles, state descriptions ("selected", "on") and merge where appropriate.
- **The LED canvas** exposes the analysis-generated description (spec §9).
- **Font scaling:** layouts tolerate 200%; there are no fixed-height text containers.
- **Colour contrast:** checked against tokens once, in a unit test, rather than per screen.

## 6. Theming specifics
- `GlyphTheme(mode = Light/Dark/System, bodyFont = Sometype|Geist|Reddit|Spline|Atkinson, gridVisibility, ambient = on/off)` is fed from DataStore settings at the root.
- **Dynamic colour is off.** Our palette is deliberate.
- **Fonts are bundled.** They're downloaded fonts only if APK size forces it (spec §9).
- **Dot display font** is a single `GwDotText` component, so swapping Doto for our own dot font is a one-line change.

## 7. Review checklist (paste into every UI PR)
- [ ] No raw colours, dp, durations or fonts outside `:designsystem` (R1)
- [ ] Only `Gw*` components and tokens used (R2)
- [ ] Strings in resources (R3)
- [ ] Semantics and 48 dp targets (R4)
- [ ] Previews and a screenshot test for new or changed components (R5)
- [ ] State hoisted, UDF, no ViewModel passed down, immutable state (R6–R9)
- [ ] Modifier conventions (R10)
- [ ] Motion and haptics via tokens; reduced motion respected (R12, R13)
- [ ] Matches the atlas screen; any change is logged in design-log.md

## Learning aside
**Design system in Compose:** think of it as your service's shared library plus config. Tokens are constants injected through `CompositionLocal`, which works like a request-scoped context. Components are the shared library's functions. Screens are thin handlers that only call those functions.

- Read: developer.android.com → "Custom design systems in Compose"
- Read: developer.android.com → "Thinking in Compose" and "State hoisting"
- Reference implementation: github.com/android/nowinandroid (`core/designsystem`)
