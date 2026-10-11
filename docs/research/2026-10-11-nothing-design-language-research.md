# Nothing design language: research and a "flavour palette" for GlyphWizard

Date: 2026-10-11 · Context: D43 (human designers first; the design language is specified up front). See also `2026-10-11-app-foundations-research.md` (theming, font licence notes) and `2026-10-11-sound-editor-research.md` Part B (editor UX).

**Goal.** GlyphWizard should feel at home on Nothing OS without copying Nothing. We take *flavours* (principles, proportions, habits). We do not take *assets* (fonts, logos, icons, wallpapers, sounds) or names that suggest we are Nothing.

**Evidence markers:**
- **[V]** verified on a fetched primary page.
- **[S]** secondary: press, an aggregator, or a search-result snippet.
- **[U]** unverified: an observation or recollection from screenshots, not confirmed by a source this session.

---

## TL;DR

- **Nothing's look in one line:** a monochrome canvas, dot-matrix display type used sparingly, a neutral grotesk for reading, small monospace labels, one red "interrupt" accent, round and pill geometry with hairline detail, and mechanical, ticking motion.
- **Fonts:** Nothing's own fonts are proprietary. These are Ndot (NDot) and NType 82 / NType 82 Mono, made by Colophon with Seventy Agency [S]. LL Lettera Mono, used on the website, is a commercial Lineto font [S].
  - **OFL stand-ins:** Doto (round dots on a 6×10 grid, variable dot size and roundness) [V], Space Grotesk [S], Space Mono [U], Inter [S], Departure Mono [S].
  - **Matrix text:** Tom Thumb (3×5, CC0 / CC-BY 3.0) [S], or our own 5×7 font.
- **Legal:** "NOTHING" is a registered US mark. Nothing also filed **"GLYPH"** in US class 9 in 2023, still pending per an aggregator [S].
  - **Flag:** the app name *GlyphWizard* uses that word. The community precedent is tolerant (Glyph Museum, Glyphify, GlyphType, GlyphMarquee) but grants no rights.
  - **Do:** ship a non-affiliation disclaimer, and keep a fallback name in reserve.
- **Recommendation:** Direction **B "Lab Bench"** as the base. Borrow Direction **C** only inside the editor and LED preview, where dots are the subject matter rather than decoration. The user and designers choose (D43).

---

## 1. Core principles, as Nothing and the press state them

| Principle | Evidence | What it means for us |
|---|---|---|
| **Strip back; start again** | Adam Bates (Design Director): "Nothing is all about starting again, stripping back, and not taking anything for granted" [V, DesignWanted] | Remove chrome before adding it. Every element must earn its place |
| **Typography reduced to its simplest form** | The dot font "was actually the result of thinking about stripping typography back to its simplest form", from experiments with a handheld label printer [V, DesignWanted] | Dot type is a *reduction* (the fewest marks that read), not ornament. Use it where reduction is the point |
| **Transparency / honesty of mechanism** | Transparent backs and visible internals; "we often have to design 10 or 20 [surfaces]… we have to become engineers" [V, DesignWanted]; Disegno describes products "rich in transparent plastic and visible circuitry" [V] | In software: show the real thing, such as grid coordinates, frame numbers, values and LED states. Avoid metaphors that hide the mechanism |
| **Process, material and technology over a rulebook** | Bates describes the language as driven by processes, materials and technology, not fixed rules [S, Disegno snippet] | We can be "Nothing-ish" by method rather than by copying a style sheet |
| **Utility first, then delight** | "The origin of the glyphs was to look at the fundamental utility of a phone, which is to communicate" [V, DesignWanted]; Vignelli's NYC subway map cited as a Glyph influence [S, Dezeen snippet] | Use systematic, diagrammatic clarity, the way a subway map is clear |
| **Calm / fewer distractions** | Monochrome icons are meant to reduce "the hierarchy of distractions" [S]; the Phone (1) review says greyscale removed "the pitfalls… of a colourful and animated interface" [S]; there is a system Monochrome UI mode [S, GSMArena] | Colour is the exception, not the default |
| **Retro-computing roots** | The brand fonts draw on "raw digital fonts and colours from IBM's 1980s mainframe era" [V, Fonts In Use]. NType 82 is named after the Olivetti Lexikon 82 typewriter [S, brand-guidelines snippet] | Industrial and typewriter references fit: mono labels and spec-sheet layouts |
| **Playful** | Glyph Toys, games on the matrix and Playground are positioned as "a space to play" [S]. Disegno: "You look like you're having too much fun" (feedback Bates received) [V] | Allow one moment of surprise per screen |
| **The community's attachment to dots** | Removing N-Dot in the OS 3.0 beta caused backlash. Carl Pei said they "should have evolved [the] design language more gradually", and N-Dot returned [S] | The dot motif is the most recognised marker. Omitting it entirely reads as "not Nothing" |

There is a lineage note: Nothing's early design language built on Teenage Engineering's [V, Disegno]. This explains the shared "lab instrument" feel: mono labels, bracketed values and orange or red signal colours.

---

## 2. Concrete visual vocabulary

### 2.1 Typography
- **Brand typefaces:** NDot (dot-matrix), NType 82 and NType 82 Mono, by Colophon Foundry with Seventy Agency [V, Fonts In Use]. The web also uses **LL Lettera Mono** (Lineto) [V, Fonts In Use].
- **Website type scale** (third-party teardown) [S, Fudge]:

| Role | Font | Notes |
|---|---|---|
| Display and body | NType 82 | Display 4 rem with −0.02 em tracking |
| Labels, buttons, metadata | Lettera Mono | 0.75 rem with **+0.05 em tracking** |
| Navigation and brand marks | Ndot | +0.08 em tracking |

- **The OS over time:**
  - **Phone (1):** Roboto for UI. The dot font appears "sparingly" in Settings headings, lock screen, charging screen and widgets [S, GSMArena].
  - **OS 3.0:** Inter replaced Roboto. NType 82 replaced N-Dot in Settings and Nothing apps. NDot 57 returned for Settings tiles and the lock-screen clock after backlash [S].
  - **OS 4.0:** refreshed clock faces [S].
- **The pattern to borrow:** three registers.
  1. **Dot display** for hero numbers, clocks and titles. One per screen.
  2. **Neutral grotesk** for reading.
  3. **Small monospace uppercase with wide tracking** for metadata and labels.

### 2.2 Colour
- **Website tokens** [S, Fudge]: ink `#000000`, canvas `#FFFFFF`, surface `#F5F5F5`. There are also seasonal promo accents (lime `#E1FF00`, navy CTAs). The website is marketing, so the OS is the better reference.
- **OS:** black, white and grey, with a monochrome icon pack [S, GSMArena]. OS 4.0 updated dark mode "with enhanced contrast" [S, GSMArena].
- **Red:** there is no official accent spec [U].
  - **Hardware red** is verified: a red recording LED on the Phone (1) Glyph, and a camera "red dot" recording indicator on Phone (3) [S].
  - **In the OS** [U, from screenshots]: red appears as a single dot or element, such as the recorder button, an active state, or a clock detail.
  - **A community skill file** formalises it as `#D71921`, "an interrupt — look HERE, NOW", one per screen, never part of the hierarchy [V, nothing-design-skill]. Treat that hex as a community estimate, not Nothing's value.
- **Greys by opacity rather than many hues:** 100 / 90 / 60 / 40 % text levels [V, nothing-design-skill]. This matches the OS feel [U].

### 2.3 Iconography
- **System:** monochrome, uniform-weight icons. OS 4.0 made native icons "cleaner, more minimal" and aligned status-bar icons with Android 16 [S].
- **Brand material** uses dot-built pictograms [U].
- **For us:** monoline outline icons at one stroke weight, with optional dot-built icons on a small grid (see Direction C). No filled or multi-colour icons [V, the skill's anti-patterns].

### 2.4 Shapes and radii
- **Quick Settings:** the large toggles changed from circles to **rounded squares** in OS 2.5, and that persists [S, GSMArena]. OS 4.0 added wider tiles [S].
- **Widgets:** circular and rounded-square widgets, many 1×1 and 2×1 sizes (OS 4.0) [S]. Circular clock and dial widgets are frequent [U].
- **Web:** pill buttons, stadium inputs and 1 px hairline borders [S, Fudge].
- **Community reading:** cards ≤ 16 px radius; buttons either pill or "technical" 4–8 px [V, nothing-design-skill].
- **The move:** soft outer geometry (circles, pills) combined with hairline technical detail.

### 2.5 Grid, spacing and whitespace
- **Generous whitespace:** big numbers with little else.
- **Community scale:** 4–8 / 16 / 32–48 / 64–96 px tiers. Dividers only when spacing can't do the job [V, nothing-design-skill]. The web uses a 4 px base unit and a 1.5 rem margin around dotted rules [S, Fudge].

### 2.6 Widgets
- **Counts:** 18 custom widgets on Phone (3a) and Phone (3), and 24 on CMF Phone 2 Pro [S]. They are clocks, weather, calendar, contacts and pedometer, styled monochrome with dot numerals [S/U].
- **OS 4.0:** a widget drawer and AI "Playground" widgets [S].
- **Pattern:** one big dot-matrix datum, one small mono label, and lots of black.

### 2.7 Motion and haptics
- **Interactive dot animations** on the lock screen were teased for OS 3.0 [S, Notebookcheck headline; article blocked]. Glyph light sequences are synced with audio and vibration in stock ringtones [S].
- **Community reading:** "mechanical and percussive… clicks and ticks, not swooshes"; ease-out only, no spring or bounce [V, nothing-design-skill]. This is a reasonable interpretation, not a stated Nothing rule [U].
- **Haptics:** the community asks for *better* haptics, and Composer ringtones have no custom haptics [S]. So haptic polish is an area where we can *exceed* the reference, not just match it.

### 2.8 Sound
- Stock ringtones and notifications are short, electronic and "bleepy" [U], with bespoke Glyph sync. Glyph Composer has a Swedish House Mafia sound pack [S].
- **Do not bundle or imitate Nothing's sounds.** If UI sounds are used at all, use original short ticks. Off by default.

### 2.9 Dark vs light
- Dark is the "hero" mode in marketing and screenshots [U]. Light mode is near-white.
- **Community reading:** dark = OLED black, light = warm off-white [V, nothing-design-skill]. Support both. Design dark first, because the LED matrix preview reads best on black.

### 2.10 Dots as texture
- **Dotted rules** between headings and body (web) [S, Fudge].
- **"Extra dots elsewhere"** in OS 3.0 to compensate for less dot type [S, Android Authority snippet].
- **Dot loaders and progress indicators** [U].
- **The rule we infer:** dots are the *atomic unit* (an echo of Glyph LEDs). They appear as type, rules, indicators and progress, but rarely as an all-over wallpaper in the core UI.

---

## 3. What makes it feel "Nothing" rather than generic minimal: the signature moves

1. **Dot-matrix display type, one hero per screen.** Used for numbers, clocks and titles, never for body text.
2. **Three type registers:** dot display, neutral grotesk and small caps-mono labels with +5–8 % tracking. The mono labels make it read "instrument", not "Scandinavian minimal".
3. **A monochrome canvas with greys made by opacity.** True black in dark mode; near-white in light mode.
4. **Exactly one red interrupt.** A recording dot, a live state or the current position. Never decoration, never a brand wash.
5. **Dots as the atomic unit:** dotted dividers, dot progress, dot pager and dot loaders.
6. **Round and pill geometry with hairline detail:** circular or rounded-square tiles, pill buttons and 1 px rules. Sparing shadows; no gradients [V, skill anti-patterns].
7. **Exposed mechanism:** bracketed status such as `[SAVED]` and `[REC]` [V, skill], coordinates, frame counters and raw values shown plainly, like a spec sheet.
8. **Mechanical motion:** stepped and ticking, with haptic clicks, rather than fluid springs.
9. **Generous emptiness:** a big datum and a lot of black.
10. **Playful toy moments:** one small surprise, such as an animated dot mascot on an empty state.

Generic minimal has moves 3 and 9 only. Moves 1, 2, 4, 5 and 7 are what make the "Nothing" read.

---

## 4. Legal and brand boundaries

### 4.1 Trademarks
- **NOTHING:** registered US word mark (Serial 90520106, registered 18 Jun 2024) [S, uspto.report / Trademarkia]. Other registrations are cited by third parties [S].
- **GLYPH:** filed by Nothing Technology Ltd, US Serial 98239520, 25 Oct 2023, class 9, "live / pending" per Trademarkia [S]. No EUIPO or UKIPO record was found [S]. Check TSDR directly before release.
- **Ndot / NType:** treated as Nothing trademarks by community projects [S].
- **Implications for GlyphWizard:**
  - **The name contains "Glyph".** Mitigations:
    - (a) Describe the product nominatively: "an animation designer **for the Glyph Matrix on Nothing Phone (3) / (4a) Pro**".
    - (b) Never use Nothing's logo, wordmark or dot-style "NOTHING" lettering.
    - (c) Add a clear disclaimer in the app About screen, the README and the store listing: *"Independent community project. Not affiliated with or endorsed by Nothing Technology Limited. Nothing and Glyph are trademarks of their respective owner."*
    - (d) Keep a fallback name in the decisions log in case Nothing objects.
  - **Precedent:**
    - Glyph Museum uses exactly this disclaimer pattern [S, Play listing and pauwma.com], and is even listed on Nothing's own Playground [S].
    - Glyphify, GlyphType and GlyphMarquee also use the word [S].
    - This shows tolerance, not permission.
  - **Do not imitate Nothing's app names or packages:** no "Nothing X", "Essential", "Glyph Composer" or "Playground"-style naming.

### 4.2 Fonts
- **Ndot / NDot 57 / NType 82 / NType 82 Mono:** proprietary, by Colophon for Nothing. No public licence. **Never bundle them, or fonts extracted from firmware.**
- **LL Lettera Mono:** a commercial Lineto font. Don't bundle it.
- **NDOT 45/47 "(inspired by NOTHING)"** by Interactivate on FontStruct is OFL. But the author notes: "Many letters are possibly well within the copyright claims, if Nothing Technology Limited owns a copyright of a similar font" [V].
  - **Font design copyright varies by jurisdiction.** In the US, typeface designs are generally not copyrightable, but font *software* is; the UK and EU are more protective [U, general legal knowledge, not advice].
  - **Verdict: avoid NDOT 47.** It is explicitly a clone, sits closest to trade dress, and Doto gives the same feel with a clean provenance.
- **A cautionary precedent:** pauwma's GlyphMatrixEditor (GPL-3.0 repo) offers text-to-pixel in "Ndot, NType, Ranyth" [V] with no visible font-licence note. Don't copy this.
- **A good precedent:** the community "nothing-design" skill substitutes **Space Grotesk + Space Mono + Doto** [V].

### 4.3 Icon packs, widgets, wallpapers, sounds
- Don't extract or redistribute Nothing's icon pack, widget art, wallpapers, ringtones, boot or charging animations, or Glyph Composer sound packs.
- Don't re-create Nothing's own stock Glyph Toys pixel-for-pixel as built-in templates, for example their battery or clock toy designs [U on their copyright status; avoid anyway].

### 4.4 SDK
- The GlyphMatrix SDK is closed-source under a proprietary licence. Commercial use needs Nothing's permission [S, LICENSE.md]. Our non-commercial status fits.
- The SDK README gives no branding or trademark guidance. Preview images should be SVG, and matrices are 25×25 / 13×13 [V]. Questions go to GDKsupport@nothing.tech [V], which we could use to ask about naming.

### 4.5 Trade dress
- We are a separate app, not an OS skin, so the trade-dress risk is low.
- **Rule of thumb:** a user should never mistake a screenshot of GlyphWizard for a Nothing first-party app. Use our own wordmark, our own accent hex and our own dot font. Avoid the exact `#D71921` + Ndot + NType triad.

---

## 5. Fonts we can legitimately use

### 5.1 Dot-matrix and pixel display fonts

| Font | Look | Licence | Fit |
|---|---|---|---|
| **Doto** (Óliver Lalan, Google Fonts) | **Round dots** on a 6×10 grid. Variable axes for dot size and roundness. Monospace. Latin Core | **OFL-1.1** [V, GitHub OFL.txt] | **Best "Nothing-ish" display face.** Round dots echo LEDs. Tune the axes to differ from Ndot. [Repo](https://github.com/oliverlalan/Doto) · [GF](https://fonts.google.com/specimen/Doto) |
| **Bitcount** (Petr van Blokland, Google Fonts) | Pixel family with an ELSH axis that morphs pixel shape, including round elements. Mono, Prop and Grid variants | OFL-1.1 [S] | A good alternative with a distinct voice. Check the round-dot rendering [U] |
| **DotMatrix** (Stefan Schmidt) | Dot-matrix printer style | OFL-1.1 [S] | Retro-printer flavour. [Repo](https://github.com/Gissio/font_DotMatrix) |
| **Departure Mono** (Helena Zhang) | Square-pixel monospace, "lo-fi techy" | OFL [S] | **Excellent for mono labels and counters** (Direction B/C). [Repo](https://github.com/rektdeckard/departure-mono) |
| **Tiny5** (Stefan Schmidt), **Micro 5**, **Silkscreen** (Jason Kottke), **Jersey 10**, **Pixelify Sans**, **DotGothic16** (Fontworks) | Square-pixel display | OFL on Google Fonts [S for Tiny5 and Jersey 10; U for the others, so confirm on each GF specimen] | Square pixels read "retro game" more than "Nothing". Use only as accents |
| **NDOT 45/47** (Interactivate) | Close Ndot clone | OFL with the author's similarity caveat [V] | **Avoid** (see §4.2) |

### 5.2 Fonts for text *on the matrix* (13×13 and 25×25)
These are bitmap data, rendered into the LED grid, not Android `FontFamily` objects.

- **13×13 (Phone 4a Pro, 137 visible cells):**
  - Two lines of 5-px-tall text fit only in the central rows, because of the circular mask.
  - Scrolling one line of 5–7 px is the practical default.
- **Tom Thumb (3×5, in a 4×6 cell):** CC0 or CC-BY 3.0, per Robey Pointer's relicensing; the original is BSD-3 by Brian Swetland [S]. Ideal for 13×13. Keep the attribution file anyway.
  [OpenGameArt](https://opengameart.org/content/tom-thumb-tiny-ascii-font-3x5) · [TTF port](https://github.com/gheja/tom-thumb-ttf)
- **Our own 5×7 (and 3×5) font:** a ~95-glyph ASCII set is a few hours of design work. It gives zero licence risk and a signature look. It can also be the *same* bitmap used to render dot headings in the UI (Direction C). **Recommended.**
- **25×25 (Phone 3):** a 5×7 or 6×10 font (Doto's grid rasterised at 1 dot = 1 LED) works. Rasterise Doto only if the OFL terms are respected; embedding rendered bitmaps is fine, and the font file just needs its licence shipped.
- **Spleen** (BSD-2, 5×8 and up) and **Pixel Operator** (CC0): licences per memory [U]. Check before use.

### 5.3 Neutral body and label fonts

| Font | Licence | Why |
|---|---|---|
| **Inter** | OFL | What Nothing OS 3 reportedly moved to [S]. Maximally "native", but also generic |
| **Space Grotesk** (Florian Karsten, derived from Colophon's Space Mono) | OFL-1.1 [S] | Quirky technical grotesk, closest in spirit to NType. Same foundry lineage as Nothing's fonts (Colophon) without being theirs |
| **Space Mono** (Colophon) | OFL on Google Fonts [U, likely] | Mono labels; natural partner of Space Grotesk |
| **IBM Plex Sans / Plex Mono** | OFL | "IBM mainframe" heritage, which Fonts In Use cites as Nothing's inspiration [V]. Sober and highly legible |
| **JetBrains Mono / Geist Mono** | OFL | Clean mono labels |

---

## 6. Expressing it in Material 3 / Compose

- **Dynamic colour:** set `dynamicColor = false` everywhere, so the wallpaper never tints the app.
- **`ColorScheme` (dark-first):**

| Token | Value |
|---|---|
| `background`, `surface` | `#000000` |
| `surfaceContainerLowest…Highest` | grey ramp, e.g. `#0A0A0A / #111 / #161616 / #1C1C1C / #232323` |
| `onSurface` | `#EDEDED` (not pure white; see §7) |
| `onSurfaceVariant` | ~60 % (≈ `#999999`) |
| `outline` | ~25 %; `outlineVariant` ~12 % |
| `primary` | **white** (primary actions are white pills with black text; that is the Nothing move, not a coloured primary) |
| `onPrimary` | black |
| `secondary` | grey |
| `tertiary`, `error` | our red (the interrupt) |
| `primaryContainer` | `#1C1C1C` |

  - **Light scheme:** mirror it, with background warm off-white (≈ `#F2F1EE`) and primary black.
  - **Watch the defaults:** M3 components default to `primary` in many places, such as switches, sliders, selected chips and progress. With white or black as primary these render monochrome, which is what we want. Audit `FloatingActionButton`, `NavigationBar` indicator (`secondaryContainer`) and `TextField` focus colours.
- **`Typography`:**
  - `display*` / `headlineLarge` → dot font (Doto or our own), only through specific composables.
  - `headlineSmall` / `title*` / `body*` → Space Grotesk, Inter or Plex Sans.
  - `label*` → mono, uppercase via the composable (Compose `TextStyle` has no text-transform), with `letterSpacing = 0.06–0.08.em`.
- **`Shapes`:**
  - extraSmall 4 dp (technical chips)
  - small 8 dp
  - medium 16 dp (cards, at most)
  - large 24 dp (sheets)
  - extraLarge `CircleShape`-like pills
  - Buttons: override `ButtonDefaults.shape` to a pill (already the M3 default).
- **Motion:** use the M3 `MotionScheme.standard()` rather than `expressive()` (Material 3 Expressive's springs bounce) [U, verify API name in the BOM we pin].
  - For "mechanical" motion, use `tween` with `FastOutSlowIn` / ease-out, or **stepped** `keyframes` (sample at 12–15 fps) for dot reveals.
  - Respect `Settings.Global.ANIMATOR_DURATION_SCALE`.
- **Haptics:**
  - Discrete moments: `HapticFeedbackConstants.CLOCK_TICK`, `SEGMENT_TICK` / `SEGMENT_FREQUENT_TICK` (API 34+), `CONFIRM` / `REJECT`.
  - Richer feedback: `VibrationEffect.Composition` primitives (`PRIMITIVE_TICK`, `PRIMITIVE_LOW_TICK`, `PRIMITIVE_CLICK`), with a fallback on devices without primitive support.
- **Custom components** (M3 has no equivalent):
  1. `DotMatrixText`: draws text from a bitmap font as circles on a Canvas, giving exact dot geometry independent of font hinting. Needs a `contentDescription`.
  2. `LedMatrixPreview`: the 13×13 / 25×25 circular-masked LED preview with per-LED brightness, glow and an "off-LED" ghost. This is the app's hero.
  3. `DottedDivider`: `drawLine` with round caps and `PathEffect.dashPathEffect(floatArrayOf(0f, gap))`.
  4. `DotProgress` / `DotLoader`: a row or ring of dots filling in steps.
  5. `StatusTag`: bracketed mono status such as `[SAVED]` and `[REC ●]`.
  6. `DotGridBackground`: a `Modifier.drawBehind` faint dot lattice for the canvas area only.
  7. `DotPagerIndicator` and a segmented dot slider for frame or brightness scrubbing, with tick haptics per segment.
  8. A pill `SegmentedButton` variant with mono labels (M3 `SegmentedButton` can be styled; it may not need to be fully custom).

---

## 7. Accessibility tensions and mitigations

**Dot fonts**
- They lose legibility below ~20 sp and at low contrast.
- **Mitigation:**
  - Use dot fonts only for display at ≥ 24 sp, ideally ≥ 32 sp.
  - Never use them for body text, buttons or anything the user must read to act.
  - Always pair them with a sans equivalent nearby or provide a `contentDescription`.
  - Scale them with `fontScale` but cap them so they don't overflow.
  - Offer a setting, "Plain headings", that swaps the dot font for the sans.

**Red contrast** (WCAG, computed this session)

| Red | On `#000` | On `#1A1A1A` | On `#FFF` | On `#F2F0EB` |
|---|---|---|---|---|
| `#D71921` (community estimate) | 4.05 | 3.36 | 5.18 | 4.55 |
| `#FF3B3B` | 5.94 | 4.92 | 3.53 | 3.10 |
| `#FF4D4D` | 6.42 | 5.32 | 3.27 | 2.87 |
| `#C8161E` | 3.59 | 2.97 | 5.85 | 5.14 |

- **What the numbers show:** a deep red passes 4.5:1 on light backgrounds but not on black. A brighter red passes on black but not on light backgrounds.
- **Mitigation:**
  - **Use two reds:** dark theme ≈ `#FF4D4D`-ish, light theme ≈ `#C8161E`-ish. Both should be our own hex, not Nothing's.
  - Red only for non-text indicators (3:1 is enough for UI components) or for large or bold text.
  - Never rely on red alone: pair it with an icon, a word (`[REC]`) or a shape.
  - Red/green colour-blindness: we have no green, so the risk is low. But "red = selected" must also show an outline or fill change.

**Pure black and white**
- Pure white text on pure black causes halation and smearing for some users (astigmatism) and OLED black smear when scrolling [U, widely reported].
- **Mitigation:** use `#EDEDED` or 90 % white text, and keep pure `#000` only for the background. Offer an optional "dim black" (`#0B0B0B`) surface.
- **Mid-grey labels:** 60 % grey (`#999`) on black is 7.4:1, which is fine. The same grey on white is only 2.85:1, so the light theme needs ≈ `#6B6B6B` (5.3:1).

**Small mono uppercase labels**
- Uppercase reduces word-shape recognition.
- **Mitigation:** keep labels short (1–3 words) and ≥ 11 sp with tracking. Never set sentences in caps.

**Motion**
- Stepped and dot-dissolve animations can flicker.
- **Mitigation:** respect reduced-motion and animator scale; keep flashes under 3 Hz. The LED preview playback is user content and is exempt, but add a "pause on open" option.

**Targets**
- The matrix cells (≈ 26 dp) are below 48 dp. The Part B mitigations apply: zoom, offset cursor and circle-aware hit-testing.

**Haptics and sound**
- Always optional, and on by default only for discrete ticks.

---

## 8. The flavour palette: three directions

These are the same app in three dialects, to be shown as mockups next. Each one names its own hexes so it isn't Nothing's.

### Direction A: "Instrument" (subtle and neutral)
*An excellent pro tool that happens to sit well on Nothing OS.*

| Aspect | Spec |
|---|---|
| **Type** | Inter (headings and body); IBM Plex Mono for small labels, sentence case, light tracking. **No dot font in the UI**; dots appear only in the LED preview |
| **Colour** | Dark default: near-black `#0B0B0B`, surfaces `#141414 / #1C1C1C`, text `#EDEDED`, secondary 60 %. Light: `#FAFAF9`. Accent: muted signal red used only for record and destructive actions |
| **Shape** | M3-standard: 12 dp cards, pill buttons, 8 dp chips |
| **Iconography** | Material Symbols Outlined (Apache-2.0), weight 300, grade 0, one size (24 dp) |
| **Motion** | M3 standard motion scheme. No stepped effects. Light haptics on frame scrub |
| **Texture** | None, except the LED preview |
| **Signature moves** | The LED preview as hero; greys by opacity; one red record dot; generous spacing |

- **Pros:** most legible and accessible; lowest legal and trade-dress risk; cheapest (almost pure M3 tokens); ages well; doesn't fatigue in long editing sessions.
- **Cons:** reads as "generic minimal Android app". Loses the dots, the thing the community cares about (see the OS 3.0 backlash). Not memorable in screenshots or on Playground.

### Direction B: "Lab Bench" (balanced; recommended base)
*A bench instrument from the same world as the phone: technical, calm, with one dot moment per screen.*

| Aspect | Spec |
|---|---|
| **Type** | **Display:** Doto, tuned (larger dots, full roundness), only for screen titles, frame counters, the FPS readout and empty-state headlines. One per screen, ≥ 28 sp. **Body/UI:** Space Grotesk. **Labels:** Space Mono or Departure Mono, UPPERCASE, +0.07 em tracking, 11–12 sp |
| **Colour** | Dark (default): `#000000` background, surfaces `#111 / #1A1A1A / #222`, text `#EDEDED`, greys at 60 / 40 / 25 %. Light: warm off-white `#F2F1EE`, text `#111`. Primary actions are **white pills with black text** in dark and the inverse in light. **Our red:** dark `#FF4A3D`-ish, light `#C4161C`-ish (final hexes chosen by designers; not `#D71921`), only for REC, the live/playing LED state and the current-frame marker |
| **Shape** | Pill buttons; 16 dp cards (at most); rounded-square toggle tiles; 1 px hairline outlines at 12–25 %; circles for the colour/brightness swatches and the play button |
| **Iconography** | Monoline outline icons, one stroke weight: Material Symbols weight 200–300, or Phosphor Light (MIT). A handful of custom dot icons (play/record/LED) drawn by us |
| **Motion** | Ease-out tweens 150–250 ms, no bounce. **Stepped dot loaders** (`DotLoader`). Frame-strip scrub gives `SEGMENT_TICK` haptics per frame. "Send to LEDs" countdown as 3 → 2 → 1 in dot numerals with tick haptics |
| **Texture** | `DottedDivider` between sections; a faint dot lattice *only* behind the drawing canvas (doubles as the cell grid); bracketed status tags `[SAVED]`, `[REC ●]`, `[13×13]` |
| **Signature moves** | 1 dot hero per screen · caps-mono labels · white-pill primary · one red interrupt · dotted rules · exposed values (coordinates, frame #, ms) · tick haptics |

- **Pros:** recognisably "at home" on Nothing OS without using any Nothing asset. All fonts are OFL. Mostly M3 tokens plus ~6 small custom composables. The dot font is confined to large sizes, so accessibility is manageable. Strong screenshots.
- **Cons:** Space Grotesk + Space Mono + Doto is becoming the *standard* "Nothing-inspired" kit (the community skill uses it [V]), so it is less distinctive. Mitigate with our own tuning or our own dot font. Needs discipline (the one-dot-hero rule) or it drifts to C.

### Direction C: "Full Matrix" (bold, dot-first)
*The whole app is drawn as if on an LED matrix. The editor and the UI share one visual atom.*

| Aspect | Spec |
|---|---|
| **Type** | **Our own GlyphWizard 5×7 / 7×9 dot font** (designed in-house, the same bitmap used for matrix text), rendered by `DotMatrixText` for all headings, tab labels and numbers. Body: Space Grotesk (accessibility floor). Labels: Departure Mono |
| **Colour** | Dark-only by default (light as an option): pure black, "off-LED" ghost dots at 8–10 % white, "on" dots at `#EDEDED`. Red = **the cursor/playhead/live LED** only. Optional per-user "LED tint" (white, amber, red) for the preview only |
| **Shape** | Circles everywhere: circular FAB, dot-cell buttons, 999 pills, round segmented controls. Cards are frameless (spacing only), with dotted outlines where needed |
| **Iconography** | **Custom dot icons on a 9×9 grid**, drawn by us. They double as starter sprites and stamps users can drop onto their animations |
| **Motion** | Everything moves like a matrix: dot-dissolve screen transitions, scanline reveals, stepped 12 fps micro-animations, a "boot" dot sweep on launch, and tick or low-tick haptic compositions on every discrete change. Optional original UI click sounds (off by default) |
| **Texture** | An app-wide faint dot lattice background; empty states are small animated matrix scenes (playful "toy" moments) |
| **Signature moves** | The UI *is* a matrix · icons = sprites · dot font = matrix font · LED ghosting · dot-dissolve transitions · red playhead · playful empty states |

- **Pros:**
  - The most distinctive and on-theme possible look, for an app about dots.
  - Icons and fonts become user content: a design system that feeds the product.
  - Our own font gives zero font-licence risk and a unique identity.
  - Delights the Nothing community.
- **Cons:**
  - The heaviest build: a custom font, ~40 dot icons, a transition system and many custom components.
  - The hardest accessibility work: dot headings everywhere, flicker risk and lower-contrast "off" dots.
  - Long editing sessions may fatigue.
  - Closest to Nothing's trade dress in spirit.
  - It can compete with the user's artwork: the UI dots versus the canvas dots blur the figure/ground.

### Recommendation (for discussion, per D43)
- Start from **B**.
- Within the **editor, LED preview, "send to LEDs" countdown and empty states**, allow **C's** behaviours: dot icons, ghost LEDs and stepped transitions.
- Keep app chrome (settings, lists, dialogs) at B, or A where density matters.
- Commission or draw **our own dot font early**. It serves the matrix text renderer anyway, and removes the remaining dependency on Doto if we want a unique voice.

---

## Open questions
- **Q1:** Final app name, given the pending GLYPH mark. Ask GDKsupport@nothing.tech informally?
- **Q2:** Doto (tuned) vs our own dot font for UI headings in B?
- **Q3:** Which red hexes (two-tone, as in §7)? Designers to pick after the mockups.
- **Q4:** Ship a light theme in v1, or dark-only?
- **Q5:** UI sounds at all? Proposal: no in v1.

## Sources
- DesignWanted, "Nothing: smartphones are not all the same" (Adam Bates quotes) [V]: https://designwanted.com/nothing-tech-design-smartphones-not-all-the-same/
- Disegno, "The Crit #19: Adam Bates" [V]: https://disegnojournal.com/newsfeed/the-crit-adam-bates
- Dezeen, Adam Bates interview (403; snippet only) [S]: https://www.dezeen.com/2024/02/20/nothing-tech-smartphone-design-adam-bates-interview/
- Fonts In Use, Nothing Phone (NDot, NType 82, Lettera Mono; Seventy and Colophon) [V]: https://fontsinuse.com/uses/59510/nothing-phone
- Fudge teardown of nothing.tech (type scale, colours, radii) [S]: https://fudge.design/share/nothing.tech-design
- Brand-guidelines summary (NType 82 / Olivetti) [S]: https://www.slideshare.net/slideshow/comprehensive-brand-guidelines-for-nothing-design-identity-and-typography/288495910
- NOS 3.0 font changes (Inter, NType 82, NDot 57 return) [S]: https://www.androidfaithful.com/nothing-os-3-0-hands-on/ · https://techissuestoday.com/nothing-ndot-font-nothing-os-3-0/ · https://techibeereview.com/n-dot-font-making-comeback-in-nothing-os-3-0-after-user-demand/
- Android Authority, NOS 3.0 hands-on "Dot matrix re-reloaded" (403; snippet) [S]: https://www.androidauthority.com/nothing-os-3-hands-on-3488739/
- NOS 4.0 coverage [S]: https://m.gsmarena.com/nothing_os_40_announced_with_refined_design_updated_dark_mode_and_quick_settings-news-69546.php · https://www.gizmochina.com/2025/11/22/whats-new-nothing-os-4-0-update-android-16/
- GSMArena Phone (3) software review (icons, widgets, QS shape, Glyph settings) [S]: https://www.gsmarena.com/nothing_phone_3-review-2853p4.php
- GSMArena Phone (1) software review [S]: https://www.gsmarena.com/nothing_phone_1-review-2453p4.php
- Community "nothing-design" skill (Space Grotesk / Space Mono / Doto, `#D71921`, rules) [V; third-party interpretation]: https://github.com/dominikmartn/nothing-design-skill/blob/main/nothing-design/SKILL.md
- GlyphMatrix Developer Kit README and LICENSE [V/S]: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- Glyph Museum disclaimer [S]: https://play.google.com/store/apps/details?id=com.pauwma.glyphmuseum · https://pauwma.com/projects/glyph-museum · https://playground.nothing.tech/detail/toy/eoRe7VEwe4xdh5Zf
- pauwma GlyphMatrixEditor (bundles Ndot/NType text) [V]: https://github.com/pauwma/GlyphMatrixEditor
- Trademarks [S, aggregators]: https://www.trademarkia.com/owners/nothing-technology-limited · https://uspto.report/TM/90520106
- NDOT 47 (FontStruct, OFL plus caveat) [V]: https://fontstruct.com/fontstructions/show/2047444/ndot-45-inspired-by-nothing-1
- Doto (OFL) [V]: https://github.com/oliverlalan/Doto
- Bitcount [S]: https://github.com/google/fonts/issues/5468
- DotMatrix [S]: https://github.com/Gissio/font_DotMatrix
- Departure Mono [S]: https://github.com/rektdeckard/departure-mono
- Space Grotesk [S]: https://github.com/floriankarsten/space-grotesk
- Tom Thumb [S]: https://opengameart.org/content/tom-thumb-tiny-ascii-font-3x5
- Google Fonts pixel fonts [S]: https://fonts.google.com/specimen/Tiny5 · https://fonts.google.com/specimen/Silkscreen
- Red recording LED (Phone 1) [S]: https://www.androidcentral.com/phones/what-can-you-do-nothing-phone-1-lights-functionality
- Haptics community threads [S]: https://nothing.community/d/9252-nothing-os-haptics-improvements · https://nothing.community/d/9398-haptics-for-ringtones-made-using-glyph-composer
- Contrast ratios: computed locally with the WCAG 2.x relative-luminance formula.
