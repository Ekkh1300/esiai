# Research: similar open-source AI chat clients

Before writing Sokhan, five widely-used open-source clients were studied to guide product and
architectural decisions. Sokhan borrows **ideas**, never code.

| Project | Stack | What it is | What Sokhan took away |
| --- | --- | --- | --- |
| **[RikkaHub](https://github.com/rikkahub/rikkahub)** | Kotlin, Jetpack Compose, Android native | BYOK Android chat client with multi-provider support, streaming and a rich message UI. | The core pattern: **persist-while-streaming** — keep message rows in the local DB while tokens arrive so state survives navigation/process death; DB-first thinking for chat UI. |
| **[Chatbox](https://github.com/Bin-Huang/chatbox)** | TypeScript/Electron (+ mobile) | Popular cross-platform BYOK chat with "My Providers" onboarding (key, endpoint, model) and conversation export. | The frictionless **connection onboarding** flow (base URL + key → test → pick model) and prompt libraries as first-class editable data. |
| **[Cherry Studio](https://github.com/CherryHQ/cherry-studio)** | TypeScript/Electron | Desktop client for dozens of providers with strong attachment/file support and assistants. | **Provider presets** + per-provider capability flags, and graceful degradation when a model lacks a capability. |
| **[LobeChat](https://github.com/lobehub/lobe-chat)** | TypeScript/Next.js | Web assistant platform: personas, plugin ecosystem, polished multi-modal UI. | The **assistant/persona model** (emoji, system prompt, bound model + generation params) and the polish bar for share/export. |
| **[Open WebUI](https://github.com/open-webui/open-webui)** | SvelteKit (self-hosted server) | Self-hosted web UI fronting Ollama/OpenAI-compatible endpoints. | Markdown-first message rendering with **GFM tables**, and the flat, human-readable Markdown transcript export format. |

## Decisions shaped by the survey

- **Native Kotlin + Compose, single APK, no server** — none of the studied clients are fully
  Android-native *and* serverless; that combination is Sokhan's niche (RikkaHub shares the stack).
- **BYOK with per-conversation overrides** — embraced by all five; Sokhan additionally lets a
  conversation pin a provider/model snapshot (with a frozen display label) so history stays
  truthful even after a provider is later renamed or deleted.
- **Visible connection diagnostics** — Chatbox/Cherry Studio's "test connection" inspired
  Sokhan's stepwise probe (reach → auth → models → chat) with per-step pass/fail details.
- **No WebView for content** — some studied clients delegate rich text to web viewports; Sokhan
  renders Markdown natively for theme/RTL consistency.
- **Export as a contract** — Open WebUI/LobeChat's export ergonomics set the bar: Sokhan ships a
  JSON full backup (with explicit key inclusion) plus per-conversation Markdown export.

## Things deliberately rejected

- Telemetry, accounts, cloud sync — contradicts the BYOK privacy promise.
- Plugin/agent marketplaces — out of scope for v1; local assistants are the primitive.
- Embedding/RAG pipelines — deferred until local vector storage has a clear on-device win.
