# Restricted settings / ECM: why the Obtainium install showed no block

Sources were fetched 2026-10-17 from android.googlesource.com branch heads (`android13-release` to `android16-release`; frameworks/base android16-release HEAD = `99b01a65cc4c104933788b3143285ab6bae65827`). Obtainium is at `fae71a4d965c8fdb52ed54188a2cf130b821999a` (v1.7.1+2359). The plugin ref is the same in tag v1.7.0. Device facts come from read-only adb on the user's phone (SDK 36, B4.1-260907-1153).

## TL;DR
- On Android 13 through 16, the trigger in AOSP is the install's **packageSource**, not whether a session was used. Only `PACKAGE_SOURCE_LOCAL_FILE` (3) and `PACKAGE_SOURCE_DOWNLOADED_FILE` (4) are restricted. The system Package Installer app stamps one of these two on every install it handles through an intent (browser, file manager, "open APK"). That is the real meaning of the old "non-session installs are restricted" rule.
- Obtainium's default installer creates its own `PackageInstaller.Session` and calls `setPackageSource(PACKAGE_SOURCE_STORE)`. On the device the spike app shows `packageSource=2`, `installerPackageName=dev.imranr.obtainium` and `ACCESS_RESTRICTED_SETTINGS: default`. ECM therefore treats it as not guarded, and **that is why no block appeared.**
- Developer options, USB debugging and adb do not appear anywhere in the ECM logic. Developer mode does not explain it.
- The device ships the stock AOSP `enhanced-confirmation.xml` (md5 identical, zero trusted-installer entries). On this phone, "trusted installer" gating is therefore **off**: any installer that doesn't claim LOCAL_FILE or DOWNLOADED_FILE escapes ECM.
- Control case on the same phone: Obtainium itself was installed by `com.google.android.packageinstaller` with `packageSource=3`. Its op is `ACCESS_RESTRICTED_SETTINGS: deny`, so Obtainium *would* get the block. That proves ECM is active on this build.

## 1. Which installs are restricted (AOSP)

**Android 13** (`InstallPackageHelper.java`, android13-release, around line 2179):
```java
// Apply restricted settings on potentially dangerous packages.
if (installArgs.mPackageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
        || installArgs.mPackageSource
        == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE) {
    enableRestrictedSettings(pkgName, pkg.getUid());   // sets OP_ACCESS_RESTRICTED_SETTINGS = MODE_ERRORED
}
```
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/services/core/java/com/android/server/pm/InstallPackageHelper.java

**Android 14:** the logic is the same at line 2372, using `installRequest.getPackageSource()` (android14-release, same path).

**Who sets those sources.** The system Package Installer sets them when it handles an intent install (android16-release):
- `packages/PackageInstaller/src/com/android/packageinstaller/v2/model/InstallRepository.kt:388-392`
  ```kotlin
  val referrerUri = intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
  params.setPackageSource(
      if (referrerUri != null) PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
      else PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
  ```
- The legacy `InstallStaging.java:189-191` does the same.
- The default for direct session installers is UNSPECIFIED: `PackageInstaller.java`, "The default value is {@link #PACKAGE_SOURCE_UNSPECIFIED}". Constant javadoc: STORE (2) is "An app store that installs an app for the user would use this". LOCAL_FILE (3) is "A file manager that is facilitating the installation of an APK file would use this". DOWNLOADED_FILE (4) is for when "the installer knows the package was downloaded". https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/content/pm/PackageInstaller.java

The press wording "session-based installers are exempt" is therefore an approximation. A third-party session installer is exempt unless it volunteers LOCAL_FILE or DOWNLOADED_FILE. The system installer's intent path always does. (Secondary sources: Esper, https://www.esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis ; Kaspersky, https://www.kaspersky.com/blog/android-restricted-settings/49991/ . These are not primary.)

**Android 15 (ECM).** `InstallPackageHelper` now sets the op to `MODE_DEFAULT` ("guardedness will be decided lazily") when the flags `enhancedConfirmationModeApisEnabled` and `extendEcmToAllSettings` are on. Otherwise it falls back to the Android 14 rule. Both flags are `ENABLED` / `READ_ONLY` in the release configs:
- `build/release/aconfig/ap3a/android.security/extend_ecm_to_all_settings_flag_values.textproto`
- `build/release/aconfig/{ap3a,bp2a}/android.permission.flags/enhanced_confirmation_mode_apis_enabled_flag_values.textproto`

The decision moves to `packages/modules/Permission/service/java/com/android/ecm/EnhancedConfirmationService.java` (android15-release lines 236-283; android16-release lines 353-401):
```java
// Always trust allow-listed and pre-installed packages
if (isAllowlistedPackage(packageName) || isAllowlistedInstaller(packageName)
        || isPackagePreinstalled(applicationInfo)) return false;
// If the package already has an explicitly-set state, use that   (op ERRORED/IGNORED -> guarded, ALLOWED -> not)
...
int packageSource = installSource.getPackageSource();
if (packageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
        || packageSource == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE) return true;
...
return !(trustPackagesInstalledViaNonAllowlistedInstallers()   // true when zero trusted-installer entries
        || isPackagePreinstalled(installingApplicationInfo)
        || isAllowlistedInstaller(installingPackageName));
```
https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/service/java/com/android/ecm/EnhancedConfirmationService.java

The op states are: ALLOWED = not guarded, ERRORED = guarded, IGNORED = guarded and acknowledged, DEFAULT = implicit (lines 102-111).

Protected settings (`PER_PACKAGE_PROTECTED_SETTINGS`) include `OPSTR_ACCESS_NOTIFICATIONS`, `OPSTR_BIND_ACCESSIBILITY_SERVICE`, `SYSTEM_ALERT_WINDOW`, `GET_USAGE_STATS`, the SMS permissions, device admin, and the dialer/SMS roles.

**What changed in 15:**
- ECM covers more settings than notifications and accessibility.
- An optional OEM allowlist of trusted installers was added. With a non-empty list, *any* non-preinstalled, non-allowlisted installer gets guarded, whatever its packageSource.
- The allowlist is empty in AOSP (`frameworks/base/data/etc/enhanced-confirmation.xml` is `<config></config>` on android15-release, android16-release and main). Its own comment says: "If zero <enhanced-confirmation-trusted-installer> entries are declared, then *all* packages will be exempt from ECM restrictions, except apps … marked by its installer as coming from an untrustworthy package source."

**What changed in 16:** the criteria are the same. The android16-release `InstallPackageHelper` comment says only: "In Android B, the op's default mode was updated to the 'should be decided later' case". `AppOpsManager` has `ACCESS_RESTRICTED_SETTINGS … setDefaultMode(MODE_DEFAULT)` at line 3170.

16 also adds a during-call restriction (`UNTRUSTED_CALL_RESTRICTED_SETTINGS`: accessibility and REQUEST_INSTALL_PACKAGES during an untrusted call, behind the `unknownCallPackageInstallBlockingEnabled` flag). It does not cover notification access.

[U] The Permission code is a Mainline module updated through Play system updates (the device runs `com.google.android.permission`). The module build on the device could differ from android16-release, but the observed behaviour matches the source.

## 2. Developer options / USB debugging / adb
- `EnhancedConfirmationService.java` (android16-release) contains no reference to adb, development settings or debuggable. A grep for `adb|develop|debug` finds nothing.
- The inputs are only: the allowlists, preinstalled status, the op state, packageSource and the installing package. Developer mode does **not** explain the missing block.
- Related point: `adb install` / `pm install` default to `PACKAGE_SOURCE_OTHER` (`PackageManagerShellCommand.java:3418-3419`: "Set package source to other by default. Can be overridden by "--package-source""). So Android Studio and adb installs are also unrestricted on AOSP config.
  - To reproduce the block over adb: `adb install --package-source 3 app.apk` (the flag parsing is at line 3602). [U] This is not tried on the device.

## 3. How Obtainium installs
- The default mode is `InstallerMode.system` (`lib/providers/settings_provider.dart:51,154-159`). It maps to `StockInstaller` (`lib/installers/stock_installer.dart:15` "Installs using Android's session-based [AndroidPackageInstaller]"; line 94 is the `AndroidPackageInstaller.installApk(...)` call).
- That plugin is the fork `github.com/ImranR98/android_package_installer` @ `94525707bd1fba8cba4d19b4cb2b928580913510` (pinned in `pubspec.yaml:33-36` and `pubspec.lock:20-28`). In `android/src/main/kotlin/com/android_package_installer/Installer.kt:61-75`:
  ```kotlin
  val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
  ... setInstallReason(INSTALL_REASON_USER) / setRequireUserAction(USER_ACTION_NOT_REQUIRED)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
  }
  ... setRequestUpdateOwnership(true)
  sessionId = packageInstaller.createSession(params)
  ```
- Other modes:
  - **Shizuku** (`ImranR98/shizuku_apk_installer` @ `9777a8bc…`, `ShizukuWorker.kt:254`) builds session params with no `setPackageSource`, so the source is UNSPECIFIED. It is not restricted.
  - **Root** runs `pm install`, so the source is OTHER. It is not restricted.
  - **External** (`lib/installers/external_installer.dart`) hands a content URI to another installer app through an intent. If that app is the system Package Installer, the result is LOCAL_FILE or DOWNLOADED_FILE, which *is* restricted. [U] This is inferred from InstallRepository.kt, not tested.
- [U] The Obtainium version on the phone is 1.6.17. I verified the plugin ref only for v1.7.0 and HEAD, not for 1.6.17. The device result (`packageSource=2`) shows it behaves the same.

## 4. Explanation, and which paths do show the block on Android 16
**Explanation.** Obtainium declared itself a STORE (2). The device's ECM allowlist is empty, so `trustPackagesInstalledViaNonAllowlistedInstallers()` is true. The app is therefore not guarded and the normal notification-access consent dialog appears.

**Paths that DO show "Restricted setting" on this phone (AOSP logic):**
- Browser download → tap the APK → system Package Installer. The source is DOWNLOADED_FILE if the browser passes `EXTRA_REFERRER`, otherwise LOCAL_FILE. Both are guarded. This is proven by Obtainium's own install (`packageSource=3`, op `deny`).
- Any file manager or app that opens the APK with `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE` → system installer → LOCAL_FILE.
- Any third-party installer that explicitly sets LOCAL_FILE or DOWNLOADED_FILE.

**Paths that do NOT show it here:** Obtainium (stock, Shizuku and root modes), F-Droid-style session installers that set STORE, OTHER or nothing, Play Store, and adb/Android Studio. [U] On an OEM build with trusted-installer entries, Obtainium would be guarded too.

**Design implication.** The onboarding assumption "sideloaded apps hit the block" holds only for the browser/file-manager install path. For Obtainium, F-Droid and adb it does not. Onboarding should handle both cases, not assume the block.

**Unlock path (still current on 16).** Google Help (https://support.google.com/android/answer/12623953): "Tap Apps → Tap the app … → Tap More … and then **Allow restricted settings** → Follow the on-screen instructions." In AOSP Settings android16-release:
- `AppInfoDashboardFragment.java:409-411` adds the menu item `app_restricted_settings_lockscreen_title` ("Allow restricted settings", `res/values/strings.xml:4480`).
- On tap it requires the lock screen and calls `EnhancedConfirmationManager.clearRestriction()`, which sets the state to ALLOWED (lines 493-517).
- Caveat (lines 542-551): the item is visible only when `isClearRestrictionAllowed()`, i.e. the state is GUARDED_AND_ACKNOWLEDGED (IGNORED). The user must **first try the restricted toggle and dismiss the "Restricted setting" dialog**, and only then does the ⋮ item appear.

https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-release/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java

## 5. adb evidence commands (all already run read-only on the device)
```sh
P=io.github.sgirishshiva.glyphwizard.spike
adb shell pm list packages -i -3 | grep -i obtainium            # installer per package
adb shell dumpsys package $P | grep -E "installerPackageName|initiatingPackageName|originatingPackageName|updateOwnerPackageName|packageSource"
adb shell appops get $P ACCESS_RESTRICTED_SETTINGS               # default = ECM decides lazily; deny = guarded; ignore = guarded+acknowledged; allow = cleared
adb shell cat /system/etc/sysconfig/enhanced-confirmation.xml    # OEM trusted-installer/package allowlist
adb shell settings get secure enabled_notification_listeners     # confirms the grant
```
Observed values:

| Package | installer / initiating | packageSource | ACCESS_RESTRICTED_SETTINGS |
|---|---|---|---|
| glyphwizard.spike | dev.imranr.obtainium / dev.imranr.obtainium (updateOwner obtainium) | 2 (STORE) | `default` |
| dev.imranr.obtainium | com.google.android.packageinstaller | 3 (LOCAL_FILE) | `deny` (guarded) |

- `enhanced-confirmation.xml`: md5 `f12feb3eca9d8fb04ad1eab943361593`, identical to AOSP android16-release. It contains no trusted entries (`<config></config>`).
- [U] I checked only /system, /system_ext, /product and /vendor `etc`. /odm and /oem were not checked.
- `SpikeListener` is present in `enabled_notification_listeners`.
- To see the block for a test, install over adb with `--package-source 3`, or install from a file manager, then toggle notification access.
