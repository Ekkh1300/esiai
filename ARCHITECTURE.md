# Architecture

ESI Agent is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose. The code is
organised in packages with a strict dependency direction:

```
ui/  →  di/  →  domain/  ←  data/, provider/, security/, core/
              (interfaces)      (implementations)
```

`ui` never touches Room, OkHttp or the Keystore directly; it observes domain-level repository
interfaces injected through Koin. Everything is constructor-injected, so every non-Android class
is unit-testable without a DI framework.

## Layers

| Package | Responsibility |
| --- | --- |
| `core/` | Zero-dependency helpers: id generation (`Ids`, SecureRandom base-36), char-class-aware token estimation (`TokenEstimator`), ring-buffer logging (`AppLog`). |
| `security/` | `SecureStore` contract + `KeystoreSecureStore`: Android Keystore–wrapped AES-GCM envelope per alias; `InMemorySecureStore` for tests. |
| `domain/` | Pure models (`Conversation`, `Message`, `ProviderConfig`, `ChatError`), repository **interfaces**, `ChatEngine` contract and orchestration services. No Android framework imports except primitives. |
| `data/` | Room database + DAOs + entities, repository implementations, DataStore-backed settings, export/backup (JSON codec, service, Markdown formatter). |
| `provider/` | Provider-agnostic contracts (`AiProviderClient`, `ChatPayload`, `ChatStreamEvent`, `ProviderContext`) and the OpenAI-compatible implementation (REST + SSE over raw OkHttp). |
| `di/` | Koin wiring. UI resolves ViewModels via `koinViewModel()` with `parametersOf(conversationId)`. |
| `ui/` | Compose screens, shared components, theming, localized wrapping, navigation graph. |

## Data flow: streaming is persistence, not UI state

The defining design decision — inspired by RikkaHub — is that **Room is the single source of
truth while a stream is running**:

1. `ChatViewModel.send()` calls the engine and returns immediately.
2. `StreamingChatEngine` creates an empty ASSISTANT row with `status = STREAMING` and flips a
   `StreamState` flag.
3. Provider deltas (`ChatStreamEvent.Delta` / `ReasoningDelta` / `Usage` / `Finished`) are folded
   into an in-memory buffer and flushed to Room at a ~250 ms throttle (`updateContent`), so the
   `messages` Flow re-emits and every collector (current chat screen, chat list previews,
   background observers) re-renders from the database — never from transient UI state.
4. On `Finished`, the row is finalized once (status, usage tokens, finish reason). On errors the
   row is finalized with a stable `errorKind` string from `ChatErrorKind.of(error)`.
5. Leaving the screen doesn't cancel anything: the engine owns the stream in its own scope; back
   navigation only leaves the UI layer. Screen rotation/process death recovers via
   `recoverOrphans()` / `sweepStreamingMessages()` at startup, which finalizes half-written rows.
6. Stop cancels the engine job; the row keeps the partial content and gets a terminal status.

Regenerate / edit-and-resend delete rows *after* the anchor (`MessageDao.deleteFrom`, an SQLite
`(createdAt, id)` tuple comparison keeps ordering stable) and re-issue the request.

## Provider layer

- `AiProviderClient` is the seam: `listModels`, `testConnection`, `chatPayload`.
- `OpenAiCompatibleClient` builds `/chat/completions` and `/models` requests (optional `apiVersion`
  prefix for Azure-style gateways, custom headers, bearer key from `ProviderContext`).
- SSE is parsed **line-wise over a raw OkHttp `BufferedSource`** (`SseFlow.sseDataFlow`) instead of
  the okhttp-sse module: `data:` payloads flow into a cold `channelFlow`, `[DONE]` is forwarded
  verbatim, cancellation tears down the call quietly, and transport/HTTP failures surface as the
  typed `ChatError` taxonomy. Reasoning (`reasoning_content`) and `include_usage` chunks are
  honoured for DeepSeek/OpenRouter-style providers.
- `DefaultProviderClientFactory` wires a shared `OkHttpClient` (timeouts tuned for long
  generations) plus a separate short-timeout probe client for connection tests.

## Errors

`ChatError` is a sealed exception hierarchy; `ChatErrorKind.of(error)` collapses it into
DB-stable enum names for persistence, and `errorResFor(kind)` turns kinds into localized strings —
a pure, non-composable mapping so snackbars can resolve text outside composition. Stack traces are
never shown to users; provider `message` details render in a subdued tone.

## Storage & keys

- Room (schema v1, `exportSchema = true`) holds conversations, messages, providers, cached models,
  assistants and attachment metadata. **No API key column exists** — `ProviderEntity` documents
  that keys live in the secure store only.
- `KeystoreSecureStore` wraps each key in an AES-GCM envelope whose AES key never leaves the
  Android Keystore; only the ciphertext sits in private storage.
- DataStore keeps UI preferences (language, theme, dynamic color, markdown toggles, backups).

## Localisation & RTL

`LocalizedApp` re-provides the configuration/locale CompositionLocals from a settings value, so a
language switch re-resolves every `stringResource` immediately — no activity restart. Full RTL
ships: logical paddings, mirrored layouts, `AutoMirrored` icons where available in the pinned
Compose version. `values/` and `values-fa/` hold identical key sets (parity is part of the PR
checklist; see CONTRIBUTING.md).

## Markdown & attachments

- `MarkdownText` parses with commonmark + GFM tables into an AST and renders nodes in Compose
  (typography spans, styled code with highlighting, boxed tables) — no WebView.
- Attachments: images (resized before upload), text files and PDFs, gated by provider capability
  flags; unsupported combinations degrade to a localized inline note instead of failing the request.

## Backup / export

- `BackupService` assembles a schema-versioned `BackupBundle` (JSON, pretty, lenient read side).
  API keys are included **only** when the user flips the explicit toggle; imports merge by id
  (counts reported) and apply keys through the same guarded `upsert` path the editor uses.
- `ExportFormatter` produces Markdown transcripts (title, system prompt block, per-role headings,
  reasoning `<details>`, token annotations, footer).

## Testing strategy

- **Unit (JVM)**: provider client against MockWebServer (payloads, SSE shapes, taxonomy mapping),
  backup codec round-trips, BackupService merge accounting with in-memory fakes, token estimator,
  id generation, error-kind mapping.
- **Instrumented (DAO suite)**: in-memory Room — card ordering/preview joins, cascade deletes,
  streaming finalize, `(createdAt, id)` anchored deletes, replace-on-conflict upserts.

`connectedDebugAndroidTest` needs a device/emulator; `:app:compileDebugAndroidTestKotlin` compiles
the suite on any machine and is the CI gate.

