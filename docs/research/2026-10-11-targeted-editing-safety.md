# Targeted Gap Research: Undo, Variants, Photosensitivity, Few-shot

Date: 2026-10-11
Answers review r1 items **R5/H4** (undo), **H3** (variants), **R7/M5** (flash safety) and **R8** (few-shot), for `2026-10-11-agent-architecture-draft.md` v0.1.
Inputs: `agent-research-brief.md`, `decisions.md` (D1–D20), the draft, `2026-10-11-agent-architecture-review-r1.md`, plus the [UX], [TR] and [CTX] source reports where the review points to them.

Evidence tags:
- **[V]**: I read the primary source in this session (full text, or the official page).
- **[V-abs]**: verified from the abstract or official listing only.
- **[S]**: secondary source only.
- **[C]**: my own calculation, with the method stated.
- **[P]**: proposal or judgement.
- **[U]**: unverified.

---

## TL;DR

1. **Undo (H4a).** Keep D9's linear history. Add three turn-card behaviours:
   - **Undo** when the turn is still the latest change.
   - **Undo this turn** when later edits don't touch what the turn changed. This is a Berlage-style selective undo, guarded by JSON-Patch `test` ops.
   - **Restore to before this turn…** otherwise. This is applied as a *new* history entry, so it never destroys anything (Google Docs "restore revision" semantics, not Photoshop's linear-mode discard).

   Don't build general selective undo. The literature shows conflicts make it unsound without OT-style machinery.
2. **Undo sizing (H4b).** One agent turn can easily exceed 256 KB uncompressed:
   - Repainting a 25×25 raster with 100 frames costs **266–730 KB** forward+inverse when diffs are stored per frame.
   - Stored per pixel, the same turn costs **~12 MB** [C].

   Fixes: store diffs at **frame granularity with binary (base64) values**, gzip the sidecar, budget by *compressed* bytes, and **pin the newest agent turn so eviction can't touch it**. With those changes, realistic turns cost ~2–20 KB.
3. **Variants (H3).** **Defer to post-v1.** Use a clarifying question in v1. If variants come back, use one turn-ending `propose_variants` tool at the top tier only, ideally limited to generator-parameter variants. That keeps the schema flat: an "array of arbitrary command sets" needs `oneOf`, which the draft bans. Evidence supports generating N alternatives in one call (more diverse at equal quality, and cheaper) over N branches.
4. **Flash safety (R7).** I read the 2026 Epilepsy Foundation consensus in full. It **keeps** ≥20 cd/m² and 3–60 Hz, says risk peaks at **10–20 Hz**, and adds two conditions: an area of **>~25% of the visual field** and a duration of **>~0.5 s**. ITU-R BT.1702-3 and Ofcom use **>3 flashes in any 1 s over >25% of screen area**.
   - On the matrix (output roughly 3,000 nits; diameter unknown), a full-matrix flash at handheld distance is **roughly at or above** WCAG's 0.006 sr area limit [C]. The "small screen" exemption can't be assumed.
   - Recommended check: a per-LED flash count. Flag it when **≥25% of mask LEDs** each flash more than 3 times in any 1 s window, for at least 0.5 s.
   - **Warn and gate; don't block.** The agent may not introduce a failing flash unless the user asks.
5. **Few-shot (R8).** The "3 ≈ 9" result has **not** been replicated for 2025–26 models in tool use. I found no controlled study either way.
   - Current evidence: reasoning models often do as well or better zero-shot (DeepSeek-R1, o1 Medprompt, OpenAI guidance). Gains are task- and model-specific and can turn negative. Anthropic still recommends 3–5 examples, including with thinking.
   - Recommendation: **0 in the prefix for reasoning/capable tiers by default; 1 canonical trajectory for mid tiers; at most 2 more on demand.** Ablate 0/1/3 before making any slot permanent. D19 needs a `maxTier`.

---

## R5/H4(a): Undo after interleaved user edits

### Evidence

| Source | What it says | Tag |
|---|---|---|
| Berlage 1994, *A selective undo mechanism for GUIs based on command objects*, ACM TOCHI 1(3):269–294 | Linear undo can't remove an isolated command without also removing all later commands. Selective undo copies the inverse of an isolated command into the current state "when meaningful". It considers only that command and the current state, not the intervening history, so users find it easier to predict. | [V-abs] https://dl.acm.org/doi/10.1145/196699.196721 |
| Prakash & Knister 1994, *A framework for undoing actions in collaborative systems*, TOCHI 1(4) (DistEdit) | Users can undo their own operations selectively. Concurrent operations can make an undo impossible (conflict). Selection by region or time also works for single-user systems. | [S] (abstract via Semantic Scholar/ResearchGate; volume/page details vary across sources) https://www.semanticscholar.org/paper/469986a1b27bede57436ae14b9900ba7f1210f73 |
| Sun 2000, *Undo any operation at any time in group editors*; Yu et al. 2015 CRDT selective undo | DistEdit can't undo an operation that conflicts with a later one. Selective undo can leave dependent edits stranded (e.g. a correction to a word that has been undone). | [S] https://dl.acm.org/doi/10.1145/358916.358990 · https://members.loria.fr/CIgnat/files/pdf/YuDAIS15.pdf |
| VS Code chat checkpoints | "Restore checkpoint" "removes subsequent requests from the conversation history and restores the workspace files to their state at the checkpoint". **Redo** brings them back. Per-edit Keep/Undo while edits are pending. The docs don't say how manual edits are treated. | [V] https://code.visualstudio.com/docs/copilot/chat/chat-checkpoints |
| Cursor checkpoints | Snapshots of agent-modified files. Restore "reverts files only; it does not remove messages". The docs don't say how manual edits are treated. | [V] https://cursor.com/docs/agent/chat/checkpoints |
| Claude Code checkpointing | Tracks only its own file-edit tools; Bash and outside edits aren't captured. Offers "restore code and conversation" or "conversation only". | [S] (docs via search) https://code.claude.com/docs/en/checkpointing |
| tldraw history | Linear stack with marks. `squashToMark` merges steps into one; `bailToMark` discards without adding a redo entry. Remote changes are "never recorded", so in effect you undo only your own changes. | [V] https://tldraw.dev/sdk-features/history |
| Figma multiplayer | Undo/redo is built so that "if you undo a lot, copy something, and redo back to the present … the document should not change". An undo modifies redo history at the time it runs. | [V] https://www.figma.com/blog/how-figmas-multiplayer-technology-works/ |
| Google Docs | Ctrl-Z only reverses your own most recent edits. "Restore this revision" restores an old version **and keeps the later revisions** in history. | [S] Computerworld, Make Tech Easier |
| Photoshop History panel | Linear mode: picking an earlier state and editing discards the later states. "Allow Non-Linear History" keeps them. 20 states by default, up to 1,000; the oldest are evicted when the limit is exceeded. | [S] https://helpx.adobe.com/photoshop/desktop/get-started/set-up-toolbars-panels/history-panel-overview.html |

**Synthesis.** The editors cluster into two families:
- **Linear undo plus a non-destructive "restore" that becomes a new version:** Google Docs, VS Code (which offers Redo after restore), Photoshop's non-linear mode.
- **Per-author undo in multi-user tools:** Figma, Google Docs, tldraw. These depend on OT/CRDT-style machinery or on ignoring remote changes.

General selective undo is known to be unsound when edits conflict. Coding agents restore whole checkpoints and say little about interleaved manual edits. That gap is exactly the trap H4 identifies.

GlyphWizard has cheap conflict detection that text editors lack. IDs are stable, and diffs are keyed by clip and frame paths. So the Berlage "when meaningful" condition reduces to: **did any later group touch the same JSON paths?** RFC 6902 already has a `test` op, which applies a patch only if the current value at a path matches the expected one. **[P]** (`test` op: RFC 6902 §4.6, cited from the spec and not re-fetched.)

### Recommended semantics [P]

History stays linear (D9). Each group records:
- `author`, `label`, `turnId?`
- `touchedPaths`, at frame granularity (e.g. `/clips/c3/frames/17`)
- forward and inverse patches

| State of the turn | Turn-card button | Mechanism | Wording |
|---|---|---|---|
| Latest group in history | **Undo** | Ordinary linear undo; redo stays available. | Toast: "Undid the agent's changes · **Redo**" |
| Later groups exist, and none of their `touchedPaths` overlap this turn's | **Undo this turn** | Apply the turn's inverse, prefixed with `test` ops asserting the turn's post-values, as a **new** forward group ("Undid agent turn 4"). If any test fails, fall through to the next row. | Subtitle: "Your later edits stay." |
| Later groups overlap | **Restore to before this turn…** | Compute `diff(current → pre-turn state)` for the touched clips and apply it as a **new** forward group. Nothing is deleted; one Undo reverts the restore. | Sheet title: "Restore to before this turn?" Body: "This also reverts 3 later changes to "heart" (2 by you, 1 by the agent). Nothing is lost: tap Undo to get them back." Buttons: **Restore** / Cancel |
| The turn's data has been evicted | (disabled) | Must not happen for pinned turns (see (b)). | "Too old to undo. Use version history." |

Other rules:
- **Global toolbar Undo/Redo stays purely linear**, whoever authored the step (as in Photoshop and tldraw). Only the turn card offers turn-scoped operations.
- **The chat transcript is never truncated** (Cursor-style, not VS Code-style). Reverted turns are marked "reverted". The next agent turn gets "user restored to before turn 4" in its change summary (draft §7).
- A restore implemented as a new forward entry satisfies Figma's invariant: undoing back, looking, and redoing leaves the document unchanged.
- Required test (from the review): agent turn → user edit (disjoint) → Undo-this-turn keeps the user edit. Agent turn → user edit (overlapping) → Restore offers the warning and is itself undoable.
- **Not in v1:** per-author undo stacks, OT/CRDT, and partial selective undo of conflicting edits.

---

## R5/H4(b): Worst-case diff size of one agent turn

### Method [C]

I ran a Python simulation in the session scratchpad (not app code):
- circular mask; brightness + alpha as 2 bytes per LED (D6)
- one clip replaced on every frame
- the inverse patch assumed to be the same size as the forward patch
- three content types: `noise` (incompressible worst case), `ring` (realistic generator-like raster), `full` (uniform fill)

KB values are **forward + inverse**, shown as raw / gzip.

| Grid, frames (@20 ms) | Content | Per-pixel JSON-Patch ops | Per-frame JSON int arrays | Per-frame base64 blobs | Binary snapshot of the clip (one side) |
|---|---|---|---|---|---|
| 13×13 (137 LEDs), 100 (2 s) | noise | 3,428 / 257 | 202 / 74 | 83 / 55 | 26 KB |
| 13×13, 100 | ring | 3,370 / 166 | 144 / 4 | 83 / 4 | 26 / 1 KB gz |
| 25×25 (489 LEDs), 100 (2 s) | noise | **12,293** / 922 | **693** / 255 | **266** / 193 | 95 KB |
| 25×25, 100 | ring | 12,044 / 553 | 444 / 21 | 266 / 8 | 95 / 2 KB gz |
| 25×25, 200 (4 s) | noise | 24,796 / 1,845 | 1,386 / 508 | 532 / 386 | 191 KB |
| 25×25, 200 | ring | 24,298 / 1,106 | 888 / 43 | 532 / 11 | 191 / 3 KB gz |

Other turn shapes:
- **Generator parameter edits and keyframe-list replacements:** tens to hundreds of bytes (e.g. `{"op":"replace","path":"/clips/c7/params/speed","value":1.2}` ≈ 60 B) [C].
- **Hidden large cases:**
  - D8's auto-convert of a generator clip to raster: the forward patch is a full raster and the inverse is a tiny generator spec.
  - Deleting a raster clip: the inverse carries the whole clip.
  - Import.
- **Scaling:** size grows linearly with frames. A 10 s, 500-frame animation on 25×25 is ~2.5× the 200-frame row.

**Conclusion.** D9's 256 KB uncompressed cap is broken by a single full repaint on Phone (3)-class hardware, even with the best uncompressed encoding. Per-pixel patches are unusable at any cap. Once compressed and stored per frame, realistic content is tiny. Only noise-like content stays large, and it's bounded by roughly 2× the clip's binary size.

### Recommended sizing policy (lean) [P]

1. **Diff granularity = frame.** A raster frame is one opaque value: base64 of the brightness/alpha bytes. Never emit per-pixel ops. This remains JSON Patch (D9 unchanged in spirit).
2. **gzip the sidecar** with `java.util.zip`: no dependency, and it's in `:core`.
3. **Budget by compressed bytes.** Suggested cap: **~4 MB per document**, plus the existing ~200-group count. That is negligible phone storage; 256 KB was a guess, not a requirement.
4. **Pinning:** never evict the newest agent turn or anything newer than it. An oversized new group evicts the *oldest* groups first.
5. **Hard ceiling per group** (e.g. 16 MB compressed): an agent tool call that would exceed it is rejected with an actionable error ("split into frame ranges"). A user action shows "This change can't be undone." This should never trigger on 13×13.
6. **No separate snapshot store in v1.** "Restore to before this turn" is computed from the inverses, which are pinned. Add per-turn clip snapshots only if profiling shows inverse replay is slow. It won't be at these sizes.
7. **RAM:** keep only the newest few groups decoded; the rest stay in the sidecar.

---

## H3: Variants

### The conflict

- §3 (from [TR]): `create_variant` forks the document; later writes take `variant?`; `present_variants` ends the turn. Building N variants takes many calls.
- §8 (from [UX] A7.3): "one call returns 2–3 command sets, rendered locally".
- §4: "ambiguous requests → 2–3 variants".

### Evidence

| Source | Finding | Tag |
|---|---|---|
| Troshin, Saparina, Fokkens, Niculae, *Asking a Language Model for Diverse Responses*, UncertaiNLP @ EMNLP 2025 | Under matched budgets, **enumeration** (n candidates in one pass) and iterative generation give **higher diversity at comparable quality** than independent parallel sampling. Enumeration "can potentially approach the efficiency of a single parallel call". Tested on GSM8k with Qwen3 4B–32B. | [V] https://aclanthology.org/2025.uncertainlp-main.8.pdf |
| Zhang et al., *Verbalized Sampling* (arXiv 2510.01171, ICML 2026) | Asking for several responses (with probabilities) in one prompt increases creative diversity 1.6–2.1× over direct prompting, without hurting quality or safety. More capable models benefit more. | [V-abs] https://arxiv.org/abs/2510.01171 |
| [UX] A7.3 | "Don't run three full agent loops for three variants." | internal |

**Not found:** any study comparing "N document branches built by an agent" with "one structured call returning N alternatives" for **tool-using agents** specifically. The evidence above is about text generation, so it transfers to variants only by analogy **[U]**.

### Cost [C, rough]

Assumptions: a round trip carries ~10–20k input tokens (prefix + schemas + state, mostly cached). A command set is ~150–600 output tokens.

| Design | Extra cost for 3 variants |
|---|---|
| Branch design (§3) | ~2–4 calls per variant → **6–12 extra round trips** → ~60–240k input tokens |
| Single call (§8) | 1 call, **~0.5–2k extra output tokens** |

The branch design's one real advantage: the model can `analyze` each variant before presenting it. The single-call design can recover this by having the host run `analyze` on every variant and attach the verdicts.

### Schema problem the review missed

"Command sets" means `variants:[{label, ops:[{tool, args}]}]`. Typing `args` for arbitrary write tools needs `oneOf`/discriminated unions, which draft §3 bans ("flat, no `oneOf`"). The alternative is untyped JSON strings, which loses constrained decoding and validation. Nested polymorphic arrays are the hardest schema shape for mid-size open models **[U, plausible; not measured]**.

### Recommendation [P]

- **Defer variants to post-v1** (agrees with review §5). In v1, ambiguity → **one clarifying question**. This already satisfies the "ambiguity" eval family ("asks or offers variants").
- When reintroduced, prefer the narrowest form:

  ```
  propose_variants{
    question,
    clipId,
    variants: [{label, params | keyframes}]
  }
  ```

  - Variants of **one generator clip's parameters or keyframes**. The schema is flat and reuses `edit_clip`'s argument shape.
  - It ends the turn. The host renders and analyzes each variant.
  - Picking one applies it as one undo group; the others are discarded and never enter history.
  - Variants of whole raster designs come later, and only if evals show demand.
- **Tier:** top tier only under D19, gated on an eval showing that tier produces valid variants.
- **Drop `create_variant`** and the `variant?` argument on write tools entirely.

---

## R7: Photosensitivity

### Primary sources read

**Fisher, Wilkins, Kasteleijn-Nolst Trenité et al., "Visually-provoked seizures: Consensus of the Epilepsy Foundation Working Group", *Epilepsia* 2026;67(2):565–581.** [V: full text, open-access copy] https://dspace.library.uu.nl/server/api/core/bitstreams/511a8fa4-deff-4845-b9af-c3d0d64e969c/content · DOI 10.1111/epi.18702

- **Table 4 "Advice for media designers" ("Suggest to avoid"):**
  1. Light flashes ≥20 cd/m² recurring at 3–60 Hz.
  2. Alternating colour flashes (especially red–blue) at 3–60 Hz.
  3. Bright or coloured stripes of 1–8 cycles per degree, particularly when oscillating at 3–60 Hz.

  "Particularly likely to provoke seizures if they occupy more than about **25% of the visual field of both eyes** for more than about **0.5 s**." "Maximum risk is from **10 to 20 Hz**."
- **§5.3:**
  - Flicker is most provocative at ~16 Hz.
  - Larger retinal area and central gaze mean higher risk.
  - Contrast: "If the contrast (difference between the peak and the time-averaged illuminance divided by the time-averaged luminance) is less than **5%**, the risk is low", reaching a maximum at **≥30%**.
- **§5.4:** risk from patterns is "of little risk" if "the entire pattern subtends less than 1 degree".
- **§5.5 (LEDs):** "it is unclear whether the brightness concentration or spectrum poses increased risks", but "the temporal variation in light output is a definite risk at low frequencies at which the flicker is visible".
- **§5.6:** "No equipment or software screener can guarantee … entirely safe, although screening can reduce the risk." The paper names PEAT (free) and the Harding FPA, and cites Apple's "Dim Flashing Lights".
- **§6 (government):** summarises five guideline sets as "frequency <3 or 5 flashes per second; brightness differential … below 20 cd/m²; avoidance of flashes to and from a saturated red; and area less than **0.006 sr or about 25% of the visual field**".
  - Note the inconsistency: 0.006 sr is 25% of a *10°* field (WCAG), not of the whole visual field.
  - Also: "No universally enforced regulations exist for seizure prevention" (said of architecture). Warnings "can be helpful but … may be ignored".
- **Patient advice:** smaller screens are less risky; "smartphones seem to cause fewer issues than larger screens".
- **Framing:** "This guide is not intended to limit imagination or creativity."

**ITU-R BT.1702-3 (11/2023).** [V: full PDF] https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.1702-3-202311-I!!PDF-E.pdf

- A harmful flash is a pair of opposing luminance changes where the darker image is <160 cd/m² and the difference is ≥20 cd/m². Above 160 cd/m², the test is Michelson contrast >1/17 (HDR).
- A saturated-red transition counts as harmful regardless.
- "Isolated single, double, or triple flashes are acceptable". A sequence is not permitted when both conditions hold:
  - the combined concurrent area is **>25% of the displayed screen area**, and
  - there are **>3 flashes (six changes) in any one-second period**.
- Leading edges ≥334 ms apart (60 Hz environment) are always acceptable.
- Flashing ">5 s might constitute a risk even when it complies".
- **Patterns:** >5 light–dark stripe pairs, either stationary over >40% of the screen or oscillating/reversing over >25%. Smooth one-direction flow is exempt.

**Ofcom Broadcast Code guidance, Section 2 (2017).** [V: PDF text] https://www.ofcom.org.uk/siteassets/resources/documents/tv-radio-and-on-demand/broadcast-guidance/programme-guidance/broadcast-code-guidance/section-2-guidance-notes.pdf
- Same structure as ITU: 20 cd/m² difference when the darker image is <160 cd/m²; "one quarter of the displayed screen area"; caution for flashing >5 s.

**WCAG 2.2 SC 2.3.1.** [V] https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html
- General flash = opposing changes in relative luminance of ≥10% of max, with the darker state <0.80.
- Fails if more than 3 flashes in any 1 s, unless the combined concurrent area is ≤0.006 sr: "25% of any 10 degree visual field … at typical viewing distance".

**Harding 2005 consensus** (≥20 cd/m², ≥3 Hz, ≥0.006 sr) is superseded by Fisher 2026. The core numbers carry over; the 0.5 s duration and the 10–20 Hz peak-risk statement are new or now explicit.

### What these mean for a ~13×13 white LED matrix [C]

- **Luminance:** the 4a Pro matrix peaks at ~**3,000 nits** with 4 user brightness levels [S: GSMArena review https://www.gsmarena.com/nothing_phone_4a_pro-review-2949p2.php; per-dot vs area-average not stated]. 20 cd/m² is <1% of that peak. **In practice every visible on↔off transition qualifies as a flash** by the ITU/Ofcom/Fisher luminance test. Only measurement can show whether the lowest brightness level falls below it.
- **Colour:** white LEDs make red and colour flashes not applicable on hardware. The on-screen preview must not tint the matrix saturated red.
- **Area:** the matrix's physical diameter is **unknown**. Reviews only say it is "57% bigger" than Phone (3)'s. Solid angle Ω ≈ π(d/2)²/D², compared with the 0.006 sr limit:

  | Diameter d | 15 cm | 20 cm | 30 cm | 50 cm |
  |---|---|---|---|---|
  | 20 mm | 233% | 131% | 58% | 21% |
  | 25 mm | 364% | 205% | 91% | 33% |
  | 30 mm | 524% | 295% | 131% | 47% |
  | 35 mm | 713% | 401% | 178% | 64% |

  At typical handheld distance (20–30 cm), a **full-matrix flash is around or above WCAG's area limit**. The device-relative ITU rule (">25% of the screen") is therefore the safer proxy, and it needs no distance assumption.
  - Fisher's literal "25% of the visual field" would almost never trigger. I treat it as loose wording, given the paper's own §6 equates it with 0.006 sr.
- **Frequency:** at the 20 ms default tick (50 fps), on/off alternation can produce 1–25 Hz. The **10–20 Hz peak-risk band is easily reachable** (toggle every 25–50 ms), so the check must run on the *effective* rate: tick × speed multiplier (D10), capped by the measured SDK frame rate.
- **Patterns:** at 20–30 cm, 1-LED stripes on the 13-wide grid are ~0.9–1.4 cycles/degree, and the whole pattern spans ~5–8°. That is inside Fisher's 1–8 cpd range and above the 1° "little risk" floor. A 13-wide grid can hold 6 stripe pairs, just over ITU's ">5 pairs". Contrast-reversing stripes and checkerboards are therefore relevant. The per-LED method below catches them automatically, because every LED in a reversing stripe flashes.

### Deterministic check `analyze.flash` (spec) [P]

**Inputs**
- Baked frames at the effective playback rate.
- `L(b)`: relative luminance per LED for brightness byte `b`, at the highest system brightness level.
  - Default before hardware data: `L = b/255`.
  - After the spike: the measured curve in cd/m².

**Algorithm**
1. **Qualifying transition** (per LED, between consecutive frames): |ΔL| ≥ θ, with the darker state below 0.8 relative.
   - Default θ = 10% of full scale (WCAG).
   - After measurement: θ = 20 cd/m² (ITU/Fisher), which is stricter.
2. **Flash** = a pair of opposing qualifying transitions on the same LED.
3. For every sliding 1 s window *w*, let `S(w)` = the LEDs with **>3 flashes** in *w*. Leading edges ≥334 ms apart never count.
4. **FAIL (`risk: high`):** |S(w)| ≥ **25% of mask LEDs** for windows spanning **≥0.5 s**.
   - Report the dominant frequency.
   - Add "peak-risk band" when it falls in 10–20 Hz.
   - Report offending frame ranges and LED sets.
   - Suggested fixes: slow to ≤3 Hz, shrink the area below 25%, or reduce contrast below 5% (Fisher's "low risk" floor).
5. **WARN (`risk: medium`):**
   - |S| ≥ 10% of mask, or
   - a compliant but continuous flashing sequence lasting >5 s (ITU/Ofcom cumulative-risk note).
6. **Not a safety finding:** a single blinking pixel, or a small sub-25% flicker. That belongs to the existing *quality* flicker metric (draft §4). This addresses the false-positive concern in review M5.
7. **Out of scope for v1:** static stripe patterns (ITU >40%). They would fire on many legitimate designs; revisit after calibration.

This costs O(LEDs × frames) and fits in `:core`. Golden tests should include:
- a full-matrix 12 Hz strobe → FAIL
- a 3 Hz pulse → pass
- a checkerboard reversing at 15 Hz → FAIL
- one LED at 20 Hz → pass (quality warning only)
- a ring sweep (smooth unidirectional motion) → pass

Cross-check a few rendered videos against PEAT (free, named by Fisher §5.6) **[P]**.

### Warn or block: **warn and gate** [P]

Reasons not to block:
- No regulation covers this device class. ITU and Ofcom cover broadcast; Fisher notes nothing is universally enforced.
- Fisher frames its table as "suggest to avoid" and "not intended to limit imagination".
- Small displays are lower-risk (Fisher patient advice).
- Users design for themselves, and blocking strobes would make the app refuse legitimate effects.

Reasons not to ignore it:
- Output is roughly 150× the luminance threshold.
- Area is at or above the limit at handheld distance.
- The 10–20 Hz band is trivially reachable.
- "Screening can reduce the risk" (Fisher), and the check is cheap.

Concretely:
1. Show a badge on any clip or document that FAILs.
2. Before **Play on LEDs**, full-screen preview, or GM export of a FAIL animation, show a **one-time confirmation** with frequency and area, e.g. "Flashes ~12×/s over 60% of the matrix".
3. Add a **"Reduce flashing in previews"** setting (Apple "Dim Flashing Lights" precedent, cited by Fisher).
4. Agent policy: never *introduce* a FAIL unless the user explicitly asks. Always run `analyze.flash` after motion edits and state the warning. Covered by the safety eval family, including the prompt-injection case.
5. **Escalate before triggers ship:** animations fired by notifications are seen unexpectedly, including by bystanders. A FAIL animation should need explicit opt-in to be assigned to a trigger. That decision belongs to the future triggers sub-project; it's listed under open questions.

### Must be measured on hardware (add to draft §11) [HW]

1. Active matrix **diameter** and LED pitch and dot size (mm). This gives Ω at 20/30/50 cm and the cycles per degree of stripes.
2. **Luminance vs brightness byte** at each of the 4 system brightness levels, measured with a spot luminance meter. Record both per-dot and averaged over the matrix disc, since standards use area-averaged screen luminance. Find the lowest byte above 20 cd/m² and the lowest visible level.
3. **Max sustained frame rate** and whether frames get dropped or merged, which changes the real flash frequency.
4. **Rise/fall time**, and whether brightness uses **PWM** (and at what frequency). Low-frequency PWM would itself be flicker (Fisher §5.5).
5. Behaviour under auto-brightness or dark rooms (contrast against a dark-adapted surround), and realistic viewing distances (handheld vs face-up on a desk).

---

## R8: Few-shot on current models

### Evidence

| Source | Finding | Tag |
|---|---|---|
| LangChain 2024, few-shot tool calling | "3 examples as messages ≈ all 9" on **Multiverse Math** only. Claude 3 Haiku 11% → 75%. OpenAI models showed "much smaller, if any, positive effects". No few-shot figures for GPT-4o or Claude 3.5 Sonnet. | [V] https://www.langchain.com/blog/few-shot-prompting-to-improve-tool-calling-performance |
| DeepSeek-R1 (*Nature* 2025) | "Few-shot prompting consistently degrades its performance"; recommends zero-shot plus an explicit output format. An authors' observation, not an ablation. | [S: quoted consistently across sources] https://www.nature.com/articles/s41586-025-09422-z |
| Nori et al., *From Medprompt to o1* (arXiv 2411.03590) | 5-shot prompting lowered o1-preview on medical QA; "in-context learning may no longer be an effective steering approach" for reasoning-native models. | [V-abs/S] https://arxiv.org/abs/2411.03590 |
| OpenAI reasoning best practices (current) | "Try zero shot first, then few shot if needed … Reasoning models often don't need few-shot examples". Examples must "align very closely with your prompt instructions". | [V] https://developers.openai.com/api/docs/guides/reasoning-best-practices |
| Anthropic prompting best practices (current, covers Opus/Sonnet/Haiku 5.x) | Examples are "one of the most reliable ways to steer … format, tone, and structure"; "Include 3–5 examples"; "Multishot examples work with thinking". | [V] https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices |
| *Revisiting CoT: Zero-shot can be stronger than few-shot* (arXiv 2506.14641) | For strong models (Qwen2.5), CoT exemplars "do not improve reasoning performance"; their "primary function is to align the output format". | [V-abs] https://arxiv.org/abs/2506.14641 |
| Tang et al., *The Few-shot Dilemma* (arXiv 2509.13196) | Over-prompting: performance peaks then declines for GPT-4o, DeepSeek-V3, Gemma 3, Llama 3.x, Mistral. The optimal count is model-specific. Tested on classification, not agents. | [V-abs] https://arxiv.org/abs/2509.13196 |
| Ovcharov, arXiv 2609.15990 (May 2026) | 12 open-weight models: effects are task-dependent (+24 pp vs +3.4 pp), and two models degraded. Tested on classification. | [V-abs] https://arxiv.org/abs/2609.15990 |
| Manus, context engineering (2025) | "Don't few-shot yourself into a rut": agents imitate repeated action patterns and drift. Fix with structured variation. | [V] https://manus.im/blog/Context-Engineering-for-AI-Agents-Lessons-from-Building-Manus |
| LearnAct (arXiv 2504.13805) | **Agentic (GUI) setting:** one demonstration took Gemini-1.5-Pro from 19.3% to 51.7%. | [V-abs] https://arxiv.org/abs/2504.13805 |

### Answer

- **"3 ≈ 9" is not established for 2025–26 models in tool-use settings.** I found no controlled study of few-shot *tool-call trajectories* on current frontier or mid models (e.g. BFCL/τ-bench with and without demonstrations). That is a gap, not evidence either way.
- **Consistent signals:**
  1. Returns saturate after a handful of examples and can turn negative; the optimal count is model-specific.
  2. Reasoning models often need none, or are hurt (R1, o1-preview, OpenAI guidance). Anthropic's current guidance is the exception: it still recommends 3–5 examples, with thinking.
  3. For strong models, exemplars mainly teach **format and protocol**, not reasoning.
  4. Demonstrations still help a lot when the **interface is unfamiliar** (Multiverse Math, LearnAct GUI). That is GlyphWizard's situation: a glyph matrix plus custom tool semantics.
  5. Repeated exemplars can lock agents into ruts.

### Recommendation per tier (D19) [P, must be ablated]

| Tier | Prefix trajectory | Retrieved exemplars (as tool results via `get_example`) |
|---|---|---|
| Capable / reasoning (incl. Claude with thinking, GPT-5.x reasoning, R1-class) | **0 by default**. Turn on 1 per model family only if the 0-vs-1 ablation shows a gain (Anthropic models are the likely exception). | On demand, ≤2 |
| Mid (the v1 support bar) | **1** canonical trajectory (~600 tokens, the provisional slot) | On demand, ≤2 (≤3 total) |
| Future guided/small | 1 | ≤1 |

Rules:
- **D19 needs `maxTier`** (or a per-tier include flag), not just `minTier`. A block that helps weak models can hurt strong ones.
- **Ablate 0/1/3 per model family** in the harness ([CTX] already plans 0/1/3). Make the slot permanent only where it shows a gain. Report it in SUPPORT.md alongside `kbVersion`.
- Exemplars must match tool schemas and instructions exactly (OpenAI's warning). The planned doc-tests enforce this.
- Vary the surface wording of exemplars (Manus).
- Teach *format and protocol* in exemplars; don't try to teach design taste through them.

---

## Recommended draft changes

1. **§7/§8 + D9:**
   - Replace "Keep/Undo bar" with the three-state turn card (Undo / Undo this turn / Restore to before this turn…).
   - Restores apply as new forward groups; the transcript is never truncated.
   - Groups record `touchedPaths`; selective undo is guarded by JSON-Patch `test` ops.
   - Add the interleaving tests.
2. **D9 amendment:**
   - Diffs at frame granularity with binary frame values.
   - gzip sidecar; cap by compressed bytes (~4 MB per document, ~200 groups).
   - Pin the newest agent turn; per-group hard ceiling with a tool error.
   - Remove the 256 KB figure.
3. **§3/§4/§8:**
   - Remove `create_variant`, `present_variants`, the `variant?` args and "ambiguous → variants".
   - v1: one clarifying question.
   - Record the future `propose_variants` (generator params/keyframes only, top tier, flat schema) as a post-v1 item.
4. **§4 flash check:** replace "more than 3 flashes/s" with the `analyze.flash` spec above:
   - per-LED flash count
   - ≥25% of mask LEDs, ≥0.5 s
   - 10–20 Hz flagged as peak risk
   - >5 s warning
   - `L(b)` curve from hardware

   Cite Fisher 2026, ITU-R BT.1702-3, Ofcom and WCAG 2.3.1. Keep the quality flicker metric separate.
5. **§9:** warn + gate (badge, one-time confirmation before LED play/export, a reduce-flashing preview setting); the agent must not introduce a FAIL unprompted; flag the trigger-assignment issue for the triggers project.
6. **§11:** add the five hardware measurements (diameter/pitch, luminance curve × 4 levels, frame rate, PWM/rise time, viewing conditions).
7. **§6 + D19:**
   - Exemplar counts per tier as tabled above.
   - Add `maxTier`.
   - Mark the 600-token trajectory slot provisional pending the 0/1/3 ablation.
   - Re-cite "3 ≈ 9" as narrow (2024, one dataset).

## Open user questions

1. Turn-card wording and behaviour: OK with "Undo this turn" (keeps your later edits when they don't overlap) and "Restore to before this turn…" (non-destructive, undoable)?
2. History budget: ~4 MB compressed per document, ~200 steps. Acceptable?
3. Variants deferred to post-v1, with a clarifying question in v1? (This resolves draft open question 4.)
4. Flash policy: warn + one-time confirmation before LED play/export, never block. And should FAIL animations need explicit opt-in before they can be assigned to notification triggers (decided in the triggers project)?
5. Hardware spike: can you measure the matrix diameter and, ideally, luminance per brightness level? A phone lux app isn't enough; a cheap luminance meter or a calibrated camera method is needed.
6. Few-shot: OK to start capable/reasoning models with **zero** prefix exemplars and let the ablation decide?
