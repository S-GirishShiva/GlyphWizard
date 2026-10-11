# Targeted Gap Research: Providers & Models (post-review r1)

Date: 2026-10-11
Scope: R2/H2, Claude/Gemini/OpenAI-compatible parameter rejections, R6, R9–R11, R12/H6 from `2026-10-11-agent-architecture-review-r1.md`.
Inputs: `agent-research-brief.md`, `decisions.md` (D1–D20), the architecture draft v0.1, [PM] `2026-10-11-agent-providers-models-research.md`, review r1.
Tags: **[V]** = verified against the primary source this session · **[S]** = secondary source (issue, aggregator, blog) · **[I]** = my inference from verified facts · **[P]** = proposal or estimate · **[U]** = unverified / not found.

---

## TL;DR

1. **H2 (OpenAI via Responses) can be deferred, provided v1 routes OpenAI models through OpenRouter.**
   - OpenRouter's model metadata lists `tools` + `reasoning` + `reasoning_effort` for every GPT-5.4 … GPT-6.1 Sol model **[V]**.
   - Its reasoning docs say a Chat Completions update "sent to an OpenAI model becomes a Responses `configuration_update` item". That implies OpenRouter calls OpenAI's Responses API upstream **[V quote, I conclusion]**.
   - No live end-to-end test was possible here, so this needs one paid probe run before we rely on it.
   - A direct OpenAI preset can also work on Chat Completions with `reasoning_effort: "none"` for GPT-6 Sol/Luna. It can't work for GPT-6 Astra or 6.1 Sol **[V]**.
   - Adding a Responses adapter later is roughly 2–4 days of Kotlin **[P]**.
2. **The claims about Claude rejecting parameters are verified, and they break [PM]'s "force the tool call on repair" advice.**
   - On Claude 4.7+, a non-default `temperature`/`top_p`/`top_k` returns a 400 **[V]**.
   - Opus 5.5, Sonnet 5.5, Fable 5.1 and Mythos 5.1 return a 400 for `tool_choice` `any`/`tool` (OpenAI-compat: `required`/named) **[V]**.
   - OpenAI GPT-5.x/6: remove `temperature`, `top_p` and `top_logprobs` whenever reasoning effort isn't `none` **[V]**.
   - DeepSeek thinking mode ignores `temperature` without an error. It **returns a 400 if `reasoning_content` isn't passed back during tool calls** **[V]**.
   - OpenRouter silently ignores parameters a provider doesn't support. That includes `tools`, which is only a soft preference **[V]**.
   - Repair strategy therefore becomes: `tool_choice: auto` + a validator error message + a prompt nudge. Forcing a call is an opt-in per-endpoint quirk only. Adapters send no sampling parameters at all.
3. **R6 has a bigger finding than the review expected.**
   - On Claude Fable 5.1, Opus 5.5, Sonnet 5.5 and Haiku 5.5, a replayed thinking block is rejected with a **400** if anything in `system`, `tools`, or the earlier messages changed. This is enforced by default for accounts created on or after 2026-08-31, which covers most new BYOK users **[V]**.
   - That covers app-update tool changes, and also two things the draft planned to do: re-rendering document state in place and clearing stale tool results.
   - I found no primary source on whether a history that names a since-removed tool returns an error on OpenAI, Anthropic or Gemini **[U]**. vLLM doesn't check it (source read) **[V]**.
   - Recommended strategy: an append-only transcript within a session, plus a schema/kb/model "epoch". When the epoch changes, compact the old turns into one plain-text summary message.
4. **R9–R11 (terms and privacy).**
   - **Cline:** the ToS (last modified 2025-09-25) is personal-use, no key sharing. The ClinePass docs explicitly allow "your own scripts, apps". BYOK looks compliant **[V]**.
   - **Chutes:** subscriptions are for "casual, direct usage" and not for "building production applications". Interactive personal use in GlyphWizard looks compatible; recommend PAYGO in the setup text **[V]**.
   - **Synthetic:** the ToS sits behind a login and couldn't be read **[U]**. Its pricing page says "Works with any agent framework" **[V]**.
   - **Gemini EEA clause:** binds whoever "uses" the Services, which is the key owner. The best reading is that it does **not** bind a BYOK developer who holds no key **[I, not legal advice]**.
   - **models.dev refresh:** compatible with D1 only if it is opt-in and off by default. That is also F-Droid's "Tracking" exemption **[V]**. Cost is a 0.54 MB gzip download from a Cloudflare host that sees the user's IP.
5. **R12/H6: which factors predict multi-turn tool reliability.**
   - **Tool/agentic training** matters more than size: on BFCL multi-turn, xLAM-2-8b scores 70.0 and Llama-3.3-70B scores 21.5 **[V]**.
   - **The integration path** matters a great deal. The same model's FC vs prompt mode swings 30–50 pp: Opus 4.5 68.4 vs 16.1; o3 14.8 vs 62.3 **[V]**. The multi-turn template is worth 6–8 pp and keeping reasoning history 2–5 pp **[V]**.
   - **Run-to-run variance** is high. On Toolathlon-Verified, pass@1 exceeds pass^3 by 8–18 pp **[V]**.
   - 2026 agentic benchmarks only cover large open-weight MoEs. There is no 2026 evidence on models of 30B or below.
   - So the probe should test **our integration path, repeated**, not the model's general skill.

---

## 1. R2/H2: OpenAI's newest models, gateways, and the Responses adapter

### Facts

| Claim | Status | Source |
|---|---|---|
| "Starting with GPT-5.4, Chat Completions does not support tool calling with `reasoning_effort` values other than `none`." | **V** | [migrate-to-responses](https://developers.openai.com/api/docs/guides/migrate-to-responses) |
| "GPT-6 Astra and GPT-6.1 Sol support Chat Completions, but tool calling requires Responses." "GPT-6 Sol and GPT-6 Luna support function calling in Chat Completions only with `reasoning_effort: "none"`." "GPT-6 Astra and GPT-6.1 Sol do not support the `none` reasoning effort." | **V** | [latest-model guide](https://developers.openai.com/api/docs/guides/latest-model) |
| Chat Completions is not deprecated: "While Chat Completions remains supported, Responses is recommended for all new projects." | **V** | migration guide |
| Responses stateless mode (`store:false`): reasoning items carry `encrypted_content` by default. "pass back all reasoning items, function call items, and function call output items" | **V** | [reasoning guide](https://developers.openai.com/api/docs/guides/reasoning) |
| OpenRouter `/api/v1/models` (fetched today) lists `tools`, `tool_choice`, `reasoning`, `reasoning_effort` for gpt-5.4, 5.4-mini/nano, 5.5, 5.6-luna/terra/sol, 6-luna, 6-sol, 6-astra, 6.1-sol. None of them list `temperature`/`top_p` | **V** (metadata only) | https://openrouter.ai/api/v1/models |
| OpenRouter: "A Chat Completions or Messages API update sent to an OpenAI model becomes a Responses `configuration_update` item." Its tool-calling example uses Chat Completions with `reasoning_details` and says `~openai/gpt-sol-latest` is "completely interchangeable" with Claude | **V** quote / **I** that OpenRouter uses Responses upstream | [OpenRouter reasoning tokens](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens) |
| OpenRouter also offers its own stateless `/api/v1/responses` (rejects `store:true` / `previous_response_id`) for many models | **V** | [OpenRouter Responses overview](https://openrouter.ai/docs/api_reference/responses/overview) |
| Several OSS clients dropped OpenRouter `reasoning_details` (encrypted, `rs_…`) on tool turns, so the model "restarts its reasoning" | **S** | [laravel/ai #1110](https://github.com/laravel/ai/pull/1110), [pi #7994](https://github.com/earendil-works/pi/issues/7994), [openclaw #150643](https://github.com/openclaw/openclaw/issues/150643) |
| OpenCode Go routes GPT/Grok only via `/responses` | **V** in [PM] | opencode.ai/docs/go |

**[U]:** I couldn't run a live OpenRouter call with GPT-6 + tools + reasoning (no key in this environment). The metadata, the docs example and the `configuration_update` statement all point the same way, but none of them is an end-to-end test.

### Cost of a Responses adapter in Kotlin [P]

What differs from Chat Completions:
- **Request:** `instructions` + an `input` item list (`message`, `function_call`, `function_call_output`, `reasoning`).
- **Tools:** a flat tool shape (`{type:"function", name, parameters, strict}`).
- **Streaming:** typed SSE events (`response.output_item.added`, `response.function_call_arguments.delta`/`.done`, `response.completed`, …).
- **Replay:** reasoning items echoed verbatim.

The draft already has an internal neutral transcript and a `providerExtras` slot. On top of that I estimate **~400–700 LOC plus golden-file tests, about 2–4 focused days**.

The official [openai-java](https://github.com/openai/openai-java) (4.80.0, supports Responses, custom `baseUrl`) is an option. However, it doesn't state Android support **[V: no statement]**, and it adds a large dependency that only serves one provider. A hand-rolled Ktor adapter fits D20 better.

### Recommendation

- **v1:** two adapters (Chat Completions + Anthropic Messages). Add an **OpenAI-via-OpenRouter** path, gated by one confirmation run of the probe (§5) on `openai/gpt-6-sol` before release.
- **Optional direct OpenAI preset:** Chat Completions with quirk `forceReasoningNone`, offered only for models that accept `none` (GPT-5.4+ non-Astra, 6 Sol/Luna). Label it "reasoning off". Exclude Astra and 6.1 Sol.
- **Post-v1:** add the Responses adapter when the harness or users show a need. Keep `wire: OpenAIResponses` in the `EndpointConfig` enum so the data model doesn't change.
- **OpenRouter requests carrying tools** must send `provider.require_parameters: true`. By default, a provider that doesn't support `tools` still receives the request and ignores them **[V]** ([provider selection](https://openrouter.ai/docs/guides/routing/provider-selection)).

---

## 2. Parameters rejected or ignored with reasoning models

| Provider / model | Rejected (400) | Silently ignored | Source |
|---|---|---|---|
| **Claude 4.7+ and Mythos Preview** | `temperature`, `top_p`, `top_k` at non-default values. Setting both temperature and top_p. On the OpenAI-compat layer: temperature < 1, and top_p ≠ 0.99 | — | [Working with messages](https://platform.claude.com/docs/en/build-with-claude/working-with-messages), [OpenAI SDK compat](https://platform.claude.com/docs/en/api/openai-sdk) **[V]** |
| **Claude 4.6+** | Assistant prefill (last message from the assistant) | — | Working with messages **[V]** |
| **Claude Opus 5.5, Sonnet 5.5, Fable 5.1, Mythos 5.1** | `tool_choice` `any`/`tool` (compat: `required`/named), including on `count_tokens` | — | [Define tools → Forcing tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools), [errors](https://platform.claude.com/docs/en/api/errors) **[V]**. Also hit via OpenRouter: [osaurus #3051](https://github.com/osaurus-ai/osaurus/issues/3051) **[S]** |
| **Claude Opus 5.5** | `thinking: disabled` and `thinking: enabled+budget` (adaptive only) | — | [Opus 5.5 migration guide](https://platform.claude.com/docs/en/models/opus-5-5/migration-guide) **[V]** |
| **Claude, manual extended thinking (any model)** | `tool_choice` any/tool | — | Define tools **[V]**. On models that accept a forced call, "a forced call skips thinking" **[V]** |
| **Claude OpenAI-compat layer** | — | `strict`, `response_format`, `reasoning_effort`, `seed`, penalties. No prompt caching. Thinking content not returned | OpenAI SDK compat **[V]** |
| **OpenAI GPT-5.x/6** | `temperature`/`top_p`/`top_logprobs` when effort ≠ `none`. Tools on Chat Completions when effort ≠ `none` (5.4+), or ever (Astra / 6.1 Sol) | — | latest-model guide **[V]** |
| **Gemini 3.x** | `thinking_level` + `thinking_budget` together. `minimal` on 3.1 Pro. Reasoning can't be turned off on 3.x / 2.5 Pro | Compat layer: unlisted parameters "silently ignored" (documented for image generation) | [Gemini 3 guide](https://ai.google.dev/gemini-api/docs/gemini-3), [OpenAI compat](https://ai.google.dev/gemini-api/docs/openai) **[V]** |
| **Gemini 3.x temperature** | Not rejected, but "strongly recommend keeping … default 1.0"; lower values "may lead to … looping" | — | Gemini 3 guide **[V]** |
| **Gemini thought signatures** | "Function call is missing a thought_signature" 400. Reportedly strict mainly for parallel calls. A placeholder `skip_thought_signature_validator` returns 200 | — | **[S]** [opencode #50833](https://github.com/anomalyco/opencode/issues/50833), [OpenDots #103](https://github.com/CopilotKit/OpenDots/pull/103). Current Google docs now describe stateless "MUST resend thought blocks exactly" + "do NOT remove or modify" ([thinking](https://ai.google.dev/gemini-api/docs/thinking)) **[V]** |
| **DeepSeek (thinking mode)** | **Missing `reasoning_content` during tool calls → 400** | `temperature`, presence/frequency penalties (no effect). `top_p` < 0.95 clamped | [DeepSeek thinking mode](https://api-docs.deepseek.com/guides/thinking_mode) **[V]** |
| **OpenRouter** | — | A parameter the routed provider doesn't support is ignored. `tools`, `response_format` and `verbosity` are "soft preference" | provider selection docs **[V]** |
| **vLLM** | `tools: []` (empty array). Named `tool_choice` that isn't in `tools`. Structured-output constraints + named tool_choice together | — | `vllm/entrypoints/openai/chat_completion/protocol.py` (main, read today) **[V]** |

### Implications for repair and retry

- **Drop [PM] Q3's "`tool_choice: required` on repair steps" as a default.** It is a 400 on the newest Claude models. On Claude models that accept it, it skips thinking. vLLM accepts it, but [PM] notes a GLM bug there.
- New default: `tool_choice: auto`. The validator error tells the model what's wrong and how to fix it (the draft already requires this). Add a one-line user-turn nudge ("Call `edit_clip` with corrected args").
- Forcing a call becomes a per-endpoint quirk, `canForceTool`. Default it to true only where the probe confirmed it. **[P]**
- **Never send sampling parameters by default** (review M7 confirmed). Make `temperature` an advanced, per-endpoint opt-in.
- **Reasoning replay is mandatory, not an optimisation**, on DeepSeek (400), Gemini (400), Claude (thinking blocks inside the tool loop) and OpenAI Responses. It is strongly recommended on OpenRouter. The probe must exercise it (§5, check C2).
- Retry policy: a 400 whose body mentions a known rejected parameter can trigger **one** automatic retry with that parameter removed. Record the result as a learned quirk. No other 400 is retried. **[P]**
- On Anthropic, don't hide the thinking-binding 400 (§3). If we retry with `drop_block`, log it, as the docs require for gateways.

---

## 3. R6: replaying conversations after tool, schema or kb changes

### Facts

1. **Claude thinking-block binding [V]** ([preserved thinking](https://platform.claude.com/docs/en/build-with-claude/preserved-thinking), [errors](https://platform.claude.com/docs/en/api/errors)).
   - Scope: Fable 5.1, Opus 5.5, Sonnet 5.5, Haiku 5.5. A replayed thinking block is valid only while its prefix is unchanged: `system`, `tools`, and the earlier messages.
   - The check is enforced (400) by default for **accounts created on or after 2026-08-31**. Older accounts are only checked when they opt in. The docs say: "If people run your tool with their own API keys, those on newer accounts get the 400 error before you do."
   - These edits invalidate later thinking blocks:
     - adding, removing, renaming or editing a tool
     - changing `system`
     - clearing or shortening an earlier `tool_result`
     - re-rendering context in the first user message
     - editing or deleting earlier messages
     - removing a thinking block from the middle
   - These are fine:
     - appending messages
     - removing thinking blocks from the start, the end, or all of them
     - changing `tool_choice`, `max_tokens` or effort
   - Escape hatches:
     - `thinking.block_binding.prefix_mismatch_behavior: "drop_block"` (beta header `thinking-binding-controls-2026-08-01`). It costs extra output tokens.
     - Mid-conversation `tool_addition` / `tool_removal` system messages (beta `inline-tools-2026-09-15`).
     - "Simple compaction (recommended)": summarise the session into one user message.
   - Sonnet 5.5 and Haiku 5.5 thinking blocks are **account-bound**, which matters if a user switches keys or imports someone else's conversation.
   - "Rebuilds a saved session from templates instead of replaying what it sent" is listed as a problem pattern. **We must persist the exact wire content.**
2. **Gemini [V]:** "You MUST always resend all thought blocks exactly as they were received" and "should NOT remove or modify thought blocks". Signatures from a different model or endpoint fail. The placeholder workaround is **[S]**.
3. **OpenAI Chat Completions / Responses:** I found no primary statement on what happens when history references a tool that is missing from `tools` **[U]**. One forum answer says to keep every historical tool defined **[S]**. Responses fails if a `function_call_output` has no matching call (forum report) **[S]**.
4. **Anthropic, history with `tool_use` but no `tools` field:** "Requests which include `tool_use` or `tool_result` blocks must define tools" **[S: Portkey error library; not found in Anthropic docs]**. Behaviour when `tools` is present but the name is missing **[U]**.
5. **vLLM [V, source]:** request validation checks only `tool_choice` against `tools`. History is rendered by the model's chat template without checking it against the tool list. Whether the model gets confused is a model-quality question **[I]**.
6. **OpenRouter:** behaviour is whatever the upstream does, plus its own message-shape validation. Example: "insufficient tool messages following tool_calls" 400s routed to DeepSeek **[S]** ([free-claude-code #326](https://github.com/Alishahryar1/free-claude-code/issues/326)).

### Recommended persistence and migration strategy [P]

- **Store two layers per conversation:**
  - (a) A **provider-neutral transcript**: user text, assistant text, tool calls (name, args, result summary), plus the `changedIds`/undo-group link. This is what the UI shows and what survives forever.
  - (b) A **wire cache**: the exact request/response content blocks, including `providerExtras` (thinking blocks, signatures, `reasoning_details`, `reasoning_content`, encrypted reasoning). Keyed by an **epoch** = `(endpointId, modelId, accountKeyFingerprint, toolsetHash, systemPromptHash, kbVersion)`.
- **Within one epoch, keep it append-only.**
  - Freeze `system` and `tools` for the session.
  - Put changing state (the document ID index/summary, the "user changed X" note) in the **newest user turn**, never re-rendered into earlier messages.
  - Shorten tool results **before first send**, never afterwards.
  - This replaces the draft's "clearing stale tool results" (§6) with "keep results short at the source; compact when over budget".
- **When the epoch changes** (app update, model or endpoint switch, key change, tier change):
  - Drop the wire cache.
  - Start the request with **one synthetic user message**: an app-written summary of the session (the design brief the draft already plans, plus the last N turns rendered as plain text, e.g. `[earlier: edit_clip c3 period_ms=800 → ok]`), followed by the new instruction.
  - No historical `tool_use`/`tool_calls` objects and no reasoning blobs are sent. This avoids every unknown above on every provider, and it is Anthropic's own "simple compaction" recommendation.
- **Compaction over budget** uses the same mechanism, so it is one code path. Never compact between a `tool_use` and its `tool_result` [V].
- **Tool-gating mid-session** (D19 tiers): fix the tool list at session start from the model's tier. Changing the tier starts a new epoch. Don't use Anthropic's inline-tools beta in v1 (it's Anthropic-only).
- **Exports:** export only the neutral transcript, never the wire cache. That covers account-bound signatures and keeps opaque blobs out of files. This also answers draft §12 Q5.

---

## 4. R9–R11: terms of service and privacy

### R9: subscription terms

| Service | What the primary text says | Assessment for BYOK GlyphWizard |
|---|---|---|
| **Cline / ClinePass** | Docs: "You can use ClinePass models from your own scripts, apps, or automation through the Cline API." **[V]** ([ClinePass](https://docs.cline.bot/getting-started/clinepass)). ToS (last modified **2025-09-25**, which predates ClinePass, and doesn't mention it): licence "solely for your personal use or internal business purposes"; no access "through any technology or means other than those provided by the Service or authorized by us" (§2.2(10)); no automated access beyond human-scale request rates (§2.2(2)); no buying, selling or transferring API keys; "you will not allow or enable anyone else to use your Subscription" (§7.3(c)(v)) **[V]** ([cline.bot/tos](https://cline.bot/tos)) | **OK** when the user pastes their own key for their own use. The docs statement is the authorisation §2.2(10) asks for. Agent loops are bounded and interactive. Risk: there are no ClinePass-specific terms, so recheck before release. |
| **Chutes** | "Subscriptions are intended for casual, direct usage and not for use in large-scale automated inference, building production applications, or similar high-volume workloads… highly automated usage must use the PAYGO"; multiple subscriptions to get around limits = ban. Data: "We do not log, store, or persist the content of your API inference requests"; "do not use your API requests… to train"; compute may be run by "independent third-party node operators". Last updated 2026-03-16 **[V]** ([chutes.ai/terms](https://chutes.ai/terms)) | Personal interactive use fits "casual, direct usage" **[I]**. The preset copy should recommend PAYGO keys and mention third-party node operators in the privacy notice. |
| **Synthetic** | `/terms`, `/tos` and `/legal/terms` all redirect to a login page; no public ToS found **[U]**. Pricing page: "Works with any agent framework", "UI and API access", "500 requests/5hr", "1 concurrent request per model" **[V]** ([pricing](https://synthetic.new/pricing)) | Probably OK, but **unverified**. Ask Synthetic or read the terms after login. Note the 1-concurrent-request limit: no parallel probe requests. |

### R10: Gemini EEA/CH/UK clause

Primary text ([Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms), last updated 2026-04-28) **[V]**:
- "You may use only Paid Services when making API Clients available to users in the European Economic Area, Switzerland, or the United Kingdom."
- In those regions, the Paid Services data-use terms "apply to all Services, including Unpaid Services".
- Users must be 18+. There is no clause about end users bringing their own key.

**How it applies to us [I, not legal advice]:**
- The terms bind the party that accepts them and *uses* the Services, meaning the key holder.
- In BYOK, the GlyphWizard developer holds no key, makes no API calls, and so isn't "using" Gemini Services.
- The key holder uses their own key for themselves and isn't "making API Clients available to users".
- So the clause most plausibly binds nobody in the BYOK case. The EEA data-use protection applies to EEA key holders automatically either way.

Action:
- No region blocking.
- The Gemini preset's privacy notice says that free-tier data may be human-reviewed outside the EEA/CH/UK, and that Google requires API users to be 18+.
- If the project ever ships a *shared* or bundled key, this conclusion flips.

### R11: optional model-registry refresh vs D1 "fully local"

- **What a refresh involves:** `https://models.dev/api.json` is **5.39 MB raw / 0.54 MB gzip**, served by Cloudflare (`server: cloudflare`, `cache-control: max-age=0`) **[V, measured]**. The repo `anomalyco/models.dev` is MIT and active (pushed 2026-10-11) **[V]**.
- **Privacy:** each fetch shows the user's IP address, the time, and the app's User-Agent to Cloudflare and the models.dev operators (the anomaly/opencode team). It sends no prompts or keys. This is a new third party the user didn't choose, unlike calls to their own provider.
- **F-Droid "Tracking" anti-feature:** applies to apps that report activity "by default", e.g. "checking for updates without your knowledge or permission". The exception is a feature that is "opt-in … and disabled by default" **[V]** ([F-Droid Anti-Features](https://f-droid.org/docs/Anti-Features/)).
- **Is it compatible with D1?** Yes **[I/P]**, if all of these hold:
  - off by default, with an explicit toggle that names the host
  - a manual "refresh now" button rather than a background schedule
  - the response is validated against a schema and treated as data only (no code, no prompts)
  - the app works fully without it
- **The better default needs no third party:**
  - live metadata from the **user's chosen** endpoint: OpenRouter `/api/v1/models` (supported parameters, context, pricing) and Ollama `/api/show`
  - a bundled registry slice refreshed with each release
  - the probe
  - This matches review r1's "registry → probe + override + tiny hand list" simplification.
- **Quirk delivery between releases** (review M7): user-toggleable quirk flags plus the one-shot "remove the rejected parameter and record a quirk" retry (§2). That covers most drift without any network refresh.

---

## 5. R12/H6: 2026 multi-turn tool-use evidence and the capability bar

### What the evidence says

| Finding | Status | Source |
|---|---|---|
| BFCL CSV is still dated 2026-04-13 (rechecked today); nothing newer has been published | **V** | gorilla.cs.berkeley.edu/data_overall.csv |
| Multi-turn: xLAM-2-70b 77.4, **xLAM-2-8b 70.0**, Opus 4.5 68.4, GLM-4.6 (FC thinking) 68.0, Gemini-3-Pro 63.1, Qwen3-32B 47.9, Qwen3-8B 41.8 > Qwen3-14B 34.8, Qwen3-30B-A3B 30.0, **Llama-3.3-70B 21.5** | **V** (my parse) | same |
| **The calling mode dominates:** Opus 4.5 FC 68.4 vs Prompt 16.1; o3 FC 14.8 vs Prompt 62.3; GPT-5.2 FC 28.1 vs Prompt 43.8; Gemini-2.5-Flash FC 36.3 vs Prompt 16.8 | **V** | same |
| The multi-turn template changes BFCL multi-turn by ~6–8%. Keeping thinking history gives +2–5% (Qwen3). Multi-turn has "notably higher variance" across seeds. Multi-turn SFT on a fixed budget *lowered* multi-turn scores | **V** | [arXiv 2606.00135](https://arxiv.org/html/2606.00135v1) |
| Toolathlon-Verified (released 2026-06-30) open-weight: GLM 5.3 Flash 78.4, Kimi K3 76.5 (pass^3 68.5), DeepSeek V4 Pro 74.4, V4 Flash 70.7 (pass^3 58.3), GLM 5.2 59.9 (pass^3 41.7), Kimi K2.6 58.0 (41.7), MiniMax M2.7 47.5, Qwen3.5-397B 40.7. **pass@1 exceeds pass^3 by 8–18 pp.** Only large MoEs are listed | **V** | [toolathlon.xyz leaderboard](https://toolathlon.xyz/docs/leaderboard) |
| τ²-bench (text) official top: Qwen3.5-397B 87.9, Gemini 3.0 Pro 85.4, Opus 4.5 85.3. Aggregators list self-reported ~99% (GLM-5.2), so it is near saturation and of little use for telling models apart | **V** official / **S** aggregators | [taubench.com](https://taubench.com), [benchlm](https://benchlm.ai/benchmarks/tau2-bench) |
| MCPMark (ICLR 2026): best gpt-5-medium 52.6 pass@1 / 33.9 pass^4. Newer "Verified" numbers are self-reported | **V** abstract / **S** | [arXiv 2509.24002](https://arxiv.org/abs/2509.24002) |
| MCP-Universe: GPT-5 43.7%. No 2026 update and no small models | **V** abstract | [arXiv 2508.14704](https://arxiv.org/abs/2508.14704) |
| No 2026 agentic benchmark reports models of 30B or below | **U** (searched; none found) | — |

### What predicts multi-turn reliability (synthesis) [I]

1. **The integration path** (FC vs text, template, reasoning replay). Effects of 30–50 pp on BFCL dwarf the size effects. A model that "can" do multi-turn fails under the wrong wire handling. *So the probe must run through our real adapter, our quirks and our tools.*
2. **Tool and agentic training** matters more than parameter count (8B tool-tuned beats 70B general-purpose, and 8B beats 14B in the same family).
3. **Consistency** is its own axis. pass^k drops 8–18 pp below pass@1 even for frontier-adjacent models, so a single successful probe run overstates reliability.
4. **Domain transfer is unknown.** No public benchmark resembles glyph editing (the review already noted this). That is exactly why D17 makes the harness the authority.

### Capability bar re-derived (consistent with D17 and D19)

**Principle:** the probe is a **conformance gate and smoke test**. It can only place a model in *Unsupported* or *Base (provisional)*. Only harness results, shipped as a bundled `SUPPORT` table keyed by model and endpoint, can confirm Base or unlock *Full*. A user override exists in both directions, with a warning. **[P]**

**Probe content [P]:** a fixed 13×13 fixture document, the real base-tier tools and the real system prompt.

| # | Check | What it catches | Pass rule |
|---|---|---|---|
| C1 | Streamed native tool call parses; JSON args are schema-valid; ids and finish reasons are sane; `usage` present | Broken streaming parser, servers without tool templates (llama.cpp without `--jinja`), text-only tool calls | Must pass |
| C2 | Second request with the tool result and the replayed reasoning payload succeeds | thought_signature, DeepSeek `reasoning_content`, OpenRouter `reasoning_details`, Claude thinking 400s | Must pass |
| C3 | When the task clearly needs a tool, a native call is emitted (not prose) | OpenRouter routing to a provider that ignores `tools`, models that narrate instead of calling | Must pass |
| C4 | Context: compare `usage.prompt_tokens` with our estimate on the largest probe request; read the declared context from metadata | Silent truncation (Ollama `num_ctx` defaults) | Effective context ≥ 32k (declared), and no truncation seen |
| B1 | Dependent 2-step task: `describe_document` → edit using the returned ID | Multi-step chaining | **2 of 2 runs** (pass^2) |
| B2 | Validator-error recovery: a planted invalid argument → structured error → corrected call within ≤ 2 attempts | Error recovery | 1 of 1 |
| B3 | Abstention: question answerable from context; no write call | Over-calling | 1 of 1 |
| B4 | Termination: ends with a text summary within the step cap; no repeated identical calls | Loops | Implied by B1–B3 |
| V | Optional: count dots in a 3-dot render | Vision flag | Separate flag |

**Cost and length [P]:**
- About 8–10 requests at ~5–7k input tokens each (system + base tools + fixture), so ~50–70k input and ~3–6k output tokens.
- At typical flash-class prices this is about **$0.01–0.05**; at frontier prices **~$0.2–0.5**.
- Wall time is **~20–90 s** with reasoning models, not the draft's ~10 s. Run sequentially, because of Synthetic's 1-concurrency limit.
- Run it on explicit tap, plus once after an endpoint or model change. Show the cost estimate first.
- Cache the result per epoch (§3).

**Tier boundaries as hypotheses for the harness [P], to be calibrated:**

| Tier (D19) | Granted by | Hypothesised boundary |
|---|---|---|
| **Unsupported** | Probe | Any of C1–C4 fails, or B1–B3 fails |
| **Base** (≈10 coarse tools) | Probe = provisional; harness = confirmed | Harness base-toolset families (edit, repair-planted-defect): **pass^3 ≥ 0.6**; safety/injection family: **0 unsafe writes over 3 runs** |
| **Full** (fine-grained edits, paint, renders, variants) | Harness only | Full toolset **pass^3 ≥ 0.7**, and **no regression** vs the same model's base-toolset score beyond 5 pp. The second condition directly tests D19's claim that a bigger surface helps capable models |
| **+Vision** | Probe V + harness perceive family | Flag only |

Thresholds are placeholders. The first harness round should plot pass^3 per family and per model, then set the cut where the support table separates cleanly. Publish the result with `kbVersion` stamped.

---

## 6. Recommended changes to the draft

1. **§5 Clients:** say "v1 = Chat Completions + Anthropic Messages. OpenAI models via OpenRouter (verify with one probe run). Direct OpenAI optional with `reasoning_effort:none` on supporting models. Responses adapter post-v1, ~2–4 days." Fold in H2.
2. **§5:** add a **"never send sampling parameters"** rule and an **"OpenRouter + tools ⇒ `provider.require_parameters: true`"** rule. Never send `tools: []`.
3. **§4 Self-repair / [PM] Q3:** replace "force the tool call on repair" with `auto` + structured error + a nudge. `canForceTool` becomes a per-endpoint quirk, default off on Claude 5.5-class models.
4. **§5 Hard part:** list reasoning replay as **required for correctness**. The concrete 400s are DeepSeek, Gemini and Claude; OpenAI Responses is next.
5. **§6 Context:**
   - Remove "clearing stale tool results".
   - Say "shorten tool results before first send; document state goes in the newest user turn; system and tools frozen per session".
   - Add the **epoch + compaction-summary** mechanism (§3).
6. **§7 Persistence:** two layers (neutral transcript + epoch-keyed wire cache). Exports carry only the neutral transcript. Closes review M8.
7. **§5 Capability bar:**
   - Reword the evidence to "multi-turn reliability is driven by integration path and tool training more than size; consistency gap 8–18 pp".
   - The probe becomes the C1–C4/B1–B4 conformance gate; it grants at most Base (provisional). The harness grants Base and Full (D17/D19).
   - Probe time is "~20–90 s, cents".
8. **§5 Detection:** use the user's own endpoint metadata + a bundled registry + the probe. The models.dev network refresh is optional, opt-in, off by default, manual.
9. **§5 Subscriptions table:**
   - ClinePass "allowed (docs) — ToS predates ClinePass, recheck"
   - Chutes "personal interactive use; prefer PAYGO; node-operator privacy note"
   - Synthetic "ToS not public — unverified"
   - Gemini "no EEA blocking needed for BYOK; 18+ and free-tier review notice"

## 7. Open questions for the user

1. **OpenAI in v1:** accept "OpenAI via OpenRouter only (plus optional reasoning-off direct preset)" for v1, with Responses later? And may we spend ~$1 on a one-off verification probe of `openai/gpt-6-sol` via OpenRouter?
2. **Optional models.dev refresh:** ship it (opt-in, off by default, manual), or skip it entirely and rely on per-release updates plus live metadata from the user's endpoint?
3. **Synthetic:** do you (or someone with an account) want to read their logged-in ToS, or should we drop the Synthetic preset and leave it to "Custom"?
4. **Epoch compaction:** OK that switching model or endpoint (or updating the app) mid-conversation summarises earlier turns instead of replaying them verbatim? The model loses its earlier reasoning but nothing breaks.
5. **Probe trigger:** auto-run after an endpoint or model change (cost shown, ~$0.01–0.5) or tap only?
6. **Tier thresholds:** are pass^3 ≥ 0.6 (Base) / ≥ 0.7 (Full) acceptable as *starting hypotheses* for the first harness round?
