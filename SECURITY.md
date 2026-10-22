# Security policy

ESIAI is a client app that talks **directly to AI provider endpoints you configure**, using API
keys you provide. There is no server component, no account system and no telemetry.

## Reporting a vulnerability

Please **do not open a public issue** for anything that could expose users. Instead use the
private-disclosure path: open a GitHub *security advisory* (Security → Advisories → New draft
security advisory) or contact the maintainers. Include a reproducible description, the affected
component and, if possible, a minimal proof of concept. Reports are acknowledged within 7 days;
fixes ship in the next release and the advisory is credited if you wish.

## How API keys are stored

| Where | What lives there |
| --- | --- |
| Android Keystore | A non-exportable AES key (AES/GCM/NoPadding), one alias per provider. |
| App-private storage | Only the **ciphertext** of user keys, wrapped with the Keystore key, plus a random IV. |
| Room database | **Never** contains API keys — `ProviderEntity` has no key column by design. |
| Logs | Never contain keys; log statements redact secrets and never serialise `ProviderContext`. |
| UI | Keys render masked (`sk-…abcd`); plaintext is only visible in the edit field while editing. |

## Honest limitations (BYOK threat model)

A client app cannot change the basic properties of BYOK; these are inherent:

1. **On-device attackers.** With read access to the process (a rooted or compromised device), the
   stored key can be decrypted — the Keystore guard is as strong as the device's Keymaster
   implementation. ESIAI uses hardware-backed keys when the device offers them.
2. **Keys necessarily leave the device toward your provider.** BYOK means the configured endpoint
   receives the key over TLS on every request. Whoever obtains your key controls billing and model
   choice accordingly.
3. **No certificate pinning.** Endpoints are user-chosen, so TLS trust uses the standard system
   certificate store. A malicious proxy that can install a CA on the device can see the traffic;
   no BYOK client can defend a device whose trust store it does not control.
4. **Backups.** JSON backup *including keys* is opt-in (the "include API keys" toggle). Files
   exported that way contain **plaintext keys** — store them in a safe place and delete copies you
   no longer need. Backups exported without the toggle contain no secrets.
5. **Clipboard.** Prefer pasting the key directly from a password manager rather than routing it
   through other apps.
6. **Debug builds** are for development; same crypto, but not intended for daily use.

## Supply chain

- Dependencies are pinned in `gradle/libs.versions.toml`.
- Mirror tiers are **opt-in** (`ESIAI.mavenMirrorUrl` in local.properties); the committed local
  seed repo `third_party/local-maven` removes cold-start dependence on any single mirror.
- The wrapper is pinned (`gradle/wrapper/gradle-wrapper.properties`); CI builds releases from
  tagged commits, not from working trees.

## Recommended hardening for users

- Use provider-side **scoped / revocable keys** where the provider supports them.
- Keep device-level disk encryption and a strong screen lock enabled (default on modern Android).
- Prefer exporting backups without keys; if you include keys, keep those files offline.
