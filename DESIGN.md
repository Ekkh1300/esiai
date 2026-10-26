# ESI Agent — design system

The visual direction, in one place. Everything here is implemented in
`app/src/main/kotlin/com/sokhan/chat/ui/theme/` and the components under
`ui/components/`.

## Point of view — "ink & signal"

A chat app is two voices in a column. The design separates them with a
metaphor from stationery: **your words are ink** (one solid, confident
colour), **the model's words are a written note** (paper surface, hairline,
quiet chrome). Everything else stays out of the way.

One memorable element carries the identity: the assistant's card. Everything
around it is deliberately plain.

## Colour

| Role | Light | Dark | Meaning |
| --- | --- | --- | --- |
| `primary` | `#4B4FD6` | `#C3C4FF` | The user's own voice |
| `primaryContainer` | `#E3E3FF` | `#23249B` | Chips, selected states |
| `secondary` | `#00696E` | `#A6F0F5` | The assistant's signal |
| `secondaryContainer` | `#A6F0F5` | `#00494D` | Model chip, icon tiles |
| `tertiary` | `#8C5A2E` | `#FFB77C` | Copper — warnings only |
| `surface` | `#FBFAFD` | `#111118` | Cool violet-neutral, never pure grey |
| `outlineVariant` | `#CBC9D8` | `#464553` | Card hairlines |

Rules that keep the palette honest:

- The user's bubble is `primary` with `onPrimary` text; the assistant card is
  `surfaceContainerHigh` with a hairline. Roles, never raw hex, in components.
- Copper is reserved for destructive and warning accents. It is not a third
  brand colour.
- Both schemes fill the whole `surfaceContainer` ladder, so elevation reads
  correctly instead of every card looking like the same grey.
- Dynamic colour (Material You) is available on Android 12+; the static
  schemes are the designed fallback, not an accident.

## Typography

One family for both scripts: **Vazirmatn** (SIL OFL, bundled in
`app/src/main/res/font/`).

It is drawn for Persian first and carries a clean Latin with it, so switching
the app language never changes the texture of the text. Persian needs more
leading than Latin because of the dots and the marks below the baseline, so
the scale is tuned around it:

| Role | Size / line | Weight |
| --- | --- | --- |
| `titleLarge` | 20 / 28 | Bold |
| `titleMedium` | 16 / 24 | Medium |
| `bodyLarge` | 16 / 26 | Regular |
| `bodyMedium` | 14 / 23 | Regular |
| `bodySmall` | 13 / 20 | Regular |
| `labelMedium` | 12 / 17 | Medium |
| `labelSmall` | 11 / 16 | Regular |

## Shape

One family of radii, so the screen does not read as a pile of unrelated
stickers: 8 / 12 / 16 / 22 / 28 dp. Bubbles use 20 dp with a 6 dp corner on
the side that faces the other speaker.

## Components

- **User bubble** — filled `primary`, 20 dp radius, the tight corner on the
  trailing side.
- **Assistant card** — `surfaceContainerHigh` with a 1 dp `outlineVariant`
  hairline, model identity in a small tinted chip above it.
- **Message actions** — copy, share, edit, regenerate, delete on one compact
  row next to the timestamp. The glyph is 18 dp, the touch target 48 dp.
- **Reasoning block** — a quiet aside: chevron, muted label, indented rule.
  Never competing with the answer.
- **Conversation row** — avatar tile, title, preview, timestamp, overflow icon.
- **Settings row** — icon in a 34 dp tinted disc, title beside it.
- **Empty state** — a soft two-tone disc, then the title, then the hint.
- **Agent tool chips** — the calls the assistant wants to make, as small
  pills with a spark glyph above the reply card; the label is localised.
- **Tool activity row** — what happened to a call: a full-width pill with
  the same glyph, the tool name, the result preview and a trailing state
  disc — teal check when it ran, muted cross when the user declined.
- **Confirmation dialog** — before anything writes or deletes: title, tool
  label, pretty-printed arguments, then *Deny* and *Allow*; destructive
  calls tint *Allow* with the error colour.

## RTL

Persian is a first-class language, not a mirror applied afterwards. Everything
uses start/end alignment and direction-aware icons, so the layout mirrors
itself: the back arrow moves to the right, "send" moves to the left, the
model chip and the action row follow the bubble they belong to.

## Verification

- `tools/check_contrast.py` measures the WCAG ratio of every text node in a
  screenshot against its surface. Current result: **0 findings** in light,
  dark and LTR.
- `tools/capture.ps1` pulls the screenshot from the emulator (redirecting
  `adb exec-out` through PowerShell corrupts the PNG into UTF-16).
- `tools/start-emulator.ps1` boots the AVD headless and clears the stale
  lock files that otherwise break the next start.
- Touch targets are 48 dp minimum; the app is checked at 1.0 and 1.3 font
  scale, in light and dark, in both languages.

