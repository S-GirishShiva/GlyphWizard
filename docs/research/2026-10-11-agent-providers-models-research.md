# Agent Research A4: Providers, BYOK, and Open-Weight / Small Model Support

Date: 2026-10-11
Status: research / recommendations (no code). Feeds D2, D12, D13, D14, D15.
Confidence markers (same as the perception report): **[verified]** = checked against a primary or near-primary source in this session; **[secondary]** = blog / aggregator / GitHub issue; **[speculative]** = my reasoning; **[verify]** = from memory or conflicting sources, check before relying on it.

---

## 1. TL;DR and recommendations

**Scope (user update, 2026-10-11):** v1 targets **mid-capability and better** models. Small / 8k-context models are supported only if that's cheap. Otherwise they're marked unsupported for now. This report therefore frames model support as **(a) the minimum capability bar for v1** and **(b) what it would cost to go lower** (§3c). The small-model evidence stays in, because it is what sets the bar.

**Main point:** "One OpenAI-compatible client" covers most of the open-weight world, but not all of it, and not without a quirk layer. You need up to **three wire adapters** (OpenAI Chat Completions, Anthropic Messages, OpenAI Responses). The hard engineering isn't the adapters. It's a **robust tool-call accumulator plus a round-trip slot for opaque reasoning data**, because that's where every real-world client breaks. On the model side, small models are fine at *single* tool calls and collapse at *multi-turn* tool use. The v1 bar is therefore "**passes a multi-step tool probe, ≥ 32k effective context, native tool calls**". Every current subscription-gateway model (OpenCode Go, ClinePass, Synthetic, etc.) clears it easily. Most self-hosted ≤ 14B dense models don't.

**v1 minimum capability bar [speculative thresholds, evidence-motivated]:**
- Native tool calling that passes our probe, including a **2-step dependent call** and **recovery after a validator error**.
- **≥ 32k effective context.** Check the server's real limit, not the model card.
- Streaming works, or the endpoint is flagged for non-streaming.
- Anything below the bar: the agent is disabled for that endpoint, with a clear message and a user override at their own risk.
- **What clears it:** frontier APIs, GLM-5.x incl. GLM-5.3-Flash, Kimi K2.6+/K3, DeepSeek V4.x, MiniMax M3, Qwen3.7/3.8, MiMo, and likely self-hosted ~30B-class tool-trained models (Qwen3-32B, GLM-4.7-Flash 30B-A3B) if the server is configured for ≥ 32k. Each still has to pass the probe.
- **What fails it on the evidence:** Qwen3-4B/8B/14B, Gemma 3, Mistral Small, Phi-4, Llama 3.x 8B. Their BFCL multi-turn is 4–42%.

Recommendations, in priority order:

1. **Build three adapters behind one internal message model.**
   - **Chat Completions** covers OpenRouter, Ollama, llama.cpp, vLLM, LM Studio, Synthetic, Chutes, z.ai, Kimi, MiniMax, most OpenCode Go models, and Gemini's compat endpoint.
   - **Anthropic Messages** covers Anthropic, plus the Anthropic routes of MiniMax, Kimi, and OpenCode Go.
   - **OpenAI Responses** is required: OpenAI's own guide says GPT-5.4+ can't do tool calling on Chat Completions with reasoning enabled, and GPT-6-class models *require* Responses for tool calling **[verified]**.
   - Skip a native Gemini adapter in v1. Use Gemini's compat endpoint, but round-trip `extra_content.google.thought_signature` **[secondary, many issues]**.
2. **Make the stream parser defensive.**
   - Accumulate tool calls by `id` *or* `index`, and accept whole calls in one chunk.
   - Accept calls that arrive in the final frame, and don't trust `finish_reason`.
   - Keep a per-endpoint "non-streaming" fallback toggle.
   - Store each provider's opaque reasoning blob (`reasoning_details`, thinking blocks, thought signatures) verbatim and replay it within the current tool loop.
   - Every one of these has a documented, recent GitHub issue (§2 Q1).
3. **v1 support is binary plus a vision flag:** *Supported* (clears the bar) or *Below bar* (agent off, user override possible). Vision is an orthogonal flag. A "T1 guided" mode for small models is a **post-v1 option** (costed in §3c).
   - Assignment combines a **bundled curated registry** (seeded from models.dev, MIT), **live metadata** (OpenRouter `supported_parameters`, Ollama `/api/show` capabilities), a **~10-second probe** of canned tool-call tests, a **user override**, and eventually **eval-harness results**.
4. **Adopt now the cheap, small-model-friendly practices that also help mid-tier models.** These cost nothing extra and make "going lower" later cheaper:
   - Task-level tools with flat params, and generator clips before pixel writes.
   - Validator errors returned as *described* feedback, plus lenient parsing / JSON repair, with a capped retry.
   - The document state, not the chat history, as memory.
   - The BFCL data shows the cliff is multi-turn (Qwen3-4B: 76% live single-call vs 22% multi-turn), so these practices help any model that is near the bar.
5. **Don't turn on constrained decoding together with tools by default.** "Tool Suppression" has been reported when JSON-schema constraints and tools are combined on open-weight models **[secondary: arXiv abstract]**. Where the server enforces it natively (vLLM `tool_choice: required` / named function, strict tools on OpenAI/Anthropic), use it per call when we *know* a tool must be called.
6. **Allow subscription gateways only by key, never by OAuth/session hacks, and warn per ToS:**
   - **ClinePass:** **explicitly OK.** "You can use ClinePass models from your own scripts, apps, or automation through the Cline API." It's plain Chat Completions at `api.cline.bot/api/v1`.
   - **Synthetic and Chutes:** OK.
   - **OpenCode Go:** grey zone. It's "designed for OpenCode and other coding agents" and requires a distinct User-Agent plus an `x-opencode-session` header.
   - **z.ai GLM Coding Plan:** *not allowed* outside officially supported tools, with a ban risk. Use z.ai pay-as-you-go instead.
   - **Kimi Code:** ambiguous, since keys are for "third-party development tools".
   - **Anthropic Pro/Max OAuth:** prohibited.
   - Ask OpenCode and Moonshot before advertising support (§6).
7. **Mobile plumbing:**
   - Hand-write thin clients in an Android-free Kotlin/JVM `:agent` module (D14): **Ktor client core + OkHttp engine** (the same engine in the app and the JVM CLI eval harness), our own SSE/NDJSON line parser, kotlinx.serialization, and recorded provider streams replayed via `MockEngine` as tests.
   - Emit tool schemas from one canonical definition in the **cross-provider safe subset** (§1d): flat, all-required with nullable optionals, `additionalProperties: false`, no `oneOf`/`$ref`/`const`, ranges enforced by our validator.
   - Never send `response_format` together with `tools` (the "constraint tax").
   - Evaluate Koog 1.x in a spike, but don't adopt a framework that hides the stream quirks.
   - Store keys as an **Android Keystore AES-GCM–wrapped** blob in DataStore. Jetpack `security-crypto` is deprecated **[verified]**. Exclude keys from backup.
   - LAN servers need cleartext opt-in. Android 17 targeting adds the `ACCESS_LOCAL_NETWORK` runtime permission **[verified-secondary]**.
   - Keep agent turns short and cancellable. A backgrounded turn either finishes under a short foreground service or rolls back its undo group.
8. **On-device model: not viable for v1** on the Phone (4a) Pro.
   - It isn't on the Gemini Nano Prompt API device list, and that API has no function calling anyway.
   - llama.cpp on a Snapdragon 7 Gen 4 does ~52 tok/s prefill and ~10 tok/s decode for a 4B Q4 model, so a 4k-token system prompt costs over a minute before the first token **[secondary, single benchmark]**. 4B models also score ~22% on BFCL multi-turn.
   - Revisit with LiteRT-LM + Gemma-4-class models later.

**Terminology warning:** "GLM flash-class" no longer means *small*.
- **GLM-5.3-Flash** (on OpenCode Go, Oct 2026) is reportedly **~320B total / ~18B active**, with a 1M context **[secondary]**. It's hosted, cheap, and capable, so it should clear the v1 bar.
- **GLM-4.7-Flash** is 30B-A3B **[secondary]**, with a vendor τ²-Bench score of 79.5 **[secondary, vendor-reported]**.
- The genuinely *small* class is 0.6B–14B dense models (Qwen3 4B/8B/14B, Gemma 3 4B/12B, Llama 3.x 8B, Phi-4, Ministral, Mistral Small) on Ollama/llama.cpp.
- **Tier by measured behavior, not by marketing name.**

---

## 2. Findings per question

### Q1. Provider landscape for BYOK, and what breaks in "OpenAI-compatible"

#### 1a. Wire formats

| API shape | Who speaks it | Notes |
|---|---|---|
| **OpenAI Chat Completions** (`/v1/chat/completions`) | OpenRouter, Ollama `/v1`, llama.cpp server, vLLM, LM Studio, Synthetic, Chutes, z.ai, Kimi Code, MiniMax, most OpenCode Go models, Gemini compat endpoint, DeepSeek, Groq, etc. | The lingua franca. OpenAI still supports it, but recommends Responses for new projects **[verified]**. |
| **OpenAI Responses** (`/v1/responses`) | OpenAI; OpenCode Go routes GPT 6 Luna, GPT 5.6 Luna, Grok 4.6/4.7 here **[verified, opencode.ai/docs/go]** | "Starting with GPT-5.4, Chat Completions does not support tool calling with `reasoning_effort` values other than `none`." "GPT-6 Astra and GPT-6.1 Sol require the Responses API for tool calling" **[verified, OpenAI migration guide]**. |
| **Anthropic Messages** (`/v1/messages`) | Anthropic; MiniMax (`api.minimax.io/anthropic`), Kimi Code (`api.kimi.com/coding/`), Synthetic, OpenCode Go (Claude Haiku 5.5, MiniMax M3/M2.7, Qwen3.8/3.7) **[verified for OpenCode Go and MiniMax docs; Kimi secondary]** | Has `strict: true` tool use + JSON outputs **[verified, Claude docs]** and a `count_tokens` endpoint **[verified]**. MiniMax recommends the Anthropic route for prompt-cache benefits **[secondary]**. |
| **Gemini native** (`generateContent`) | Google | Optional. The compat endpoint works with caveats (below). |

**Conclusion:** one Chat Completions adapter covers the *open-weight* world. Frontier OpenAI needs Responses, and Anthropic is best served natively. That makes **three adapters, not one, and not five**.

#### 1b. Provider / gateway table (BYOK suitability)

| Provider | API | Third-party use of key allowed? | Evidence |
|---|---|---|---|
| Anthropic API key | Messages | Yes (API key product) | standard |
| Anthropic **Pro/Max subscription OAuth** | — | **No.** "Using OAuth tokens obtained through Claude Free, Pro, or Max accounts in any other product, tool, or service … is not permitted" | [The Register, Feb 2026](https://www.theregister.com/software/2026/02/20/anthropic-clarifies-ban-on-third-party-tool-access-to-claude/5014546) **[secondary; reports of later wording changes unverified]** |
| OpenAI API key | Responses (+ Chat Completions for older/non-reasoning use) | Yes | [migration guide](https://developers.openai.com/api/docs/guides/migrate-to-responses) |
| Google Gemini API key | native or OpenAI compat | Yes | [compat docs](https://ai.google.dev/gemini-api/docs/openai) |
| **OpenRouter** | Chat Completions (normalizes tools to OpenAI shape for non-OpenAI upstreams) | Yes | [parameters docs](https://openrouter.ai/docs/api/reference/parameters) **[verified]** |
| **OpenCode Go** ($10/mo Go, $40/mo Go Plus; per-model dollar allowances split into 5-hour / weekly / monthly windows) | Base `https://opencode.ai/zen/go/v1`. Chat Completions for GLM-5.x, Kimi K2.6–K3, DeepSeek V4.x, MiMo, Hy3/4 etc.; Messages for Claude Haiku 5.5, MiniMax, Qwen3.8; Responses for GPT/Grok. Model list at `/models` | **Grey.** "OpenCode Go is designed for OpenCode and other coding agents that produce similar types of requests." Traffic is monitored for abuse. Clients must send their own User-Agent (not a generic SDK name) and a stable `x-opencode-session` header per conversation. Validated clients include Claude Code, Codex, and Pi. A design app isn't a coding agent. | [opencode.ai/docs/go](https://opencode.ai/docs/go/) **[verified]** |
| **ClinePass** (Cline's subscription, $9.99/mo; 5-hour / weekly / monthly usage windows; "2–5x the usage … compared to standard API rate") | **OpenAI Chat Completions** at `https://api.cline.bot/api/v1/chat/completions`. Bearer key from app.cline.bot → Settings → API Keys. Model slugs like `cline-pass/glm-5.3`, `cline-pass/glm-5.3-flash`, `cline-pass/kimi-k3`, `cline-pass/deepseek-v4-pro`, `cline-pass/minimax-m3`, `cline-pass/qwen3.8-max` (12 models; GLM-5.2, Kimi K2.6/K2.7 Code, and DeepSeek V4 Flash discontinued). Reportedly the `/models` list endpoint 404s, so slugs must be configured explicitly **[secondary: oh-my-pi #3833]** | **Yes, the most permissive of the coding subscriptions.** "You can use ClinePass models from your own scripts, apps, or automation through the Cline API." The page has no fair-use/ToS text. Personal accounts only, and a hard `ClinePassLimitError` at the cap **[secondary: comparison site]**. | [docs.cline.bot/getting-started/clinepass](https://docs.cline.bot/getting-started/clinepass) **[verified]**; models.dev `cline-pass` entry **[verified]** |
| **z.ai GLM Coding Plan** | Dedicated "Coding" endpoints in Messages / Chat Completions / Responses shapes | **No.** The plan "may only be used within officially supported tools and products". Violations → restricted benefits, rate limiting/freezing, ban after >3 violations. A pi-ai issue reports that generic SDK headers to this endpoint risk bans. **Use the pay-as-you-go z.ai API instead.** | [usage policy](https://docs.z.ai/devpack/usage-policy) **[verified]**; [pi #4187](https://github.com/earendil-works/pi/issues/4187) **[secondary]** |
| **Kimi Code** (Moonshot membership) | OpenAI-compat `https://api.kimi.com/coding/v1`; Anthropic-compat `https://api.kimi.com/coding/` | **Ambiguous.** Docs: "Subscribers can also obtain an API Key to integrate Kimi Code's model capabilities into third-party development tools and platforms." Keys are separate from the Moonshot open platform. | [Kimi Code docs](https://www.kimi.com/code/docs/en/) **[secondary-ish: search snippet of official page]** |
| **MiniMax Token Plan** | `api.minimax.io/v1` (OpenAI) and `/anthropic` | Docs give generic "other tools" setup. No restriction found. | [MiniMax other tools](https://platform.minimax.io/docs/token-plan/other-tools) **[secondary: snippet; ToS not read]** |
| **Synthetic** ($30/mo sub or usage-based) | OpenAI-compat (`https://api.synthetic.new/openai/v1`; docs conflict with `/v1`) and Anthropic-compat | **Yes.** The subscription covers both the UI and the API. | [Synthetic API overview](https://dev.synthetic.new/docs/api/overview) **[secondary: snippet]** |
| **Chutes** (pay-as-you-go; Plus $10 / Pro $20 daily-quota plans) | OpenAI-compat `llm.chutes.ai/v1`, `cpk_` keys | Yes (standard API product) | [starter guide](https://chutes.ai/docs/guides/starter-guide) **[secondary]** |
| **Self-hosted**: Ollama, llama.cpp `llama-server --jinja`, vLLM, LM Studio | Chat Completions | n/a | below |

**Implications [speculative]:**
- Support gateways **only via API keys the user pastes**. Never implement OAuth/session flows that piggyback consumer subscriptions.
- Always send a GlyphWizard User-Agent and, for OpenCode Go, a session header.
- Show a ToS note when the base URL matches a known coding-plan host (`/zen/go/`, z.ai coding endpoint, `api.kimi.com/coding`).
- Ask OpenCode whether a non-coding design agent is acceptable before naming it in docs.

#### 1c. What breaks in "OpenAI-compatible" (all documented in the wild)

| Breakage | Who | Evidence | Mitigation |
|---|---|---|---|
| Streamed tool-call deltas lack `index`, or parallel calls share index 0 with different `id`s, or a whole call arrives in one chunk | Ollama `/v1`, Gemini compat | [ollama #7881](https://github.com/ollama/ollama/issues/7881), [MoonAliza PR #8](https://github.com/StepenkoAnatoli/MoonAliza/pull/8), [home-mind PR #44](https://github.com/hoornet/home-mind/pull/44) **[secondary]** | Accumulator keyed by (index, id). A new id means a new call. |
| Tool calls arrive only in the final `done` frame; `finish_reason: "stop"` despite tool calls | Ollama (e.g., qwen3-coder), Gemini compat | [new-api #7252](https://github.com/QuantumNous/new-api/issues/7252), [home-mind PR #44](https://github.com/hoornet/home-mind/pull/44) | Decide on "tool turn?" from collected calls, not `finish_reason`. |
| Thinking output in a non-standard `reasoning` / `reasoning_content` field hides tool calls from clients that ignore it | Ollama + Gemma 4, DeepSeek-style servers | [opencode #20995](https://github.com/anomalyco/opencode/issues/20995) | Parse `reasoning`, `reasoning_content`, `reasoning_details`; allow `reasoning_effort: none` per endpoint. |
| Tool-call JSON leaks into `delta.content` as text when streaming (non-streaming works) | Ollama via LiteLLM, OpenClaw reports | [litellm #35711](https://github.com/BerriAI/litellm/issues/35711), [openclaw #5769](https://github.com/openclaw/openclaw/issues/5769) | A "stream: false" toggle per endpoint, plus a lenient text-protocol fallback parser. |
| **Opaque reasoning must be replayed** within a tool loop or the next call 400s | Gemini (`thought_signature`), OpenRouter (`reasoning_details`), Anthropic thinking blocks | [pi #10157](https://github.com/earendil-works/pi/issues/10157), [pi #9444](https://github.com/earendil-works/pi/issues/9444), [OpenRouter reasoning docs](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens), [HA #159812](https://github.com/home-assistant/core/issues/159812) | Store a per-message `providerExtras` blob verbatim. Replay it only to the same model/endpoint, and only for the in-progress tool loop ([Geist PR #381](https://github.com/DavidDworetzky/Geist/pull/381)). |
| `reasoning_effort` rejected with 400 by some models; reasoning tokens eat `max_tokens` | various | [home-mind PR #44](https://github.com/hoornet/home-mind/pull/44); [Gemini docs](https://ai.google.dev/gemini-api/docs/openai): thinking can't be disabled on 2.5 Pro / 3-series **[verified]** | Remember per-endpoint "param rejected" and drop it. Budget output tokens generously. |
| Tool calling requires server config | llama.cpp needs `--jinja` and a tool-aware template, else a "Generic" format (more tokens). Parallel calls off by default. Extreme KV-cache quantization "can substantially degrade tool calling" | [llama.cpp function-calling.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/function-calling.md) **[verified]** | Setup docs. Probe detects failures. |
| Silent context truncation | Ollama default `num_ctx` depends on VRAM (4k under 24 GiB) and truncates oldest turns silently | [several blogs, e.g. particula.tech](https://particula.tech/blog/ollama-num-ctx-silent-prompt-truncation) **[secondary; docs disagree with each other]** | Read context length from `/api/show`, warn, and tell the user to set `OLLAMA_CONTEXT_LENGTH`. Whether `/v1` accepts `num_ctx` per request is **[verify]**. |
| Error formats differ (OpenAI `{error:{message,type,code}}`, Anthropic `{type:"error",error:{type,message}}`, bodiless 400s from Gemini compat, HTML from proxies) | all | home-mind PR #44 (bodiless 400) | Normalize to {httpStatus, retryable, userMessage, raw}. Show the raw body in a debug view. |
| JSON-schema support varies widely across providers and engines | OpenAI/Anthropic strict, Gemini, llama.cpp, XGrammar… | §1d | The intersection subset in §1d. |
| Image input | Not all models / servers. OpenRouter metadata exposes `input_modalities`. Ollama exposes `vision` in capabilities | OpenRouter `/api/v1/models` and Ollama `api.md` **[verified this session]** | Vision flag from metadata plus a probe. |

**Kotlin library bugs exist here too:** LangChain4j has an open NPE in `OpenAiStreamingResponseBuilder` with Gemini streaming tool calls **[secondary: search snippet]**. This is an argument for owning the thin client.

#### 1d. Tool-schema subsets: what each provider rejects (hand-off from tools/runtime research)

| Provider / mode | Rule | Evidence |
|---|---|---|
| **OpenAI strict** (`strict: true`, Structured Outputs) | Root must be an `object` and not `anyOf`. **All fields must be `required`** (express optional as `type: [X, "null"]`). `additionalProperties: false` is mandatory on every object. ≤ 10 nesting levels, ≤ 5000 properties, ≤ 1000 enum values. **Unsupported → error:** `allOf`, `not`, `if/then/else`, `dependentRequired/Schemas`. `oneOf` isn't on the supported list. `anyOf`, `$defs`/`$ref`, and recursion are supported. Strings support `pattern` and `format` | [OpenAI structured outputs guide, "Supported schemas"](https://developers.openai.com/api/docs/guides/structured-outputs) **[verified, .md source]** |
| **Anthropic strict** (`strict: true` / JSON outputs) | `additionalProperties` must be `false`. Supported: `anyOf`/`allOf` (limited), internal `$ref`, `enum`, `const`, `default`, simple `pattern`, some `format`s, `minItems` 0/1 only. **Unsupported:** recursion, `minimum`/`maximum`/`multipleOf`, `minLength`/`maxLength`, complex enums, external `$ref`. `oneOf` isn't listed. **Per request: ≤ 20 strict tools, ≤ 24 optional params, ≤ 16 union-typed params**. "Schema is too complex for compilation" 400. Non-strict tools are not grammar-enforced | [Claude structured outputs docs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) **[verified]** |
| **Gemini function declarations** | Historically an OpenAPI-3.0 subset: 400 `Unknown name "additionalProperties"` and rejections reported for `const`, `propertyNames`, `$ref`, `oneOf`, non-string enums, and empty `OBJECT` with no properties. Google later announced JSON Schema support (`anyOf`, `$ref`…) for structured outputs. Behavior differs by endpoint (Gemini API vs Vertex vs OpenAI-compat) and SDK | [jaypie #593](https://github.com/finlaysonstudio/jaypie/issues/593), [LibreChat PR #13850](https://github.com/danny-avila/LibreChat/pull/13850), [python-genai #1815](https://github.com/googleapis/python-genai/issues/1815), [Google blog](https://blog.google/technology/developers/gemini-api-structured-outputs/) **[secondary, conflicting]** |
| **Self-hosted grammar engines** (llama.cpp, vLLM backends) | Coverage of JSON Schema differs ~2× between engines (Guidance > llama.cpp ≈ XGrammar > Outlines on the official test suite). XGrammar had many *under-constrained* cases | [JSONSchemaBench](https://arxiv.org/abs/2501.10868) **[verified abstract; per-engine numbers secondary]** |

**Rule for GlyphWizard (the intersection that every provider accepts) [derived from the above]:**
- Root `type: object`.
- Flat properties of `string` / `number` / `integer` / `boolean` / string-`enum`, plus at most one level of arrays of primitives or small flat objects.
- Every property in `required`, with nullable types for optional fields. Keep optional/union params few (Anthropic's 24 / 16 caps).
- `additionalProperties: false`.
- **No `oneOf`/`allOf`/`not`/`if`, no `$ref`/recursion, no `const`.**
- Put numeric ranges and lengths in the *description*, and enforce them in our validator (the same trick the Anthropic SDK helpers use).
- Polymorphism (e.g., generator params that differ per generator) goes to **separate tools or a `kind` enum plus a flat superset of nullable params**, never a union.
- Keep one canonical schema and emit per-provider variants. That's a tiny "schema sanitizer", needed for Gemini anyway.

**Constraint tax (vLLM/SGLang/llama.cpp):**
- Combining a JSON-schema output constraint (`response_format`) with `tools` can make open-weight models **stop calling tools**. Grammar masks make tool-call tokens unreachable ("Tool Suppression"). The proposed fix is two-pass execution: tool phase unconstrained, final structured answer constrained separately ([arXiv 2606.25605](https://arxiv.org/abs/2606.25605) **[secondary: abstract]**).
- vLLM also has an open bug where GLM-4.7/5.x `required`/named tool calls are lost when reasoning flows straight into a tool call ([vllm #59608](https://github.com/vllm-project/vllm/issues/59608) **[secondary]**).
- vLLM's docs say only `tool_choice: required`/named calls get schema-guaranteed arguments, and `auto` doesn't ([vLLM docs](https://docs.vllm.ai/en/latest/features/tool_calling/)).
- **Policy:** never send `response_format` together with `tools`. Our agent doesn't need a structured *final* answer, because all output goes through tools. Use `required`/named only on steps where a call is mandatory.

**Text-protocol fallback** is covered in Q3 (NLT evidence, Cline XML fallback). Under the boxed v1 scope it's a *post-v1* item (§3c), unless the probe shows a popular endpoint whose native tool calling is broken.

### Q2. Small / open-weight tool-calling reliability: evidence

#### 2a. BFCL v4 (official leaderboard CSV, last-modified 2026-04-13) **[verified: downloaded `gorilla.cs.berkeley.edu/data_overall.csv`]**

The snapshot lags current releases (no GLM-5.x, Qwen3.5+, Gemma 4), but it's the best apples-to-apples data for *small* models.

| Model (mode) | Overall | Live (single-turn, real user queries) | **Multi-turn** | Multi-turn long-context | Irrelevance (abstain) |
|---|---|---|---|---|---|
| Claude Opus 4.5 (FC) — reference | 77.5 | 79.8 | 68.4 | 70.5 | 84.7 |
| GLM-4.6 (FC thinking) | 72.4 | 80.9 | 68.0 | 66.5 | 85.0 |
| Kimi-K2-Instruct (FC) | 59.1 | 78.7 | 50.6 | 55.0 | 87.3 |
| Qwen3-32B (FC) | 48.7 | 82.0 | 47.9 | 43.0 | 76.4 |
| xLAM-2-8b-fc-r (FC, tool-tuned) | 46.7 | 68.0 | **70.0** | 67.0 | 63.3 |
| Qwen3-8B (FC) | 42.6 | 80.5 | 41.8 | 34.5 | 79.1 |
| Qwen3-14B (FC) | 41.0 | 80.0 | 34.8 | 32.5 | 81.9 |
| Qwen3-30B-A3B-2507 (FC) | 41.4 | 77.9 | 30.0 | 41.0 | 79.9 |
| Mistral-Small-2506 (FC) | 37.2 | 77.3 | 11.5 | 12.0 | 87.9 |
| Qwen3-4B-Instruct-2507 (FC) | 35.7 | 76.4 | 22.1 | 25.5 | 84.9 |
| Gemma-3-12b-it (Prompt) | 30.4 | 74.2 | 5.8 | 4.0 | 70.3 |
| Llama-3.3-70B (FC) | 31.9 | 76.6 | 21.5 | 26.5 | 53.5 |
| Phi-4 (Prompt) | 28.8 | 60.7 | 3.9 | 3.0 | 87.6 |
| Qwen3-1.7B (FC) | 28.4 | 74.6 | 11.0 | 11.0 | 76.5 |
| Llama-3.1-8B (Prompt) | 25.8 | 70.8 | 11.1 | 13.0 | 42.7 |
| Gemma-3-4b-it (Prompt) | 19.6 | 60.8 | 0.4 | 0.5 | 53.9 |
| Llama-3.2-3B (FC) | 22.0 | 58.3 | 4.0 | 3.5 | 52.1 |

What the data says:
1. **Single calls are nearly solved at 4B–32B.** Live accuracy is 74–82% for Qwen3 4B–32B, Gemma 3 12B, and Mistral Small, which is close to frontier (≈80%).
2. **Multi-turn is the cliff.** Qwen3-4B drops to 22%, Mistral Small to 12%, Gemma 3 12B to 6%, and Phi-4 to 4%. Our agent is inherently multi-turn (edit → analyze → revise).
3. **Tool-specialized fine-tunes buck the trend.** xLAM-2-8b gets 70% multi-turn, but lower relevance/irrelevance. That points to *training*, not size, as the main factor, and supports eval-based tiering over parameter count.
4. **Format sensitivity is huge for small models in prompt mode** (max accuracy delta across prompt formats). Phi-4 81.5 pts, Gemma-3-12B 67.5, Gemma-3-4B 69.5, Llama-3.1-8B 74.5, Qwen3-4B 18, Qwen3-8B 16.5; frontier ≈ 8–13. **Small models are fragile to how tools are presented**, so our tool-description format must be tested per model family, not assumed.
5. **Native FC vs prompt mode isn't universally better either way.** Claude/GPT-4.1 in BFCL's prompt mode collapse on multi-turn (<10%), while Gemini-3-Pro is better in prompt mode. Per-model choice matters.

#### 2b. Other evidence

| Claim | Source |
|---|---|
| TinyLLM (BFCL, edge): Qwen3-4B (prompt) 62% overall but 35% multi-turn; Qwen3-1.7B 17% multi-turn; 0.6B 1.4% multi-turn; xLAM-2-3b 66%/56% multi-turn with hybrid optimization | [arXiv 2511.22138](https://arxiv.org/abs/2511.22138) **[verified: PDF text]** |
| LongFuncEval: accuracy drops 7–85% as the tool catalog grows to ~120K tokens. Answer retrieval drops 7–91% as tool responses grow from 10K to 80K. **13–40% degradation as multi-turn conversations lengthen.** Function-name hallucination appears at long context | [arXiv 2505.10570](https://arxiv.org/abs/2505.10570) **[secondary: search summary of paper]** |
| "LLMs Get Lost in Multi-Turn Conversation": −39% average across 15 LLMs when info is spread over turns; mostly **unreliability** (+112%), not aptitude. Concatenating everything into one message recovers ~95% | [arXiv 2505.06120](https://arxiv.org/abs/2505.06120) **[secondary]** |
| Tool-count: accuracy degrades as catalogs grow and with **semantically similar distractor tools** (e.g., `calc_area_triangle` vs `calculate_triangle_area`). Fewer, query-dependent candidates improve choice accuracy (93% at K≈2 vs 87% at K=5, Sonnet 4.6) | [arXiv 2605.24660](https://arxiv.org/abs/2605.24660) **[verified HTML]** (frontier model only) |
| Small DB-agent study (39 local open-weight models, 8,199 runs): **75.7% of losses were in tool-using runs. The largest class was "transport" (tools used, result never delivered, 36%). Pure capability failures were the smallest (17%).** Five *server-side* fixes (e.g., contradictory required fields between sibling tools) moved six models by 6–21 of 30 cells. An uncapped 262k context made a 7 GB model eat 51 GB of RAM and fail as "timeouts" | [arXiv 2609.21341](https://arxiv.org/abs/2609.21341) **[verified abstract]** |
| Repeating a failed call: on 135M–1.7B models, P(repeat failed call) rose 0.06 → 0.54 once the failure was in the transcript. Replacing the verbatim failed call with a runtime-written *description* of the failure removed 76% of repetition | cited in 2609.21341 (Gumaan) **[secondary: citation]** |
| Small LMs often pick the right tool but emit invalid args (missing required fields, type/enum errors). Schema validation before execution plus budgeted local repair isolates errors per call | [arXiv 2602.18968](https://arxiv.org/abs/2602.18968) **[secondary: snippet]** |
| MCP benchmarks: MCPMark / MCP-Bench / LiveMCPBench report only large models (Qwen3-32B lowest in LiveMCPBench at 30.5%). No useful sub-10B numbers found | [LiveMCPBench](https://arxiv.org/html/2508.01780v1), [mcpmark](https://github.com/eval-sys/mcpmark) **[secondary]** |
| GLM-4.7-Flash (30B-A3B): τ²-Bench 79.5 vs Qwen3-30B-A3B-Thinking 49.0 vs GPT-OSS-20B 47.7. Vendor says to enable "Preserved Thinking" for multi-turn agentic tasks (so reasoning must be round-tripped, see Q1) | [z.ai GLM-4.7 docs](https://docs.z.ai/guides/llm/glm-4.7) **[secondary: vendor-reported]** |

**Known failure modes (consolidated):**
- Wrong tool among similar names.
- Hallucinated function names at long context.
- Missing required args, or type/enum errors.
- Malformed JSON (Mistral 7B and Qwen3-VL produced ~0% valid structured calls in the NLT replication).
- Tool JSON emitted as plain text.
- Repeating a failed call (loops).
- Premature final answers in multi-turn.
- Over-calling or under-calling tools (systematic biases in small models, [arXiv 2605.00737](https://arxiv.org/html/2605.00737v1) **[secondary]**).
- Silent truncation producing "amnesia".

### Q3. Techniques to make small models reliable, with evidence

| Technique | Evidence | Verdict for GlyphWizard |
|---|---|---|
| **Fewer, flatter, distinct tools; subset per phase** | Catalog-size and distractor degradation (2605.24660, LongFuncEval). The animation-model synthesis already found task-level tools beat fine-grained ones (one benchmark). | **Do it.** T1 gets ~5–8 macro tools with flat params and dissimilar names. Phase subsets: *plan* (read/analyze), *build* (create/edit clips), *check* (analyze/look). |
| **Validate + lenient parse + JSON repair + described error feedback, capped retries** | 2602.18968; the Gumaan repetition result (describe the failure, don't echo the call); 2609.21341 (server-side contract bugs dominate); JSON-repair PRs in tau/mastra. The command layer already validates (D-synthesis: "lenient parsing plus validation beats forced strict JSON mode"). | **Do it, for all tiers.** Return `{ok:false, error:"param 'radius' must be 0.05–1.0, got 3", hint:"..."}`, not the raw call. Cap at ~2 repairs per call and ~N steps per turn. Log raw model args (2609.21341 shows the cost of not logging them). |
| **Constrained decoding (GBNF / JSON schema / strict tools)** | JSONSchemaBench: engines differ ~2× in schema coverage; constrained decoding improved downstream accuracy up to ~4% **[verified abstract]**. "Let Me Speak Freely?" (format restrictions hurt reasoning) vs dottxt's rebuttal (the confound was JSON-mode prompting, not structured generation): **unresolved**. "Constraint Tax": JSON-schema constraint + tools ⇒ open-weight models **stop calling tools** (grammar masks make tool tokens unreachable); fix is two-pass **[secondary: abstract]**. vLLM guarantees schema-valid args only for `tool_choice: required` / named function, and "auto" isn't guaranteed **[secondary: docs snippet]**. Open vLLM bug: GLM-4.7/5.x required/named tool calls lost when going from reasoning straight to a tool call **[secondary]**. | **Use selectively.** Don't combine `response_format` with tools. Use `tool_choice: required`/named only on steps where a call is mandatory (e.g., the repair step) and only where the server supports it. Treat grammar enforcement as an optional per-endpoint toggle, not a foundation. |
| **Native tool calls vs text-protocol tools (XML/YAML/"NLT")** | NLT (natural-language YES/NO tool selection): +18.4 pp original, +14.9 pp replication across 14 models. Structured errors 755 vs 51. Mistral 7B 0% → 39% **[verified HTML]**. **Selection only, single-turn, no arguments**, and Gemini 2.5 Pro / Kimi-K2 / GPT-OSS-120B were better with structured calls. Cline moved *from* XML *to* native for frontier models (fewer malformed responses, ~15% fewer tokens) but **kept XML for models without native support** **[secondary: Cline blog]**. BFCL format sensitivity: a 16–80 pt spread for small models. "Bitter Lesson of Tool Calling": code-style calls ≥ JSON for 11/14 *frontier* models; failures were escaping (`\n`) **[verified HTML]**. | **Native first, text fallback.** Implement one lenient text protocol (simple XML-ish tags, one call per message) as a per-endpoint fallback when the probe shows native FC broken. Skip code-execution tools for v1 (no sandbox; D-synthesis already rejected embedded JS). |
| **ReAct-style "think then act"** | Indirect: reasoning models need reasoning replayed (GLM-4.7 "Preserved Thinking"); Constraint Tax says to keep reasoning free text. No small-model-specific controlled evidence found. | Allow free-text thinking before calls. Don't force JSON-only replies. |
| **Planner–executor split** | IFFC: moving function-calling into a dedicated small instruction-following model beat native and prompt FC, robust under Q4 quantization **[secondary: abstract + partial PDF]**. | **Defer.** It adds a second model config for users. Revisit if T1 multi-turn evals are bad. |
| **Macro tools** (one call = whole effect) | Follows from the multi-turn cliff: fewer turns means less exposure. Asset Forge / Pixel Art Bench show small models can't emit raw grids (perception report). | **Core T1 strategy.** e.g. `create_effect(kind="ring_pulse", center, period_ms, …)` creates the clip, sets keyframes, and returns preview + metrics in one call. |
| **Short loops, document-as-memory** | Laban et al. (concatenating context recovers 95%); LongFuncEval multi-turn degradation. | For T1, rebuild context each turn from {system, compact doc summary, last user message, last 1–2 tool results}, not the full transcript. **[speculative design, evidence-motivated]** |

### Q4. Capability tiers and detection

**Proposed tiers** (refines the perception report's T0/T1/T2 for the boxed v1 scope; vision becomes orthogonal):

| Tier | Gate (all must pass) | Agent configuration | In v1? |
|---|---|---|---|
| **Below bar** (old T0, and T1 for now) | Fails the multi-step probe, or effective context < 32k | Agent disabled. Explain why and link to setup docs. User override "try anyway" with a warning. | yes (as "unsupported") |
| **Supported** (old T2) | Passes the multi-step + error-recovery probe, context ≥ 32k, native tool calls | Full toolset, parallel calls if the endpoint allows, step cap ~15–20, critique loop per the perception report | **yes** |
| **+V (vision flag)** | Metadata says image input, *and* the image probe (count dots in a 3-dot render) passes | Enables `look`/render tools (perception report tier 2) | yes |
| *T1 Guided (future)* | Passes single-call probes only, or context 8k–32k | 5–8 macro tools, phase subsets, no parallel calls, ≤ ~4k-token fixed prompt, step cap ~6, document-as-memory context rebuild, text-protocol fallback | no. See §3c for the cost |

**Assignment pipeline [speculative design; data sources verified]:**
1. **Curated registry**, bundled JSON, updated with app releases:
   - Seed it from **models.dev** (MIT; per-model `tool_call`, `attachment`, `reasoning`, `modalities.input`, `limit.context/output`, `cost`, `open_weights`, `interleaved`), which **[verified]** already lists OpenCode Go, z.ai, MiniMax/Kimi coding plans, OpenRouter, etc. with per-provider base URL and SDK shape. Example: OpenCode Go → `@ai-sdk/openai-compatible`, `https://opencode.ai/zen/go/v1`.
   - Add our own fields: `tier`, `preferredToolFormat`, `quirks` (e.g. `noStreamTools`, `replayReasoning`, `rejectsReasoningEffort`).
2. **Live metadata** when available:
   - OpenRouter `/api/v1/models` gives `supported_parameters` (contains `tools`, `structured_outputs`, `reasoning`), `architecture.input_modalities`, and `context_length`. 390 of 458 models listed `tools` today **[verified this session]**.
   - Ollama `/api/show` returns `capabilities` (e.g. `completion`, `vision`, and tools) and `*.context_length` **[verified in api.md example]**.
   - Generic endpoints: `/v1/models` (ids only).
3. **Probe** (user taps "Test model", ~3–6 requests, ~10 s, pennies). **[speculative design]**
   - (a) Single required call with nested-free args.
   - (b) Abstention (no tool needed).
   - (c) 2-step dependent calls (call → result → second call using the result).
   - (d) Malformed-arg recovery after a validator error.
   - (e) Optional image count.
   - Record native-FC vs text-protocol pass/fail and streaming quirks.
4. **User override** (with a warning) both ways.
5. **Eval harness** (perception report §4) produces per-model tier evidence, which feeds back into the registry. This is the only *real* evidence. Vendor claims and BFCL are priors.

### Q5. Context constraints (8k–32k)

*Under the boxed v1 scope this mainly matters for the post-v1 T1 path. v1 requires ≥ 32k effective context. The silent-truncation pitfall at the end of this section applies to v1 too, because self-hosted servers often default far below the model's nominal context.*

- **Budget [speculative, sized from typical schemas]:** at 8k, roughly 2.5k for system + tool schemas + core glossary, ~1.5k for the document summary, ~1k for the frame view/analysis result, ~1k for user + last tool results, and ~2k left for output/reasoning. That works only with T1's small toolset.
- **Knowledge base:**
  - A tiny always-loaded core: coordinate convention, the glossary top-20, and tool usage rules.
  - Everything else becomes retrievable "skills" via `read_guide(topic)`, ≤ ~800 tokens each.
  - Retrieval can be **keyword/topic-indexed, no embeddings needed**. The KB is small and authored by us. **[speculative]**
- **Document representation:**
  - Timeline summary: clip ids, types, generator name@version + params, time spans.
  - Frames only on demand.
  - The perception report's quantized text grid for 13×13 is ~200 tokens/frame. For 25×25 it's ~650, so never dump multiple frames for T1.
- **Compaction:**
  - Because the *document* is the source of truth and every turn is one undo group, the agent doesn't need old chat turns to know the state.
  - T1 drops all but the last exchange, plus a 2–3 line rolling "intent notes" field.
  - T2 can use summarization-based compaction.
  - This also sidesteps the multi-turn degradation in Q2.
- **Self-hosted pitfall:** Ollama's silent truncation (Q1). Also the opposite: an uncapped 262k default context made a 7 GB model use 51 GB of RAM (2609.21341). Read and display the effective context; don't trust model-card limits.

### Q6. Mobile specifics

| Topic | Finding | Recommendation |
|---|---|---|
| HTTP/SSE client | Ktor client (multiplatform, SSE plugin) or OkHttp + `okhttp-sse` **[verify versions]**. Official `anthropic-java` is a JVM SDK on OkHttp with no documented Android target. A spike issue lists desugaring / R8 / Jackson-reflection risks **[secondary]**. `anthropic-java-core` doesn't depend on OkHttp **[secondary: docs snippet]**. aallam `openai-kotlin` last released 4.1.0 (Feb 2026), with an unanswered backlog **[secondary]**. LangChain4j: active, no Android focus, and a Gemini streaming tool-call NPE **[secondary]**. | **Hand-write the adapters** (~a few hundred lines each) in a plain Kotlin/JVM `:agent` module (D14: no Android deps, so the JVM CLI eval harness runs the same code). The quirk layer *is* the product here, and SDKs hide it. See the client choice below. |
| **Client choice for an Android-free `:agent` module** | **OkHttp** is a plain JVM library that runs unchanged on Android and desktop JVM, with `MockWebServer` for tests. **Ktor client** (`ktor-client-core`) is pure Kotlin with suspend APIs, pluggable engines (OkHttp engine on JVM *and* Android; Ktor docs list OkHttp 5.0+ as an engine, Android 8+), a built-in SSE plugin (only `ktor-client-core` needed), and `MockEngine` for tests **[verified: ktor.io docs]**. Ollama's *native* API streams **NDJSON, not SSE** **[verify]**, and gateways vary in SSE edge cases (comments, `[DONE]`, multi-line `data:`). | **Recommendation:** Ktor client core + **OkHttp engine in both the app and the JVM CLI** (the same engine means the eval harness sees identical transport behaviour). Use `preparePost(...).execute { bodyAsChannel() }` with **our own ~50-line SSE/NDJSON line parser**, not the SSE plugin, because LLM streams are POST and we want byte-level control and replayable test fixtures. Whether the SSE plugin supports POST bodies cleanly is **[verify]**. Record raw streams from each provider as golden fixtures and replay them through `MockEngine` in unit tests. Plain OkHttp + coroutines is an equally valid, slightly leaner choice. Don't use both. |
| Agent frameworks | **Koog 1.0** (JetBrains, May 2026): KMP incl. Android, providers OpenAI/Anthropic/Google/OpenRouter/Ollama, MCP, local LiteRT on Android, decoupled HTTP transport, 1-year stability for stable modules **[verified blog + secondary AGENTS.md]**. Its custom-base-URL / quirk handling wasn't verified. | Worth a **half-day spike**: does it pass our probe on Ollama + OpenCode Go + OpenRouter, and can we inject the accumulator/replay behavior? If not, don't adopt. Flagged as possible overengineering for a solo project. |
| Key storage | `androidx.security:security-crypto`: "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore" (1.1.0-alpha07, Apr 2025) **[verified]**. | A Keystore-generated AES-256-GCM key (non-exportable). Encrypt the key string and store the ciphertext + IV in DataStore (or use Tink with a Keystore master key). Exclude from cloud backup / device transfer (`dataExtractionRules`). Never log keys. Redact them in the debug view. |
| Cleartext to LAN | Cleartext is disabled by default since API 28. Opt-in is via `base-config` or per-`domain-config`. Examples use hostnames/IPs; no IP-range syntax **[verified docs page summary]**. From API 37 an implicit localhost cleartext rule exists (loopback only). | User-entered arbitrary LAN IPs realistically need `base-config cleartextTrafficPermitted="true"` **[verify: no CIDR support]**. Mitigate with in-app checks: allow `http://` only for RFC1918/.local/loopback hosts and warn. Alternatively recommend HTTPS via a reverse proxy or Tailscale **[speculative]**. |
| Local network permission | Android 17 (targeting API 37): local network access needs the **`ACCESS_LOCAL_NETWORK`** runtime permission (Nearby devices group). Android 16 used `NEARBY_WIFI_DEVICES` as a temporary gate. Apps targeting ≤36 keep implicit access **[secondary, docs pages in search]**. | Request it only when the user configures a LAN endpoint. |
| Background execution during a turn | Cached processes are **frozen 10 s** after entering the cached state (Android 14+) **[verified AOSP]**. FGS needs a type. `shortService` ≈ 3 min. `dataSync` is capped at 6 h/24 h on Android 15+ **[verified]**. UIDT jobs suit long user-initiated transfers (API 34+, notification) **[verified docs summary]**. | Turns should be short (step caps). If the user leaves mid-turn: either promote to a `shortService` FGS until the turn finishes, or cancel and roll back the turn's undo group (cheap, since one turn = one undo group). UIDT is overkill. **[speculative]** |
| Cost / token counting | Every API returns `usage` (OpenAI `prompt_tokens/completion_tokens`, Anthropic `input_tokens/output_tokens` + cache fields). Anthropic has `count_tokens` **[verified]**. Pricing per model is in models.dev `cost` and OpenRouter `pricing` **[verified this session]**. OpenCode Go uses per-model dollar allowances. | Show tokens + estimated cost per turn from `usage` × registry price. Pre-flight estimate with chars/4 (no local tokenizers). Hard per-turn caps (steps, output tokens). Local = free. |

### Q7. On-device model on the Nothing Phone (4a) Pro

| Option | Evidence | Verdict |
|---|---|---|
| **Gemini Nano / AICore (ML Kit Prompt API)** | Device list (nano-v2/v3/v4) includes Pixel, Samsung, OnePlus, Xiaomi, etc. **No Nothing phones.** No tool/function calling mentioned. Per-app inference and battery quotas (`BUSY`, `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`) **[verified: ML Kit GenAI page]**. | **Not available** on the target device. Not agent-capable anyway. |
| **llama.cpp on device** | Device: Snapdragon 7 Gen 4 (SM7750-AB, 4 nm), 8/12 GB LPDDR5X **[secondary: GSMArena]**. Same SoC, Qwen3.5-4B Q4_0: prefill ≈52 tok/s CPU / 74 GPU (172 with an experimental NPU patch), decode ≈10 tok/s CPU **[secondary: single GitHub benchmark]**. 4B-class models: ~22% BFCL multi-turn. | A 4k-token prompt ≈ 80 s prefill on CPU before any output, per agent step. **Not viable for v1.** |
| **LiteRT-LM (+ Koog local provider)** | Has function calling with constrained decoding. Gemma 4 E2B: 52 tok/s decode on a flagship GPU. Gemma 3n E4B: ~9 tok/s decode on S24 Ultra **[secondary: Google blog/model cards]**. No mid-range tool-calling benchmark found. | **Watch.** Possibly a T1 "offline" experiment post-v1, gated on our own probe + eval on the device. |

---

## 3. Proposed provider abstraction and tier system

### 3a. Abstraction (pure-Kotlin core module) **[speculative design]**

```
EndpointConfig { id, label, wire: ChatCompletions | AnthropicMessages | OpenAIResponses,
                 baseUrl, apiKeyRef, modelId, extraHeaders, quirks: Set<Quirk>, tierOverride? }

Quirk = NoStreamingWithTools | ReplayReasoning | RejectsReasoningEffort | ToolCallsMayLackIndex
      | TextProtocolTools | NoParallelToolCalls | NeedsSessionHeader(name) | MaxContext(n) ...

Internal model:
  Message(role, parts: [Text | Image(png bytes) | ToolCall(id,name,argsRaw:String) | ToolResult(id, content, isError)],
          providerExtras: JsonElement?)        // opaque reasoning blobs, replayed verbatim
  TurnResult(messages, usage{in,out,cacheRead?,costUsd?}, stopReason, rawEvents for debug)

interface ChatAdapter { suspend fun stream(req: ChatRequest): Flow<ChatEvent> }   // 3 implementations
ToolCallAccumulator   // shared: (index,id) keying, whole-call chunks, final-frame calls, finish_reason-agnostic
ToolArgParser         // strict JSON → lenient repair → text-protocol fallback; returns typed error for feedback
CapabilityResolver    // registry ⊕ live metadata ⊕ probe results ⊕ user override → Tier + visionFlag + quirks
```

- Gemini is handled via ChatCompletions + `ReplayReasoning` (thought_signature in `extra_content`).
- Presets: Anthropic, OpenAI, Gemini, OpenRouter, OpenCode Go, Synthetic, Chutes, Ollama (LAN), llama.cpp (LAN), LM Studio (LAN), vLLM, "Custom OpenAI-compatible". Each preset carries known quirks and its ToS note.

### 3b. Tier system

Summarized in Q4. Key principles:
- (1) Support comes from **measured behavior**: registry priors, overridden by probe/eval.
- (2) Vision is a flag, not a tier.
- (3) v1 ships "Supported / Below bar". A future T1 isn't a degraded full agent. It's a **different agent loop**, designed around the multi-turn cliff.
- (4) The command layer and validators are tier-independent. Only tool *exposure* and loop policy would differ.

### 3c. What it would cost to go lower (small / 8k–32k models)

| Step down | What it needs | Cost estimate (solo dev) **[speculative]** | Evidence that it helps |
|---|---|---|---|
| **Free now (do in v1 anyway)** | Described validator errors, JSON repair, task-level tools, document-as-memory, a probe that *measures* instead of assuming | ~0 extra: already implied by the command-layer design | 2602.18968, Gumaan repetition result, 2609.21341 |
| **Self-hosted ~30B at ≥ 32k** (Qwen3-32B, GLM-4.7-Flash, Qwen3-30B-A3B) | Nothing beyond v1, plus setup docs (llama.cpp `--jinja`, Ollama `OLLAMA_CONTEXT_LENGTH`), the defensive stream parser, and a non-streaming fallback | Low: docs + quirk flags | BFCL: Qwen3-32B 48% multi-turn (vs Opus 68%). Borderline, and the probe decides |
| **T1 Guided for 4B–14B models / 8k–32k context** | A second tool exposure (macro tools + phase subsets), a compact prompt variant, a context rebuild per step, lower step caps, its own eval set | Medium: a parallel prompt/tool surface to maintain and evaluate, roughly +20–30% agent-layer work and an ongoing eval tax | Multi-turn cliff (BFCL), catalog-size effects (2605.24660), Laban et al. The *magnitude* of recovery for our domain is **unknown**. |
| **Text-protocol tool fallback** (for servers or models with broken native FC) | A parser for an XML-ish tag format, prompt templates, and per-family format testing (BFCL format sensitivity: 16–80 pt swings for small models) | Medium | NLT +15–18 pp on *selection only*. Cline keeps XML for non-native models |
| **Grammar / constrained decoding per endpoint** | Per-server toggles (vLLM `tool_choice: required`, llama.cpp grammar), and avoiding the tools + `response_format` combination | Medium-high for small gain, with known bugs (Tool Suppression, vLLM GLM bug) | JSONSchemaBench up to ~4%. Mixed evidence |
| **On-device model** | LiteRT-LM / llama.cpp integration, model download UX, thermal/battery handling | High. Not viable on the 4a Pro today (§Q7) | — |

**Recommendation:** ship v1 with the "free now" row and the ~30B row (docs + quirks). Revisit T1 only if users ask, and only with eval-harness data from the actual small models they run.

---

## 4. Risks

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| ToS violation via coding-plan gateways (z.ai ban, OpenCode Go abuse flags) hurting users | Medium / high for affected users | Key-only, distinct User-Agent, session header, explicit ToS notes. Don't market z.ai Coding Plan support. Ask OpenCode/Moonshot. |
| Wire-format drift (new reasoning fields, Responses-only models) | High / medium | Registry quirks, raw-event debug view, the probe, three adapters isolated behind one interface. |
| Users with self-hosted small models feel excluded, given D13's "open-weight first-class" | Medium / low-medium | Open-weight ≠ small: every major open-weight family is supported via gateways and ~30B self-hosting. Below-bar override plus a clear message. T1 path costed (§3c). **Note: this narrows D13's "small models (e.g. GLM flash-class)" wording, so decisions.md should be updated.** |
| Overengineering (registry + probe + 3 adapters + text protocol + tiers) for a solo first Android app | Medium / medium | Staging: v1 = ChatCompletions adapter (covers all gateways incl. ClinePass/OpenCode Go/OpenRouter/self-hosted) + Anthropic adapter, registry seeded from models.dev, probe (a)–(d), Supported/Below-bar. Add Responses, text protocol, T1, and Koog/LiteRT only when needed. |
| Key leakage (logs, backups, debug export) | Low / high | Keystore wrap, backup exclusion, redaction, never include keys in `.glyphwiz` exports. |
| Cleartext permission broadening network exposure | Low / low-medium | Restrict `http://` to private ranges in app logic. Warn. |
| Benchmark evidence is stale (BFCL CSV from Apr 2026, no GLM-5/Qwen3.5+/Gemma 4) | Certain / medium | Treat it as directional. Our own eval decides tiers. |

---

## 5. Open questions for the user

1. **Gateways:** ClinePass explicitly allows app use. Since OpenCode Go says "designed for OpenCode and other coding agents", are you OK with us asking OpenCode whether a non-coding app may use Go keys (or with shipping a ToS warning)? Is the z.ai GLM Coding Plan in scope? Its policy says no.
2. **Confirm the v1 bar:** multi-step probe pass + ≥ 32k context + native tool calls. "GLM flash-class" (GLM-5.3-Flash, ~320B-A18B hosted) clears it. Do you also want self-hosted ~30B (Qwen3-32B / GLM-4.7-Flash) documented as supported-if-probe-passes? Should D13's wording be updated to "open-weight via gateways/self-hosted ≥ bar"?
3. Is **OpenAI frontier** (Responses API) a v1 need, or can the third adapter wait?
4. Do you run Ollama/llama.cpp on **LAN over plain HTTP**? That decides the cleartext/local-network permission work for v1.
5. Accept **"cancel and roll back the turn if the app is backgrounded"** for v1, instead of a foreground service?
6. Should the probe run automatically on endpoint save (it costs a few cents on paid APIs) or only on tap?
7. OK to bundle a **models.dev-derived registry** (MIT) and refresh it per release (no runtime fetch, which keeps D1 "fully local")? Or allow an optional runtime refresh?

---

## 6. Sources

Primary / near-primary (checked this session):
- OpenCode Go docs: https://opencode.ai/docs/go/
- ClinePass docs: https://docs.cline.bot/getting-started/clinepass ; models list issue: https://github.com/can1357/oh-my-pi/issues/3833 ; comparison: https://1devtool.com/compare/clinepass-vs-opencode-go
- z.ai Coding Plan usage policy: https://docs.z.ai/devpack/usage-policy ; supported tools: https://docs.z.ai/devpack/tool/others
- OpenAI migrate to Responses: https://developers.openai.com/api/docs/guides/migrate-to-responses
- Gemini OpenAI compatibility: https://ai.google.dev/gemini-api/docs/openai
- OpenRouter parameters: https://openrouter.ai/docs/api/reference/parameters ; models API: https://openrouter.ai/api/v1/models ; reasoning tokens: https://openrouter.ai/docs/guides/best-practices/reasoning-tokens
- models.dev: https://github.com/sst/models.dev ; https://models.dev/api.json
- BFCL leaderboard data: https://gorilla.cs.berkeley.edu/leaderboard.html (CSV `data_overall.csv`, Last-Modified 2026-04-13)
- llama.cpp function calling: https://github.com/ggml-org/llama.cpp/blob/master/docs/function-calling.md
- Ollama API (`/api/show` capabilities): https://github.com/ollama/ollama/blob/main/docs/api.md
- OpenAI structured outputs (supported schemas): https://developers.openai.com/api/docs/guides/structured-outputs
- Ktor client SSE / engines: https://ktor.io/docs/client-server-sent-events.html ; https://ktor.io/docs/client-engines.html
- Claude structured outputs / token counting: https://platform.claude.com/docs/en/build-with-claude/structured-outputs ; https://platform.claude.com/docs/en/build-with-claude/token-counting
- AndroidX Security release notes: https://developer.android.com/jetpack/androidx/releases/security
- Network security config: https://developer.android.com/privacy-and-security/security-config
- Local network permission: https://developer.android.com/privacy-and-security/local-network-permission ; Android 17 behavior changes: https://developer.android.com/about/versions/17/behavior-changes-17
- FGS timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout ; UIDT: https://developer.android.com/develop/background-work/background-tasks/uidt ; cached apps freezer: https://source.android.com/docs/core/perf/cached-apps-freezer
- ML Kit GenAI device list: https://developers.google.com/ml-kit/genai
- Koog 1.0: https://blog.jetbrains.com/ai/2026/05/koog-1-0-is-out-stable-core-better-interop-and-multiplatform-observability/

Papers:
- TinyLLM (BFCL on edge SLMs): https://arxiv.org/abs/2511.22138
- How Many Tools Should an LLM Agent See?: https://arxiv.org/abs/2605.24660
- The Bitter Lesson of Tool Calling: https://arxiv.org/abs/2608.06370
- NLT replication: https://arxiv.org/abs/2607.03953 ; original NLT: https://arxiv.org/abs/2510.14453
- What Stops a Small Language Model From Driving a Database Agent: https://arxiv.org/abs/2609.21341
- Constraint Tax / Tool Suppression: https://arxiv.org/abs/2606.25605
- Small Reasoning Models are Instruction Followers in Function Calling (IFFC): https://arxiv.org/abs/2608.22472
- LongFuncEval: https://arxiv.org/abs/2505.10570
- LLMs Get Lost in Multi-Turn Conversation: https://arxiv.org/abs/2505.06120
- JSONSchemaBench: https://arxiv.org/abs/2501.10868
- Let Me Speak Freely?: https://arxiv.org/abs/2408.02442 ; rebuttal: https://blog.dottxt.ai/say-what-you-mean.html
- Layered execution with reflective correction: https://arxiv.org/abs/2602.18968
- To Call or Not to Call: https://arxiv.org/html/2605.00737v1
- LiveMCPBench: https://arxiv.org/html/2508.01780v1 ; MCPMark: https://github.com/eval-sys/mcpmark

Secondary (issues, blogs, aggregators):
- Anthropic OAuth ban: https://www.theregister.com/software/2026/02/20/anthropic-clarifies-ban-on-third-party-tool-access-to-claude/5014546
- pi z.ai issue: https://github.com/earendil-works/pi/issues/4187 ; Gemini thought signature: https://github.com/earendil-works/pi/issues/10157 , https://github.com/earendil-works/pi/issues/9444
- Ollama streaming tool calls: https://github.com/ollama/ollama/issues/7881 , https://github.com/StepenkoAnatoli/MoonAliza/pull/8 , https://github.com/QuantumNous/new-api/issues/7252 , https://github.com/anomalyco/opencode/issues/20995 , https://github.com/BerriAI/litellm/issues/35711 , https://github.com/openclaw/openclaw/issues/5769
- Gemini compat streaming/finish_reason: https://github.com/hoornet/home-mind/pull/44
- OpenRouter reasoning replay: https://github.com/home-assistant/core/issues/159812 , https://github.com/DavidDworetzky/Geist/pull/381
- vLLM tool calling: https://docs.vllm.ai/en/latest/features/tool_calling/ ; GLM required-call bug: https://github.com/vllm-project/vllm/issues/59608
- Cline native tool calling: https://cline.bot/blog/cline-v3-35
- Kimi Code docs: https://www.kimi.com/code/docs/en/ ; MiniMax other tools: https://platform.minimax.io/docs/token-plan/other-tools ; Synthetic API: https://dev.synthetic.new/docs/api/overview ; Chutes: https://chutes.ai/docs/guides/starter-guide
- GLM-4.7 docs: https://docs.z.ai/guides/llm/glm-4.7 ; GLM-5.3-Flash specs: https://codersera.com/blog/glm-5-3-flash-complete-guide-2026/
- Ollama num_ctx truncation: https://particula.tech/blog/ollama-num-ctx-silent-prompt-truncation
- anthropic-java on Android spike: https://github.com/voitta-ai/virgil/issues/4 ; Claude Java SDK: https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/java
- openai-kotlin: https://libraries.io/maven/com.aallam.openai:openai-client ; LangChain4j issues: https://github.com/langchain4j/langchain4j/issues
- Nothing Phone (4a) Pro specs: https://www.gsmarena.com/nothing_phone_(4a)_pro_5g-14529.php
- SD 7 Gen 4 llama.cpp numbers: https://github.com/karusrus/npu-enablement
- LiteRT-LM: https://developers.google.com/edge/litert-lm/overview ; Gemma 3n LiteRT-LM card: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
