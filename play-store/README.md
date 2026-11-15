# نشر سوا على Google Play — الملفّ الكامل

> آخر تحديث: ١٥ نوفمبر ٢٠٢٦ · الحزمة: **c282** · المعرّف: `com.sawaaap.app` · targetSdk 36

## محتويات هذا المجلّد

| الملفّ | الاستعمال | الحجم |
|---|---|---|
| `README.md` | هذا الدليل | — |
| `listing-ar.txt` | نصوص صفحة المتجر بالعربيّة (للصق) | ~٣ KB |
| `listing-en.txt` | نصوص صفحة المتجر بالإنجليزيّة | ~٣ KB |
| `release-notes.txt` | «ما الجديد» لأوّل إصدار (عربيّ/إنجليزيّ بوسوم Play) | ~٠٫٥ KB |
| `icon-512.png` | أيقونة المتجر 512×512 (PNG، بلا شفافيّة) | 152 KB |
| `feature-graphic-1024x500.jpg` | الصورة الرئيسيّة 1024×500 | 45 KB |

ملفّ التطبيق نفسه (**AAB**) لا يُحفظ هنا، بل يبنيه GitHub (الخطوة ١).

---

## الخطوة ١ — بناء ملفّ AAB

1. في مستودع **Sawa-android** ← تبويب **Actions** ← «Android build (native)» ← **Run workflow** (الفرع `main`).
2. بعد انتهائه (٥–١٠ دقائق) افتح التشغيل وتأكّد من ملخّصه:
   - «✅ كلّها متوافقة مع صفحات 16 KB»
   - «targetSdk 36»
   - الحزمة **c282**
3. **الأسهل من الهاتف:** صفحة الإصدار https://github.com/khalidlinux87-png/Sawa-android/releases/tag/android-latest ← نزّل `Sawa-2.0.N.aab` مباشرةً (بلا فكّ ضغط). الرابط نفسه يظهر في ملخّص التشغيل.
4. أو من أسفل صفحة التشغيل ← **Artifacts** ← نزّل `Sawa-android-native` (ملفّ zip). فيه:
   - `Sawa-2.0.N.aab` ← **هذا ما يُرفع إلى Google Play**.
   - `Sawa-2.0.N.apk` ← للتجربة على هاتفك فقط.

> رقم الإصدار يزيد تلقائيّاً مع كلّ تشغيل (versionCode = 1000 + رقم التشغيل)، فلا تحتاج تعديله يدويّاً.

## الخطوة ٢ — حساب المطوّر

1. https://play.google.com/console ← سجّل برسم ٢٥ دولاراً لمرّةٍ واحدة.
2. اختر نوع الحساب:
   - **منظّمة** (إن كان لـ Ether Services LTD رقم D-U-N-S): لا يُشترط الاختبار المغلق.
   - **شخصيّ**: إلزاميّ اختبارٌ مغلق بـ **١٢ مختبِراً لمدّة ١٤ يوماً متّصلة** قبل الإنتاج (الخطوة ٩).
3. وثّق الهويّة والهاتف كما يطلب Console.

## الخطوة ٣ — إنشاء التطبيق

Play Console ← **Create app**:
- اسم التطبيق: `سوا — محادثات وصلة الرحم`
- اللغة الافتراضيّة: **العربيّة – ar**
- App: **App** · مجّانيّ: **Free**
- وافق على الإقرارات ← Create.

## الخطوة ٤ — «محتوى التطبيق» (App content)

من القائمة: **Policy and programs ← App content**. أكمل كلّ بند:

| البند | الجواب |
|---|---|
| Privacy policy | `https://www.sawaaap.com/privacy.html` |
| App access | **All or some functionality is restricted** ← أضف حساب مراجعة: بريد + كلمة مرور (حساب سوا حقيقيّ فيه محادثة وشجرة تجريبيّة) مع الشرح: «سجّل الدخول بالبريد. حذف الحساب: تبويب حسابي ← أسفل الصفحة. الإبلاغ والحظر: افتح محادثة ← اضغط اسم الطرف الآخر.» |
| Ads | **No** |
| Content rating | استبيان IARC ← الفئة **Social / Communication**: تفاعل المستخدمين = نعم · مشاركة الموقع = نعم · العنف/المقامرة/الإعلانات/الشراء الرقميّ = لا |
| Target audience | **13+** فقط (13–15، 16–17، 18+). لا تختر أصغر من 13 |
| News app | No |
| Data safety | انظر الجدول في الخطوة ٥ |
| Government app | No |
| Financial features | **No financial features** (المحفظة للمشرفين فقط — انظر أدناه) |
| Health | No |
| Account deletion | `https://www.sawaaap.com/delete-account.html` |
| Foreground service permissions | انظر الخطوة ٦ |
| Full-screen intent | **Calling / video calling** |

### المحفظة: للمشرفين والمدير فقط (C321)

المحفظة (ومعها المحاسب الشخصيّ) تظهر للمشرفين والمدير فقط، ولا تظهر لأيّ مستخدمٍ عاديّ، ولا في نسخة Google Play. الشاشة نفسها لا تُفتح لغيرهم بأيّ طريق.
- في نموذج **Financial features** اختر: **«My app doesn't provide any financial features»**.
- **حساب المراجعة يجب أن يكون مستخدماً عاديّاً.** لا تعطِ المراجع حساب مشرفٍ أو مدير أبداً.

## الخطوة ٥ — Data safety (أمان البيانات)

أسئلة عامّة:
- هل يجمع التطبيق أو يشارك بيانات؟ **نعم**
- مشفّرة أثناء النقل؟ **نعم**
- يمكن طلب حذفها؟ **نعم** (رابط حذف الحساب)
- المشاركة مع جهات خارجيّة: **لا** (Firebase وCloudflare مزوّدا خدمة يعالجان لحساب سوا)

| الفئة في النموذج | النوع | يُجمع | اختياريّ | الغرض |
|---|---|---|---|---|
| Personal info | Name | نعم | لا | App functionality, Account management |
| Personal info | Email address | نعم | نعم | Account management |
| Personal info | Phone number | نعم | نعم | App functionality, Account management |
| Personal info | User IDs | نعم | لا | App functionality |
| Personal info | Other info (شجرة العائلة والمناسبات) | نعم | نعم | App functionality |
| Location | Precise location | نعم | نعم | App functionality (مشاركة موقع يختارها المستخدم) |
| Messages | Other in-app messages | نعم | لا | App functionality |
| Photos and videos | Photos, Videos | نعم | نعم | App functionality |
| Audio | Voice or sound recordings | نعم | نعم | App functionality (الرسائل الصوتيّة) |
| Files and docs | Files and docs | نعم | نعم | App functionality |
| Calendar | Calendar events (المواعيد والتذكيرات) | نعم | نعم | App functionality |
| App activity | Other user-generated content (اللحظات، الاستطلاعات) | نعم | نعم | App functionality |
| App info and performance | Diagnostics (سجلّ جودة المكالمات) | نعم | لا | Analytics, App functionality |
| Device or other IDs | Device or other IDs (رمز FCM) | نعم | لا | App functionality |
| Contacts | Contacts | **لا** — تُقرأ على الجهاز فقط ولا تُرفع* | — | — |

\* تحقّقتُ من الكود: دفتر الهاتف يُعرض داخل التطبيق فقط ولا يُرفع؛ ما يُرسل هو بطاقة جهة اتّصال يختارها المستخدم داخل رسالة، وهذا مشمول في «Messages».
المكالمات نفسها (الصوت والصورة) لا تُسجّل ولا تُخزَّن، فلا تُعلن كبيانات مجموعة.

## الخطوة ٦ — الأذونات الحسّاسة (إقرارات)

**Foreground service permissions** ← لكلّ نوع اختر الاستعمال وأرفق **فيديو قصيراً** (YouTube «غير مدرج»):

| النوع في Manifest | الاستعمال المختار | ما يُصوَّر في الفيديو |
|---|---|---|
| `microphone` | **VoIP / calls** | مكالمة صوتيّة ← زرّ الرئيسيّة ← الإشعار «مكالمة جارية» ظاهر والصوت مستمرّ |
| `camera` | **Video calls** | مكالمة فيديو ← الخروج من التطبيق ← الإشعار ظاهر |
| `mediaProjection` | **Screen sharing (user-initiated)** | في غرفة ← «مشاركة الشاشة» ← نافذة إذن النظام ← المشاركة تعمل وإشعارها ظاهر |

فيديو واحد (٣٠–٦٠ ثانية) يغطّي الثلاثة إن أردت؛ صوّره بتسجيل الشاشة في الهاتف.

**أذونات أخرى:**

| الإذن | الحاجة لإقرار | ملاحظة |
|---|---|---|
| `USE_FULL_SCREEN_INTENT` | نعم: «Calling» | شاشة المكالمة الواردة على قفل الشاشة |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | قد يُسأل عنه | مقبول لتطبيقات المكالمات؛ إن رُفض نحذفه ونجعل الزرّ يفتح إعدادات البطّاريّة |
| `READ_CONTACTS` | لا | يُطلب فقط عند ضغط زرّ دعوة جهات الهاتف |
| `ACCESS_FINE_LOCATION` | لا | أثناء الاستعمال فقط، لا موقع في الخلفيّة |
| `CAMERA` / `RECORD_AUDIO` | لا | المكالمات والرسائل الصوتيّة |

## الخطوة ٧ — صفحة المتجر (Main store listing)

**Grow users ← Store presence ← Main store listing**:
1. انسخ الاسم والوصف القصير والكامل من `listing-ar.txt`.
2. **App icon:** `icon-512.png` · **Feature graphic:** `feature-graphic-1024x500.jpg`.
3. **Phone screenshots:** ٢ على الأقلّ (والأفضل ٤–٨)، عموديّة، بين 1080×1920 و1080×2400، PNG أو JPG حتّى 8 MB. خذها من هاتفك بحساب المراجعة (أسماء تجريبيّة لا بيانات أهلك):
   1. قائمة المحادثات · 2. محادثة · 3. مكالمة فيديو · 4. شجرة العائلة · 5. تذكير صلة الرحم · 6. غرفة اجتماع
4. **Translations ← Add ← English (en-US)** ← الصق `listing-en.txt`، والصور نفسها تكفي.

**Store settings:** الفئة **Communication** · البريد: `support@sawaaap.com` · الموقع: `https://www.sawaaap.com`.

## الخطوة ٨ — رفع AAB (الاختبار الداخليّ أوّلاً)

1. **Test and release ← Testing ← Internal testing ← Create new release**.
2. عند أوّل رفع يعرض **Play App Signing** ← **Continue / Use Google-generated key** (الافتراضيّ). يبقى مفتاحك في GitHub «مفتاح رفع» (upload key)، ولا تفقده.
3. ارفع `Sawa-2.0.N.aab` ← الصق «ما الجديد» من `release-notes.txt` ← Save ← Review ← **Start rollout**.
4. **Testers:** أنشئ قائمة بريدك وبريد من يجرّب ← انسخ رابط الانضمام وافتحه على الهاتف ← ثبّت من Play.

### ‼️ مباشرةً بعد أوّل رفع: بصمة Google

Google يوقّع النسخة النهائيّة بمفتاحٍ آخر، فيتوقّف الدخول بـ Google وفتح الروابط في التطبيق إن لم تُضف بصمته:

1. **Test and release ← Setup ← App signing** ← قسم «App signing key certificate» ← انسخ **SHA-1** و**SHA-256**.
2. **Firebase** ← Project settings ← تطبيق `com.sawaaap.app` ← **Add fingerprint** ← أضف الاثنتين (البصمات الحاليّة تبقى).
3. **Sawa-app** ← الملفّ `well-known/assetlinks.json` (المنشور في `/.well-known/assetlinks.json`) ← أضف SHA-256 الجديدة بجانب الحاليّة:
   ```json
   "sha256_cert_fingerprints": [
     "76:81:8E:EA:D3:7D:F4:47:26:2C:D9:FD:2B:5C:09:4A:E3:DA:2D:38:14:AD:74:57:0D:56:BD:D0:44:24:C9:D5",
     "<SHA-256 من Play App Signing>"
   ]
   ```
4. بعد النشر جرّب من نسخة Play: الدخول بـ Google، وفتح رابط `https://www.sawaaap.com/...` من واتساب.

> لا حاجة لتنزيل `google-services.json` من جديد بعد إضافة البصمات.

## الخطوة ٩ — الاختبار المغلق (للحساب الشخصيّ)

1. **Testing ← Closed testing ← Create track** (أو Alpha) ← انقل نفس الإصدار إليه (Promote).
2. أضف **١٢ مختبِراً على الأقلّ** (حسابات Gmail) عبر قائمة بريد أو Google Group.
3. أرسل لهم رابط الانضمام: يقبلون ← يثبّتون من Play ← **يبقون مشتركين ١٤ يوماً متّصلة** ويستعملون التطبيق فعلاً (محادثة، مكالمة).
4. بعد ١٤ يوماً يظهر في Dashboard زرّ **Apply for production** ← أجب الأسئلة (من المختبرون، ماذا تغيّر بناءً على ملاحظاتهم، لماذا التطبيق جاهز).

## الخطوة ١٠ — الإنتاج

1. **Production ← Countries/regions** ← اختر كلّ الدول (أو ابدأ بالسودان والخليج ومصر).
2. **Create new release** ← اختر الإصدار نفسه من المكتبة ← **Staged rollout 20%** ← Review ← Start.
3. المراجعة الأولى تأخذ عادةً ٣–٧ أيّام. تابع **Publishing overview** والبريد.
4. بعد يومين بلا أعطال ← ارفع النشر إلى 100%.

## التحديثات اللاحقة

- أغلب التعديلات في **Sawa-app** (الموقع) تصل فوراً دون رفعٍ جديد.
- عند تغيير `sawa-native.zip`: Run workflow ← نزّل الـ AAB الجديد ← Production ← Create new release ← ارفعه.

## إن رُفض التطبيق

| سبب شائع | الحلّ |
|---|---|
| Broken functionality / login | تأكّد من حساب المراجعة وأنّ كلمة المرور صحيحة |
| Foreground service | الفيديو لا يُظهر الإشعار ← أعد التصوير |
| Financial services | تأكّد أنّ حساب المراجعة عاديّ لا مشرف، وأنّ الموقع فيه C321 (sw v504 أو أحدث) |
| User-generated content | تأكّد من وجود الإبلاغ والحظر وأنّ طريقهما مكتوب في App access |
| Battery optimization | نحذف `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ونبني من جديد |
| Data safety mismatch | قارن الجدول بسياسة الخصوصيّة؛ يجب أن تتطابقا |

## قائمة التحقّق النهائيّة

- [ ] بناء c282: ✅ 16 KB · targetSdk 36
- [ ] حساب المراجعة مستخدمٌ عاديّ (لا يرى المحفظة) · الموقع على C321
- [ ] حساب المراجعة (بريد + كلمة مرور) يعمل
- [ ] App content: كلّ البنود خضراء
- [ ] فيديو الخدمات في المقدّمة على YouTube (غير مدرج)
- [ ] صفحة المتجر: النصوص + الأيقونة + الصورة الرئيسيّة + ٤ لقطات على الأقلّ (عربيّ وإنجليزيّ)
- [ ] بعد أوّل رفع: بصمة Play في Firebase و`assetlinks.json`
- [ ] الاختبار المغلق ١٤ يوماً بـ ١٢ مختبِراً (حساب شخصيّ)
- [ ] الإنتاج بنشرٍ تدريجيّ 20%
