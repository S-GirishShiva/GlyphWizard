# Targeted Research: Android Platform, Play Policy, Distribution, minSdk, i18n

2026-10-11. This follows up adversarial review r1 and answers its gaps R3/H1, R4/H5, R1, L8 (minSdk) and R13/L6.

Tags:
- **[V]** = verified against the primary source this session
- **[S]** = secondary source
- **[I]** = my inference from verified facts
- **[U]** = unverified or speculative; must be checked

---

## TL;DR

1. **Backgrounding (H1).**
   - **Without a foreground service, a streaming turn dies about 10 s after the user leaves the app.** Once the activity stops and no other component is running, the process is *cached*. On Android 14+ a cached process is frozen 10 s after it enters that state. When all of an app's processes are frozen, **the system terminates its TCP sockets** [V].
   - So [UX]'s plan to "let the in-flight request finish" holds for roughly 10 s at most. [PM]'s "cancel and roll back" works but throws away work and money.
   - **Recommendation:** start a `shortService` FGS at turn start, while the app is visible. Re-extend it at each step while the app is still visible. Once backgrounded, the turn has at most about 3 min, enforced by an app-side 150 s soft deadline plus `onTimeout` → Stop path. On return the user sees "Paused: Continue / Keep / Undo".
   - This reverses the review's "no FGS in v1" cut. The cost is about one Service class, with no extra permission beyond `FOREGROUND_SERVICE`.
   - Rejected alternatives: `dataSync` (needs a Play declaration with a video, and is a weak semantic fit) and UIDT jobs (built for bounded, resumable transfers).
2. **Nothing OS:** there's no dontkillmyapp.com entry for Nothing [V], and no evidence of OEM killing beyond AOSP [S, weak]. Still measure on the 4a Pro.
3. **Play AI-content policy (H5):** probably in scope. The policy requires in-app reporting of offensive output **to the developer**, "without needing to exit the app" [V]. A local-only report likely does *not* satisfy this [I]. That collides with D1 (no backend) → user decision.
4. **Distribution (R1):**
   - **IzzyOnDroid is ruled out.** Its policy rejects apps "fully or in part created by generative AI tools" and apps that "integrate with" LLM services [V].
   - **F-Droid** has no AI-code policy [S]. An LLM client (GPTMobile) is listed with no anti-features [V]. Only the SDK-free flavour could be listed, and it can't drive the LEDs.
   - **Recommended v1: GitHub Releases + Obtainium.** Optionally add Play later.
   - Every channel that ships the SDK inside the APK depends on Nothing's permission. The EULA forbids redistributing "the Software" and is *silent* on distributing apps that contain it [V]. D18's build-time fetch only fixes the repository, not the APK.
5. **Developer verification:** from 30 Sep 2026 in BR/ID/SG/TH, and globally in 2027, sideloaded apps on certified devices need a verified developer. Otherwise users go through a one-time advanced flow with a 1-day wait. ADB installs are exempt [V]. This affects GitHub/Obtainium distribution, and there's nothing to build for it now.
6. **SDK levels:** **minSdk 35, targetSdk 36, compileSdk 37.**
   - targetSdk 36 is mandatory on Play since 31 Aug 2026 [V].
   - Phone (3) launched on Android 15 and got Android 16 in Nov 2025 [S]. The 4a Pro launched on Android 16 [S].
   - Don't target 37 yet. It would force the runtime `ACCESS_LOCAL_NETWORK` permission for LAN Ollama [V].
7. **i18n (R13):** keep the KB, glossary and schemas in English, and let the model reply in the user's language.
   - The main multilingual tool-call failure is **parameter values in the user's language** [V, abstract]. Schemas therefore use closed enums, and validation errors name the valid values.
   - Add ~5 non-English eval tasks. Use string resources from day one, English UI only.

---

## R3 / H1: Backgrounding an in-flight agent turn

### Verified platform facts

| Fact | Source |
|---|---|
| Freezer exists since Android 11. **Android 14+:** "App processes in the cached state are frozen 10 seconds after entering the cached state." Frozen = all threads suspended. | AOSP Cached Apps Freezer [V] |
| "If all processes for a particular app are frozen, the system terminates any active TCP sockets maintained by the app" (Android 14+). | same [V] |
| Android 16 adds public APIs to observe frozen state (`IBinder.FrozenStateChangeCallback`). No Android 15 freezer change. | same [V] |
| A process with a foreground service is a *visible* process. A process whose activities are all stopped and which has no other active component is *cached*. "apps should cease all work while in the cached state." Since Android 13, cached processes "may receive limited or no execution time". | Processes and app lifecycle [V] |
| ⇒ Pressing Home with no FGS: the process goes cached, gets frozen ~10 s later, and the SSE socket is killed. | [I] from the two rows above. A system might keep a "previous app" briefly above cached; **measure** [U] |
| `shortService`: about 3 min from `startForeground()`. On timeout, `Service.onTimeout()` gives "a brief window" to stop. If the service still isn't stopped, the app gets an **ANR**, even if other FGSs are running. Not sticky. Can't start other FGSs. Battery-optimisation exemption doesn't lift the limit. **Calling `startForeground(...SHORT_SERVICE)` again while the app is visible extends it by another 3 min.** Needs only `FOREGROUND_SERVICE`. | FGS types [V] |
| From Android 12, an FGS can't be started from the background, apart from listed exemptions such as "transition from a user-visible state" and the user turning off battery optimisation. | Background-start restrictions [V] |
| `dataSync` (target 35+): 6 h per 24 h *while in background*, per type. The timer resets when the user brings the app to the foreground. Over the limit → `onTimeout(int,int)` → a few seconds to `stopSelf()`, otherwise a fatal `RemoteServiceException` (crash, not ANR). Needs `FOREGROUND_SERVICE_DATA_SYNC`. | FGS timeout page; Android 15 behaviour changes [V] |
| UIDT (`setUserInitiated`, API 34+): JobScheduler job that needs `RUN_USER_INITIATED_JOBS` and a notification. Must be scheduled while visible. No fixed runtime limit ("as system conditions allow"). The system can stop it if it runs "longer than necessary to complete the data transfer task", and **the app must persist and resume transfers**. Intended for "longer-duration data transfers … such as downloading a file". | UIDT page [V] |
| Android 16: jobs running concurrently with an FGS, or started while top and continuing after, now count against the job runtime quota. This affects WorkManager. | Android 16 behaviour changes (all apps) [V] |
| Android 17 (stable 16 Jun 2026 [S]): no freezer, FGS or job changes in the behaviour-change pages, apart from background-audio hardening and new RAM-based memory limits (`MemoryLimiter`). | Android 17 behaviour changes and release notes [V] |
| Without `POST_NOTIFICATIONS`, an FGS still starts. Its notice appears only in Task Manager, not in the notification drawer. | Notification permission page [V] |
| **Nothing OS:** dontkillmyapp.com has no Nothing entry (`/nothing` returns 404; Nothing isn't in the ranking). Search found no reports of Nothing-specific killing. One low-quality source calls Nothing OS near-stock. | dontkillmyapp.com [V]; web search [S, weak] |

### Reconciling the three source positions

| Source | Position | Verdict |
|---|---|---|
| [UX] | No FGS; let the in-flight request finish, resume on return | **Wrong beyond ~10 s** on Android 14+ (socket termination) [I]. "Resume on return" is kept, but as a *user-tapped* Continue. |
| [PM] | `shortService` *or* cancel and roll back | Rollback alone discards paid work every time the user glances at another app. Keep it only as the fallback when the FGS can't start. |
| [TR] | Optional `shortService`, ~5 min per-turn wall clock, no auto-resume (spending) | Right mechanism. Fix the 5 min vs 3 min conflict by extending the service while visible (below). |

### Recommended design (agent turn up to ~5 min)

1. **Turn budget:**
   - Hard wall clock **5 min** total.
   - **Background budget ≤ 150 s** after the last extension, which leaves 30 s of margin under the ~3 min limit.
2. **Start:** when the user taps Send (the app is visible), the `AgentSession` host starts `AgentTurnService` with type `shortService`.
   - Its ongoing notification reads "Designing… [Stop]".
   - If it fails to start (`ForegroundServiceStartNotAllowedException`), run without it and treat backgrounding as Stop.
3. **Extend while visible:** at each step boundary while an activity is started, re-call `startForeground(…, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)`, rate-limited to about once per 60 s. A turn in the foreground can then reach 5 min.
4. **Backgrounded:**
   - The loop finishes the current streaming call.
   - At the **first step boundary after the 150 s soft deadline**, it makes no new LLM call: persist, `stopSelf()`, and set state **Paused (left app)**.
5. **`onTimeout()`:**
   - Cancel the in-flight call (the same code path as the Stop button).
   - Persist the completed steps as the turn's undo group, then `stopSelf()` immediately. This avoids the ANR.
6. **Return to the app:** a card shows "Paused after N steps: **Continue** / Keep / Undo". Continue is a deliberate tap and no auto-resume happens, which honours [TR]'s spending concern.
7. **Process death** (OOM, user swipe, OEM kill): the next launch shows the same card, "Interrupted".
   - Use `ApplicationExitInfo` to log why.
   - This adds Android 17 `MemoryLimiter` as a possible reason [V].
8. **Notifications:** don't ask for `POST_NOTIFICATIONS` just for this. The FGS works without it [V].
9. **Not chosen:**
   - `dataSync`: needs a Play declaration with a video, and is a weak "network transfer" fit (see R4). Keep it as a fallback if the hardware spike shows 150 s is often too short.
   - UIDT: built for bounded, byte-estimated, resumable transfers, and our turn mixes LLM calls with local compute. Its suitability for streaming isn't documented [V: the page is silent].
   - WorkManager: quota changes on Android 16 [V].
10. **Hardware spike additions (§11):** on the 4a Pro, run a streaming turn and press Home. Check what happens at 10 s / 60 s / 170 s / 200 s, with and without the FGS. Record:
    - `adb shell dumpsys activity processes` (proc state)
    - the freezer state
    - socket errors
    - `ApplicationExitInfo`

    Repeat with the screen locked.
11. **Wording fix** for the draft: "cached processes are frozen 10 s after becoming cached (Android 14+), and their TCP sockets are closed". Not "~10 s after going to background".

[U] Whether a cancelled streaming request is still billed for tokens generated server-side depends on the provider. Don't claim either way in the UI.

---

## R4 / H5: Google Play policy

### (a) AI-Generated Content policy

- **Requirement** [V, Developer Program Policy, answer/17190352]: "Apps that generate content using AI must contain in-app user reporting or flagging features that allow users to report or flag offensive content **to developers** without needing to exit the app." Developers should use reports to inform filtering and moderation.
- Apps must also prevent generation of Restricted Content and Deceptive Behavior content.
- The June 2024 blog adds that you should test against adversarial prompts and document testing, "Google may ask to review it" [V].
- **Scope** [V, answer/14094294]:
  - **In scope:** text-to-text chatbots "where the AI generated chatbot interaction is a central feature"; text/voice/image-to-image generation.
  - **Out of scope:** pure hosting, summarisation-only apps, and "productivity apps that use AI to improve an existing feature".
- **BYOK / user-provided model:** not mentioned anywhere in the policy [V: absent]. The User Data policy says its requirements "also apply to third-party AI integrations" [V]. No exemption should be assumed [I].
- **Does it apply to GlyphWizard?** [I] It's arguable either way:
  - The editor is primary and the agent "improves an existing feature", which fits the productivity carve-out.
  - But the agent is a chat UI that generates animated images from text prompts, which fits "text-to-image".
  - The *offensive* risk lies mostly in the chat text, not 13×13 pixels. Note, though, that a 25×25 matrix can render words and symbols.
  - Plan for **in scope** if shipping on Play.
- **What satisfies it:**
  - A "Report response" action on every turn card that reaches the developer without leaving the app.
  - Local-only (a debug bundle written to disk) doesn't reach the developer, so it likely fails the requirement [I].
  - An `ACTION_SEND` email or a browser GitHub issue leaves the app, so it likely fails "without needing to exit" [I].
  - A compliant option needs an HTTPS POST to something the developer controls, e.g. a form endpoint. That **conflicts with D1** → user question Q2.

### (b) FGS declaration for `shortService`

- Play's FGS policy page requires apps targeting 14+ to declare "any foreground service types that you use" in Play Console → App content [V].
- **The page's list of types doesn't include `shortService`** [V: absent]. The Android FGS page also says to declare FGS types in Play Console [V]. Whether `shortService` gets a declaration form is **[U]**: confirm in Play Console at first submission. [TR]'s claim of "no special Play declaration" is unconfirmed.
- If a form appears, it needs [V, for listed types]:
  - a description
  - the user impact if the work is deferred or interrupted
  - a video link
  - a use case
- For **`dataSync`**, those same four items, and Play itself *recommends UIDT* for network transfers started by explicit user request [V]. That's another reason not to use `dataSync`.
- `POST_NOTIFICATIONS` isn't needed for the FGS [V].

### (c) Users pasting third-party API keys

- **No Play policy addresses BYOK keys** [S: search found none; I found nothing in the policy pages].
- **Data safety:** "collect" means "transmitting data from your app off a user's device" [V]. Sending prompts and document content to the user-chosen provider is therefore *collection*, to be declared, e.g. app activity / user-generated content [I].
  - The user-initiated-action exemption covers only *sharing* [V].
  - The key itself never reaches the developer. Store it in Keystore and exclude it from backup (already planned).
  - Say in the privacy policy that data goes to the provider the user picks, under that provider's terms.
- **Provider terms, not Play, are the real constraint:**
  - An old (2022) OpenAI community statement said users may not share keys with BYOK apps. Later forum posts dispute it [S, unverified, likely outdated].
  - [PM] already tracks subscription ToS. Don't proxy keys anywhere (already the design).

---

## R1: Distribution

### The SDK question applies to every channel

- EULA §1 grants a licence "to use" the SDK within your applications. §2.1(c) forbids "redistribute … the Software". §2.2 bans commercial use without permission. The EULA **doesn't say whether shipping an app that bundles the `.aar` is allowed** [V].
- D18's build-time fetch keeps the binary out of *git*, but **any APK containing the SDK still ships it** [I]. The GDKsupport email (D18) gates every channel except an SDK-free build. Ask Nothing explicitly about: (1) bundling it in a free OSS APK; (2) GitHub Releases; (3) Play; (4) a public build script that downloads the `.aar` from Nothing's GitHub.
- The SDK is distributed only as `glyph-matrix-sdk-2.0.aar` committed to Nothing's GitHub repo, with no Maven coordinates [V]. The build-time fetch is therefore a raw GitHub URL plus a pinned SHA-256 [I].

### Channel comparison (solo dev)

| | GitHub Releases + Obtainium | Google Play | F-Droid (main) | IzzyOnDroid |
|---|---|---|---|---|
| Acceptance | None needed. Obtainium tracks GitHub, Codeberg and others [V] | $25; ID verification; **new personal accounts: closed test with ≥12 testers opted in for 14 consecutive days** [V]; review | FLOSS licence; build from source on F-Droid's servers; non-free deps forbidden, "build flavour" accepted [V] | **Rejected**: "strongly opposed to apps which are fully or in part created by generative AI tools", and LLM front-ends "or integrate with such services, will be rejected" [V] |
| SDK flavour shipped | Full, if Nothing permits | Full, if Nothing permits | **SDK-free only**: preview + export; can't drive LEDs (D18). The Gradle build must not download the `.aar` in that flavour [I] | n/a |
| Recurring effort | Tag → CI builds a signed APK → release | Yearly targetSdk bump (36 now) [V]; Data safety; FGS form; AI-report feature; policy emails | fdroiddata metadata MR once; auto-updates by tag; review queue time [U] | n/a |
| Signing | Own key | Play App Signing | F-Droid's key, **unless reproducible**. Then F-Droid publishes the developer-signed APK only when it matches byte-for-byte [V]. Different signature → users can't switch channels without reinstalling [I] | n/a |
| Policy extras | Developer verification (below) | AI-content reporting (R4a) | Anti-features: no NonFreeNet for user-configurable servers [I, Tethered/NonFreeNet definitions]; precedent: GPTMobile listed with none [V] | n/a |
| AI-assisted code | No rule | No rule found [U] | **No formal policy** (forum, Sep–Dec 2025) [S]; an Oct 2026 survey found many LLM-assisted apps in the 12 Sep 2026 update batch [S] | Banned |

**Developer verification** [V, developer.android.com/developer-verification + FAQ]:
- Enforcement starts on **30 Sep 2026** in Brazil, Indonesia, Singapore and Thailand on certified devices running Android 7+. It goes **global in 2027**.
- Options:
  - Android Developer Console, full distribution: $25, government ID, register package name + signing-key SHA-256.
  - A free "limited distribution" account (≤20 devices).
- Users without a verified developer can use the **advanced flow**: developer mode, a one-time 24 h wait, biometric confirmation.
- **ADB installs are exempt.**
- Open-source re-signing platforms such as F-Droid: register the platform's key fingerprint, using an `adi-registration.properties` asset snippet if needed [V].

**Reproducible builds:** only matter if you want F-Droid to ship *your* signature, or for trust.
- Pitfalls [V]: `baseline.prof` non-determinism, timestamps, mixed JDKs, CPU-count dex differences.
- Sign with build-tools 34, not 35.0.0-rc1+.
- **Defer.** It's not needed for GitHub/Obtainium.

**Recommendation** [I]:
- **v1: GitHub Releases + Obtainium**, own signing key, with the SDK included only after Nothing's written OK. Until then, publish the SDK-free APK plus "build it yourself" instructions.
- Register with the Android Developer Console before the 2027 global rollout.
- **Play: later and optional.** The 12-tester/14-day gate, the AI-report endpoint and yearly target bumps are real costs, and the audience (Nothing-phone enthusiasts) already sideloads.
- **F-Droid:** possible for the SDK-free flavour, but low value without LEDs.
- **Skip IzzyOnDroid.**

---

## minSdk / targetSdk

### Evidence

- Phone (4a) Pro: Android 16 / Nothing OS 4.1 at launch, Mar 2026 [S: Notebookcheck].
- Phone (3): Android 15 / Nothing OS 3.5 at launch, Jul 2025 [S]. Nothing OS 4.0 (Android 16) stable rolled out to the Phone (3) from 21 Nov 2025 [S: GSMArena, Android Authority].
- These are the only devices in the Glyph Matrix SDK table [V].
- Play target requirement: **API 36 for new apps and updates from 31 Aug 2026** (extension to 1 Nov 2026) [V].

### Recommendation

**minSdk 35**:
- covers any Phone (3) not yet updated
- costs nothing versus 36 (no Android 16-only API is needed)

**targetSdk 36** (required on Play; sensible elsewhere). **compileSdk 37.**

### What this unlocks or forces

| Item | Effect | Source |
|---|---|---|
| `shortService`, `onTimeout`, UIDT | Available on every supported device (API 34+). `onTimeout(int,int)` exists on 35+ | [V] |
| `dataSync` 6 h/24 h, BOOT_COMPLETED FGS ban | Apply (target 35+). Irrelevant to the recommended design | [V] |
| Edge-to-edge | Enforced at target 35. **At target 36 the opt-out is gone.** Compose must handle insets (`Scaffold`/`WindowInsets`) | [V] |
| Predictive back | On Android 16 devices at target 36, `onBackPressed` isn't called and `KEYCODE_BACK` isn't dispatched. Use `OnBackPressedDispatcher` / Compose `BackHandler`. Temporary opt-out: `enableOnBackInvokedCallback="false"` | [V] |
| Large-screen orientation/resizability | Ignored on sw ≥ 600dp at target 36 (opt-out ends at 37). Irrelevant for these phones | [V] |
| `elegantTextHeight` | Ignored at target 36. Affects only some non-Latin scripts | [V] |
| `scheduleAtFixedRate` | At most one missed run is replayed (target 36). Use coroutines, not executors, for playback ticks anyway | [V] |
| **Local network** | **At target ≤36:** INTERNET gives an implicit `ACCESS_LOCAL_NETWORK` grant. **Don't** declare it. **At target 37:** a runtime permission (NEARBY_DEVICES group); TCP to LAN hosts times out without it. Applies to OkHttp/Ktor/all stacks [V]. ⇒ Bump to 37 only together with a permission prompt in the "Ollama/LAN" preset | [V] |
| Cleartext | `usesCleartextTraffic` will be deprecated. Use a network security config for LAN opt-in | Android 17 changes [V] |
| Android 17 runtime (all apps) | RAM-based memory limits; cross-profile loopback blocked | [V] |
| Live Updates (Android 16) | Nothing OS 4.0 maps Android 16 Live Updates to "Glyph Progress" [S]. A possible *future* "turn in progress" indicator; not v1 | [S] |
| 16 KB page size | Matters only for native libs. A pure-Kotlin app is unaffected | [U: Play's 16 KB requirement not re-checked this session] |

---

## R13: i18n

### Evidence

- **Ticket-Bench** (arXiv 2509.14477) [V]: multilingual function calling in en/es/fr/it/de/pt. Top models score pass^3 0.85–0.93, with a **~5–6 pt best-to-worst language spread**. No language is uniformly hardest.
- **Luo et al., "Lost in execution"** (arXiv 2601.05366, abstract) [V]:
  - Tested Chinese, Hindi and Igbo.
  - The dominant error is a **language mismatch in parameter values**: models emit values in the user's language where execution expects language-invariant values. This happens even when intent and tool choice are right.
  - Inference-time mitigations shrink the gap but don't close it.
- **MASSIVE-Agents** (EMNLP Findings 2025) [V]:
  - 52 languages in BFCL format.
  - The best model averages 34% AST accuracy: **57% English vs 6.8% Amharic**.
  - Small models score 0 on hard languages.
  - The models are 2025 Bedrock models, so this says more about trends than about current numbers.

### Minimal v1 approach [I/P]

1. **English KB, English glossary, English tool/enum names.**
   - The system prompt says: "Reply in the user's language. Tool arguments, enum values and parameter names are always the English identifiers from the schema. Map the user's descriptive words (any language) to the nearest glossary term."
   - The glossary carries *metrics*, so the model only has to translate the adjective.
2. **Closed enums plus validation errors that list the valid values.** This targets the documented failure mode directly. Free-text fields, such as clip labels, may be in any language.
3. **No translated KB, skills or glossary in v1.** The review already drops BM25 retrieval, so the tokenizer question is moot.
4. **UI:** use Android string resources from day one; ship English only.
5. **Evals:** add ~5 non-English variants of existing edit/repair tasks.
   - Languages the user can check, e.g. German/Spanish plus one non-Latin script.
   - Report the gap per model in `SUPPORT.md` and gate nothing on it in v1.
   - If a supported model drops more than ~10 pts, document "best in English" rather than engineering a fix.

---

## Recommended changes to the draft

1. **§7 Backgrounding:** replace it with the R3 design above.
   - `shortService` at turn start, extended while visible.
   - 5 min total cap; ≤150 s background budget.
   - `onTimeout` → Stop path.
   - Paused/Interrupted card with a user-tapped Continue.
   - Correct the freezer wording.
   - **Reverse the review's "FGS → none" cut** (cost ≈ one Service).
2. **§11 Hardware spike:** add the backgrounding matrix (10/60/170/200 s, with/without FGS, screen locked). Log `ApplicationExitInfo`.
3. **§2 Modules:** `:glyph-sdk` behind an interface plus an `sdkFree` flavour (D18). The fetch task is pinned by SHA-256 and **disabled in `sdkFree`**.
4. **§8 UX:** if Play is ever a target, add a "Report response" action on the turn card. It needs a developer-reachable endpoint (Q2).
5. **§9 Safety:**
   - Keep target 36. Don't declare `ACCESS_LOCAL_NETWORK` yet.
   - Add a "target 37 ⇒ runtime LAN permission in the Ollama preset" note.
   - Use a network security config, not `usesCleartextTraffic`.
6. **§6 Context:** add the i18n rule (reply in the user's language; English identifiers; closed enums).
7. **§10 Evals:** add a non-English task set (~5 tasks).
8. **§12:** Q1 (minSdk) is answered as **35/36**. Remove it or convert it to a confirmation.
9. **decisions.md:** record the backgrounding design, SDK levels, distribution channel and i18n rule once the user confirms.

## Open user questions

1. **Backgrounding:** accept `shortService` in v1, with a ≤150 s background budget, then Paused + Continue? Or no FGS (the stream dies about 10 s after leaving)?
2. **Play AI-report requirement vs D1:** if Play is ever a target, is a minimal report endpoint acceptable? Options: a form POST, opt-in, sending only the reported turn. Or will GlyphWizard stay off Play?
3. **Distribution v1:** GitHub Releases + Obtainium only? Add the F-Droid SDK-free flavour (no LEDs)?
4. **Nothing email (D18):** add the four explicit questions above (bundling in an OSS APK, GitHub Releases, Play, public fetch script).
5. **Developer verification:** register in the Android Developer Console (full, $25 + ID) before the 2027 global rollout, or rely on the users' advanced flow / ADB?
6. **minSdk 35 / targetSdk 36:** confirm.
7. **i18n:** English-only UI with multilingual chat in v1, and which 1–2 non-English languages the user can judge for evals?

## Sources

Platform
- AOSP Cached Apps Freezer: https://source.android.com/docs/core/perf/cached-apps-freezer
- Processes and app lifecycle: https://developer.android.com/guide/components/activities/process-lifecycle
- FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types
- FGS timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
- FGS background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- UIDT: https://developer.android.com/develop/background-work/background-tasks/uidt
- Notification permission: https://developer.android.com/develop/ui/views/notifications/notification-permission
- Android 15 behaviour changes (target 35): https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 behaviour changes, all apps: https://developer.android.com/about/versions/16/behavior-changes-all
- Android 16 behaviour changes, target 36: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behaviour changes, target 37: https://developer.android.com/about/versions/17/behavior-changes-17
- Android 17 behaviour changes, all apps: https://developer.android.com/about/versions/17/behavior-changes-all
- Android 17 release notes: https://developer.android.com/about/versions/17/release-notes
- Local network permission: https://developer.android.com/privacy-and-security/local-network-permission
- Android 17 stable 16 Jun 2026 [S]: https://en.wikipedia.org/wiki/Android_17
- dontkillmyapp (no Nothing entry): https://dontkillmyapp.com/ (and /nothing → 404)

Play
- Developer Program Policy (AI-Generated Content, in-app reporting): https://support.google.com/googleplay/android-developer/answer/17190352
- Understanding the AI-Generated Content policy (scope): https://support.google.com/googleplay/android-developer/answer/14094294
- AI content guidance: https://support.google.com/googleplay/android-developer/answer/13985936
- Enabling safe AI experiences (Jun 2024): https://android-developers.googleblog.com/2024/06/enabling-safe-ai-experiences.html
- FGS requirements on Play: https://support.google.com/googleplay/android-developer/answer/13392821
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- New personal account testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- OpenAI BYOK forum thread [S]: https://community.openai.com/t/openais-bring-your-own-key-policy/14538

Distribution
- Developer verification: https://developer.android.com/developer-verification
- Verification FAQ: https://developer.android.com/developer-verification/guides/faq
- Open-source app registration: https://developer.android.com/developer-verification/guides/open-source-app-registration
- F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- F-Droid Anti-Features: https://f-droid.org/docs/Anti-Features/
- F-Droid Reproducible Builds: https://f-droid.org/docs/Reproducible_Builds/
- F-Droid AI policy forum thread [S]: https://forum.f-droid.org/t/does-f-droid-have-a-formal-policy-on-libre-ai/33279
- GPTMobile on F-Droid: https://f-droid.org/packages/dev.chungjungsoo.gptmobile/
- IzzyOnDroid App Inclusion Policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
- Example of a dev skipping Izzy/F-Droid for GitHub + Obtainium [S]: https://github.com/t-seki/kikidame/issues/123
- Obtainium: https://github.com/ImranR98/Obtainium
- Glyph Matrix Developer Kit (+ LICENSE.md): https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

Devices
- 4a Pro launch (Android 16 / NOS 4.1) [S]: https://www.notebookcheck.net/Nothing-Phone-4a-Pro-launches-with-Glyph-Matrix-144-Hz-AMOLED-and-140x-zoom-alongside-new-Phone-4a.1243156.0.html
- Phone (3) update policy / launch OS [S]: https://9to5google.com/2025/06/18/nothing-phone-3-will-get-five-years-of-android-updates/ · https://phonelifespan.tech/phones/nothing-phone-3
- Nothing OS 4.0 (Android 16) on Phone (3) [S]: https://www.gsmarena.com/nothing_os_40_based_on_android_16_is_now_rolling_out_to_the_nothing_phone_3-news-70401.php

i18n
- Ticket-Bench: https://arxiv.org/html/2509.14477
- Luo et al., Lost in execution (abstract): https://arxiv.org/abs/2601.05366
- MASSIVE-Agents: https://aclanthology.org/2025.findings-emnlp.1099.pdf
