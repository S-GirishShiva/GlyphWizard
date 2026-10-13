# GlyphWizard — Code Guidelines

**Status:** v1 (D45). Mandatory for every agent and contributor. UI-specific rules are in `docs/design/frontend-guidelines.md`, which applies on top of this file.

## 1. Workflow: before any code is written
1. **Read the task** fully.
2. **Read** `CLAUDE.md`, this file, the relevant spec sections, and (for UI work) the frontend guidelines.
3. **Discover first.** Search the codebase for code that already does or nearly does the job, and for code that could be refactored to be reused.
4. **Map every flow you will touch.** Read the callers, the callees and the tests of each piece you will change, end to end, in this turn. If you can't explain how a piece works and why it's shaped that way, you may not change it yet.
5. **State the plan** in a short note: what you will reuse, what you will refactor, which flows are affected.
6. Only then write code.

**Reviewers follow the same steps 1–4** before judging a change.

## 2. Principles
- **Clean, elegant, no hacks.** If a workaround is unavoidable (for example a platform bug), isolate it behind a well-named function and add a one-line comment saying *why*, with a link.
- **No duplication.** Extract shared logic the second time it appears. Reuse beats re-implementing.
- **Small, focused units by default.** Name functions after what they do. Prefer clear decomposition.
- **Performance overrides decomposition only in measured hot paths:**
  - the renderer / baker
  - `GwLedCanvas` drawing
  - the stream parser
  - the trigger event → frame path

  There, flatten or use `inline` when a benchmark (JMH / Macrobenchmark) shows it matters, and keep the benchmark in the repo. (R8 and ART already inline small methods, so don't pre-optimise elsewhere.)
- **Leave it better (boy-scout rule):** if code you pass through is poor, first understand why it's that way, then improve it. Do this in a **separate refactor commit before the feature commit**, behaviour-preserving and covered by tests.
- **Dependency injection everywhere.** Constructor injection only. No hidden singletons, no `object` holding state, no service locators. One manual container per app process (D44) wires the graph. `:core` and `:agent` never know about Android or the container.

## 3. Kotlin style: idiomatic, Go-flavoured where idioms allow
- **Formatting:** the official Kotlin style via ktlint ⚙, line length 120.
- **Explicitness over magic.** No reflection-based frameworks, no annotation-processing DI, and no operator overloading unless the meaning is mathematically obvious.
- **Small interfaces, defined by the consumer** (like Go): an interface lives next to the code that *uses* it and has only the methods that consumer needs.
- **Composition over inheritance.** No class hierarchies deeper than an interface plus its implementation. Prefer `sealed interface` + `data class` for closed sets.
- **Errors as values for expected failures:**
  - `:core` and `:agent` return a sealed result type (e.g. `Outcome<T, E>` with `Ok`/`Err` and a domain error `sealed interface`), like Go's `(value, err)`.
  - Exceptions are only for programmer errors and truly unexpected states.
  - Never swallow an error; handle it or propagate it.
- **Immutability by default:** `val`, data classes, immutable collections. Mutation is local and contained.
- **Concurrency:** coroutines with structured concurrency (scopes are like Go's `context`, `Flow` like channels). No `GlobalScope`. Dispatchers are injected, never hard-coded.
- **Top-level functions are fine** for pure helpers (like Go package funcs). Extension functions sparingly: only when they read as natural vocabulary of the type.
- **Naming:** full words, no abbreviations except well-known ones (`id`, `ms`, `url`). Booleans read as questions (`isLocked`, `hasTrigger`).
- **Visibility:** `internal` by default inside modules. `public` only for real module APIs.

## 4. Comments
- Code should explain itself through names and structure.
- **Allowed:**
  - a short *why* comment for non-obvious decisions, constraints, workarounds and units or ranges
  - short KDoc on **public module APIs** (`:core` commands, the document model, tool schemas): one or two lines of purpose, plus non-obvious params
- **Not allowed:**
  - comments that restate the code ("renders screen X")
  - narrating a function step by step
  - commented-out code
  - TODOs without an issue link

## 5. Testing: tests after, comprehensive
- **Not TDD.** This overrides the superpowers TDD skill for this project. Build the functionality first, then write a comprehensive regression suite **in the same task, before the work counts as done**.
- **Coverage (enforced in CI ⚙, Kover):**

  | Module | Target |
  |---|---|
  | `:core`, `:agent` | **100% line coverage**, high branch coverage |
  | `:app`, `:designsystem` | ≥ 80% line coverage, plus a screenshot test per component and screen, plus flow tests for each user journey |

  Excluded: generated code and previews.
- **Test style:** behaviour-focused (not implementation-pinned), fakes over mocks, property tests for invariants (undo, commands, serialization round-trips), golden files for rendering, and a test name that reads as the behaviour.
- **Every bug fix ships with a test** that fails without the fix.
- **The suite also runs against the minified release build** (spec §9).

## 6. Commits & branches
- **Commit format:** `<slug> [Siddanth, Claude] - brief message`, blank line, details, then the Co-Authored-By trailer.
- **Commit order:** refactors and features in separate commits (refactor first).
- **Branches:** one per milestone (`m0-foundations`, …); merge after `/code-review` with findings resolved.

## 7. Definition of done (every task)
- [ ] Workflow §1 followed; flows mapped and stated
- [ ] No duplication introduced; reuse and refactor opportunities taken (refactor in a separate commit)
- [ ] Guidelines, and frontend guidelines for UI, satisfied; ktlint, Lint and detekt clean
- [ ] Tests written after implementation; coverage targets met; release-build tests pass
- [ ] Comments minimal and *why*-only
- [ ] Spec, decisions or design log updated if anything changed
- [ ] Learning aside added for any new Android/Kotlin concept (D23)
