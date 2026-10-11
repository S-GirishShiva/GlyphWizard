# Dev tooling: letting Claude Code see and drive GlyphWizard on device

**Date:** 2026-10-11 · **Scope:** developer-loop tooling only (not the in-app agent) · **Host:** CachyOS/Arch, zsh, Android Studio **Rabbit 1 (2026.2.1, build AI-262.9437)**, Claude Code 2.1.296, Node 26, uv, ImageMagick 7, ffmpeg 9, v4l-utils · **Device:** Nothing Phone (4a) Pro (USB / wireless ADB)

Legend: **[V]** verified from a primary source (README, official docs, registry API, or local inspection) on 2026-10-11 · **[S]** secondary source (press, blog, directory) · **[U]** unverified or inferred: check before relying on it.

Facts about the local machine come from direct inspection. Studio is installed at `/opt/android-studio` (AUR `android-studio 2026.2.1.8-2`), and the SDK is at `~/Android/Sdk` (adb 37.0.1). **adb is not on PATH, `ANDROID_HOME` is unset, and `java` is not on PATH.** No device was attached at the time of inspection. There is an integrated webcam at `/dev/video0`.

---

## TL;DR: ranked recommendation

| Rank | Tool | Role | Why |
|---|---|---|---|
| **1 (primary)** | **Google Android CLI** (`android`) + raw `adb` via Bash | Eyes and most hands: screenshot (with numbered element boxes), UI tree as JSON, deploy, **render Compose `@Preview` to PNG through the running Studio**, Studio code analysis, docs | Official (Google, I/O 2026, 1.0 stable). Built for agents. Shell-native, so no MCP server and no extra token overhead. PNGs are viewed with Claude Code's `Read` tool. |
| **2 (primary, optional)** | **mobile-mcp** (`mobile-next/mobile-mcp`) | Structured hands: tap/swipe/type on elements, launch/terminate, logs, through MCP tool calls | The most popular and most actively maintained mobile MCP (about 9k stars, Apache-2.0, v1.0.9 on 2026-10-09). Use it when ad-hoc `adb shell input` gets clumsy. |
| **Must-build** | **In-app Glyph Matrix debug mirror** (debug build only) | The only way the agent can "see" the rear LEDs | Text frame dumps to logcat (cheap, exact) plus PNG/contact-sheet dumps triggered by an adb broadcast. Specified below. |
| Fallback A | `scrcpy` 5.0.1 | Live mirror for *you*, and screen recording | Not an agent tool, but it makes watching the agent work painless. `--record` gives video evidence. |
| Fallback B | **Maestro MCP** (`maestro mcp`) | Repeatable YAML flows / regression smoke tests | Good once you want "replay this journey after every change". It needs Java 17+. |
| Fallback C | **JetBrains "MCP Server" plugin** in Studio | IDE problems/inspections, run configurations, symbol search | Not bundled in Android Studio, but the Marketplace lists a compatible build for this Studio build. Overlaps heavily with `android studio …`. |
| Fallback D | **Roborazzi** `generateComposePreviewRobolectricTests` | Headless `@Preview` → PNG with no Studio and no device (CI, or when Studio is closed) | Mature (v1.76.0, 2026-09-29). It ships an agent skill and JSON UI-tree sidecars. |
| Always on | Claude Code JetBrains plugin | Diffs in the Studio viewer, `getDiagnostics` | Officially supports Android Studio. It exposes only one model tool (`mcp__ide__getDiagnostics`). |

**Skip for this project:** android-tools-mcp (supports Studio builds 253–261 only, and you are on 262), DroidMind (last push Jan 2026), minhalvp/android-mcp-server (last push May 2025), srmorete/adb-mcp (archived), minitap mobile-use (a separate agent that needs its own LLM keys), Appium MCP (heavyweight and test-framework oriented), and Firebender (a competing in-IDE agent, not a tool Claude Code can drive).

---

## 1. Candidates compared

### 1.1 Google Android CLI: the strongest option, and new this year
- **What:** an official agent-oriented CLI announced stable at Google I/O on 2026-05-19. **[S: TechCrunch, InfoQ]** The release notes list 1.0.16500706 (Oct 2026) as the latest version. **[V: release notes]**
- **Relevant commands [V: command reference pages]:**
  - `android screen capture -o ui.png [--annotate] [--device=SERIAL]` writes a PNG. With `--annotate` it overlays numbered bounding boxes (`#1`, `#2`…) detected by edge detection. `android screen resolve --screenshot=ui.png --string="input tap #7"` then rewrites `#7` to that box's center coordinates, which you pipe to `adb shell`.
  - `android layout [--pretty] [--full] [--flat] [--no-idle] [-o file]` returns the live UI hierarchy as JSON. By default it includes only interactive, visible elements, so it is token-cheap.
  - `android studio render-compose-preview <file.kt> <PreviewFn> [--output-image-file=x.png] [--print-semantics]` renders a `@Preview` **with Studio's own layout engine** (preview fidelity, not on-device fidelity) and can also print the semantics tree as JSON. It **requires a running Android Studio, Quail 2 or later, with the project open.** Your Rabbit 1 is newer than Quail, so this should work. **[V for the requirement; U for real-world behaviour on Rabbit]**
  - `android studio analyze-file | check | find-declaration | find-usages | open-file | version-lookup` are IDE-backed code intelligence commands.
  - `android run [--use-delta-install]`, `android install` (incremental APK push), `android describe` (prints Gradle build errors), `android docs search|fetch`, `android emulator start --headless`.
  - `android init` installs the `android-cli` agent skill. `android skills add …` installs Google's Android skills (repo `android/skills`, about 7.8k stars, Apache-2.0) for Claude Code. **[V]**
  - **Journeys** are natural-language UI test flows that "your agent" executes with vision. The page documents no file format or run command, so they appear to be skill-driven. **[V: page content; format U]**
- **There is no tap command.** Taps go through `adb shell input …`, which is exactly what `screen resolve` produces.
- **There is no MCP mode** (none documented). **[V]** That is fine: Claude Code calls it through Bash.
- **Platforms:** Linux x86_64 only for Linux (fine for you). It is a closed binary and the docs state no license. **[U]** It collects usage telemetry (command names, anonymized stack traces) by default. Opt out with `--no-metrics`. **[V]**
- **Caveat:** `android layout` very likely uses UiAutomation on the device. [U] Only one UiAutomation client can be connected at a time. That conflicts with mobile-mcp's on-device server (see 1.2). Don't run both concurrently.

### 1.2 mobile-mcp (mobile-next)
- 8,958 stars, Apache-2.0, release **1.0.9 on 2026-10-09**, pushed 2026-10-10. Requires Node ≥ 20 (you have 26). **[V: GitHub API, README]**
- **Tools:** screenshot (inline or saved), list elements on screen (accessibility tree with coordinates), tap, double-tap, long-press, swipe (by coordinates or element), type text, hardware buttons, open URL, list/launch/terminate/install/uninstall apps, foreground app, screen size and orientation, screen recording, device logs and crash reports, batch commands. **[V]**
- **Install:** `claude mcp add mobile-mcp -- npx -y @mobilenext/mobile-mcp@latest`. Alternatively, inside Claude Code: `/plugin marketplace add mobile-next/mobile-mcp` then `/plugin install mobile-mcp@mobile-mcp` (adds a `/mobile-mirror` command). **[V]**
- **How it works on Android:** by default it drives devices through **`mobilecli`**. mobilecli starts a small on-device server (`com.mobilenext.mobilecli.DeviceServer`) that **holds the device's only UiAutomation connection**, so `uiautomator dump` and similar tools cannot run alongside it. **[V: mobilecli README]** mobilecli is licensed **FSL-1.1-Apache-2.0** (source-available, converts to Apache later), which differs from mobile-mcp's Apache-2.0. **[V: badge]** `MOBILEMCP_LEGACY_ROBOT=1` switches Android to the legacy adb-based robot. **[V]**
- **Token note:** each screenshot tool call returns an inline image (see §6 for cost). Prefer the elements-list tool, and take screenshots only when you need pixels.

### 1.3 Maestro MCP
- Maestro: 16k stars, Apache-2.0, `cli-2.11.0` released 2026-09-29. **[V]** Its MCP server is built in: `maestro mcp` over stdio, added with `claude mcp add maestro -- maestro mcp`. **[V: docs]**
- **Tools:** `inspect_screen` (compact hierarchy JSON), `take_screenshot`, `run` (inline YAML or flow files: taps and asserts live here), `list_devices`, `cheat_sheet`, `open_maestro_viewer`, plus cloud tools that need a login. **[V]**
- **Install:** `curl -fsSL "https://get.maestro.mobile.dev" | bash`. It needs **Java 17+ with `JAVA_HOME` set**. **[V]** You have no system Java; point it at Studio's JBR (`/opt/android-studio/jbr`). [U whether the JBR is 17+ compatible for Maestro; it is a recent JDK, so it should be]
- **Best use:** turn a working manual exploration into a committed `.maestro/*.yaml` smoke flow that the agent re-runs after each change.

### 1.4 Appium MCP
- `appium/appium-mcp`: 496 stars, Apache-2.0, v1.95.8 released 2026-10-10. Requires Node ≥ 22 and a JDK. The UiAutomator2 driver is bundled for local sessions. **[V]** It is powerful (locator generation, Java/TestNG test generation, optional AI vision), but its session/capabilities model is heavier than you need for a single-developer inner loop. **Not recommended.**

### 1.5 Other adb MCP servers
| Repo | Stars | License | Last activity | Verdict |
|---|---|---|---|---|
| CursorTouch/Android-MCP | 890 | MIT | v0.2.0 2026-05-14, pushed 2026-10-07 | Decent Python alternative (uvx, **Python 3.13**). Has a State tool (accessibility tree) and click/swipe/type/shell. `SCREENSHOT_QUANTIZED=true` cuts tokens. **[V]** |
| hyperb1iss/droidmind | 438 | Apache-2.0 | v0.4.0 2026-01-07 (stale) | Broad (files, apps, logcat, shell) with a "security framework". README shows no screenshot/hierarchy tool. **[V]** |
| minhalvp/android-mcp-server | 811 | Apache-2.0 | 2025-05-28 | Stale. |
| srmorete/adb-mcp | 55 | MIT | archived | Dead. |
| iksnerd/adb-mcp | 7 | MIT | 2026-08 | 73 tools, very new, tiny community. **[V metadata]** |
| EF-Code/android-agent-mcp | 3 | MIT | 2026-09 | Security-focused, tiny. |
| MauricePutinas/Android-Studio-MCP-Claude-Code | 3 | MIT | 2026-06 | 73 tools, tiny community. |
| minitap-ai/mobile-use | 3,245 | Apache-2.0 | v3.3.0 2026-01-12 | A **separate agent** with its own LLM keys. It duplicates Claude Code. |

### 1.6 Google "official Android MCP"?
- **There is none for device control.** **[V by absence: Android CLI docs and release notes never mention MCP; searches turned up only the Android Management API MCP, which is enterprise fleet policy and irrelevant here]** Google's agent strategy is **CLI + skills**, not MCP. Android Studio's Gemini Agent Mode is an MCP *client*, not a server. **[S: Android Developers Blog, 2025-06]**

### 1.7 Android Studio integrations
- **Built-in IntelliJ MCP server:** JetBrains bundles the "MCP Server" plugin (`com.intellij.mcpServer`) in IntelliJ IDEA 2025.2+. **[V: JetBrains docs]** **Android Studio does not bundle it.** `/opt/android-studio/plugins` contains no MCP server plugin; the only MCP code is `gemini/lib/mcp-sdk-libraries.jar`, which is Gemini's client. **[V: local]** The Marketplace API does return a compatible build for your Studio: **`262.9437.214` (since-build 262.9437, until 262.\*)**. **[V: plugins.jetbrains.com API]** Exposed tools (per the IntelliJ docs): `build_project`, `get_file_problems`, `lint_files`, `get_run_configurations`, `execute_run_configuration`, search tools, `rename_refactoring`, `execute_terminal_command`, and others. **[V for IntelliJ; U how well `build_project` and run configs behave for an Android Gradle project in Studio]** Connect it from *Settings → Tools → MCP Server → Auto-Configure for Claude Code*. **[V]** It renders **no** Compose previews. Use `android studio render-compose-preview` for that.
- **Gemini in Studio, Journeys (Studio flavour), Layout Inspector, Compose Preview pane:** these are UI features inside the IDE with **no supported external API**. **[V by absence]** The supported bridges are the Android CLI's `android studio …` commands (code analysis plus preview rendering) and `android layout` (a live hierarchy that substitutes for Layout Inspector). The unofficial bridge `amsavarthan/android-tools-mcp` re-exports Gemini's internal tools (`render_compose_preview`, `take_screenshot`, `ui_state`, `deploy`, `read_logcat`, …). It uses **undocumented internal APIs** and supports **builds 253–261**, while you are on **262**. **[V]** Avoid it.
- **Claude Code JetBrains plugin** ("Claude Code [Beta]", Marketplace 27310): officially supports Android Studio. It gives diffs in the IDE viewer, shares your selection and open file, and exposes **one** model tool, `mcp__ide__getDiagnostics` (read-only). The IDE server listens on `127.0.0.1` on an ephemeral port with a token stored in `~/.claude/ide/<port>.lock`. **[V: code.claude.com/docs/en/jetbrains]** Install it: from an external terminal, `/ide` offers to install it.

### 1.8 Compose `@Preview` → PNG without a device
| Option | Status | Speed | Fidelity | Notes |
|---|---|---|---|---|
| `android studio render-compose-preview` | GA in Android CLI 1.0 | Fast if Studio is already warm (exact speed not measured) [U] | Same as Studio's preview pane (layoutlib) | Needs Studio running with the project open. Can also print semantics JSON. **Best for the inner loop.** |
| Roborazzi (`generateComposePreviewRobolectricTests`, `./gradlew recordRoborazziDebug`) | v1.76.0, Apache-2.0, very active | Robolectric JVM: seconds per preview after a Gradle warm-up; `renderScale<1` speeds it up [V] | Robolectric native graphics, close to device but not identical | Output in `build/outputs/roborazzi/`. **Ships an agent skill** (`npx skills add takahirom/roborazzi`) and **JSON UI-tree sidecars**. **[V]** Works headless, so it is good for CI. |
| Compose Preview Screenshot Testing (`com.android.compose.screenshot`, 0.0.1-alpha16) | **Deprecated** in favour of AGP test suites from **AGP 9.5.0-alpha03** [V] | Gradle host tests | layoutlib, so it matches the Studio preview [U, inferred] | You are on AGP 9.4.1. Wait for AGP 9.5 test suites rather than adopting the deprecated plugin. |
| Paparazzi | 2.0.0-alpha05.1, Apache-2.0 [V] | Fast JVM layoutlib | layoutlib | Fine, but Roborazzi has the better agent story. |

Note: the project currently has **no Compose dependencies** (`app/build.gradle.kts` uses appcompat/material only). Previews become relevant once Compose is added.

---

## 2. Raw fallback: what Claude Code can do with Bash alone (always works)

```zsh
export ANDROID_HOME=$HOME/Android/Sdk
A=$ANDROID_HOME/platform-tools/adb

# Screenshot -> PNG, downscaled for tokens, then the agent opens it with Read
$A exec-out screencap -p > /tmp/gw/screen.png
magick /tmp/gw/screen.png -resize 50% /tmp/gw/screen_small.png

# UI hierarchy (XML). Compose nodes show text/content-desc. testTags appear as
# resource-id only if the root sets Modifier.semantics { testTagsAsResourceId = true }
$A shell uiautomator dump /sdcard/ui.xml >/dev/null && $A exec-out cat /sdcard/ui.xml > /tmp/gw/ui.xml

# Input
$A shell input tap 540 1200
$A shell input swipe 540 1800 540 600 300      # x1 y1 x2 y2 ms
$A shell input text 'hello%sworld'              # %s = space
$A shell input keyevent KEYCODE_BACK

# App lifecycle
$A shell am start -n com.example.glyphwizard/.MainActivity
$A shell am force-stop com.example.glyphwizard

# Logs: only our process, recent, non-blocking (-d dumps and exits; never stream in an agent)
$A logcat -c
$A logcat -d --pid=$($A shell pidof -s com.example.glyphwizard) -v brief | tail -200
$A logcat -d -s GlyphMirror:V AndroidRuntime:E '*:S'

# Screen size, for coordinate sanity
$A shell wm size
```

- **Viewing PNGs:** Claude Code's `Read` tool renders images inline, so the agent "sees" any PNG it saved.
- **Never run streaming commands** (`logcat` without `-d`, `scrcpy`, `screenrecord` with no limit) in the foreground. Use `-d`, `--time-limit`, or Claude Code's background Bash.
- `uiautomator dump` fails while another UiAutomation client is connected (mobile-mcp/mobilecli, Maestro, Appium). Pick one driver per session.
- **scrcpy 5.0.1** (151k stars, Apache-2.0, released 2026-10-08) **[V]**: `scrcpy --stay-awake --turn-screen-off` gives you a live mirror while the agent works. `scrcpy --no-window --record=/tmp/gw/run.mp4 --time-limit=20` records evidence, and `ffmpeg -i run.mp4 -vf fps=2 /tmp/gw/f_%03d.png` turns it into frames the agent can read.

---

## 3. Special need: the Glyph Matrix is on the back

Screenshots never show the LEDs. Options, best first:

### 3.1 Debug frame mirror inside the app (build this; it is cheap and exact)
Design: one `FrameSink` seam right where frames are pushed to `setAppMatrixFrame(int[])`. In debug builds, wrap the device sink with a `MirrorSink` that also:
1. **Logs a text frame** (tag `GlyphMirror`) on demand or at a throttled rate, as 13 rows of 13 brightness glyphs (` .:-=+*#%@` buckets, or hex 00–FF) plus frame index and timestamp. That is about 200 characters (roughly 100 tokens) per frame and **exact**, which beats an image for verification. Mask the 32 dead corner cells (137 real LEDs, row widths 5,9,11,11,13,13,13,13,13,11,11,9,5 per the existing research).
2. **Dumps PNGs on an adb command** using a debug-only receiver:
   ```zsh
   adb shell am broadcast -a com.example.glyphwizard.debug.DUMP_GLYPH \
       -n com.example.glyphwizard/.debug.GlyphDumpReceiver --ei frames 24 --ei every 2
   adb exec-out run-as com.example.glyphwizard cat files/glyph/sheet.png > /tmp/gw/glyph.png
   ```
   Render each LED as a circle on black at about 24 px per LED (13×13 becomes 312×312, about 144 visual tokens), with real-LED masking. For animations, write a **contact sheet** (a grid of N frames with indices). Claude only sees the first frame of a GIF, so don't use GIFs.
3. **Shows an on-screen mirror** (debug overlay or in the editor), so ordinary screenshots also show "what the back is showing".
- **Security:** put the receiver in `src/debug/AndroidManifest.xml` only. Protect it with `android:permission="android.permission.DUMP"` so that, in practice, only the adb shell (which holds DUMP) can trigger it. **[U: confirm shell holds DUMP on this ROM]** `run-as` works only for debuggable builds. Release builds contain none of this.
- **Limit:** this mirrors *what the app sent*, not what the hardware displayed. It can't show gamma, real brightness, refresh dropouts, or conflicts with a system Glyph Toy. Use 3.2 for those.

### 3.2 A camera pointed at the back (feasible for occasional checks)
- **Laptop webcam:** prop the phone screen-toward-you, back-toward-webcam. Then:
  ```zsh
  v4l2-ctl -d /dev/video0 -l                               # list controls; names vary per camera
  v4l2-ctl -d /dev/video0 -c auto_exposure=1 -c exposure_time_absolute=50   # manual, short exposure [U control names]
  ffmpeg -loglevel error -f v4l2 -video_size 1280x720 -i /dev/video0 -frames:v 1 -y /tmp/gw/back.jpg
  magick /tmp/gw/back.jpg -crop 500x500+390+110 /tmp/gw/back_crop.jpg   # crop to the matrix
  ```
  All of these tools are already installed. **[V: local]**
- **A second Android phone as the camera** gives much better optics: `scrcpy -s <serial2> --video-source=camera --camera-facing=back --no-window --record=/tmp/gw/back.mp4 --time-limit=10`, then extract frames with ffmpeg. **[V: scrcpy camera mirroring exists since 2.2; flags U for v5]**
- **Pitfalls:** the matrix peaks around 3,000 nits, so auto-exposure clips it; lock exposure low. LED PWM and rolling shutter cause banding and beating. The camera framerate is not synced to the 20 ms tick, so this is **not frame-accurate**. Webcam frames are about 1,000–1,300 visual tokens each. Use the camera for "does it look right on the hardware" spot checks and the mirror for logic.

### 3.3 Frame logging in tests
Unit-test the compositor and baker on the JVM with golden **text** frames (the same format as 3.1). The agent can diff those without any device.

---

## 4. Setup on CachyOS/Arch (do not run blindly; read each line)

```zsh
# --- 0. PATH and SDK env (append to ~/.zshrc) ---
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
path=($ANDROID_HOME/platform-tools $ANDROID_HOME/emulator $path)
export JAVA_HOME=/opt/android-studio/jbr          # for Maestro / Gradle CLI
export ADB=$ANDROID_HOME/platform-tools/adb       # make scrcpy use the SDK adb (avoids server-version flapping)

# --- 1. USB permissions + mirroring ---
sudo pacman -S --needed android-udev scrcpy        # android-udev 20260922, scrcpy 5.0.1 in extra/cachyos [V]
# scrcpy pulls in Arch's android-tools (adb 37.0.0). Keep ONE adb on PATH (the SDK's 37.0.1, first in $path);
# mismatched client/server versions make adb kill and restart its server.
sudo udevadm control --reload-rules && sudo udevadm trigger
# If the device still shows "no permissions": sudo usermod -aG adbusers $USER, then log out and back in  [U: group name per package]

# --- 2. Phone ---
# Settings > About phone > tap Build number x7; Developer options: USB debugging ON.
adb devices -l                                     # accept the RSA prompt on the phone

# Wireless debugging (Android 11+): Developer options > Wireless debugging > Pair device with pairing code
adb pair 192.168.1.50:37123                        # enter the 6-digit code (pairing port differs from connect port)
adb connect 192.168.1.50:41234                     # port shown on the Wireless debugging screen
adb mdns services                                  # optional: discover

# --- 3. Google Android CLI (primary) ---
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash   # user-local [V]
android update
echo '--sdk=/home/gubba/Android/Sdk' >> ~/.androidrc    # pin SDK [V: ~/.androidrc supports flags]
echo '--no-metrics' >> ~/.androidrc                     # opt out of telemetry [U: whether --no-metrics is accepted in .androidrc]
cd ~/AndroidStudioProjects/GlyphWizard && android init   # installs the android-cli skill for agents [V]
android studio check                                    # with Studio open on GlyphWizard

# --- 4. mobile-mcp (primary, optional) - project scope so it's versioned with the repo ---
cd ~/AndroidStudioProjects/GlyphWizard
claude mcp add --scope project mobile-mcp -- npx -y @mobilenext/mobile-mcp@1.0.9    # pin a version, not @latest
#   or per-user:  claude mcp add --scope user mobile-mcp -- npx -y @mobilenext/mobile-mcp@1.0.9
claude mcp list

# --- 5. Fallbacks (only if wanted) ---
curl -fsSL "https://get.maestro.mobile.dev" | bash && claude mcp add --scope project maestro -- maestro mcp
# Studio: Settings > Plugins > Marketplace > "MCP Server" (JetBrains, id 26071) -> install 262.9437.214
#         then Settings > Tools > MCP Server > Enable > Auto-Configure "Claude Code" (project-level)
# Studio: Settings > Plugins > Marketplace > "Claude Code [Beta]"   (or run /ide in Claude Code)
```

**Claude Code scopes [V: code.claude.com/docs/en/mcp]:** `local` (default; you, this project; stored in `~/.claude.json`), `project` (writes `.mcp.json` at the repo root to commit; Claude asks for approval on first use; reset with `claude mcp reset-project-choices`), and `user` (you, all projects). Use `--env K=V` / `-e` for environment variables, and put `--` before the server command. MCP output is capped by `MAX_MCP_OUTPUT_TOKENS` (default 25k; a warning appears above 10k). Larger results spill to a file.

**Permissions (`.claude/settings.json` in the repo).** Reduce prompts for read-only and observational actions, and keep a human in the loop for destructive ones:
```json
{
  "permissions": {
    "allow": [
      "mcp__mobile-mcp__*",
      "Bash(adb devices *)", "Bash(adb exec-out screencap *)", "Bash(adb shell uiautomator dump *)",
      "Bash(adb shell input *)", "Bash(adb logcat -d *)", "Bash(adb shell wm size)",
      "Bash(android screen *)", "Bash(android layout *)", "Bash(android studio *)",
      "Bash(magick *)"
    ],
    "ask": [
      "Bash(adb install *)", "Bash(adb uninstall *)", "Bash(adb shell pm *)",
      "Bash(adb shell rm *)", "Bash(adb push *)", "Bash(adb reboot *)", "Bash(android run *)"
    ]
  }
}
```
Rules are matched per sub-command (a compound `a && b` must match both). Allow globs for MCP tools must start with a literal `mcp__<server>__`. For a narrower grant, list mobile-mcp's tools individually. **[V: permissions docs]** Note that `Bash(adb shell input *)` lets the agent tap anything on the phone, including outside the app.

---

## 5. How the agent uses it (inner loop)

1. **Build and deploy:** `./gradlew :app:installDebug` (or `android run --use-delta-install`), then `adb shell am start -n …`. On failure: `android describe` or `mcp__ide__getDiagnostics`.
2. **Look:** `android layout --pretty` first (cheap JSON: text, ids, bounds). Take a screenshot only when appearance matters: `android screen capture --annotate -o /tmp/gw/ui.png`, then `magick … -resize 50%`, then `Read`.
3. **Act:** `android screen resolve --screenshot=/tmp/gw/ui.png --string="input tap #N"` and run the result with `adb shell`. Or use mobile-mcp's element tap/type/swipe tools.
4. **Verify:** re-run `android layout` and assert on text/state. Check `adb logcat -d -s GlyphWizard:V AndroidRuntime:E '*:S'` for crashes.
5. **Glyph:** trigger the mirror broadcast, then read the `GlyphMirror` text frames (or pull `sheet.png` and `Read` it).
6. **Compose UI work without a device:** `android studio render-compose-preview app/src/main/kotlin/…/EditorScreen.kt EditorPreview --output-image-file=/tmp/gw/p.png --print-semantics`, then `Read`. In CI or with Studio closed, use `./gradlew recordRoborazziDebug`.
7. **Regression (later):** save working sequences as Maestro flows and have the agent re-run `maestro test .maestro/` after changes.

---

## 6. Screenshot token cost (the main budget lever)

Per Anthropic's vision docs **[V]**: cost is `ceil(w/28) × ceil(h/28)` visual tokens. **Claude 4.7 and later models (including this Opus 5.5) use the high-resolution tier**: images are downscaled only above a 2576 px long edge or 4,784 tokens.

| Image | Approx. tokens |
|---|---|
| Full-res phone screenshot, about 1080×2400 (check with `adb shell wm size`) | about **3,350** |
| Same at 50% (540×1200) | about **860** |
| Same at 33% (360×800) | about **380**. Text is still mostly legible; check for small fonts. |
| `android layout` default JSON for a typical screen | usually a few hundred to ~2k text tokens [U] |
| Glyph mirror text frame | about 100 |
| Glyph mirror PNG, 312×312 | about 144 |

Images stay in context for the rest of the session, so 30 full-res screenshots cost about 100k tokens. **Default to the layout JSON, downscale screenshots to 50% or less, and crop to the region of interest.** MCP-returned screenshots (mobile-mcp, Maestro) arrive at whatever size the server sends. Prefer their "save to file" variants plus your own downscale when the server offers that option.

---

## 7. Limitations

- **No official MCP for device control or for Android Studio.** Google's supported path is the Android CLI plus skills. Studio's Gemini, Journeys-in-Studio, Layout Inspector, and Preview pane are not externally scriptable except through `android studio …`.
- `render-compose-preview` needs Studio open with this project, and shows preview (layoutlib) fidelity: no real GPU, fonts may differ slightly, no live Glyph hardware.
- **One UiAutomation client at a time:** mobile-mcp/mobilecli vs `uiautomator dump` vs Maestro vs `android layout` [U for the last]. Use one per session.
- Compose accessibility trees only expose what the semantics expose. Add `contentDescription`/`testTag` (with `testTagsAsResourceId = true`) to custom canvases such as the matrix editor, or they appear as one opaque node.
- Secure surfaces (`FLAG_SECURE`) screenshot as black. System dialogs and permission prompts can't always be driven.
- Wireless ADB drops when the phone sleeps or Wi-Fi changes, and the pairing port differs from the connect port.
- The Phone (4a) Pro Glyph SDK path (AOD toys vs `setAppMatrixFrame`) is still partly unverified. See `2026-10-11-targeted-android-platform.md`.

## 8. Security notes (what you are granting)

- **ADB = full shell-user control of the phone:** it can screenshot any app (including banking or 2FA), read shared storage, install/uninstall apps, type into any field, and read all logcat (other apps leak tokens there too). Anything that can run `adb` (the agent, any MCP server, any npm/pip package they pull) gets this. Prefer a dev/test phone profile, keep sensitive apps closed while the agent works, and revoke USB debugging authorizations when you are done.
- **Wireless debugging** exposes adbd on your LAN (pairing-protected). Turn it off on untrusted networks. Never expose port 5555 (legacy `adb tcpip`).
- **MCP servers run as you on the host,** with your files and network. `npx -y pkg@latest` and `uvx --from git+…` run whatever the registry serves today. **Pin versions** (done above) and prefer well-maintained repos. mobilecli also installs and runs an on-device helper (`DeviceServer`).
- **Android CLI:** a closed binary from Google with default telemetry (opt out with `--no-metrics`). `curl | bash` install: read the script first.
- **JetBrains MCP Server plugin:** exposes `execute_terminal_command` and run configurations. Leave "Brave mode" (run without confirmation) **off**. The Claude Code JetBrains plugin binds to loopback only by default; keep "Accept connections from all network interfaces" off.
- **Debug Glyph receiver:** debug source set only, permission-guarded, never in release builds.
- **Webcam capture** can also capture you and your room. Crop before sending to the model, and don't leave a capture loop running.

---

## Learning aside: ADB and UIAutomator

**ADB** is a client/server trio: the `adb` client on your PC talks to a local **adb server** (port 5037), which talks to the **adbd** daemon on the phone over USB or TCP. That is why version mismatches restart the server and why `adb shell` runs as the limited `shell` user. `exec-out` streams raw binary (PNGs) without terminal mangling, unlike `shell`. **UIAutomator** reads the screen through Android's **accessibility layer** (`UiAutomation`), not the View tree directly. That is why Compose UIs appear as semantics nodes (text, content descriptions, test tags) and why only one automation client can attach at a time.
Links: [ADB docs](https://developer.android.com/tools/adb) · [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator) · [Compose semantics & testTagsAsResourceId](https://developer.android.com/develop/ui/compose/accessibility/semantics) · [Wireless debugging](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi)

---

## Sources
- Android CLI: https://developer.android.com/tools/agents/android-cli · release notes https://developer.android.com/tools/agents/android-cli/release-notes · download https://developer.android.com/tools/agents/android-cli/download · commands `screen_capture`, `layout`, `studio`, `studio_render-compose-preview` under https://developer.android.com/tools/agents/android-cli/commands/ · Journeys https://developer.android.com/tools/agents/android-cli/journeys
- Press [S]: https://techcrunch.com/2026/05/19/agentic-app-coding-gets-an-upgrade-with-googles-release-of-android-cli/ · https://www.infoq.com/news/2026/05/agent-friendly-android-cli/
- Android Studio Rabbit 1 stable: https://developer.android.com/studio/releases · Quail timeline [S]: https://androidstudio.googleblog.com/
- mobile-mcp: https://github.com/mobile-next/mobile-mcp · mobilecli: https://github.com/mobile-next/mobilecli
- Maestro MCP: https://docs.maestro.dev/getting-started/maestro-mcp · install: https://docs.maestro.dev/maestro-cli/how-to-install-maestro-cli
- Appium MCP: https://github.com/appium/appium-mcp
- CursorTouch Android-MCP: https://github.com/CursorTouch/Android-MCP · DroidMind: https://github.com/hyperb1iss/droidmind · mobile-use: https://github.com/minitap-ai/mobile-use · android-tools-mcp: https://github.com/amsavarthan/android-tools-mcp
- JetBrains MCP Server: https://www.jetbrains.com/help/idea/mcp-server.html · Marketplace https://plugins.jetbrains.com/plugin/26071-mcp-server (compatibility checked via the `plugins.jetbrains.com/plugins/list?pluginId=com.intellij.mcpServer&build=AI-262.9437…` API)
- Claude Code: MCP https://code.claude.com/docs/en/mcp · permissions https://code.claude.com/docs/en/permissions · JetBrains https://code.claude.com/docs/en/jetbrains
- Vision token cost: https://platform.claude.com/docs/en/build-with-claude/vision
- Compose screenshot testing: https://developer.android.com/studio/preview/compose-screenshot-testing · Roborazzi https://github.com/takahirom/roborazzi · Paparazzi https://github.com/cashapp/paparazzi · scrcpy https://github.com/Genymobile/scrcpy
- Android Management API MCP (irrelevant; fleet policy): https://developers.google.com/android/management/use-android-management-mcp
- Firebender [S]: https://plugins.jetbrains.com/plugin/25224-firebender
- GitHub stars, licenses, release dates: GitHub REST API, queried 2026-10-11.
