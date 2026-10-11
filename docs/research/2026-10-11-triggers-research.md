# Triggers & Glyph Toy Research

2026-10-11. Covers the **Triggers** sub-project (D21: v1 must-have) and whether to ship a **Glyph Toy** for the Phone (4a) Pro. It builds on `decisions.md` (D7, D12, D18, D21, D23) and `2026-10-11-targeted-android-platform.md` (minSdk 35 / targetSdk 36, backgrounding, distribution via GitHub Releases + Obtainium).

Tags:
- **[V]** = verified against a primary source this session (Android docs, AOSP source, the GDK README or SDK binary, or project code I read)
- **[S]** = secondary source: a third-party app's code or comments, an issue thread, a review or a forum post. Code is good evidence that something was *attempted*, but weaker evidence that it *works*.
- **[I]** = my inference from verified facts
- **[U]** = unverified; must be checked on hardware

The repos I studied were cloned at their HEAD on 2026-10-11. File paths are given relative to each repo.

---

## TL;DR and recommendations

1. **The `NotificationListenerService` (NLS) is the runtime. You don't need a foreground service for alerts.**
   - The system binds every enabled listener with `BIND_FOREGROUND_SERVICE` [V: AOSP `NotificationManagerService`].
   - That puts our process in the *bound foreground service* (BFGS) state. A BFGS process isn't cached, so it isn't frozen. Its partial wake locks are still honoured in Doze, because Doze only disables wake locks for processes ranked below BFGS [V: AOSP `PowerManagerService`]. It may also start services, including foreground services [V: AOSP `ActiveServices`/`OomAdjuster`; Android docs list "notification listener" as making an app count as foreground].
   - So the listener can call `setAppMatrixFrame` itself, holding a wake lock for one playback. Glyphifier does exactly this and measured `BFGS` + standby bucket EXEMPTED [S].
   - Keep a `specialUse` FGS as a fallback only, in case 4a Pro testing shows kills.
2. **Use the system's priority order; don't fight it.**
   - Nothing's published priority diagram puts "Third-Party App Usage (GDK)" **below** Glyph Flashlight, the previews, Contact Identifier, Phone (3) toys, Notification and Essential Recording Light. It puts it **above** Flip to Glyph, the Volume indicator, NFC effects, **Essential Notification** and the **AOD Toy** [V: GDK `image/23111_25111_priority.svg`].
   - Reverse engineering on a Phone (3) found fixed per-light "ordinals": app frames (light 123) always lose to the ringtone light (103) and the notification light (102). **Re-sending frames on a timer can't win** [S: Glyphifier `CLAUDE.md`].
   - Design for "our alert plays after Nothing's glyph", which is what Glyph Museum does [S]. Tell users about the *Sound only* settings.
3. **Detect calls through notifications first. No extra permissions.**
   - Dialers post `CATEGORY_CALL` / `CallStyle` notifications with `EXTRA_CALL_PERSON` (API 31) [V]. Combined with `EXTRA_CALL_TYPE` and `AudioManager.getMode() == MODE_RINGTONE`, this tells you a call is ringing without `READ_PHONE_STATE` or `READ_CONTACTS` [S: Glyphifier, measured].
   - **Avoid `CallScreeningService` in v1.** The role is exclusive. Its callback must answer within 5 s. Contacts are excluded from it unless you hold `READ_CONTACTS` [V].
4. **Pick contacts without `READ_CONTACTS`.** Use the system contact picker (`ACTION_PICK`) to get a one-shot grant [S: Glyphifier], and move to the Android 17 Contact Picker later [V]. Play's upcoming Contacts policy (27 Jan 2027) pushes apps that way too [S].
5. **Rule model: copy Glyph Museum's Alerts UX and keep it small.**
   - Trigger filters combine with AND across categories and OR within one: apps, contacts, keywords.
   - Show it for a count of loops or seconds, or *until unlock*.
   - Two switches: *High priority* and *Respect DND*.
   - One alert plays at a time. Bursts play in sequence. High priority jumps the queue [S: Glyph Museum guide].
   - Add Glyphifier's queue rules: calls jump the queue, entries expire, the queue is bounded, re-posts are deduped [S].
6. **Face-down: Nothing exposes no flip-state API** [V: absent from GDK]. Use a **one-shot gravity-sensor read when the event fires**, as a condition. Don't monitor continuously in v1.
   - A Phone (3) system build reportedly **suppresses the panel when the phone is face-up or pocketed** (`mScreenUpward`, `mPocketMode`) [S]. If the 4a Pro does the same, "face-down" is partly enforced for us already. **Test this first** (§8).
7. **Glyph Toy for the 4a Pro: not in v1. Plan it as v1.1** (about 1–2 days).
   - It's the only always-on surface and has the *lowest* priority, so our own alerts preempt it automatically [V].
   - It doubles the SDK-integration pitfalls: one process-wide `GlyphMatrixManager` callback, two write channels, and toy-wake bugs [S].
   - Evidence suggests toys aren't limited to 1 update/min. `EVENT_AOD` is a minute tick, and apps push frames between ticks [S].
8. **Warm binding is mandatory for latency.**
   - Measured by Glyphifier: a warm `GlyphMatrixManager` bind costs **5 ms**. A cold bind after `unInit()` **didn't connect within 3 s** and dropped a call alert [S].
   - Bind once when the listener connects and stay bound.
   - Pre-bake each rule's animation when the rule is saved. At trigger time, only bake templates whose inputs come from the event (D7).
   - Budget: event → first frame < 150 ms, to be measured.
9. **Privacy:**
   - Notification text is matched in memory and thrown away.
   - Persist only rules, plus an opt-in, capped, content-free event log.
   - No notification content in release logcat. The agent never sees any of it (D12).
   - Android 15 already redacts OTP notifications for untrusted listeners [V], so keyword rules won't match OTPs. That's a feature: say so in the UI.
10. **Sideloading caveat:** on Android 13+, *Restricted settings* blocks Notification access for apps sideloaded through a non-session installer, and Android 15 widened it. Users then need **App info → ⋮ → Allow restricted settings** [S].
    - Whether Obtainium installs count as "session-based" is **[U]**: test both a browser-downloaded APK and an Obtainium install.
    - Onboarding must explain the steps.

---

## 1. How existing apps do glyph alerts (prior art and lessons)

### 1.1 Projects studied

| Project | Device | Event source | Runtime | Notable |
|---|---|---|---|---|
| **GlyphMaps** (capad-xyz) | 4a Pro | NLS filtered to the Google Maps package + `CATEGORY_NAVIGATION` (Android 16 `ProgressStyle` Live Update) | NLS → `startForegroundService` → `specialUse` FGS **only while navigating**; 20 s idle watchdog; `START_NOT_STICKY` | `requestRebind` in `onListenerDisconnected`; replays active notifications on connect; `closeAppMatrix()` + `unInit()` in `onDestroy` "hands matrix back" [V: code] |
| **Glyph-Matrix-Notifications** (toberfasanok) | Phone (3) | NLS; contact from `EXTRA_PEOPLE_LIST` → MessagingStyle sender → `android.messages` → title | Plain `startService` from the NLS + `PARTIAL_WAKE_LOCK` per show; **AccessibilityService just to detect screen-on/unlock** | Hard-codes `DEVICE_23112`; `QUERY_ALL_PACKAGES`; `START_REDELIVER_INTENT` [V: code] |
| **Glyph-Matrix-Calls** (toberfasanok) | Phone (3) | `CallScreeningService` (role request) + `TelephonyCallback` for answered/ended; `READ_CONTACTS` + `PhoneLookup` for the name | Same pattern | Needs the exclusive call-screening role and 2 dangerous permissions [V: code] |
| **Glyphifier** (AhmedYasser5) | Phone (3) + 4a Pro (13×13 untested) | NLS only. Calls via CallStyle / `EXTRA_CALL_TYPE` / audio mode. Contacts via system picker | NLS-hosted `PlaybackCoordinator`, wake lock per playback, no FGS, warm ref-counted binding | Most rigorous prior art. Rules matched by specificity, bounded expiring queue, conflict reader. Long `CLAUDE.md` engineering log [V: code + doc] |
| **glyph-gif-rotator** (ChichoXD) | Phone (3) | Screen/unlock/battery broadcasts, media sessions, optional NLS, Usage access | `specialUse` FGS, `START_STICKY`, boot receiver, battery-optimisation exemption | Steps aside 6 s after any non-silent notification; polls usage stats to yield to Glyph Museum; shared single `GlyphMatrixConnection` [V: code] |
| **ClipMatrix** (Sukhendu2002) | 4a Pro | Toy service + NLS + proximity/significant-motion sensors | **AOD Glyph Toy** pushing `setMatrixFrame` between `EVENT_AOD` ticks (puppy animation, music waveform) | Shows a toy can animate on the 4a Pro [S: code, not hardware-verified by me] |
| **GlyphSense** (mohitsharma16) | Phone (3) + 4a Pro | NLS → in-memory store | Toy renders pending notifications when activated (auto-cycle on 4a Pro) | `QUERY_ALL_PACKAGES` [V: code] |
| **Glphy-Nothing** (manttii) | claims 4a Pro | NLS + `ACTION_PHONE_STATE` broadcast (`EXTRA_INCOMING_NUMBER`) | `microphone`-type FGS | README contradicts the code (says Phone 1/2). Mic FGS type for triggers is a misuse [V: code] |
| **GlyphFlow** (ThePrabhnoor) | Phone (3) | n/a | n/a | Repo has only a README and LICENSE [V] |
| **Glyph Museum 2.0** (pauwma, closed source) | Phone (3) + 4a Pro | NLS ("Matched on-device", "Works app-closed") | "the same lightweight service the Glyph effects use" | See §1.2 [S: official guide] |

GDK repo issues read: #3, #6, #8, #12, #14, #16, #17. Example-project issues read: #5.

### 1.2 Glyph Museum 2.0 Alerts (the UX bar to meet)

From the official guide at glyphmuseum.com/guides/nothing-phone-glyph-alerts-guide [S]:

- **Trigger filters:** Apps, Contacts ("when a sender's name shows up", whatever the app) and Keywords (case-insensitive, *newest message only*).
  - Filters combine with AND across categories and OR within one. An empty filter is ignored.
- **Design and style:** any frame, static or animated. Speed, loop direction (forward/reverse/ping-pong), rotate, invert, flip.
- **Show on matrix:**
  - **Count:** loops for animations, seconds for static art; then it auto-clears.
  - **Until unlock:** "If you're already using the phone it flashes briefly instead". Swiping the notification away also clears it.
- **Behaviour:**
  - **High priority:** "wins ties, interrupts" an alert that's already showing.
  - **Respect Do Not Disturb:** when on, the alert stays dark during DND.
- **Notes:**
  - "Plays after Nothing's glyph… then your alert takes over. No clash."
  - "One at a time. A burst plays in sequence, and a high-priority alert jumps the line."
  - "Nothing runs when no alert is on screen."
- Free tier: 2 alerts.
- Separate "Trigger Effects": Unlock, Lock and Charging (1–5 s or 1–5 loops), Shake, Torch (on while lit) and Volume. "Each one can only be active on one Frame at a time" [S: effects guide].
- 2.0 moved to a single rendering engine with a **20 ms minimum frame time** (down from 50 ms). "Always-on Glyph Toys just work now. Grant battery access once and your animations keep running, no more frozen frames" [S: pauwma, Nothing Community post 299391]. This implies AOD toys do animate between minute ticks, and that freezing was a real problem.
- **Licensing note:** Glyph Museum designs belong to their authors. The ToS (glyphmuseum.com/developers §3, ToS 5.4) requires attribution and a back-link, an untouched meta block, and no bundling or scraping [S: pauwma in glyph-gif-rotator #1]. This matters if GlyphWizard ever imports from Glyph Museum.

### 1.3 Mistakes and lessons (from code, comments and issues)

| # | Lesson | Evidence |
|---|---|---|
| L1 | **`GlyphMatrixManager` is a process-wide singleton with one callback.** A second `init()` silently replaces the first caller's callback, and that caller never connects. Use one shared, ref-counted connection object. | glyph-gif-rotator `GlyphMatrixConnection.kt`; Glyphifier `CLAUDE.md` [S]. SDK strings show `getInstance`, `mCallback`, `newSingleThreadExecutor` [V: AAR] |
| L2 | **`init()` after `unInit()` in the same process may never reconnect.** Bind once per process lifetime, and don't `unInit()` between alerts. Release the *display* with `closeAppMatrix()` instead. | rotator comment; Glyphifier measured a 3 s timeout vs a 5 ms warm bind [S] |
| L3 | **Register the right device constant at runtime** (`Common.is25111p()` → `DEVICE_25111p`). A wrong constant returns `false` or no-ops silently: "the app works, the back stays dead". Note GDK issue #17: "No `DEVICE_25111p`" in an older SDK build. | rotator; Glyphifier; GDK #17 [S] |
| L4 | **Two apps writing `setAppMatrixFrame` are indistinguishable**, and the SDK has no "who owns the panel" query. GlyphMaps README: "grant Notification Access to only one Glyph-Matrix app at a time — two listeners will fight." Glyphifier found **four** rival listeners on the developer's own phone. Another holder can stop `init()` from ever calling back (rotator comment on Glyph Museum). | GlyphMaps README; Glyphifier; rotator [S] |
| L5 | **System lights outrank the app channel permanently.** During a ringing call, `selected id:103` (ringtone) appeared 99×, and app light 123 only showed after ringing stopped. The fix is user settings: *Glyph Interface → Ringtones → Sound only* and *Notifications → … Sound only*. Third-party VoIP ringtones (WhatsApp etc.) light the panel through the audio framework with no off switch. | Glyphifier `CLAUDE.md`, measured on Phone (3) [S]. Consistent with the official priority diagram [V] |
| L6 | **Nothing's own notification glyph is gated by `!isScreenOn`** (and `!isInCall`, `!soundOnly`, importance). So system clashes mostly happen when the screen is off. | Glyphifier, dexdump of `nt-services.jar` [S] |
| L7 | **Face-up or pocketed phones may not light at all** (`mPocketMode`, `mScreenUpward`). | Glyphifier, Phone (3) [S]. **[U] on 4a Pro** |
| L8 | **Don't hog the panel.** Release on end-of-event, add an idle watchdog, and use `START_NOT_STICKY`. A `START_STICKY` resurrection "was re-claiming the matrix forever". | GlyphMaps `GlyphRenderService.kt` [S] |
| L9 | **Answering a call doesn't remove its notification.** It's updated in place. Treat "stopped ringing" as the end signal and cap it at 60 s. | Glyphifier [S] |
| L10 | **`fullScreenIntent` is stripped** for apps without `USE_FULL_SCREEN_INTENT` (restricted since Android 14), so you can't infer ringing from it. Use `EXTRA_CALL_TYPE == CALL_TYPE_INCOMING`, then `AudioManager.getMode() == MODE_RINGTONE`. | Glyphifier, measured [S]; `EXTRA_CALL_TYPE` exists since API 34 [V] |
| L11 | **Dialers post a call more than once under one key.** The first post may show the number; the contact name lands ~57 ms later. Let a rule match on the re-post replace a fallback that is already playing. | Glyphifier, measured [S] |
| L12 | **Messengers decorate the sender** ("Mum (3 messages)"). Normalise the sender title before matching. | Glyphifier [S] |
| L13 | **Apps re-post on every update.** Dedupe per notification key with a cooldown, and skip group summaries, ongoing notifications and your own package. | Glyphifier, rotator `MusicSessionListener.isSilent` [S] |
| L14 | **Screen-state broadcasts can be missed** (service started while the screen was off, Smart Lock not firing `ACTION_USER_PRESENT`). Re-check `PowerManager.isInteractive` and `KeyguardManager.isKeyguardLocked` when deciding. | rotator `syncScreenState` comment [S] |
| L15 | **Don't use an AccessibilityService to detect unlock.** It's Play-restricted, hit by restricted settings, and overkill: a dynamically registered `ACTION_USER_PRESENT`/`SCREEN_ON` receiver inside the NLS process is enough. | toberfasanok uses one [V: code]; Play Accessibility policy [S] |
| L16 | **`QUERY_ALL_PACKAGES` isn't needed** for an app picker. Use a `<queries>` launcher-intent element. It's Play-restricted. | Play package-visibility policy [S] |
| L17 | **The toy "doesn't wake after standby" when it's last in the toy list. Long-press events stop after AOD starts** (later resolved by itself). | GDK #14, #6 [S] |
| L18 | **The matrix turned off after 3–4 min** when driven by `setMatrixFrame` from the example project. The SDK has an **undocumented `setGlyphMatrixTimeout(boolean)`**. | Example-project #5 [S]; method name in SDK 2.0 AAR [V: strings] |
| L19 | **Raw brightness range is disputed.** A Nothing staffer said "the maximum value for each element in the array for `setMatrixFrame(int[] color)` is 2047". The README documents 0–255 for objects. Glyphifier says the system scales the sent values by the user's Glyph brightness. **Affects D6 (0–255)**: measure. | GDK #8 [S: Nothing staff]; README [V] |
| L20 | **App logs prove nothing about the panel.** Verify on the system side: `adb logcat \| grep -E 'NtGlyphServiceImpl: setLightFrame\|invalidateLightStates selected id'`. Glyphifier also reports a ~25 fps panel ceiling on the Phone (3) [U on 4a Pro]. | Glyphifier [S] |
| L21 | Glyphifier's `PanelConflicts` reads only no-permission settings (`Settings.System` ringtone URIs, `enabled_notification_listeners`) to **explain** conflicts to the user. It never writes them. That's a good v1.x feature. | Glyphifier [S] |

---

## 2. Event sources on Android 15/16

### 2.1 NotificationListenerService

**Verified facts:**
- **Declaration:** a service protected by `BIND_NOTIFICATION_LISTENER_SERVICE` with the `android.service.notification.NotificationListenerService` action.
- **Optional `meta-data` for filters:**
  - `default_filter_types` sets the initial filter (`conversations|alerting|ongoing|silent`).
  - `disabled_filter_types` hides types the user can't enable.
  - `default_autobind` = `false` means the listener is bound on demand via `requestRebind`.
- **Lifecycle rules:**
  - Wait for `onListenerConnected()`.
  - After `onListenerDisconnected()`, only `requestRebind(ComponentName)` is safe to call. It "will fail for listeners that have not been granted the permission".
  - Listeners in a work profile are ignored.
- **Android 15 OTP redaction:** untrusted listeners can't read unredacted content of notifications where an OTP is detected [V: Android 15 behaviour changes].

  **Implication [I]:** keyword rules can't match OTP texts, and we shouldn't try.

**Permission flow:**
- Notification access is a *special access*, not a runtime permission. Send the user to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, or on API 30+ to `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` for our component. Then check with `NotificationManager.isNotificationListenerAccessGranted(ComponentName)`.
- [U] The exact Nothing OS screens.
- Sideloaded apps hit *Restricted settings* first (TL;DR #10).

**Filtering per app, conversation or contact.** Fields to read from `StatusBarNotification`/`Notification` [V: API reference]:

| Need | Field |
|---|---|
| App | `sbn.packageName`; `sbn.user` |
| Dedupe / identity | `sbn.key`, `sbn.postTime`, `notification.flags` (`FLAG_GROUP_SUMMARY`, `FLAG_ONGOING_EVENT`), `sbn.isOngoing` |
| Conversation | `notification.shortcutId` (`getShortcutId()`), `EXTRA_CONVERSATION_TITLE`, `EXTRA_IS_GROUP_CONVERSATION` |
| Sender / contact | `EXTRA_PEOPLE_LIST` (`ArrayList<Person>`, API 28): `Person.name`, `Person.uri` (may be `tel:`, `mailto:` or a contacts lookup URI), `Person.key`. MessagingStyle via `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification` → last `Message.person`. Fallback `EXTRA_TITLE` |
| Call | `category == CATEGORY_CALL`, `EXTRA_CALL_PERSON` (API 31), `EXTRA_CALL_TYPE` (API 34) |
| Text (keywords) | `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, newest `EXTRA_MESSAGES` entry |
| Importance / DND | `NotificationListenerService.Ranking` (`getCurrentRanking()`): `importance`, `matchesInterruptionFilter()`; listener-level `getCurrentInterruptionFilter()` and `onInterruptionFilterChanged` |

**Contact matching [I/U]:**
- Messaging apps differ in what they put in `Person.uri`. Phone-number URIs need normalisation, e.g. `PhoneNumberUtils.areSamePhoneNumber` or E.164.
- v1 matches a contact by any of:
  - normalised sender display name
  - phone numbers from the picked contact
  - lookup key from the picked contact
- Log which field matched (debug build only).
- **Hardware/app test:** WhatsApp, Signal, Google Messages, Telegram.

**Reliability:**
- The system rebinds after a crash. `ManagedServices` has `ON_BINDING_DIED_REBIND_DELAY_MS = 10000` [V: AOSP].
- Listeners use `BIND_NOT_PERCEPTIBLE` "because too many 3P apps could be kept in memory as notification listeners" [V: AOSP comment]. So under memory pressure we can be killed and rebound.
- GlyphMaps calls `requestRebind` from `onListenerDisconnected` and replays `activeNotifications` on connect [V: code]. **Copy both.**
- "Until unlock" alerts must survive a rebind: persist "pending until-unlock alert" (rule id + notification key only).

### 2.2 Incoming calls

| Option | Permissions | Caller identity | Problems |
|---|---|---|---|
| **A. NLS CallStyle notification** (recommended v1) | none extra | `EXTRA_CALL_PERSON.name`, or the title (dialer already resolved the contact) | Dialer-specific; first post may show a number only (L11); ringing must be inferred (L10) [S] |
| B. `TelephonyCallback.CallStateListener` | `READ_PHONE_STATE` (dangerous, runtime) | **No number** (the number moved to `READ_CALL_LOG` paths) [I/U: the API has no number parameter] | Gives reliable ringing/offhook/idle for *cellular* calls only, not VoIP. Good as an *end-of-ring* signal later [V: API exists] |
| C. `CallScreeningService` | `ROLE_CALL_SCREENING` (exclusive, user-granted) | `Call.Details.getHandle()` number. **Contacts are excluded unless you hold `READ_CONTACTS`** | Must `respondToCall` within 5 s, or ringing is delayed. One role holder, so it competes with the user's spam app. Bound only briefly. Cellular `tel:` only [V: API reference] |
| D. `ACTION_PHONE_STATE` broadcast | `READ_PHONE_STATE` (+`READ_CALL_LOG` for the number) | number | Legacy; Glphy uses it [V: code] |

**Recommendation:**
- Use A in v1, gated by L9–L11.
- Add B only if hardware testing shows the ringing inference failing on the stock Nothing dialer.
- Don't use C: it's a role grab for a cosmetic feature.

### 2.3 Other conditions (no special permissions needed)

| Condition | Source | Notes |
|---|---|---|
| Screen on/off, unlocked | `ACTION_SCREEN_ON/OFF`, `ACTION_USER_PRESENT`, registered **dynamically** in the long-lived NLS process. Plus `PowerManager.isInteractive`, `KeyguardManager.isKeyguardLocked` checked when deciding | Manifest receivers for these are disallowed since Android 8 [V: background limits]. Smart Lock caveat L14 |
| Charging / battery | Sticky `ACTION_BATTERY_CHANGED`; `ACTION_POWER_CONNECTED/DISCONNECTED` (dynamic) | — |
| Time window / quiet hours | Evaluate the wall clock when the event arrives. No alarms needed, because we only *gate* events. | [I] No `SCHEDULE_EXACT_ALARM` needed in v1 |
| DND | `NotificationListenerService.getCurrentInterruptionFilter()` / `Ranking.matchesInterruptionFilter()` | Per-rule "Respect DND" (Glyph Museum) |
| Face-down | §3 | — |
| Battery saver | `PowerManager.isPowerSaveMode` | Glyphifier gates on it [S] |

### 2.4 Permission sensitivity

| Permission / access | Needed in v1? | GitHub/Obtainium | Play (if ever) |
|---|---|---|---|
| `com.nothing.ketchum.permission.ENABLE` | yes | fine | fine |
| Notification access (NLS) | **yes** | **Restricted settings** step on sideload [S] | No formal declaration form found. GlyphMaps prepared an in-app prominent disclosure + console declaration [S: GlyphMaps `PUBLISHING.md`] [U] |
| `WAKE_LOCK` | yes | normal | normal |
| `POST_NOTIFICATIONS` | no (only if we add an FGS and want it visible) | — | — |
| `FOREGROUND_SERVICE_SPECIAL_USE` | fallback only | fine | Declaration + justification + video [S/V: platform research] |
| `READ_PHONE_STATE` | no (option B later) | runtime prompt | Not on Play's restricted list [S] |
| `READ_CONTACTS` | **no**: use the picker | — | Play Contacts policy from 27 Jan 2027 favours the Contact Picker [S] |
| `ROLE_CALL_SCREENING` | no | — | — |
| `QUERY_ALL_PACKAGES` | **no**: use `<queries>` | — | Restricted [S] |
| AccessibilityService | **no** | restricted settings | Restricted [S] |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | not expected (the NLS is already BFGS/EXEMPTED [S]) | fine | Restricted to acceptable use cases [U: from memory, not re-checked] |
| Usage access | no | restricted settings (Android 15) [S] | — |

---

## 3. Face-down, system priority, screen-off behaviour

### 3.1 Flip state

- **Nothing exposes no flip-state API.** The GDK README and SDK 2.0 have no flip query or event [V].
- The system tracks something (`mScreenUpward`, `mPocketMode`) [S, Phone (3)], but it isn't readable by apps [I].
- No public `Settings` key for "Flip to Glyph" was found [V: search found none].

**Sensor approach:**
- Use `TYPE_GRAVITY` (fused) or `TYPE_ACCELEROMETER`. Face-down ≈ gravity z ≈ −9.8 m/s² (screen facing the table). Optionally add `TYPE_PROXIMITY` (near).
- Proximity and significant motion are traditionally **wake-up** sensors. Most others are **non-wake-up**: their events sit in a hardware FIFO while the SoC sleeps, unless an app holds a partial wake lock [V: AOSP sensors suspend-mode].
- **v1 design:** when an event fires, we already hold a playback wake lock. Register the gravity listener, take the first 1–3 samples (~tens of ms), then unregister.
- Battery cost is negligible, because there's no continuous sensing [I].
- ClipMatrix uses wake-up proximity + significant motion this way [S].

**Not in v1:** "phone *placed* face-down" as a *trigger*. It needs continuous sensing: batched accelerometer with `maxReportLatencyUs`, or a significant-motion wake-up followed by sampling. It costs battery and needs a hardware power measurement.

### 3.2 Priority and etiquette

**Official order** (high → low) [V: GDK priority SVG]:
1. Glyph Flashlight
2. Preview (Glyph brightness)
3. Camera Countdown Preview
4. Contact Identifier
5. Preview (Notification/Ringtone)
6. Toy (GDK, Phone 3 only; "time out or inactivated")
7. Notification
8. Essential Recording Light Effect
9. **Third-Party App Usage (GDK)**
10. Flip to Glyph
11. Volume Indicator
12. NFC Triggered Light Effects
13. Essential Notification
14. **AOD Toy (GDK)**

**Consequences [I]:**
- Our `setAppMatrixFrame` **overrides Essential Notification, Flip to Glyph and any AOD toy**, including our own future toy, while we hold the panel.
- Our frames are **hidden under** Nothing's per-app Notification glyph, ringtone/Contact Identifier, flashlight and Phone (3) toys.
- README: "If the user interacts with the Glyph Button, the Glyph Toy carousel it triggers will override your app's content" [V].

**Etiquette:**
- Call `closeAppMatrix()` the moment an alert ends.
- Never keep a frame lit without an end condition. "Until unlock" gets a hard cap, e.g. 30 min [I].
- Don't use `setMatrixFrame` from the app path. The README says it "may conflict with active Glyph Toys" [V].

**Two listener apps fighting:**
- Real and undetectable (L4). Mitigate in UI.
- During onboarding, read `Settings.Secure "enabled_notification_listeners"` without permission and list other known Glyph apps [S: Glyphifier does this; readability on Android 16 is **[U]**].
- Tell the user they may fight.

### 3.3 Screen off / locked

- The GDK README is silent on screen-off behaviour of `setAppMatrixFrame` [V: absent].
- Evidence that it works with the screen off:
  - GlyphMaps (phone flipped, screen off, 4a Pro) [S]
  - Glyphifier ("rules only fire with the screen off by default") [S]
  - toberfasanok [S]
- Nothing's notification glyph only plays with the screen off (L6) [S].
- Glyph Museum "until unlock" flashes briefly when the phone is already unlocked [S]. Copy that.

---

## 4. Rule engine design (lean v1)

Prior art: Tasker and MacroDroid's **trigger → constraints → actions** with priority; Glyph Museum's filter/show/behaviour panels; Glyphifier's specificity order and queue.

### 4.1 Rule model

```
Rule {
  id, name, enabled, createdAt
  trigger: one of
    Notification { apps: [pkg]?, contacts: [ContactRef]?, keywords: [string]?, conversationsOnly: bool }
    Call         { contacts: [ContactRef]? }           // ringing incoming call, via NLS
    Unlock | ChargingStarted | BatteryBelow(pct)        // simple system events
  conditions {                                          // all must hold
    screen: Any | OffOnly | LockedOnly                  // default OffOnly for notifications
    timeWindow: { start, end, days }?                   // "only between"
    respectDnd: bool                                    // default true; Call default false
    faceDownOnly: bool                                  // one-shot gravity read
  }
  action {
    animationId                                         // GlyphWizard document
    inputs: { param -> Binding }                        // D7 templates; see 4.4
    show: Loops(n) | Seconds(s) | UntilUnlock(capMin) | WhileRinging(capS)
  }
  priority: Normal | High
}
ContactRef { displayName, phoneNumbers[normalised], lookupKey? }   // from system picker
Global { masterEnabled, quietHours?, perAppCooldownS = 20, maxQueue = 6 }
```

- Filters combine with AND across categories and OR within one. An empty filter means "any" [S: Glyph Museum].
- Keywords are case-insensitive and checked against the newest message only.

### 4.2 Choosing a rule for an event

1. Gate, cheap checks first [S: Glyphifier order]: own package / group summary / ongoing (except calls) → master switch → quiet hours → per-key dedupe and cooldown.
2. Candidates = rules whose trigger and conditions match.
3. Pick **one** rule: High before Normal, then the **most specific** (contact > keyword > app > any), then the newest rule.

   Specificity means users never reorder lists [S: Glyphifier]. The High flag is the Glyph Museum escape hatch.
4. If nothing matches, do nothing. Glyphifier's "generated default glyph per app" is a nice-to-have, not v1.

### 4.3 Arbitration of simultaneous events (single player, single queue)

- **One alert on the panel at a time.**
- **Normal** events queue (FIFO). **Calls and High** go to the queue head.
- **High preempts** a playing Normal alert, as in Glyph Museum. A call never preempts another call.

  Glyphifier deliberately preempts nothing [S]. Pick Glyph Museum's behaviour for High only; that's an open question for the user.
- **Re-posts** of the same key replace their queued entry in place.
- **Expiry:** 30 s for messages, 10 s for calls.
- **Bounded queue (6):** drop the oldest Normal message first.
- **Coalescing:** if several queued entries resolve to the *same rule*, play once [I].
- **Rate limit:** per app 1 alert / 20 s by default (Glyphifier floor). Global: no more than N alerts per minute [I].
- **End conditions:**
  - notification removed
  - user unlock (for UntilUnlock)
  - stopped ringing (L9)
  - hard cap
  - kill switch, which **clears the panel** (Glyphifier: cancelling the job leaves the last frame lit)
- **System-glyph overlap:**
  - Option 1: start after a short delay so Nothing's glyph plays first.
  - Option 2: loop "UntilUnlock" alerts so they show once the system light closes.
  - The delay value is **[U]**: measure on the 4a Pro.

### 4.4 Templates at trigger time (D7)

- `render(doc, t, inputs)` runs when the event arrives *only if* some input is event-bound. Otherwise the baked frames are cached when the rule is saved.
- Allowed v1 bindings are deliberately small and **content-free**:
  - constant
  - `event.kind`
  - battery %
  - time (HH:MM)
  - pending-count for the app
  - contact *slot* (an index the user assigns, not the name)
- Rendering a sender's name as matrix text is possible but leaks content onto a back panel. Make it opt-in and leave it out of v1 [I].
- Cost estimate [I]: 5 s at 20 ms = 250 frames × 169 cells ≈ 42 k values. That's milliseconds of Kotlin. Verify with a JVM microbenchmark in the core module.

---

## 5. Runtime

### 5.1 Process model (verified from AOSP main; Nothing OS may differ, so test)

| Fact | Source |
|---|---|
| NLS bind flags: `BIND_AUTO_CREATE \| BIND_FOREGROUND_SERVICE \| BIND_NOT_PERCEPTIBLE \| BIND_ALLOW_WHITELIST_MANAGEMENT` | AOSP `NotificationManagerService.NotificationListeners.getBindFlags()` [V] |
| A persistent client binding with `BIND_FOREGROUND_SERVICE` raises the bindee to `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` and propagates the `PROCESS_CAPABILITY_BFSL` capability | AOSP `OomAdjuster` [V] |
| FGS start is allowed when procstate ≤ BFGS and the uid has BFSL | AOSP `ActiveServices.shouldAllowFgsStartForegroundNoBindingCheckLocked` [V] |
| Docs: an app is "in the foreground" (may start services) if the system binds its notification listener | Background execution limits [V] |
| Doze: partial wake locks are disabled only for uids with procstate > BFGS (and not allowlisted) | AOSP `PowerManagerService.setWakeLockDisabledStateLocked` [V]. The Doze docs simply say "ignores wake locks", so the docs are less precise than the code |
| Cached-app freezer applies to *cached* processes only. BFGS isn't cached | Platform research (AOSP freezer) [V] |
| Glyphifier measured `BFGS` and standby bucket 5 (EXEMPTED). "a foreground service would buy nothing" | [S] |

**Design [I]:**
- `GlyphWizardListener` (the NLS) owns:
  - a `TriggerEngine` (pure-Kotlin matcher and queue in a core module, unit-testable; D14/D20 spirit)
  - a `Player` (coroutine that ticks baked frames into a `MatrixSink`)
  - the shared `GlyphConnection` (bound in `onListenerConnected`, never `unInit` while connected)
  - the dynamic receivers
- Each playback takes a `PARTIAL_WAKE_LOCK` with timeout = duration + 2 s. Release it in `finally`.
- **No FGS in v1.** Fallback if testing shows kills or throttling: start a `specialUse` FGS for the playback duration (allowed from BFGS). That costs `POST_NOTIFICATIONS` for visibility plus a Play declaration.
- The editor's live preview on the LEDs is separate (Activity-driven). It must use the *same* `GlyphConnection` (L1), and the trigger player must yield to it or pause.

**Timing:**
- Use `delay()` on a dedicated single-thread dispatcher, with drift-corrected scheduling (absolute deadlines).
- Coalesce, don't stretch, if the panel can't keep up (Glyphifier `FrameSchedule` lesson [S]).
- The SDK itself submits to a single-thread executor [V: AAR strings], so calls are async and ordered.

**Battery [I/U]:**
- Idle cost is the bound NLS (already resident for any listener) plus dynamic receivers: near zero.
- Active cost is CPU at 50 fps for a few seconds, plus LED power.
- Measure with `dumpsys batterystats` / Battery Historian over a 24 h day of ~100 notifications.

### 5.2 Latency budget (event → first frame), to measure

| Step | Budget | Note |
|---|---|---|
| App posts → `onNotificationPosted` | system, unknown | [U] measure with `postTime` vs `SystemClock` |
| Gate + match | < 5 ms | pure Kotlin |
| Bake (only if event-bound inputs) | < 20 ms | [I] |
| Warm bind ready | ~0 (already bound; 5 ms measured [S]) | cold bind ≥ 3 s, so it must stay warm |
| First `setAppMatrixFrame` → LED | unknown | [U] logcat `setLightFrame` timestamps |
| **Target** | **< 150 ms** app-side | — |

---

## 6. Glyph Toy for the 4a Pro

**Facts:**
- The 4a Pro supports **AOD toys only**. Manifest needs `aod_support=1`. The user enables it via *Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy*.
- The toy receives `EVENT_AOD` "every minute".
- The 4a Pro shows a single always-on toy chosen by radio button [V: GDK README + 4a Pro manifest SVG; S: Glyph Museum guide].
- `EVENT_AOD` fires at the start of each minute (issue #3 comment) [S].
- **Toys can push frames between ticks:** ClipMatrix animates a puppy and a music waveform via `setMatrixFrame` on the 4a Pro [S: code]. Glyph Museum 2.0 says AOD toy animations "keep running" after granting battery access [S]. So **1 update/min is a floor, not a ceiling**. The freeze and battery risk is real.
- **Priority:** AOD Toy is the lowest, so our trigger alerts preempt it with no extra code [V].
- `ToysManagerActivity` intent (system ≥ 20250829) sends the user straight to the toy list [V].

**Effort [I]:**
- One exported `Service` with the `com.nothing.glyph.TOY` action and meta-data.
- A 1:1 preview drawable (per-device spec SVG).
- A settings screen to pick a document.
- A playback loop that reuses the baked-frame player but writes through the **toy channel** (`setMatrixFrame`).
- About 1–2 days, plus hardware time.

**Pitfalls:**
- L1: the shared singleton; the toy and the NLS live in one process.
- The toy channel and app channel compete within our own process (rotator `GlyphToyPresence` [S]).
- GDK #14 / #6 wake bugs.
- Freezing (Glyph Museum).

**Value:**
- High for a "design studio": it's the only way to show your design persistently on the back.
- **Not needed for triggers.**

**Recommendation:**
- **v1.1, right after triggers.** v1 is scoped to editor + export + live preview + triggers.
- If included early, limit it to a **static or slow** design: re-render on `EVENT_AOD`, or loop ≤ 1 fps.
- Measure battery before allowing 50 fps loops.

---

## 7. Privacy

| Data | Treatment |
|---|---|
| Notification text, sender, number | **Memory only**, for matching. Never written to disk, logs, crash reports or agent context (D12). Release builds log no content (GlyphMaps practice [S]) |
| Rules (incl. ContactRef names/numbers) | App-private storage. **Exclude from cloud backup** (`dataExtractionRules`) or ask. `.glyphwiz` export contains *animations only*; rules export (if any) strips ContactRefs by default [I] |
| Event log | Off by default. Opt-in ring buffer (~200 entries): timestamp, package, matched rule id, outcome (played / gated / reason). No titles or text [I] |
| Agent | Can't read rules, notifications, contacts or installed apps (D12). It may *design* an animation the user later assigns |
| Network | The trigger path makes no network calls (consistent with D1). The `TriggerEngine` module has no network dependency [I] |

---

## 8. Hardware test list (4a Pro, before committing the design)

1. **Panel visibility vs orientation:** with the screen off, play via `setAppMatrixFrame` face-down / face-up / in pocket (proximity covered). Is face-up suppressed (L7)?
2. **Screen off / locked / AOD on / AOD off:** do frames show? Any auto-off after N minutes (L18)? Does `setGlyphMatrixTimeout(false)` change it?
3. **System overlap:** a notification with Nothing's per-app glyph on, then our alert. What's visible, and when does ours appear? Measure the delay. Repeat with *Sound only*. Repeat with an incoming cellular call and a WhatsApp call.
4. **Essential Notification, Flip to Glyph, AOD toy active:** confirm we override them, and that they come back after `closeAppMatrix()`.
5. **Rival app:** install Glyph Museum (or GlyphMaps) with notification access. Does our `init()` still connect? Who wins?
6. **Process state:** `adb shell dumpsys activity processes | grep -A5 glyphwizard` (expect BFGS) and `am get-standby-bucket`. Run `dumpsys deviceidle force-idle` and check wake lock + playback in deep Doze.
7. **Kill and rebind:** `am kill`, low-memory simulation, force-stop. Does the listener rebind, and does a pending UntilUnlock alert survive?
8. **Latency:** post → first `setLightFrame` (`adb shell cmd notification post …`, logcat timestamps), warm vs cold bind.
9. **Frame rate:** maximum sustainable `setAppMatrixFrame` rate (20 ms target; ~25 fps ceiling claimed on Phone (3)). Check for dropped or stretched frames.
10. **Brightness range:** 0–255 vs 0–2047 raw values (L19); scaling by the user's Glyph brightness.
11. **Calls via the stock Nothing dialer:** CallStyle present? `EXTRA_CALL_PERSON` name? `EXTRA_CALL_TYPE`? `getMode()`? Repost timing (L11).
12. **Contact matching:** WhatsApp, Google Messages, Signal, Telegram. Which of `Person.name`, `Person.uri` and `title` are populated?
13. **Restricted settings:** browser-downloaded APK vs Obtainium install vs `adb install`. Can notification access be granted, and which steps are needed?
14. **Face-down one-shot read:** latency and correctness of the gravity sample with the screen off.
15. **Battery:** a 24 h day with ~100 notifications and 10 alerts (Battery Historian).
16. **(v1.1 toy)** `EVENT_AOD` cadence, frames between ticks, freezing without a battery exemption, coexistence with the NLS in one process.

Debug commands:
- `adb shell cmd notification allow_listener <pkg>/<cls>`
- `adb shell "cmd notification post -t 'title' tag 'text'"`
- `adb logcat | grep -E 'NtGlyphServiceImpl|invalidateLightStates'` [S: Glyphifier]

---

## 9. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| System lights hide our alert (calls, notifications) | High [S] | Alert seen late or not at all | Delay / loop; *Sound only* guidance; conflict explainer (L21) |
| Face-up suppression (if true on 4a Pro) | Medium [S/U] | "Alerts don't work" on a desk | Test #1; UX copy; maybe default to face-down use |
| Another Glyph app with notification access | Medium [S] | Flicker or theft, undetectable | Onboarding check + warning |
| Restricted-settings friction on sideload | High [S] | Setup drop-off | Step-by-step onboarding screen with screenshots |
| OEM kills / rebind gaps | Low–Med [S: no Nothing entry on dontkillmyapp] | Missed alerts | `requestRebind`, replay active notifications, `specialUse` FGS fallback |
| Nothing OS update changes priority, ordinals or SDK behaviour | Medium | Regressions | Keep the SDK behind `:glyph-sdk` (D18); hardware smoke checklist per OS update |
| SDK redistribution permission (D18) | Open | No LED build | Already tracked |
| Rule engine creep (Tasker-like) | Medium | Scope blow-up | v1 = 3 trigger kinds + 4 conditions; no chaining, variables or actions beyond "play" |
| Content leakage via matrix text templates | Low | Privacy | Content-free bindings only in v1 |

---

## 10. Open questions for the user

1. **v1 trigger kinds:** Notification (apps / contacts / keywords) + Call + Unlock/Charging/BatteryLow. Is that enough? Is anything else a must (e.g. "phone placed face-down" as a continuous trigger, Bluetooth connect, alarm)?
2. **High priority preempts a playing alert** (Glyph Museum) **or never preempts, queue only** (Glyphifier)?
3. **Default screen condition:** fire only when the screen is off (like Nothing's own glyph), or always?
4. **"Until unlock" hard cap:** 30 min?
5. **Calls without `READ_PHONE_STATE`** (NLS inference) in v1, adding the permission only if the stock dialer misbehaves. OK?
6. **Glyph Toy:** v1.1 (recommended), or squeeze a static-only toy into v1?
7. **Fallback/default glyph** for unmatched notifications (Glyphifier-style generated patterns): out of v1?
8. **Rules export:** never include contacts, or include them with a warning?
9. **Brightness:** keep D6's 0–255 model and map to whatever raw range the hardware test reveals (L19)?

---

## 11. Learning asides (D23)

> **NotificationListenerService.** A service the *system* binds to your app once the user grants "Notification access" in Settings. Every posted or removed notification is delivered to it with its full `Notification` object (title, text, people, category). It's the standard way for Android apps to react to other apps' notifications, and the system keeps it running for you.
> - https://developer.android.com/reference/android/service/notification/NotificationListenerService
> - https://developer.android.com/develop/ui/views/notifications (notification anatomy, styles)

> **Notification styles, Person and conversations.** Modern messaging notifications use `MessagingStyle`, which carries `Person` objects (name, uri, key) for each sender, and a `shortcutId` that marks it as a "conversation". Call notifications use `CallStyle` with a caller `Person`. Reading these structured fields is more reliable than parsing the title text.
> - https://developer.android.com/develop/ui/views/notifications/build-notification#messaging-style
> - https://developer.android.com/reference/android/app/Person
> - https://developer.android.com/develop/ui/views/conversations

> **Process states, services and foreground services.** Android ranks every process (top, bound-foreground-service, cached…). Cached processes get frozen and killed. A foreground service (FGS) shows an ongoing notification to stay high-ranked. A process the system is *bound to*, like a notification listener, is already ranked high, which is why we don't need an FGS.
> - https://developer.android.com/guide/components/activities/process-lifecycle
> - https://developer.android.com/develop/background-work/services/fgs
> - https://developer.android.com/about/versions/oreo/background

> **Wake locks and Doze.** When the screen is off, the CPU sleeps unless someone holds a *partial wake lock*. Doze is a deeper idle mode that defers jobs, alarms and network, and ignores wake locks from low-ranked apps. Hold a wake lock only for the exact length of an alert and always release it.
> - https://developer.android.com/develop/background-work/background-tasks/awake/wakelock
> - https://developer.android.com/training/monitoring-device-state/doze-standby

> **The permissions model.** Android has (1) *normal* permissions, granted at install (e.g. `WAKE_LOCK`); (2) *dangerous/runtime* permissions, which need a dialog (e.g. `READ_CONTACTS`); (3) *special access* granted only in Settings (notification access, usage access); and (4) *roles*, one app per role (default dialer, call screening). Ask for the weakest one that works.
> - https://developer.android.com/guide/topics/permissions/overview
> - https://developer.android.com/reference/android/app/role/RoleManager

> **Restricted settings (sideloading).** Since Android 13, apps installed outside an app store can't be granted notification or accessibility access until the user taps *Allow restricted settings* in App info. Android 15 extended this. It's anti-malware protection that legitimate sideloaded apps must guide users through.
> - https://support.google.com/android/answer/12623953
> - https://www.androidauthority.com/android-15-restricted-settings-sideloading-3481098/ (secondary)

> **Broadcast receivers.** Apps can listen for system events like `SCREEN_ON`, `USER_PRESENT` (unlocked) and `POWER_CONNECTED`. Most can't be declared in the manifest any more. Register them in code while a long-lived component (our listener) is running.
> - https://developer.android.com/develop/background-work/background-tasks/broadcasts
> - https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions

> **Telephony and call screening.** `TelephonyCallback` reports the cellular call state (idle/ringing/offhook) with `READ_PHONE_STATE`. `CallScreeningService` lets one user-chosen app inspect each incoming call before it rings. We avoid both in v1 by reading the dialer's call notification.
> - https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener
> - https://developer.android.com/reference/android/telecom/CallScreeningService

> **Sensors.** `SensorManager` gives accelerometer, gravity and proximity readings. "Wake-up" sensors can wake a sleeping CPU, while others queue events in a hardware FIFO ("batching"). For "is the phone face-down?" we read the gravity vector once (z ≈ −9.8 m/s²) and stop listening.
> - https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
> - https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
> - https://source.android.com/docs/core/interaction/sensors/suspend-mode

> **Bound services (Glyph Toys).** A Glyph Toy is a `Service` that Nothing's system app *binds* to when the user selects it: `onBind` starts it, `onUnbind` stops it. Events arrive as `Message`s through a `Messenger`. Same idea as the notification listener, but a different binder.
> - https://developer.android.com/develop/background-work/services/bound-services
> - https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit#developing-a-glyph-toy-service

---

## Sources

**Nothing**
- GlyphMatrix Developer Kit README, priority diagram `image/23111_25111_priority.svg`, 4a Pro manifest diagram `image/25111_manifest.svg`, `glyph-matrix-sdk-2.0.aar` (method names via `strings`): https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- GDK issues #3, #6, #8, #12, #14, #16, #17: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/issues
- Example project, issue #5: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project/issues/5

**Open-source apps** (code read at HEAD, 2026-10-11)
- GlyphMaps: https://github.com/capad-xyz/GlyphMaps (`service/GlyphRenderService.kt`, `service/MapsNotificationListener.kt`, `glyph/GlyphRenderer.kt`, `PUBLISHING.md`, PR #3)
- Glyph-Matrix-Notifications: https://github.com/toberfasanok/Glyph-Matrix-Notifications
- Glyph-Matrix-Calls: https://github.com/toberfasanok/Glyph-Matrix-Calls
- Glyphifier: https://github.com/AhmedYasser5/Glyphifier (`README.md`, `CLAUDE.md`)
- glyph-gif-rotator: https://github.com/ChichoXD/glyph-gif-rotator (`GlyphMatrixConnection.kt`, `DisplayDecider.kt`, `NotificationFlash.kt`, issue #1)
- ClipMatrix: https://github.com/Sukhendu2002/ClipMatrix
- GlyphSense: https://github.com/mohitsharma16/GlyphSense-Smart-Notification-Filter-for-Nothing-Phones
- Glphy-Nothing: https://github.com/manttii/Glphy-Nothing
- GlyphFlow: https://github.com/ThePrabhnoor/GlyphFlow

**Glyph Museum**
- Alerts guide: https://glyphmuseum.com/guides/nothing-phone-glyph-alerts-guide
- Effects guide: https://glyphmuseum.com/guides/nothing-phone-glyph-effects-guide
- 2.0 announcement (Nothing Community, post 299391): https://nothing.community/d/60931-glyph-museum-20-is-here
- Review [S]: https://nothingtec.com/en/2026/08/07/glyph-museum-2-0-nothing-phone-app/
- Developer terms: https://glyphmuseum.com/developers

**Android (official)**
- NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Notification (extras): https://developer.android.com/reference/android/app/Notification
- Person: https://developer.android.com/reference/android/app/Person
- CallScreeningService: https://developer.android.com/reference/android/telecom/CallScreeningService
- TelephonyCallback.CallStateListener: https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener
- Background execution limits: https://developer.android.com/about/versions/oreo/background
- FGS background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Doze: https://developer.android.com/training/monitoring-device-state/doze-standby
- Implicit broadcast exceptions: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- Android 15 behaviour changes (OTP redaction): https://developer.android.com/about/versions/15/behavior-changes-all
- Android 16 behaviour changes: https://developer.android.com/about/versions/16/behavior-changes-all
- Android 17 features (Contact Picker): https://developer.android.com/about/versions/17/features
- Sensors (overview, position, motion): https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
- Sensor suspend mode (wake-up vs non-wake-up): https://source.android.com/docs/core/interaction/sensors/suspend-mode
- Restricted settings help: https://support.google.com/android/answer/12623953

**AOSP source** (frameworks/base, `refs/heads/main`, fetched 2026-10-11)
- `services/core/java/com/android/server/notification/NotificationManagerService.java` (`NotificationListeners.getBindFlags`)
- `services/core/java/com/android/server/notification/ManagedServices.java` (rebind delay)
- `services/core/java/com/android/server/am/ActiveServices.java` (FGS start checks)
- `services/core/java/com/android/server/am/OomAdjuster.java` (BFGS / BFSL propagation)
- `services/core/java/com/android/server/power/PowerManagerService.java` (Doze wake-lock disabling)
- Base URL: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/

**Secondary**
- Restricted settings (Android 13): https://www.xda-developers.com/android-13-restricted-setting-notification-listener/
- Restricted settings (Android 15): https://www.androidauthority.com/android-15-restricted-settings-sideloading-3481098/
- Android 15 enhanced confirmation mode: https://www.androidpolice.com/android-15-enhanced-confirmation-mode-sideloading/
- Play policy summary (permissions; Contacts policy 27 Jan 2027) via fetch: https://support.google.com/googleplay/android-developer/answer/16558241
