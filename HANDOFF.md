# ESI Agent — Project Handoff (2026-10-26)

## چه کار شده (Completed)

### Brand / Repo
- Repo renamed & public: `https://github.com/Ekkh1300/esi-agent` (PUBLIC)
- App display name: `ESI Agent` (EN) / `ایجنت ESI` (FA) — strings updated
- Launcher icon redesigned: ink bubble + indigo spark (`#4B4FD6`); background `#111118`
- All docs updated (README, CHANGELOG, DESIGN, ARCHITECTURE, CONTRIBUTING, RESEARCH, SECURITY, RELEASES, CI workflow)

### Agent Mode (core feature)
- 12 tools in `AppAgentTools`: `set_theme`, `set_dynamic_colors`, `set_language`, `search_conversations`, `read_conversation`, `create_conversation`, `rename_conversation`, `delete_conversation`, `send_message`, `list_assistants`, `list_providers`, `export_backup`
- `StreamingChatEngine`: max 6 turns, graceful downgrade on 400/404/422/Malformed/EmptyResponse
- Risk-level confirmations: READ / WRITE / DESTRUCTIVE; WRITE+DESTRUCTIVE require `Allow/Deny` dialog (`PendingToolAction` flow)
- UI: `ToolCallRows` (assistant chips), `ToolActivityRow` (result/declined), confirmation `AlertDialog`
- Room v2 + `MIGRATION_1_2` (adds `toolCalls`, `toolCallId`, `toolName`)
- `Message.errorKind` reused: `DECLINED` / `FAILED`; `toolName` carries name; TOOL rows always `status=SUCCESS`

### Verification (device, emulator AVD `esiai`, headless `-no-window`)
- Mock LLM server (`mock_llm.py`, port 8787) running; emulator reaches `10.0.2.2:8787`
- End-to-end loop verified: send message → `set_theme` call → confirmation dialog → Allow → theme flips dark → TOOL row (`The theme is now "dark".`) → final Persian reply → 2nd POST with `"role":"tool"`
- Decline path verified: Deny → TOOL row `DECLINED` + "The user declined this action." → conversation continues
- Debug-only `AndroidManifest.xml` (`usesCleartextTraffic="true"`) allows mock/local endpoints; release keeps blocked

### Build / Tests (session 1)
- Gradle 9.3.1 / AGP 9.1.0 / Kotlin 2.4.0 / KSP 2.3.12 / minSdk 26 / compile+target 36
- 53 unit tests, 0 failures; 23 instrumented tests, 0 failures (incl. 2 `DatabaseMigrationTest` v1→v2)
- `assembleDebug` green; `assembleRelease` green (APK 2.8MB); `lintDebug` green (pre-existing 8 lint errors unchanged)
- CI (`ci.yml`) green: only `compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `assembleDebug`

### Commits / Release
- `bd618fb`: rename repo, public, icon, docs fix
- `719ab09`: agent mode feature (34 files, +2708)
- `v0.1.0` tag points to `9b179cd` (final with brand + icon), pushed to `Ekkh1300/esi-agent`
- Release assets: `esi-agent-0.1.0-release.apk` + `esi-agent-0.1.0-debug.apk` uploaded
- Keystore outside repo: `E:\ai and op\sokhan-keystore\esiai-release.jks` (alias `esiai`)

---

# سشن ۲ — 2026-10-26 (ادامهٔ کار)

## 1) حالت ایجنت روی هر API و مدلی کار می‌کند ✅

**مشکل:** فقط مدل‌هایی که function calling بومی داشتند کار می‌کردند؛ مدل‌های بدون tool-call
(یا endpointهایی که پارامتر `tools` را رد می‌کنند) اصلاً ابزاری اجرا نمی‌کردند.

**راه‌حل (اضافه‌شده، بدون حذف کد قبلی):**

- فایل جدید `app/src/main/kotlin/com/sokhan/chat/domain/agent/TextToolProtocol.kt`
  - دستورِ پروتکل متنی + پارس بلاک JSON از متن پاسخ (بلاک ` ```tool_call ` ، بلاک ` ```json `
    یا شیء JSON خام داخل متن) + بازسازی `ToolCall` + پاک‌سازی بلاک از متن نمایش‌داده‌شده
  - تحمل‌گر: کلید `arguments` / `args` / `parameters`، نام ابزار با `^[A-Za-z][A-Za-z0-9_.-]{0,63}$`
- `StreamingChatEngine`: به‌جای پرچم `toolsAllowed` حالا سه‌حالتهٔ `ToolMode` دارد
  - `NATIVE` → آرایهٔ `tools` روی درخواست (بهترین حالت)
  - `TEXT` → بدون `tools`؛ سیستم‌پرامپت قواعد پروتکل را دارد (انگلیسی، برای پیروی بهتر مدل) و
    پاسخِ حاوی بلاک پارس می‌شود؛ تاریخچه هم flatten می‌شود (پیام‌های `role=tool` → پیام USER با
    `Tool result …`، فراخوانی‌های قبلی → همان بلاک متنی داخل پیام ASSISTANT) چون endpointهای
    بدون tool-call این نقش‌ها را رد می‌کنند
  - `OFF` → چت ساده
- قانون انتخاب حالت (`initialToolMode`) و زنجیرهٔ downgrade (یک‌بار در هر generation):
  1. `agentMode` خاموش → `OFF`
  2. endpoint این جلسه `tools` را رد کرده (`nativeToolsBroken`) → `TEXT`
  3. وگرنه **همیشه** `NATIVE` — فلگ `modelRow.capabilities.tools` فقط راهنمای UI است، نه گیت
  4. خطا در حین `NATIVE` (BadRequest / **NotFound (404)** / Malformed / EmptyResponse و متن خالی) → `TEXT`
  5. خطا در حین `TEXT` → `OFF` (چت ساده، مثل رفتار قبلی)
- نکته: فقط **یک** پیام SYSTEM ساخته می‌شود؛ قواعد پروتکل به همان سیستم‌پرامپت append می‌شود
  (بعضی endpointها پیام SYSTEM دوم را رد می‌کنند).

**تست:** `app/src/test/kotlin/com/sokhan/chat/domain/agent/TextToolProtocolTest.kt` (۷ تست)

**تأیید روی ایمیولاتور (هر دو مسیر):**
- `gpt-test` (caps.tools=false) → `TEXT`: پاسخِ حاوی بلاک ` ```tool_call ` → بلاک از متن پاک شد،
  دیالوگ Allow/Deny با `theme="dark"`، Allow → `The theme is now "dark".` → پاسخ نهایی `تم.`
  و لاگ mock: `has_tools=False` برای هر دو POST
- `gpt-4-mock` (caps.tools=true) → `NATIVE`: لاگ mock: `has_tools=True`، دیالوگ تأیید ظاهر شد،
  Allow → ابزار اجرا، POST دوم هم `has_tools=True` (بازپخش native `role=tool`)، پاسخ نهایی `تم.`

## 2) مشکل کیبورد موقع تایپ ✅

**علت:** `enableEdgeToEdge()` در `MainActivity` + از Android 15 به بعد نادیده گرفتن
`windowSoftInputMode="adjustResize"` → کیبورد روی کل پنجره می‌نشست و فیلد Message را می‌پوشاند.

**راه‌حل:** `Modifier.imePadding()` روی ستون ریشهٔ `ChatComposer` در
`app/src/main/kotlin/com/sokhan/chat/ui/components/Composer.kt` (تنها تغییرِ همان فایل).
**تأیید روی دستگاه:** با باز شدن کیبورد، فیلد پیام + دکمهٔ ارسال کاملاً بالای کیبورد دیده می‌شوند.

## 3) آیکون برنامه (زیباتر + درست شدن بیلد) ✅

**مشکل فوری:** نسخهٔ قبلیِ آیکون (uncommitted) از `<circle android:radius=…>` استفاده کرده بود؛
`<circle>` در VectorDrawable اندروید وجود ندارد → **کل `processDebugResources` و بیلد می‌شکست.**

**کار انجام‌شده:**
- `app/src/main/res/drawable/ic_launcher_foreground.xml` بازنویسی کامل با `<path>` معتبر:
  حباب گفتگو با دُم + سه موج سیگنال indigo + هالهٔ روشن و ستارهٔ فیروزه‌ای + گرادیان شعاعی indigo
  نرم پشت حباب (به‌جای دایرۀ توپر کل صفحه که پسندۀ `#111118` مستندشده را می‌پوشاند)
- فایل جدید `app/src/main/res/drawable/ic_launcher_background.xml`: گرادیان عمودی `#1E1F3A → #0C0D15`
- فایل جدید `app/src/main/res/drawable/ic_launcher_monochrome.xml`: سیلوئت تک‌رنگ برای آیکون موضوعی
- `mipmap-anydpi-v26/ic_launcher.xml` و `ic_launcher_round.xml`: background/monochrome به فایل‌های جدید وصل شد
- `colors.xml` (`ic_launcher_background`) دست‌نخورده ماند؛ دیگر استفاده نمی‌شود ولی حذف نشده

**تأیید:** اسکرین‌شات کشوی برنامه‌ها — آیکون درست رندر شد (`_verify/icon1.png`، `_verify/icon_zoom.png`).
برچسب لانچر `ESI Agent` است (هم `app_name` EN و هم FA).

## 4) تکمیلِ باقی‌مانده‌های تغییر نام برند ✅

فقط فارسی عوض شده بود؛ انگلیسی/کد جا مانده بود. اصلاح شد:
- `values/strings.xml`: ۶ رشتهٔ `ESIAI` → `ESI Agent` + نام فایل خروجی `ESI Agent-export-%1$s.json`
- `BackupFileWriter.kt`: نام فایل پشتیبان و پوشهٔ Downloads → `ESI Agent`
- `SettingsScreen.kt`: نام پیش‌فرض خروجی → `ESI Agent-backup.json`
- `Color.kt` (کامنت)، `LICENSE` (کپی‌رایت)، `tools/check_contrast.py` (کامنت)
- `README.md`: کلید mirror درست شد — کد واقعاً `sokhan.mavenMirrorUrl` را می‌خواند
  (`settings.gradle.kts`) نه `ESIAI.mavenMirrorUrl`
- **عمداً عوض نشد:** پکیج `com.sokhan.chat`، دیتابیس `sokhan.db`، alias کی‌استور `esiai`،
  نام AVD `esiai`، کلید mirror داخلی `sokhan.*` (قرارداد داخلی، فقط نام برندِ عمومی عوض شده)

---

## Verification — سشن ۲ (همه سبز)

| بررسی | نتیجه |
|---|---|
| `./gradlew testDebugUnitTest` | **60 تست، 0 خطا** (53 قبلی + 7 تست جدید پروتکل) |
| `./gradlew assembleDebug` | **سبز** — APK: `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew connectedDebugAndroidTest` | **`Finished 23 tests on esiai(AVD) - 16`، بدون شکست** |
| جریان agent با پروتکل متنی (روی دستگاه) | تأیید شد (ابزار + Allow + پاسخ نهایی) |
| جریان agent با tool-call بومی (روی دستگاه) | تأیید شد (`has_tools=True`، دیالوگ، اجرا) |
| کیبورد بالای فیلد پیام | تأیید شد (اسکرین‌شات) |
| آیکون لانچر | تأیید شد (اسکرین‌شات) |

اسکرین‌شات‌ها: `E:\ai and op\_verify\` (`app1..app10`, `nav1..3`, `icon1`, `icon_zoom`)

**نکتهٔ mock:** مسیر native فقط وقتی تست می‌شود که mock در حالت `accept` باشد؛
`C:\Users\META PC\AppData\Local\Temp\opencode\mock_llm_text.py [accept|reject]`
- `accept` → درخواستِ دارای `tools` جواب native `tool_calls` می‌گیرد
- `reject` → همان درخواست `400` می‌گیرد (باید downgrade به TEXT را تریگر کند — کد آماده است،
  خودِ reject هنوز روی دستگاه تست نشده چون caps.tools مسیر TEXT را از قبل انتخاب می‌کند)

---

# سشن ۲ب — 2026-10-26 («روی API خودم هم agent نمی‌شد»)

**گزارش کاربر:** روی گوشی واقعی با API خودش (OpenRouter / `Claude-opus-5`) مدل جواب می‌داد ولی
هیچ ابزاری صدا نمی‌زد («متأسفانه نمی‌توانم به دستگاه شما دسترسی داشته باشم»).

**تحلیل علت** (سه محتمل‌ترین، به ترتیب):
1. نسخهٔ نصبی روی گوشی همان `v0.1.0` قدیمی است → در آن نسخه هر بار که endpoint پارامتر
   `tools` را رد می‌کرد، پرچم `toolsRejected` برای **کل provider و کل عمر پروسه** می‌نشست و بعد از آن
   همهٔ چت‌ها بی‌سروصدا «چت ساده» می‌شدند — یعنی عیناً همین علامت. (یک مدل `:free` که قبلاً 400 داده
   بود می‌توانسته Claude را هم مسموم کند.)
2. سوییچ **Agent mode** در Settings خاموش باشد (پیش‌فرض روشن است).
3. مدل قبلاً از `/models` fetch نشده بوده و حدس `capabilities.tools` اشتباه بوده.

**تغییرات کد (سشن ۲ب):**
- `StreamingChatEngine`: پرچم `nativeToolsBroken` حالا به‌ازای **provider+model** کلید می‌خورد نه
  فقط provider → یک مدلِ بی‌ابزار دیگر نمی‌تواند بقیهٔ مدل‌های همان provider را از کار بیندازد.
- **دستور پروتکل در هر دو حالت** به سیستم‌پرامپت اضافه می‌شود (`TextToolProtocol.instruction(native=…)`):
  - حالت NATIVE: ترجیح با `tools` پارامتر API است، ولی بلاک ` ```tool_call ` به‌عنوان پلن B
    (برای endpointهایی که `tools` را بی‌صدا حذف می‌کنند یا مدلی که native را نادیده می‌گیرد) —
    با این جملهٔ کوتاه برای صرفه‌جویی توکن.
  - حالت TEXT: همان نسخهٔ کامل قبلی.
- **پارس بلاک متنی در حالت NATIVE هم** انجام می‌شود (قبلاً فقط TEXT)؛ با قید اینکه نام ابزار
  حتماً در `AgentToolRegistry` ثبت شده باشد تا JSON معمولیِ داخل جواب، ابزارِ جعلی نسازد.

**تأیید روی ایمیولاتور (مسیر جدید):**
- mock در حالت `reject`: درخواست اول `mode=reject has_tools=True` → **HTTP 400**
- درخواست دوم `mode=reject has_tools=False` (بدون آرایهٔ tools، با قواعد متنی) → **HTTP 200**
- بلاک پارس شد → دیالوگ `Allow this action? / theme=dark` → Allow → ابزار اجرا → `تم.`
- جمعاً سه مسیر agent روی دستگاه سبز است: NATIVE، TEXT مستقیم، و NATIVE→TEXT (reject)

**نکتهٔ مهم برای ادامه:** با `adb install -r` روی این AVD دیتای اپ پاک شد
(`firstInstallTime == lastUpdateTime == 20:53:32`، دیتابیس تازه ساخته شد) — provider و
گفتگوها برای تست دوباره دستی ساخته شدند. قبل از هر نصب، اگر دیتا مهم است، بک‌آپ بگیر.

**آنچه کاربر باید انجام دهد:** نسخهٔ جدید را روی گوشی نصب کند (APK انتشاریِ ساخته‌شده در
`app/build/outputs/apk/release/app-release.apk`) و مطمئن شود سوییچ **Agent mode** در Settings روشن است.

---

---

# سشن ۳ — 2026-10-27 (تست agent روی API واقعی کاربر)

**خواسته:** تست قابلیت ایجنت روی endpoint واقعی کاربر:
`http://localhost:20128/v1` · کلید `sk-1eeaf39…` · مدل `claude-opus-5`
(ایمیولاتور این میزبان را با `10.0.2.2` می‌بیند نه `localhost`.)

## ۱) بررسی API با curl (قبل از اپ)

| درخواست | نتیجه |
|---|---|
| `GET /v1/models` | 200 — ۸۳۷ مدل |
| `POST …` مدل `claude-opus-5` | **200** ✅ |
| `POST …` مدل `ag/claude-opus-5-5-low` | **403** ❌ (خطای سمت provider، نه اپ) |
| `POST …` مدل `openrouter/openrouter/free` ساده | **200** ✅ |
| `POST …` همان مدل + آرایهٔ `tools` | **200** با `finish_reason=tool_calls` و
  `set_theme {"mode":"dark"}` ✅ |

پس خطای ۴۰۳ که قبلاً در اپ دیده شده بود از خودِ مدل `ag/claude-opus-5-5-low` می‌آمد، نه از لایهٔ agent.

## ۲) انتخاب مدل بدون اسکرول لیست ۸۳۷تایی

**مشکل عملی:** پیکر مدل هیچ جستجو ندارد و ردیف‌ها فقط با `text` نمایش داده می‌شوند
(`uiautomator dump` هم روی LazyColumn این اپ ردیفی برنمی‌گرداند) → رسیدن به `openrouter/openrouter/free`
دستی تقریباً نشدنی بود.

**راه‌حل (فقط داده، بدون تغییر کد):**
1. `adb shell am force-stop com.sokhan.chat.debug`
2. `run-as com.sokhan.chat.debug sqlite3 databases/sokhan.db` و
   `UPDATE conversations SET modelId='openrouter/openrouter/free' WHERE id='r1y1u1pzz7kl8clv';`
3. استارت دوبارهٔ اپ

چرا این کار جواب می‌دهد: `StreamingChatEngine` مدل را از
`conversation.modelId ?: provider.config.defaultModelId ?: settings.defaultModelId` می‌گیرد و اگر ردیف
مدل در جدول `models` نباشد فقط `modelRow=null` می‌شود (حدس `capabilities.tools` نادیده گرفته می‌شود و
مسیر `NATIVE` می‌ماند). یعنی برای تست، تغییر `conversation.modelId` کافی است.

نکتهٔ رابط کاربری: پیام «Choose a model» در صفحهٔ خالی به انتخاب مدل ربطی ندارد — شاخهٔ
`messages.isEmpty()` است و برای هر مکالمهٔ تازه نمایش داده می‌شود.

## ۳) نتیجهٔ تست agent روی `openrouter/openrouter/free` ✅

روی ایمیولاتور، مکالمهٔ `Switch_the_theme_to_dark`:
1. ارسال پیام `Switch the theme to dark` → حالت «Generating…»
2. **دیالوگ تأیید** `Allow this action? / ⚡ Change the theme / theme="dark"` با Deny/Allow ظاهر شد
   (یعنی `ToolMode.NATIVE` انتخاب شده و فراخوانی ابزار واقعی بود)
3. **Allow** → ردیف TOOL `Change the theme` + `The theme is now "dark".` با تیک موفقیت
4. **پاسخ نهایی مدل:** `The theme has been switched to dark.`

→ حلقهٔ کامل ایجنت (درخواست → tool_calls → تأیید → اجرا → پاسخ) روی یک endpoint واقعیِ ثالث سبز است.

## ۴) باگ پیدا‌شده و رفع‌شده: متن ناخوانا (mojibake) در خط آمار توکن

**علت:** در `ChatBubble.kt` تابع `usageLine`، کاراکترها دوباره‌کد شده بودند — بایت‌های UTF-8
فلش (`E2 86 93`) به‌جای UTF-8 با `cp1252` خوانده و دوباره ذخیره شده بودند، پس در متن برنامه
`â†“4545 Â· 81` دیده می‌شد نه `↓4545 · 81`.

**اصلاح (تنها فایلِ خراب، `app/src/main/kotlin/com/sokhan/chat/ui/components/ChatBubble.kt`):**
- `usageLine`: `"â†“"` → `"↓"`، `"â†‘"` → `"↑"`، `"  Â·  "` → `"  ·  "`
- یک مورد دیگر در همان فایل (خط ~709، جداکنندهٔ تاریخ/ساعت): `" Â· "` → `" · "`
- پویش بایتی کل `app/src` نشان داد **هیچ فایل دیگری** الگوی خراب ندارد؛ هیچ تستی به این رشته وابسته نیست

**تأیید:** `./gradlew assembleDebug` سبز، نصب روی ایمیولاتور، اسکرین‌شات:
خط زیر پیام حالا `↓4545 · ↑81 · ~4562` و `↓4596 · ↑9 · ~4605` است.

**نکته:** عدد `total` که API برمی‌گرداند گاهی با جمع prompt+completion نمی‌خواند (4545+81=4626 ≠ 4562) —
این از سمت provider می‌آید، اپ فقط همان را نمایش می‌دهد.

---

# سشن ۴ — 2026-10-30 («با API واقعیِ OpenRouter حالت ایجنت کار نمی‌کند»)

**گزارش کاربر:** روی گوشی واقعی، با کلیدی که از OpenRouter می‌دهد، مدل جواب می‌دهد ولی
هیچ ابزاری اجرا نمی‌شود (تم عوض نمی‌شود و …).

**ریشه‌ها (هر چهار مورد واقعی و در کد قابل ردیابی بود):**

1. **گیتِ حدسِ قابلیت** — `initialToolMode` وقتی `modelRow.capabilities.tools == false` بود،
   مستقیم `TEXT` می‌رفت و آرایهٔ `tools` **هرگز** فرستاده نمی‌شد. این فلگ پیش‌فرض `false` است و
   `ModelHeuristics` فقط چند خانوادهٔ قدیمی را می‌شناخت، پس مدل‌های رایجِ OpenRouter
   (`openai/gpt-oss-…`، `meta-llama/llama-4-…`، `x-ai/grok-…`، `cohere/command-…`، مدل‌های
   دستی‌اضافه‌شده) بی‌صدا به پروتکل متنی می‌افتادند — و اگر مدل دستور متنی را پیروی نکند،
   «هیچ اتفاقی نمی‌افتد». **حالا همیشه اول NATIVE امتحان می‌شود**؛ فلگ فقط راهنمای UI است.
2. **۴۰۴ به‌عنوان ردِ ابزار شناخته نمی‌شد** — OpenRouter برای مدلی که endpoint ابزاردار ندارد
   پاسخ می‌دهد: `404 No endpoints found that support tool use`. قبلاً این خطا به کاربر نشان داده
   می‌شد و downgrade رخ نمی‌داد. اکنون `ChatError.NotFound` هم در `isToolRejectionFor` پذیرفته است.
3. **خطای درون‌جریانی (in-band)** — OpenRouter شکست provider را داخل همان استریمِ HTTP 200
   می‌فرستد: `{"error":{"code":502,"message":"Provider returned error"}}`. قبلاً نادیده گرفته
   می‌شد و نتیجه `EmptyResponse` گیج‌کننده بود؛ اکنون `mapInBandError` آن را به تاکسونومی خطاها
   نگاشت می‌کند (۴۰۴ → `NotFound` → همان downgrade).
4. **آرگومان‌های رشته‌ای در پروتکل متنی** — مدل‌ها اغلب شکل wire اوپن‌ای‌آی را کپی می‌کنند:
   `"arguments": "{\"theme\":\"dark\"}"` (رشته، نه شیء). پارسر قبلاً آن را «آشغال» می‌دانست و
   رد می‌کرد. اکنون رشتهٔ JSON باز می‌شود.

**تغییرات:**
- `domain/service/StreamingChatEngine.kt` — `initialToolMode` بدون گیت قابلیت؛
  `isToolRejectionFor` به‌عنوان تابع top-level و قابل تست (BadRequest/NotFound/Malformed/EmptyResponse)
- `provider/openai/SseFlow.kt` — `mapInBandError(JsonObject)`
- `provider/openai/OpenAiCompatibleClient.kt` — پرتاب خطا برای فریم `error` درون استریم
- `provider/openai/OpenAiDtos.kt` — فیلد `error` در `OpenAiStreamChunk`
- `provider/ModelHeuristics.kt` — فهرست خانواده‌های ابزاردار به‌روز (فقط برای چیپ‌های UI)
- `domain/agent/TextToolProtocol.kt` — باز کردن `arguments` رشته‌ای
- `res/xml/network_security_config.xml` (+ نسخهٔ `debug/`) — نسخهٔ release حالا cleartext را
  فقط برای `localhost` / `127.0.0.1` / `10.0.2.2` اجازه می‌دهد؛ یعنی پروکسی یا سرور محلی
  (مثل همان `localhost:20128` سشن ۳) در بیلد release هم کار می‌کند، بقیهٔ دنیا همچنان HTTPS

**تست‌های جدید:** `ToolFallbackPolicyTest` (۳) + ۴ تست OpenRouter در `OpenAiCompatibleClientTest`
(۴۰۴ ابزار، خطای in-band، ۴۰۴ درون‌باند، کامنت `: OPENROUTER PROCESSING` + چانک usage با
`choices: []`) + ۲ تست رشته‌ای‌بودن آرگومان‌ها → **۷۰ تست، ۰ خطا**.

| بررسی | نتیجه |
|---|---|
| `testDebugUnitTest` | **۷۰ تست، ۰ خطا** |
| `assembleDebug` | **سبز** |
| `lintDebug` | **سبز** (بدون خطای جدید) |

**mock برای بازتولید OpenRouter:** `C:\Users\META PC\AppData\Local\Temp\opencode\mock_llm.py`
حالا حالت را از `mock_mode.txt` می‌خواند:
- `native` — tool-call بومی (مثل قبل)
- `openrouter` — درخواستِ دارای `tools` → دقیقاً ۴۰۴ واقعی OpenRouter؛ درخواست بدون `tools`
  (همان turn، پس از downgrade) → پاسخ متنی با بلاک ` ```tool_call `؛ turn بعد از نتیجهٔ ابزار →
  پاسخ نهایی. مدل `/models` هم `acme/quasar-7b` است که هیچ hint ای matches نمی‌شود
  (یعنی `capabilities.tools=false` و مسیر رگرسیون را دقیقاً می‌سنجد).
- `inband` — HTTP 200 با فریم خطا
هر درخواست در `mock_requests.jsonl` آرشیو می‌شود تا کل turn بازپخش شود.

**وضعیت تأیید روی دستگاه:** نصب debug روی گوشیِ `8BG7S16331006402` با دیالوگ نصب هواوی
(`INSTALL_CANCELED_BY_USER`) و بعد قطع‌شدن USB ناتمام ماند؛ باید با گوشیِ وصل‌شده تکرار شود
(مرحله‌ها در «Next Steps»).

---

## What's Open / Next Steps (Continue Here)

0. **نصب و تأیید سشن ۴ روی گوشی** — `app/build/outputs/apk/debug/app-debug.apk` آماده است:
   1. گوشی را وصل کن و دیالوگ «نصب» هواوی را روی صفحه تأیید کن (`adb install -r` تا تأیید
      معلق می‌ماند؛ با رد شدن `INSTALL_CANCELED_BY_USER` می‌دهد)
   2. `adb reverse tcp:8787 tcp:8787` و mock را در حالت `openrouter` بگذار (`mock_mode.txt`)
   3. در اپ: provider موجود را روی `http://127.0.0.1:8787/v1` نگه دار → «Refresh models» → مدل
      `acme/quasar-7b` را انتخاب کن (capabilities.tools=false) → پیام `Switch the theme to dark`
   4. انتظار: `mock_requests.jsonl` سه رکورد داشته باشد — اولی `"tools":[...]` + ۴۰۴، دومی بدون
      tools (متن)، سومی شامل «Tool result»؛ و تم تیره شود
   5. سپس با کلید واقعی OpenRouter همان مکالمه را تکرار کن؛ `AppLog` اگر جایی خطا داد

1. **Commit کردن کار سشن ۲ و ۳** — همهٔ تغییرات بالا **uncommitted** هستند (`git status`).
   شاخه ۱ کامیت هم از `origin/main` جلوتر است (`9b179cd` هنوز push نشده).
2. **آپلود release روی `v0.1.0`** — `./gradlew assembleRelease` سبز است و
   `app/build/outputs/apk/release/app-release.apk` با کلید محلی امضا شده؛ آپلود طبق
   `docs/RELEASING.md` هنوز انجام نشده.
   توجه: `git log --oneline -1 v0.1.0` محلی نشان داد `bd618fb` نه `9b179cd` — قبل از آپلود درستش کن.
2ب. **نصب روی گوشی کاربر + چک سوییچ Agent mode** — گزارش «agent روی API خودم کار نمی‌کند» با
   نسخهٔ جدید + سوییچ روشن باید حل شده باشد؛ اگر نشد، لاگ کندبیفِپ دستگاه را از `AppLog` گرفت.
3. **(اختیاری) چیپ «Tools» در ویرایشگر مدل** — `capabilities.tools` فقط هنگام fetch حدس زده می‌شود و
   در UI قابل تغییر نیست (چیپ‌های فعلی فقط Vision/PDF/Text). اگر مدلی اشتباه طبقه‌بندی شود
   کاربر راهی برای اصلاح ندارد. چیپ + رشتهٔ EN/FA اضافه کن.
4. **(اختیاری) تست مسیر reject** — با mock در حالت `reject` و ری‌استارت اپ (پرچم
   `nativeToolsBroken` داخلی است و با restart پاک می‌شود) می‌شود downgrade NATIVE→TEXT را روی دستگاه دید.
5. **(کم‌اهمیت) اسکرین‌شات نهایی لانچر** — آیکون در کشو تأیید شد؛ اگر روی هوم‌اسکرین هم خواستی یک
   اسکرین‌شات دیگر بگیر.
6. **Placeholder برای آینده:** اگر پکیج `com.esi.agent` یا نام دیتابیس (`sokhan.db`) عوض شود — طبق
   قرارداد طراحی عمداً تغییر نکرده.
7. **اسکرین‌شات‌های قدیمی `icon2.png`** از سشن قبلی ناتمام مانده بود؛ جایگزین: `_verify/icon1.png`.

## Key Decisions (Don't Re-invent)

### سشن ۱ (بدون تغییر)
- Internal id `com.sokhan.chat`, DB `sokhan.db`, namespace NOT renamed — only app brand is `ESI Agent`
- Debug manifest (`src/debug/AndroidManifest.xml`) keeps `usesCleartextTraffic`; release stays blocked
- Mock server (`mock_llm.py`) uses turn-detection: last message `role="tool"` = turn 2, else turn 1
- `boolArg` intentionally tolerant (accepts quoted `"true"`)
- `ModelPickerSheet` has NO refresh button — provider row tap triggers `pickProvider()` reload
  (فقط از DB می‌خواند؛ رفرش شبکه از **Settings → Open provider list → مدل → Refresh models** است)

### سشن ۲ (جدید)
- ~~انتخاب حالت ابزار بر اساس `capabilities.tools`~~ — **در سشن ۴ باطل شد:** همیشه اول NATIVE،
  فلگ قابلیت فقط راهنمای UI است؛ downgrade فقط بر اساس خطای واقعی (۴۰۰/۴۰۴/۴۲۲/استریم خالی)
- پروتکل متنی **فقط انگلیسی** (پیروی دستور مدل)؛ خروجی/خطاها همچنان محلی‌اند
- در حالت TEXT هیچ `tool_calls` یا `role=tool` روی درخواست نمی‌رود (flatten کامل تاریخچه)
- حداکثر **یک** فراخوانی در هر پاسخِ حالت TEXT (در دستور گفته شده؛ پارسر هم فقط اولی را برمی‌دارد)
- `nativeToolsBroken` به‌ازای **provider+model** و برای همهٔ مکالمه‌های همان جلسه sticky است
- کیبورد با `imePadding` حل شد، نه با تغییر `windowSoftInputMode` (edge-to-edge عمداً روشن است)
- آیکون: دایرۀ توپر indigo حذف شد چون کل پسندۀ تیره را می‌پوشاند؛ مونوکروم لایهٔ جدا دارد

## Security / Privacy (critical — do NOT regress)

- Keystore at `E:\ai and op\sokhan-keystore\` — never commit; `.gitignore` covers `*.jks`
- `keystore.properties` رمزها داخلش است ولی git-ignored است و tracked نیست (بررسی شد)
- API keys AES-GCM via Keystore; never in Room/logs; masked in UI
- Cleartext blocked in release; debug-only manifest is the exception
- GitHub repo PUBLIC; no secrets in commit history (verified via `git rev-list --objects`)
- mock سرور فقط روی `0.0.0.0:8787` محلی است؛ کلیدی در کار نیست

---

*Created: 2026-10-26 · سشن ۲ (ادامه): پروتکل متنی agent، فیکس کیبورد، آیکون، تکمیل برند.
Project: ESI Agent (formerly Sokhan/ESIAI). Repo: `github.com/Ekkh1300/esi-agent`*
