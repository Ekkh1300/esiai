# Contributing to ESIAI

Thanks for helping! ESIAI is an open-source Android BYOK chat client built in Kotlin with
Jetpack Compose. This document gets you productive quickly.

## Development setup

1. **JDK 17** (Temurin recommended), **Android SDK Platform 36** installed, and a
   `local.properties` pointing at the SDK (Android Studio generates it).
2. Build from source with the Gradle wrapper: `./gradlew :app:assembleDebug` (Linux/macOS) or
   `gradlew.bat :app:assembleDebug` (Windows). No global Gradle install is needed.
3. Behind a network-restricted network? See the README's *Network-restricted build environments*
   section (opt-in mirrors + `tools/fetch-mirror-artifact.ps1`). Normal builds need nothing extra.
4. Android Studio Ladybug or newer recommended; the IDE should not fight the Gradle 9.3.1 wrapper.

## Branches & commit messages

- Feature branches off `main`: `feat/markdown-tables`, `fix/sse-cancel-race`, `docs/fa-strings`.
- Conventional-commit style is appreciated: `feat: …`, `fix: …`, `docs: …`, `test: …`, `refactor: …`.
- One logical change per PR; keep whitespace-only churn out of logic PRs.

## Code guidelines

- **Kotlin**: official style, no wildcard imports, explicit
  `@OptIn(ExperimentalMaterial3Api::class)` per composable (the project does not use global opt-in
  compiler flags).
- **Architecture**: UI never touches Room/OkHttp/Keystore directly; go through domain interfaces.
  Constructor injection everywhere — tests must run without the DI framework.
- **Streaming**: any change to chat streaming must preserve the Room-as-source-of-truth flow —
  write rows while streaming, finalize once, keep `sweepStreamingMessages()` consistent.
- **Keys never leave the security boundary**: no key values in Room entities, logs, analytics or
  default exports. Update `SECURITY.md` if you touch `security/`.
- **Compose**: prefer stateless/state-holding splits, hoist parameters, and route user-visible
  text through resources (never hardcode English inside composables).

## Strings (English + fa-IR)

Every user-visible string exists in **both** `app/src/main/res/values/strings.xml` (English) and
`app/src/main/res/values-fa/strings.xml` (Persian) with identical key names. Write Persian text
directly in the XML files with proper characters (console tools can mangle UTF-8). String parity
is part of the review checklist — if a `values-fa` key is missing, the runtime silently falls back
to English, so fix parity before requesting review.

## Tests

New logic needs coverage next to the change:

- **Unit tests** (`app/src/test`) for pure logic: provider payload/SSE parsing (MockWebServer),
  backup codec/service, token estimation, error mapping.
- **DAO tests** (`app/src/androidTest`) for any Room query/structure change; keep them in-memory
  (`Room.inMemoryDatabaseBuilder`) like the existing suite.
- Run: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`.
- `./gradlew :app:lint` should not regress (new high-severity issues block merge).

## Where to start

- `ARCHITECTURE.md` maps the packages and the streaming pipeline.
- `RESEARCH.md` explains the product inspiration and the deliberate rejections.
- Good first issues: string polish, accessibility annotations (`contentDescription`), provider
  presets, DAO test gaps.

## PR checklist

- [ ] `:app:assembleDebug` green locally
- [ ] `:app:testDebugUnitTest` green
- [ ] `:app:compileDebugAndroidTestKotlin` green (DAO tests updated if schema/queries changed)
- [ ] New strings exist in **both** languages with identical keys
- [ ] No secrets, no `local.properties`, no generated files committed
- [ ] `CHANGELOG.md` updated under *Unreleased* for user-facing changes
- [ ] Screenshots/GIFs attached for visible UI changes

## Running the app on a local emulator

`tools/start-emulator.ps1` boots the AVD headless and waits until it is ready:

```powershell
.\tools\start-emulator.ps1 -Wait
```

Headless is not a preference: the windowed emulator is killed by a graphics-overlay conflict on
some machines, and a killed emulator leaves a headless `qemu` process behind that keeps the AVD
lock — the next start then fails with *"A snapshot operation is pending"*. The script clears
stale processes and lock files before booting.

## Licensing

By contributing you agree your work is released under the repository's [MIT license](LICENSE).
