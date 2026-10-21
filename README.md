# Sokhan · سخن

**Sokhan** (Persian for "speech") is an open-source, bring-your-own-key (BYOK) AI chat client for
Android, written in Kotlin + Jetpack Compose. It talks to any **OpenAI-compatible** API —
OpenAI, DeepSeek, OpenRouter, Together, Groq,LM Studio, Ollama (OpenAI mode), vLLM, llama.cpp
server, or any other endpoint that speaks the `/chat/completions` + `/models` dialect.

No accounts. No telemetry. No server component — your API keys stay in your pocket.

## Highlights

- **Bring your own provider** — create connections with base URL + key + model list; ships with
  presets and a step-by-step connection tester (reachability → auth → models).
- **Real-time streaming** — token deltas appear as the model types; the stream keeps running even
  when you leave the chat screen (Room is the single source of truth, so history, status and token
  usage survive navigation, backgrounding and even process death via orphan recovery on restart).
- **Assistants** — reusable personas (name, emoji, system prompt, model, temperature, streaming
  behaviour) you can one-tap into a new chat.
- **Attachments** — images (gallery / camera), PDFs and plain text files where the configured model
  supports them, with graceful degradation and clear sizing errors.
- **Markdown rendering** — headings, lists, links, emphasis, inline & fenced code with syntax
  highlighting, block quotes and GFM tables, rendered natively in Compose (no WebView).
- **Message actions** — copy, share, edit-and-resend, delete, retry, regenerate, stop mid-stream.
- ** speaking English and فارسی, with full RTL** — switch language and (optionally) material you
  dynamic colours inside the app, without restarting the activity.
- **Local data control** — conversations, assistants and provider configs live in an encrypted-at-
  rest-by-SQLite-on-disk Room database; keys live in Android Keystore–wrapped storage and are never
  written to the database or logs. One-tap JSON backup (with an *explicit* "include API keys"
  toggle) and Markdown export/share of any conversation.

## Screens

| Route | Purpose |
| --- | --- |
| Chats | conversation list with pin/archive/search |
| Chat | streaming conversation, model picker, attachments |
| Providers | provider connections (create / duplicate / test / set default) |
| Provider editor | base URL, headers, presets, capability flags, params, per-model config |
| Assistants | persona library |
| Assistant editor | emoji picker, model binding, temperature, streaming |
| Settings | language, theme, defaults (provider/model/system prompt), display, backup, about |
| About / Licenses | project info + open-source dependency licenses |

## Build

Requirements: **JDK 17**, **Android SDK 36** (platforms; android-35 also fine to develop), Android
Studio Ladybug/Meerkat or any Gradle 9.3.x shell.

```bash
# from the repository root
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:connectedDebugAndroidTest   # Room DAO tests (device or emulator required)
```

Or open the folder in Android Studio and press Run. `minSdk 26`, `targetSdk/compileSdk 36`.

### Network-restricted build environments

This repository was developed behind a network where `dl.google.com` is unreachable. Mirrors are
**opt-in** and never affect normal builds: when nobody sets the property, `settings.gradle.kts`
uses plain `mavenCentral()` / `google()` / `gradlePluginPortal()`.

- the local seed repo `third_party/local-maven/<group>/<artifact>/<version>/…` is always consulted
  first when present; artifacts pinned there (e.g. the committed KSP plugin marker POM) resolve
  even when every remote mirror is flaky;
- setting `sokhan.mavenMirrorUrl=<base>` in `local.properties` (git-ignored) enables two extra
  tiers *before* the default repositories:
  1. Aliyun mirrors — `<base>/google`, `<base>/public`, `<base>/gradle-plugin` (pluginManagement)
     and `<base>/google`, `<base>/public` (dependencies); e.g.
     `sokhan.mavenMirrorUrl=https://maven.aliyun.com/repository`
  2. Tencent (`mirrors.cloud.tencent.com/nexus/repository/maven-public`) as a gap-filler;
- for a single missing artifact, pre-seed the local repo with the helper script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/fetch-mirror-artifact.ps1 `
  -Group androidx.example -Module example -Version 1.2.3 -Classifier aar `
  -MirrorUrl https://mirrors.cloud.tencent.com/nexus/repository/maven-public
```

CI (GitHub Actions) runs on internet-connected runners with no mirror property set, so the extra
tiers are inert there.

## Project structure

```
app/src/main/kotlin/com/sokhan/chat/
├── core/        # small dependency-free helpers (ids, token estimation, logging)
├── security/    # Android Keystore–backed API key storage (AES-GCM)
├── domain/      # models, repository interfaces, ChatEngine + services
├── data/        # Room entities/DAOs, repositories, DataStore settings, export
├── provider/    # provider-agnostic contracts + OpenAI-compatible client (SSE)
├── di/          # Koin modules
└── ui/          # Compose screens, shared components, navigation, theming
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design, [RESEARCH.md](RESEARCH.md) for the survey of
similar open-source clients, [SECURITY.md](SECURITY.md) for the security model and its honest
limitations, and [CONTRIBUTING.md](CONTRIBUTING.md) to get involved. User-facing changes are
recorded in [CHANGELOG.md](CHANGELOG.md).

## Credits & inspirations

Sokhan was written from scratch, but ideas were consciously borrowed from excellent open-source
peers:

- **[RikkaHub](https://github.com/rikkahub/rikkahub)** — Android Kotlin/Compose BYOK chat client.
  Influenced the "persist-while-streaming" design that keeps conversation state as a Room-backed
  stream rather than in-memory UI state.
- **[Chatbox](https://github.com/Bin-Huang/chatbox)** — popular cross-platform BYOK chat app with a
  similar "my providers" onboarding experience.
- **[Cherry Studio](https://github.com/CherryHQ/cherry-studio)** — desktop multi-provider client; a
  reference for provider presets and capability flags.
- **[LobeChat](https://github.com/lobehub/lobe-chat)** — web-based assistant platform; influenced
  the assistant/persona model and the emphasis on share/export.
- **[Open WebUI](https://github.com/open-webui/open-webui)** — self-hosted web UI; influenced the
  flat "role → content" Markdown export format and the multi-provider-friendly settings layout.

None of their code is used; Sokhan is MIT-licensed independent work.

## License

[MIT](LICENSE) © 2026 Sokhan contributors
