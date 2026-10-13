# سوا — تطبيق أندرويد (TWA)

يبني هذا المستودع ملفّ APK وAAB لتطبيق «سوا» بطريقة **Trusted Web Activity**: التطبيق يفتح www.sawaaap.com داخل Chrome نفسه بلا شريط عنوان، ويستهدف أحدث أندرويد فلا يحجبه Play Protect، وهو الشكل الذي يقبله Google Play. البناء يجري على خوادم GitHub — لا تحتاج حاسوباً.

## الإعداد (مرّة واحدة)
1. **أنشئ المستودع** `Sawa-android` (خاصّ) وارفع إليه: `twa-manifest.json` و`README.md`، ثمّ أنشئ ملفّاً باسم المسار الكامل `.github/workflows/build.yml` والصق محتواه.
2. **السرّ الأوّل:** Settings ← Secrets and variables ← Actions ← New repository secret:
   - `KEYSTORE_PASSWORD` = كلمة مرور قويّة تختارها (احفظها في مكانٍ آمن — لا تُستعاد).
3. **شغّل البناء:** تبويب Actions ← «Build Sawa Android» ← Run workflow. ينتهي خلال ٥–٨ دقائق.
4. **من نتيجة التشغيل (Artifacts)** نزّل `sawa-android-1` وفيه:
   - `sawa-1.apk` — ثبّته على هاتفك (بعد حذف الـAPK القديم).
   - `assetlinks.json` — ارفعه إلى مستودع **Sawa-app** في المسار `.well-known/assetlinks.json` (أنشئ ملفّاً جديداً بهذا المسار). بدونه يظهر شريط عنوان Chrome داخل التطبيق.
   - `keystore-b64.txt` — افتحه وانسخ كلّ نصّه إلى سرّ جديد باسم `KEYSTORE_B64`. **هذه الخطوة ضروريّة**: بدونها يُنشأ مفتاحٌ مختلف في كلّ بناء فيرفض أندرويد التحديث فوق النسخة المثبَّتة.
5. احتفظ بنسخةٍ من `keystore-b64.txt` وكلمة المرور في مكانٍ آمن خارج GitHub، ثمّ احذفه من هاتفك.

## اختياريّ: أتمتة الخطوتين الأخيرتين
أنشئ رمز وصول Fine-grained (Settings الحساب ← Developer settings ← Personal access tokens ← Fine-grained) بصلاحيّات: على `Sawa-android`: **Secrets: Read and write** · على `Sawa-app`: **Contents: Read and write**، وضعه في سرّ باسم `GH_PAT`. عندها يحفظ البناء `KEYSTORE_B64` بنفسه ويرفع `assetlinks.json` إلى Sawa-app تلقائيّاً.

## كلّ بناءٍ لاحق
Actions ← Run workflow. رقم الإصدار يزيد تلقائيّاً (1.0.N). الـAPK للتثبيت المباشر، والـAAB لرفعه على Google Play Console.

## ملاحظات
- التطبيق يعرض الموقع الحيّ، فكلّ تحديثٍ ترفعه إلى Sawa-app يصل إلى التطبيق فوراً دون إعادة بناء. أعد البناء فقط عند تغيير الاسم أو الأيقونة أو الألوان في `twa-manifest.json`.
- المفتاح (`signing.keystore`) لا يُحفظ في أيّ مستودع؛ يعيش في الأسرار فقط.
- Google Play: عند أوّل رفع فعّل **Play App Signing** (الافتراضيّ) وارفع الـAAB؛ Google يعطيك بصمة توقيعٍ جديدة — أضفها بصمةً ثانية في `assetlinks.json` (المصفوفة `sha256_cert_fingerprints` تقبل أكثر من بصمة).
