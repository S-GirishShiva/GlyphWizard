# Threat model

**Status:** M0 (2026-10-13), spec §9 security. Revised at each milestone that adds an entry point (M1 file import, M3 triggers, M5/M7 agent). Hardware facts come from `docs/research/hardware-spike-results.md`.

## Assets

| Asset | Why it matters | Exists from |
|---|---|---|
| Notification content (sender, text) | private messages from third parties | M3 (listener); M0 spike logs package names only |
| API keys for agent endpoints | spend on the user's account | M7 |
| The user's animations and trigger rules | user work; rules reveal who matters to the user | M2/M3 |
| The Glyph Matrix itself | a lit matrix can leak a notification to people nearby, flash at unsafe rates, or drain power | M0 |
| Build integrity (source → APK) | a tampered dependency or CI run ships code to users | M0 |

## Entry points and mitigations

| Entry point | Threat | Mitigation | Status |
|---|---|---|---|
| Gradle plugins and libraries | compromised mirror or upstream ships malicious code | dependency verification: SHA-256 for every artifact (`gradle/verification-metadata.xml`, generated from an empty Gradle home); version catalog pins; ≥ 14-day pin rule, also enforced by Renovate's `minimumReleaseAge` | M0 ✔ |
| The Nothing SDK fetch | the AAR changes upstream or is tampered with | resolved by Gradle from a commit-pinned URL (`exclusiveContent`, one module only); SHA-256 independently checked and pinned in the verification metadata; `check-glyph-sdk-fetch.sh` proves a tampered hash, an unreachable host and offline mode all fail | M0 ✔ |
| JDK provisioning (foojay toolchain resolver) | a machine without JDK 25 downloads one through foojay's API, **outside** Gradle dependency verification | CI never takes this path (setup-java installs Temurin 25) | **open** (contributor machines) |
| Robolectric's runtime download of `android-all-instrumented` jars | fetched by Robolectric itself at test time, **outside** Gradle dependency verification | none yet. Test-only (never reaches the APK), but a compromised jar runs on CI and developer machines. Candidate fix: pre-resolve the jars through a Gradle configuration (so they are verified) and set `robolectric.offline=true` | **open, M1** |
| CI (GitHub Actions) | a compromised action or a leaked token | actions pinned by full commit SHA; `permissions: contents: read`; `persist-credentials: false`; no secrets in M0; Renovate PRs for action updates; open-source cache provider (`basic`) | M0 ✔ |
| Release artifacts | an SDK-containing APK published before Nothing's permission (D26) | CI attaches no artifacts; `check-sdkfree-apk.sh` proves the sdkFree APK has no SDK classes, ENABLE permission or NothingKey (with a positive control on the full APK) | M0 ✔; release signing M8 |
| Glyph ENABLE permission | another app holding it drives the matrix | normal-level permission any app can request; third-party apps are **not arbitrated**: two apps writing the app-matrix light overwrite each other frame by frame (measured with Glyph Museum). We can't prevent this; we avoid streaming unchanged frames and document coexistence | accepted; M3 design input |
| Process kill / crash while playing | **force-stop leaves the matrix lit indefinitely** on the last frame; the glyph service doesn't clean up after a dead client (measured). A notification glyph could stay visible to others, and a bright static frame drains power | clear the matrix on the next start; avoid long-held bright static frames; one-shot triggers end with `closeAppMatrix` (the system's auto-timeout had no effect, measured) | **open, M1/M3** |
| Notification text (NotificationListener) | sensitive content leaks to logs, disk, the agent or backups | held in memory only, never sent to the agent (spec §4.2, §5.1); the spike logs package names only, never text | M0 spike ✔; M3 enforces in the runtime |
| The spike's adb hooks (`SpikePatternService` receiver) | other apps drive the matrix through the exported receiver | exported but guarded by `android.permission.DUMP`, which only the adb shell holds; throwaway module, never released, deleted at the end of M1 | M0 ✔ |
| adb and `run-as` on debug builds | local access to app data on a debug install | debug builds are developer-only; release builds are not debuggable | accepted |
| Imported files (Glyph Museum JSON, `.glyphwiz`, images/GIF) | parser crashes, zip/decompression bombs, path tricks | input limits (spec §3.5); fuzzing of the parsers (spec §9) | M1/M6 |
| Agent endpoints (LLM APIs, LAN servers) | key theft, prompt injection from imported text, cleartext keys on a LAN | keys only in the Keystore, excluded from backup; imported text delimited as data, no auto-rendered links or images; cleartext only for RFC1918, `.local` and loopback, with a warning (spec §5.5) | M5/M7 |
| Flashing content | an animation triggers photosensitive seizures | flash analysis, warn and gate (D37); absolute luminance unmeasured, so thresholds stay conservative | M1 |

## Open risks

1. **Robolectric downloads bypass dependency verification** (see above). Fix candidate in M1.
2. **A crash or force-stop leaves the matrix lit** until the next start of our app or another app's frame. Mitigation lands with the M1/M3 playback code.
3. **Third-party Glyph apps are not arbitrated.** Accepted; documented to users in M3.
4. **Brightness above 255 relies on undocumented behaviour** (D56). Firmware updates could change it; consider a runtime self-test (hardware-spike-results.md).
5. **JDK downloads through foojay** on machines without JDK 25 are not covered by dependency verification.
6. **Pre-stable detekt (D47)** could silently stop enforcing; mitigated by the CI canary and the SARIF-based crash/findings split.
