# Releasing ESIAI

This project is published as a **signed** release APK. The signing key is deliberately
**not** part of the repository.

## One-time setup (already done for the maintainer)

The keystore lives outside the git working copy:

```
E:\ai and op\sokhan-keystore\esiai-release.jks      (alias: esiai, RSA 4096, valid 27 years)
```

The repository root contains a git-ignored `keystore.properties`:

```properties
storeFile=E:/path/to/esiai-release.jks
storePassword=…
keyAlias=esiai
keyPassword=…
```

`keystore.properties`, `*.jks` and `*.keystore` are listed in `.gitignore` — keep it that way.
A keystore committed to git is a leaked signing key.

## Build a signed release

```powershell
.\gradlew.bat :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (minified with R8 + resource shrinking).

When `keystore.properties` is missing the release build still works and simply stays
**unsigned** (`app-release-unsigned.apk`) — that is what CI produces, and what forks will
produce.

## Verify the signature

```powershell
D:\dev\android-sdk\build-tools\37.0.0\apksigner.bat verify --verbose --print-certs `
    app\build\outputs\apk\release\app-release.apk
```

Expect `Verified using v2 scheme: true` and `v3 scheme: true` (v3 enables key rotation).

## Install it

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

The release build uses the application id `com.sokhan.chat`; the debug build is
`com.sokhan.chat.debug`, so both can be installed side by side.

## Releasing on GitHub

```powershell
gh release upload v0.1.0 app\build\outputs\apk\release\app-release.apk --clobber
```

## Signing in CI (optional)

The workflow intentionally does **not** sign — the runner has no key. To publish signed
artefacts from CI:

1. Encode the keystore: `base64 -w 0 esiai-release.jks`
2. Store it as a repository secret `ESIAI_KEYSTORE_BASE64`, plus `ESIAI_KEYSTORE_PASSWORD`,
   `ESIAI_KEY_ALIAS`, `ESIAI_KEY_PASSWORD`.
3. In the workflow, decode the keystore into the runner's home directory, write a
   `keystore.properties` next to `settings.gradle.kts`, and run `:app:assembleRelease`.

Never print the secrets into the workflow log; mask them with `::add-mask::`.

## Backup rules — important

The same keystore must be used for every future release. If you lose it, users can never
receive an update for an already installed app (Android refuses updates signed with a
different key). Back up **both** the `.jks` file and the passwords somewhere safe and
private — a password manager plus an encrypted offline copy is enough.

The key uses APK Signature Scheme v3, so a *key rotation* plan is possible in the future,
but only if you keep the old key around as well.
