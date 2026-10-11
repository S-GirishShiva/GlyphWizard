# Design Tooling for the UI Design Phase: Research

2026-10-11. Inputs: `decisions.md` (esp. D22 superpowers process, D38 quality over deadlines, D43 human designers first, design language + wireframes + full flows up front), `2026-10-11-sound-editor-research.md` Part B (editor UX, text wireframes), `2026-10-11-app-foundations-research.md` §1 (Compose, M3 components + custom monochrome theme, custom-drawn domain widgets), `2026-10-11-dev-tooling-visual-mcp.md` §1.8 (Compose preview → PNG). No installs, no app code.

**The question:** Which tools should Claude (Claude Code CLI, Opus) use to produce GlyphWizard's design language, wireframes of every screen and full user flows, with the user reviewing and choosing, ending in Jetpack Compose code?

**Evidence tags** (same as the other docs):
- **[V]** verified in a primary source fetched today (vendor docs, help centre, changelog, official blog, local plugin source)
- **[S]** secondary source (blog, review, forum, search summary)
- **[I]** my inference from verified facts
- **[U]** unverified; check before relying on it
- **[E]** my estimate or judgement

---

## TL;DR

1. **Nothing on the market turns a design into Compose for us.** Google's Relay (Figma → Compose) was sunset on 30 Apr 2025 [V-ish: relay.material.io notice + several secondaries]. Figma's MCP outputs React + Tailwind by default and its docs never mention Compose [V]. Claude Design and `/design-sync` are web/React-centred [V]. **Every route ends with Claude hand-writing Compose.** The only question is what the *reference* is while it does so.
2. **So the cheapest reference is one Claude already writes fluently and the repo can own:** Markdown + Mermaid + self-contained HTML mockups, then Compose `@Preview`s rendered to PNG as soon as the theme exists. Compose becomes the design system's source of truth early. There is no second "design file" that drifts.
3. **Recommended workflow (four stages):**
   - **Ethos in words** (terminal, brainstorming skill).
   - **Direction exploration** in the superpowers **visual companion** (local, free, click-to-choose).
   - **Wireframe atlas + flows** as repo-owned HTML/Mermaid, reviewed as a published **Claude artifact**. Optionally try **`/design`** (Claude Design canvas) for flow storyboards.
   - **Compose theme + components with `@Preview` → PNG** (Roborazzi or `android studio render-compose-preview`), reviewed in a component-gallery artifact. Screenshot tests then lock the look.
4. **Figma: not recommended now.**
   - Writing to canvas needs a **Full seat (Professional, $16/mo)** [V].
   - `use_figma` **doesn't support custom fonts or images** [V]. That kills a dot-matrix type direction.
   - Starter read limits are tiny (20 tool calls/month per the official page; forums say 6) [V/S].
   - It's a new tool for a non-designer, and there's still no Compose output.
   - Revisit only if a human designer joins and wants Figma.
5. **Claude Design: useful, but as a viewer/sketchpad, not the system of record.**
   - `/design-sync` converts a **React** design system [V], so it doesn't fit Compose.
   - Claude Design has **no version history yet** [V].
   - Designs live on claude.ai, not in git.
   - `/design` in the CLI is a good, cheap way to get editable flow artboards you can tweak and export to PNG/PDF [V].

---

## 1. Claude Design (claude.ai/design → "Design" artifacts)

**What it is now [V]:**
- Launched **17 Apr 2026** as an Anthropic Labs research preview, "powered by … Claude Opus 4.7". It creates "designs, prototypes, slides, one-pagers" and builds a design system from your codebase and design files (Anthropic launch post).
- On **16 Sep 2026** it was folded into Claude: "Create designs, decks, and docs in any conversation … including in Claude Code and the Artifacts tab" (release notes).
- It is now an artifact **template**, available on **all plans including Free** (Enterprise: off until an owner enables it; default-on 15 Oct 2026).
- The standalone **claude.ai/design closes 14 Dec 2026**, so migrate any design systems before then (help centre).

**Capabilities [V, help centre "Get started"]:**
- A canvas of **artboards with layers**.
- Refine by chat, inline comments, or direct manipulation (drag, resize, align).
- "2–3 alternative layouts" side by side.
- Accessibility/contrast review.
- Click-through prototypes and multi-screen journeys: "Walk Claude through the journey and it will generate each screen in context" (Claude Academy tutorial).
- **Exports:** .zip, PDF, PPTX, Google Slides, standalone HTML, partner tools (Canva, Lovable, v0, …), and a **handoff bundle** (design files + chat + README) for Claude Code.
- **Nothing Android-specific is documented.** Mobile screens are just HTML artboards sized like a phone [I].

**In Claude Code (CLI ≥ v2.1.265; you have 2.1.296) [V, code.claude.com commands + artifacts docs]:**
- **`/design [brief]`** drafts "UI mockups, screen flows, landing pages, or posters as artboards on one canvas, published as a Claude Design artifact".
  - "You edit the artboards in a desktop browser, and your edits save automatically. You can export each artboard as PNG or PDF."
  - The Week 34 notes say: "Pick one, tweak it, then have Claude implement it."
  - Needs a claude.ai login (not an API key) and a Pro/Max/Team/Enterprise plan for artifacts. Research preview.
- **`/design-sync [hint]`**: "Convert your repo's **React** design system and upload it to Claude Design, so designs it produces use your real components. A first-time sync verifies every component and can take a few hours."
  - `/design-login` authorises it.
  - The underlying **DesignSync** tool (per the leaked tool description [S, Piebald-AI repo]) does list/get/create projects, a `finalize_plan` that locks write paths, then `write_files`/`delete_files`.
  - It syncs "one component at a time, never as a wholesale replace".
  - A Zenn write-up shows plain HTML components with a `<!-- @dsCard -->` marker also worked [S].
  - **No Compose path.** We'd have to maintain an HTML mirror of every Compose component just to feed it. That's a second codebase, so it's rejected [E].
- **Usage:** design work counts against the same shared limits as Claude Code. There's no separate allowance (help centre) [V].

**Limitations [V unless noted]:**
- "Claude Design doesn't have version history yet". The workaround is to ask Claude to save a copy. Artifacts in general do keep a version per publish (Claude Code artifacts docs), but the canvas's own edits are on claude.ai, not in git.
- Design systems are beta, and import quality "depends on the source".
- Multi-person editing is "basic".
- Adding new layers by hand needs a mouse.
- **Independent review** (Dibia, 21 Apr 2026) [S]: good token fidelity and a fast visual loop, but ~30 min of human redirection on the handoff. Mobile not tested.

**Verdict:** great for *looking at* and *nudging* screens and flows, with zero setup. Weak as a system of record for a Compose app (no git, no versions, React-only sync) [E].

---

## 2. Figma + connectors

**Official Figma MCP server [V, developers.figma.com tools page]:**
- **Read** tools work on any seat but are rate-limited: `get_design_context`, `get_metadata`, `get_screenshot`, `get_variable_defs`, `get_motion_context`, `search_design_system`, ….
- **Write ("write to canvas", since 24 Mar 2026)**:
  - Tools: `use_figma` (runs Plugin-API JavaScript to create frames, components, variants, variables, auto layout), `generate_figma_design` (live web UI → layers), `create_new_file`, `upload_assets`, `generate_diagram`.
  - All are remote-server only.
  - Supported clients include **Claude Code**. It ships skills (`/figma-use`, `/figma-generate-design`, `/apply-design-system`, …).
  - **Free during beta, "eventually … a usage-based paid feature"** (Figma blog).
- **Seat/plan:**
  - "You need a **Full seat** to write to Figma files with agents. Dev seats get read-only workflows" [V].
  - Prices: Professional **Full $16/mo**, Dev $12, Collab $3; Starter is free with limited access [V, figma.com/pricing].
  - Read limits: Dev/Full on Professional 200 calls/day, 10/min; View/Collab/Starter **20/month** [V].
  - Limits follow the team that owns the file, so files in Drafts on Starter hit Starter caps [S, forum].
  - Whether a Starter "Full seat" can write is **[U]**.
- **Write limitations [V]:**
  - 20 KB output per call.
  - **No image/asset support.**
  - **Custom fonts aren't supported.**
  - Components must be manually published before Code Connect.
- **Setup in Claude Code [V]:** `claude plugin install figma@claude-plugins-official`, then authorise via `/plugin`.
- **Code output:**
  - `get_design_context` defaults to **React + Tailwind**. Other targets come only "in your prompt" [V].
  - Compose isn't mentioned.
  - Forum reports say Code Connect with `android-compose` still returned React (2025) [S].
- **Relay** (Google's Figma → Compose): **sunset 30 Apr 2025**. The migration advice is to keep the generated code and remove the plugins [S ×3; the relay.material.io page body didn't render for me, so this is effectively V-by-consensus].
- **Material 3 Design Kit** (free Figma Community file; works with the Material Theme Builder plugin) [S]. It's useful only if we design in Figma. Our theme is custom monochrome anyway.

**Other Figma routes:**
- **Claude.ai Figma connector:** creates **FigJam diagrams only** (flowcharts, state diagrams). It's not UI frames [S, Figma help].
- **Figma Make:** prompt → functional prototypes "backed by your React components" [S, Figma marketing page]. It's web output, not Compose.
- **Community write MCPs:**
  - `grab/cursor-talk-to-figma-mcp`: WebSocket relay + Figma plugin + MCP; works with Claude Code [S, README].
  - `southleft/figma-console-mcp`: claims 96–121 tools, Plugin-API variables on any plan via a Desktop Bridge plugin [S, vendor docs].
  - Both need Figma Desktop and a running plugin. They are more moving parts, and the vendors' own claims are unverified.

**Verdict:** strongest canvas tool, but for *us*:
- It needs a paid seat to write.
- It can't place a custom dot-matrix font via the agent.
- It still produces no Compose.
- It adds a new tool for a non-designer to learn.
- Value only appears if a human designer joins [E].

---

## 3. Superpowers brainstorming visual companion

**How it works [V, local plugin source `superpowers/6.4.1/skills/brainstorming/visual-companion.md`]:**
- A local Node server watches a directory. Claude writes HTML *fragments* (or full documents) into `.superpowers/brainstorm/<session>/content/`.
- The browser shows the **newest** file.
- Clicks on `data-choice` options are logged as JSONL to `state/events`, which Claude reads next turn alongside the user's terminal reply.
- It provides built-in classes: `.options`, `.cards`, `.mockup`, `.split`, `.pros-cons`, wireframe blocks.
- Sessions are keyed with a URL `?key=`, auto-exit after 4 h idle, and mockups persist when started with `--project-dir`.
- The skill tells Claude to offer it only for genuinely visual questions, with 2–4 options per screen.

**Strengths for us:**
- Zero cost beyond tokens, fully local, and already part of D22's process.
- Fastest A/B/C loop: click to choose.
- Full-document mode lets Claude render real LED-grid mockups with CSS/SVG/canvas, load a local font, and animate a frame strip.
- The files are plain HTML in the project dir [E].

**Limits:**
- One screen at a time, with no gallery or side-by-side history browsing. Each iteration is a new file.
- No comments or annotations on the page; feedback goes through the terminal.
- Not shareable (localhost).
- The skill suggests gitignoring `.superpowers/`, so picked results must be copied into `docs/design/` to persist.
- Built for *decisions*, not for hosting a 15-screen atlas. Not a design system [E].

**Verdict:** ideal for **stage 2** (choosing a direction, type, LED rendering style, density, component variants). Wrong tool for the full wireframe atlas [E].

---

## 4. Other options

**Claude artifacts (plain HTML published from Claude Code) [V, code.claude.com/docs/en/artifacts]:**
- Claude writes an HTML/MD file (to a path we name, e.g. inside `docs/design/`) and publishes it to a private claude.ai URL. The browser auto-opens and updates live on republish.
- "**Each publish becomes a version**", selectable from Share.
- A single page with no relative links, so a multi-screen atlas uses in-page anchors. Google Fonts and data-URI fonts/images are allowed; 16 MiB cap.
- Comments readable by Claude when shared **within an organization**. On Pro/Max (no org) that likely doesn't apply [I/U], so feedback goes through the terminal.
- **Fit:** the best *review surface* for a whole wireframe atlas + flows, with the source file living in git [E].

**Compose `@Preview`-first [V, prior doc §1.8]:**
- `android studio render-compose-preview` (Android CLI 1.0, needs Studio open) or **Roborazzi** (headless Robolectric, PNG + JSON UI tree, agent skill) render previews to PNG that Claude can view.
- Compose Preview Screenshot Testing is deprecated in favour of AGP 9.5 test suites.
- **Fit:**
  - 100% fidelity to the real Canvas LED grid, real fonts, and real M3 components.
  - The design system *is* the code: zero translation.
  - The PNGs double as screenshot-regression baselines (D38 quality).
- **Costs:**
  - Needs the Compose scaffold first (the project has no Compose deps yet).
  - Slower iteration than HTML.
  - Less suited to throwaway exploration and flows [E].

**Penpot + MCP:**
- Penpot's own site says its AI workflows "run through Penpot MCP Server", hosted or self-hosted, "free and open" [V].
- The tool surface is small, with `execute_code` running Plugin-API JS [S].
- Open source and git-friendlier than Figma, but output is HTML/CSS/SVG, it's another tool to learn, and there's no Compose.
- **Fit:** a possible future shared space if outside designers contribute [E].

**Google Stitch (Labs):**
- An AI design canvas with a **DESIGN.md** "agent-friendly markdown file" for design rules, an MCP server + SDK, and prototypes with linked screens [V, Google blog 18 Mar 2026].
- Compose export is claimed only by third parties [S/U]. Pricing unclear.
- **Worth noting:** the DESIGN.md idea is a good pattern to copy in-repo [E].

**Fonts (affects every HTML route):**
- Nothing's Ndot 55/57 are proprietary (prior research).
- OFL candidates: **Doto** (OFL-1.1, variable dot-size and dot-roundness axes, 6×10 matrix) [V, repo; Google Fonts listing S], or NDOT 47 (OFL, author's similarity caveat).
- Doto via Google Fonts loads natively in artifacts. The same TTF goes into `res/font` for Compose, so it's one font across all stages [I].

---

## 5. Evaluation matrix

Scale: ●●● strong, ●● ok, ● weak. [E] throughout, grounded in the facts above.

| Criterion | Visual companion | Artifact (repo HTML) | `/design` (Claude Design canvas) | Figma + MCP write | Compose @Preview → PNG | Penpot + MCP |
|---|---|---|---|---|---|---|
| (a) Claude does most work autonomously | ●●● | ●●● | ●●● | ●● (plugin JS, 20 KB/call, no fonts) | ●●● (once scaffolded) | ●● |
| (b) User reviews/chooses easily | ●●● click-to-pick | ●●● scroll atlas, versions | ●●● direct edit, PNG export | ●●● (if you know Figma) | ●● static PNG gallery | ●● |
| (c) Dot-matrix / custom components fidelity | ●●● (any HTML/SVG/canvas, local font) | ●●● | ●● (HTML; font via upload [U]) | ● (no custom fonts, no images) | ●●● real thing | ●● |
| (d) Path to Compose, least rework | ● (reference only) | ● (reference only) | ● (React handoff) | ● (React default; Relay dead) | ●●● zero translation | ● |
| (e) Cost | ●●● free | ●●● in plan | ●●● in plan (shared limits) | ● $16/mo Full seat; paid later | ●●● free | ●●● |
| (f) Persistence/versioning in git | ●● (copy picks out of `.superpowers/`) | ●●● file in repo + publish versions | ● on claude.ai, "no version history yet" | ● Figma cloud | ●●● code + PNG baselines | ●● |
| (g) Learning curve | ●●● none | ●●● none | ●●● none | ● new tool + seats + MCP | ●● (learn Compose, needed anyway) | ●● |

---

## 6. Recommended workflow

The principle: **words and HTML for deciding, Compose for defining.** Everything that matters lives in the repo (`docs/design/`, later `:app` theme code). claude.ai pages are only review windows.

**Stage 1: Design ethos (terminal, brainstorming skill, ~1 session).**
- Output: `docs/design/ethos.md`. Covers:
  - Principles (e.g. "the LED is the hero; chrome recedes", "monochrome + one signal red", "dot-matrix for display only, a clean grotesk for UI text", "mechanical honesty: show real LED states")
  - What we borrow from Nothing vs. deliberately don't
  - Accessibility floors from Part B5
  - Tone of copy
- Also a `docs/design/DESIGN.md`-style token table (colours, type scale, spacing, shape, motion, haptics). This is the agent-readable contract, a pattern borrowed from Stitch [E].
- Record choices as D-entries.

**Stage 2: Direction exploration (visual companion).**
- Three named directions × the same 2 screens (Library, Frames editor), with real LED-grid rendering (13×13 circle mask, glow on/off, off `#1a1a1a`).
- Then single-variable rounds: type pairing (Doto vs NDOT 47 headings, UI sans), accent usage, density, corner/shape language, LED-cell states (lit/off/transparent, from Part B2), and toolbar/swatch styles.
- Copy each winner into `docs/design/explorations/` (or commit `.superpowers/brainstorm/` selectively).

**Stage 3: Wireframe atlas + flows (repo HTML/Mermaid, reviewed as an artifact).**
- `docs/design/screens.md` holds the screen inventory. Derive it from B6 + roadmap: Library, Editor-Frames, Editor-Timeline, Effects sheet, Brightness sheet, Export/Import, GIF import (D25), LED play countdown + flash warning (D37), Triggers list/editor (D40), Agent sheet + endpoint setup (D42), Settings, Onboarding, plus empty/error/loading states.
- `docs/design/flows.md` holds Mermaid flow diagrams, one per user journey: first run, create→paint→play on LEDs, import GIF, assign a trigger, agent turn + undo, export.
- `docs/design/atlas.html` is one self-contained page with every screen in phone frames (≈412×915 dp at 1 px = 1 dp), anchored nav, flows rendered beside the screens, and per-screen notes and open questions.
- Publish it as an artifact for review. Republish = new version. Feedback goes through the terminal (or comments, if on a Team org).
- *Optional experiment:* run `/design` once for 2–3 key flows (e.g. "create → paint → play on LEDs") to see if direct-manipulation artboards beat the atlas for you. If it's better, keep using it for flows, but export PNGs into `docs/design/` so git keeps a record.

**Stage 4: Design system in Compose (the source of truth from here on).**
- In the first UI milestone (M2), build `GlyphWizardTheme` (M3 `ColorScheme`/`Typography`/`Shapes`, dynamic colour off) plus the domain components: `LedGrid`, `FrameStrip`, `BrightnessSwatches`, `TimelineTrack`, `KeyframeButton`. Each gets `@Preview`s for every state and light/dark.
- Render with Roborazzi (headless, CI-friendly) or `android studio render-compose-preview` for the inner loop.
- Publish a "component gallery" artifact embedding the PNGs (data URIs) for review. Commit PNGs as screenshot-test baselines.
- Screens are then built against the atlas. Each screen PR includes before/after preview PNGs.
- Once Compose exists, retire HTML for anything already implemented. Don't maintain two systems [E].

**Setup steps (nothing installed by this research):**
1. Add `.superpowers/` to `.gitignore` (it isn't there today). Decide that picked mockups get copied into `docs/design/explorations/`.
2. Confirm artifacts work: claude.ai login via `/login`, artifacts on in `/config`. `/design` requires v2.1.265+, and you have 2.1.296.
3. Pick the font licence up front (Doto or NDOT 47, both OFL). Put the TTF in `docs/design/fonts/` so HTML mockups and later `res/font` use the identical file.
4. Add a "Design system" block to `CLAUDE.md` pointing at `docs/design/DESIGN.md`. The artifact design skill prioritises a project design system over its own choices [V, artifacts docs].
5. Stage 4 needs the Compose scaffold (M0) and Roborazzi (or Studio + Android CLI). See `2026-10-11-dev-tooling-visual-mcp.md` §1.8.
6. Skip Figma. If a human designer later joins: Figma Professional Full seat + official plugin (`claude plugin install figma@claude-plugins-official`), with Compose still hand-written from frames. Or Penpot if they prefer open source.

---

## 7. Risks and open questions

- **Claude Design churn:** standalone closes 14 Dec 2026; `/design` is a research preview; features moved twice this year [V]. Keep nothing important only there.
- **HTML ≠ Compose:** HTML mockups can promise things M3/Compose makes awkward (e.g. glow rendering cost, sheet behaviours, edge-to-edge insets). Mitigation: mock at dp scale, use M3 component vocabulary in wireframes, and spike the `LedGrid` in Compose early (M0/M2) [E].
- **Artifact comments on Pro/Max** are likely unavailable (they need org sharing) [U]. Plan for terminal feedback.
- **Usage limits:** large canvases and many iterations consume the shared Claude limit [V]. HTML atlases with embedded images are token-heavy; prefer SVG/CSS [V, artifacts docs].
- **User questions:**
  - (1) Which plan are you on (Pro/Max)? This affects artifacts/comments.
  - (2) Is there an appetite to try `/design` once in stage 3?
  - (3) Doto vs NDOT 47 for display type?

---

## Learning asides (D23)

> **Design tokens and design systems.** A design system is the set of reusable decisions (colour, type, spacing, shape, motion) plus the components built from them. Tokens are those decisions as named values (`color.surface`, `type.display`). In Compose, `MaterialTheme(colorScheme, typography, shapes)` *is* your token layer. Write tokens once and every screen inherits them.
> - [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
> - [Custom design systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom)

> **Wireframes vs flows.** A wireframe is one screen's layout at low fidelity. A flow is the path between screens for one user goal, including errors and back-navigation. Designing flows first keeps you from drawing screens nobody reaches.
> - [Nielsen Norman Group: Wireflows](https://www.nngroup.com/articles/wireflows/) [U: not re-fetched]

> **Screenshot testing.** You render a composable to PNG in a test and compare it to a committed baseline, which catches unintended visual changes the way unit tests catch logic changes.
> - [Roborazzi](https://github.com/takahirom/roborazzi)

---

## Sources

**Anthropic / Claude**
- Introducing Claude Design (17 Apr 2026): https://www.anthropic.com/news/claude-design-anthropic-labs
- Get started with Claude Design (help centre): https://support.claude.com/en/articles/14604416-get-started-with-claude-design
- Set up your design system in Claude Design: https://support.claude.com/en/articles/14604397-set-up-your-design-system-in-claude-design
- Claude release notes (16 Sep 2026 entry): https://support.claude.com/en/articles/12138966-release-notes
- Claude Code commands (`/design`, `/design-sync`, `/design-login`): https://code.claude.com/docs/en/commands
- Claude Code artifacts (incl. "Draft a design canvas", versions, comments, availability): https://code.claude.com/docs/en/artifacts
- Claude Code What's new, week 34 (`/design` research preview): https://code.claude.com/docs/en/whats-new/2026-w34
- Claude Academy, prototypes and UX: https://academy.claude.com/tutorials/using-claude-design-for-prototypes-and-ux
- [S] DesignSync tool description (Piebald-AI): https://github.com/Piebald-AI/claude-code-system-prompts/blob/main/system-prompts/tool-description-designsync.md
- [S] Zenn, /design-sync from the CLI: https://zenn.dev/tottoko_hamu/articles/2026-06-20-150000?locale=en
- [S] Victor Dibia, How good is Claude Design: https://newsletter.victordibia.com/p/how-good-is-anthropics-claude-design

**Figma**
- Tools and prompts: https://developers.figma.com/docs/figma-mcp-server/tools-and-prompts/
- Write to canvas: https://developers.figma.com/docs/figma-mcp-server/write-to-canvas
- Rate limits & access: https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/
- Agents, meet the Figma canvas (24 Mar 2026): https://www.figma.com/blog/the-figma-canvas-is-now-open-to-agents/
- Claude Code + Figma setup: https://help.figma.com/hc/en-us/articles/39888612464151-Claude-Code-and-Figma-Set-up-the-MCP-server
- Pricing: https://www.figma.com/pricing/
- [S] Create FigJam diagrams with Claude: https://help.figma.com/hc/en-us/articles/37883260397975-Create-FigJam-diagrams-with-Claude
- [S] Compose framework forum report: https://forum.figma.com/report-a-problem-6/figma-mcp-with-code-connect-doesn-t-return-the-right-framework-in-get-design-context-53081
- [S] Material 3 Design Kit: https://www.figma.com/community/file/1035203688168086460/material-3-design-kit
- [S] grab/cursor-talk-to-figma-mcp: https://github.com/grab/cursor-talk-to-figma-mcp
- [S] southleft/figma-console-mcp: https://github.com/southleft/figma-console-mcp
- Relay: https://relay.material.io/ ; [S] https://blogs.perficient.com/2025/03/31/comparing-figma-to-compose-conversion-methods-for-android-development/

**Others**
- Superpowers visual companion (local): `~/.claude/plugins/cache/claude-plugins-official/superpowers/6.4.1/skills/brainstorming/visual-companion.md`
- Penpot AI workflows: https://penpot.app/ai/ai-workflows ; [S] https://www.smashingmagazine.com/2026/01/penpot-experimenting-mcp-servers-ai-powered-design-workflows/
- Google Stitch (18 Mar 2026): https://blog.google/innovation-and-ai/models-and-research/google-labs/stitch-ai-ui-design/
- Doto font (OFL-1.1): https://github.com/oliverlalan/Doto
- Compose preview rendering: see `2026-10-11-dev-tooling-visual-mcp.md` §1.8
