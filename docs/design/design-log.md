# Design Log

Every visual or UX decision signed off by the user, in order. Mockups live in `.superpowers/brainstorm/` (not committed). Their outcomes are recorded here.

| # | Date | Screen | Decision |
|---|---|---|---|
| UI1 | 2026-10-11 | 01-directions | Base direction: **A "Instrument"** (calm layout, near-black, soft canvas card), with **C's typography** (dot font for titles, numbers and values; own dot font later, Doto as stand-in). **B's footer**: outlined **Preview** (left) + white pill **Play on Glyph** (renamed from "Send to LEDs"). B's button shapes kept. Icons must be far more polished. |
| UI2 | 2026-10-11 | 02-direction-merge | Header info line under the title is **user-configurable** (Settings → Editor). Defaults ON: frame n/total, total length, brush level, cursor x,y, [SAVED]. Defaults OFF: grid size, frame ms, FPS, lit LEDs, flash badge. Principle: **sensible defaults, user-configurable detail** across the app (other areas TBD with the user). |
| UI3 | 2026-10-11 | 02-direction-merge | Frame strip: horizontally scrollable, frames fade out at the edges (no half-cut frames); the current frame is **enlarged + red**. The space below the strip holds frame actions (Add, Duplicate, Onion, ms/frame, Delete). Tools are grouped (more tools via groups). |
| UI4 | 2026-10-11 | 03-editor-wow | Canvas: **pure black LED panel, white outline**, grey translucent surround. |
| UI5 | 2026-10-11 | 03-editor-wow | Wow / signature: **ambient background** = blurred copy of the current artwork behind frosted UI, **static (no pulsing)**; plus **(a)** real LED physics on the canvas (bloom, bleed, warm-up/decay), **(b)** dot-dissolve transitions for big moments only, **(c)** "Play on Glyph" ritual (3·2·1 dot numerals + haptic ticks + flip hint + live mirror), **(d)** tactile per-frame haptic scrubbing, **(e)** living library of tiny animating matrices. (Confirmed in 04.) |
| UI6 | 2026-10-11 | 03-editor-wow | Tool group sub-tools open **in place of the frame strip** (not floating over the canvas); tapping the group tool again closes it and the strip returns. |
| UI7 | 2026-10-11 | 04-editor-states-icons | Icons: **duotone** family at **24 dp** (touch targets ≥ 48 dp); exploring translucent **red** fill. Unlit LEDs: grey dots rejected; alternatives pending. |
| UI8 | 2026-10-11 | 05/06 | **LEDs on screen are flat discs**: lit LED = solid grey whose shade equals its brightness; no gradient, glow, bloom or 3D ("exactly how it looks on the Glyph, no fluff"). Unlit LEDs = hairline rings (~12% white). Canvas matrix approved. |
| UI9 | 2026-10-11 | 07-red-tile | Tool tray: white duotone icons (24 dp); **selected tool = translucent red tile (~18%) with a hairline red edge**, icon white. |
| UI10 | 2026-10-11 | terminal | Wow option (a) "real LED physics" (bloom/bleed) **dropped** in favour of flat true-to-hardware LEDs. Kept: static ambient background, dot-dissolve transitions (big moments only), Play-on-Glyph ritual, tactile scrubbing, living library. |
| UI11 | 2026-10-11 | terminal | Canvas shows **perceived brightness** (how it actually looks on the Glyph) using a calibration curve measured in the M0 hardware spike; exact stored values remain visible in brush/inspector. |
| UI12 | 2026-10-11 | 08-typography | Labels: **Geist Mono, lowercase/sentence case**. Display: dot font, **bold, ~30 sp, caps** (Doto stand-in; own dot font planned). Body: none of Space Grotesk / Inter / IBM Plex Sans / Geist; second round pending. |
| UI13 | 2026-10-11 | 09/10 | **All-mono interface**: body + labels in one monospace family, **user-selectable** in Settings from Sometype Mono, Geist Mono, Reddit Mono, Spline Sans Mono (default pending). Labels lowercase. Check readability on text-heavy screens before locking. |
