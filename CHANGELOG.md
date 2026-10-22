# Changelog

All notable changes to Sokhan are documented here. Format based on
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

### Fixed — post-release hotfix (2026-10-22)

- **App name**: the launcher label and the in-app brand are now **ESIAI** (English + Persian).
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
