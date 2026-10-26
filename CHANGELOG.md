# Changelog

All notable changes to ESI Agent are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- (nothing yet)

## [0.1.0] — 2026-10-21

First public release.

### Added

- OpenAI-compatible providers: create/edit/duplicate connections with base URL, optional api
  version + custom headers, capability flags, generation-parameter defaults, and a stepwise
  connection tester (reach → auth → models → chat) with model discovery.
- Streaming chat (`/chat/completions` SSE) with delta / reasoning / usage events, content
  persisted to Room while streaming (Room as the single source of truth), stop, retry, regenerate
  and edit-and-resend; orphan sweep after process death.
- Conversations: pin, archive, search, rename, per-conversation provider/model/system-prompt/
  temperature/topP/maxTokens overrides, frozen model labels for history accuracy.
- Assistants: built-in starter personas plus a full editor (emoji, prompt, model, params).
- Attachments: images (gallery/camera), text files and PDFs gated by provider capability flags,
  with graceful degradation for unsupported models.
- Markdown rendering in Compose (headings, emphasis, links, inline/fenced code with syntax
  highlighting, block quotes, GFM tables) and message actions: copy, share, edit, delete.
- Dual-language UI: English and Persian (فارسی) with complete RTL and in-app language switching
  without an activity restart.
- Security: Android Keystore–wrapped AES-GCM key storage; keys never stored in Room or logs;
  masked display in the UI; SECURITY.md documents the BYOK threat model.
- Data portability: schema-versioned JSON backup (explicit opt-in for including keys) with merge
  import counts, plus Markdown export/share of conversations.
- Tests: a JVM unit suite (MockWebServer provider tests, backup/exports, ids/token estimator,
  error-kind mapping) and an instrumented Room DAO suite; GitHub Actions CI workflow.

### Added — agent mode (2026-10-26)

- **Agent mode** (Settings → *Agent mode*, on by default): the model can call 12 on-device
  tools — `set_theme`, `set_dynamic_colors`, `set_language`, `search_conversations`,
  `read_conversation`, `create_conversation`, `rename_conversation`, `delete_conversation`,
  `send_message`, `list_assistants`, `list_providers`, `export_backup` — and ESI Agent runs them
  locally for up to 6 turns per request.
- Every write or destructive call pauses for an explicit **Allow this action?** dialog that
  shows the tool name and its arguments; *Deny* feeds a polite decline back to the model so the
  conversation continues. Results render as a tool row (✓ with a result preview, ✗ when the
  user declined) beneath the assistant's call chips.
- Providers without tool support degrade to an ordinary reply instead of failing the message.
- Room schema **v2** — message rows carry `toolCalls`, `toolCallId` and `toolName` for tool
  results — with an automatic v1 → v2 migration; backups include tool rows.
- Debug builds permit cleartext HTTP so on-device local endpoints (Ollama, LM Studio, a mock
  server on the host machine) work; release builds keep cleartext blocked by default.
- Verified end-to-end on an Android 16 emulator against a mock OpenAI endpoint: tool call →
  confirmation dialog → execution → tool row → final reply, in both the approve and the
  decline path, plus instrumented v1 → v2 database migration tests.

### Fixed — post-release hotfix (2026-10-22)

- **App name**: the launcher label and the in-app brand are now **ESI Agent** (English + Persian).
- **Startup crash**: `ProviderRepositoryImpl` asked Koin for the concrete `SettingsStore` type, which
  had no definition (`NoDefinitionFoundException`) — the app died on the first screen. It now depends
  on the `SettingsRepository` interface.
- **Dead settings actions**: `ActionRow` defaulted to `clickable = false`, so *Export data*,
  *Import data* and *Clear all data* could never be tapped.
- **Activity-result crash**: `rememberLauncherForActivityResult` lost its registry owner because the
  locale wrapper replaces `LocalContext`; the activity now provides the registry and back-press
  owners explicitly at the composition root.
- **Dialog language**: dialogs live in their own windows and used the *system* language; the
  activity now serves locale-aware resources, so every window follows the in-app language.
- Backup import keeps attachments (`@Upsert` instead of `REPLACE`), a failed regenerate no longer
  deletes the previous reply, and Keystore writes are atomic (temp file + rename).
- **Chat list was upside down** — the conversation is rendered bottom-up, so the newest message now
  sits at the bottom and the oldest at the top.
- **Conversation history stays reachable** when no provider is configured; the "add a provider"
  hint only appears when there is nothing else to show.
- Polish: timestamp and message actions share one compact row, the jump-to-latest button is
  anchored to the trailing edge instead of floating in the middle, the "clear conversation" icon
  is a proper sweep icon, and assistant bubbles get a hairline border.
- **Visual pass** — the interface is now set in Vazirmatn (bundled, SIL OFL) with a type scale
  tuned for Persian, an "ink & signal" palette (indigo for your own voice, teal for the
  assistant, copper for warnings), paper-like assistant cards with a model chip, a real card
  layout for the conversation list, tinted icon tiles in the settings, a softer empty state and a
  rounder composer. Message actions now use 48dp touch targets, and the light and dark schemes
  fill the full surface-container ladder.
- CI is green again: the Gradle wrapper is committed as executable (the Linux runner could not run
  it before) and the workflow uses current action versions.
- New instrumented test walks the whole Koin graph, so a missing dependency definition fails the
  test run instead of crashing the app on startup.

