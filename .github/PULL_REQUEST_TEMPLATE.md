# Pull request

## What does this PR change?

<!-- 1-3 sentences: the problem and the fix/feature. -->

## Checklist

- [ ] `./gradlew :app:assembleDebug` green
- [ ] `./gradlew :app:testDebugUnitTest` green
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` green (DAO tests updated when schema changed)
- [ ] New user-facing strings added to **both** `values/strings.xml` and `values-fa/strings.xml`
- [ ] `CHANGELOG.md` updated under *Unreleased* (user-facing changes only)
- [ ] No secrets / generated build files / `local.properties` committed
- [ ] UI changes include screenshots (or GIFs)
