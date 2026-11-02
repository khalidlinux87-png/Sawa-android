package com.sawaaap.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;

import java.util.Map;

/** قنوات الإشعارات وعرضها: المكالمة الواردة (رنينٌ متكرّر بملء الشاشة) · المكالمة الجارية · الرسائل. */
public final class Notifier {
    public static final String CH_CALLS = "sawa_calls_v1";
    public static final String CH_ONGOING = "sawa_ongoing_v1";
    public static final String CH_MSGS = "sawa_messages_v1";
    public static final int ID_ONGOING = 7001;
    static final String PREFS = "sawa";

    private Notifier() {}

    public static void channels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        final boolean en = !"ar".equals(java.util.Locale.getDefault().getLanguage());   // C129

        NotificationChannel calls = new NotificationChannel(CH_CALLS, tr(en, "المكالمات الواردة", "Incoming calls"), NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription(tr(en, "رنين المكالمات الواردة", "Ringing for incoming calls"));
        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        calls.setSound(ring, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        calls.enableVibration(true);
        calls.setVibrationPattern(new long[]{0, 800, 600, 800, 600});
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        calls.setBypassDnd(false);
        nm.createNotificationChannel(calls);

        NotificationChannel ongoing = new NotificationChannel(CH_ONGOING, tr(en, "المكالمة الجارية", "Ongoing call"), NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription(tr(en, "تُبقي المكالمة حيّةً حين تخرج من التطبيق", "Keeps the call alive when you leave the app"));
        ongoing.setSound(null, null);
        nm.createNotificationChannel(ongoing);

        NotificationChannel msgs = new NotificationChannel(CH_MSGS, tr(en, "الرسائل", "Messages"), NotificationManager.IMPORTANCE_HIGH);
        msgs.setDescription(tr(en, "الرسائل والطلبات", "Messages and requests"));
        msgs.enableVibration(true);
        nm.createNotificationChannel(msgs);
    }

    // C142: شعار سوا الملوّن بجانب الإشعار (الأيقونة الصغيرة أحاديّة اللون بشرط النظام) + لون العلامة
    private static android.graphics.Bitmap LOGO;
    static android.graphics.Bitmap logo(Context ctx) {
        // C144: صورةٌ مخصّصة ٢٥٦ بكسل (drawable-nodpi: بلا تصغيرٍ حسب كثافة الشاشة) — الشعار داخل دائرةٍ بيضاء بهامش،
        //       فالنظام يقصّها دائرةً دون أن يقطع أطراف الشعار (كان شعار sawa_logo يلامس الحواف ويُصغَّر مرّتين فيبدو باهتاً متكسّراً)
        if (LOGO == null) {
            try { android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inScaled = false;
                  LOGO = android.graphics.BitmapFactory.decodeResource(ctx.getResources(), R.drawable.notif_logo, o); } catch (Throwable t) { }
        }
        return LOGO;
    }
    static int brand(Context ctx) { try { return androidx.core.content.ContextCompat.getColor(ctx, R.color.sawa_primary); } catch (Throwable t) { return 0xFF2F80C8; } }

    static int flagsImmutable() { return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE; }

    static PendingIntent openUrl(Context ctx, String url, int req) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, req, i, flagsImmutable());
    }

    /** «ردّ»: يفتح التطبيق مباشرةً (أندرويد ١٢+ يمنع فتح نشاطٍ عبر مستقبِلٍ وسيط) ومعه رقم المكالمة ليقبلها تلقائيّاً. */
    static PendingIntent answer(Context ctx, String callId, int req) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(callId.startsWith("test_") ? "https://www.sawaaap.com/?calltest=1" : "https://www.sawaaap.com/?call=" + Uri.encode(callId) + "&answer=1"));
        i.putExtra("answer_call", callId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, req, i, flagsImmutable());
    }

    static String s(Map<String, String> d, String k) { String v = d.get(k); return v == null ? "" : v; }
    /** C129: لغة نصوص الإشعارات = لغة التطبيق. الخادم يضع lang=en في بيانات الإشعار لمن اختار الإنجليزيّة؛ نحفظها
     *  لنصوص لا تصلها بيانات (شاشة المكالمة الواردة · المكالمة الجارية). بلا قيمة محفوظة ⇒ لغة الهاتف. */
    static boolean en(Context ctx, Map<String, String> d) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String l = d == null ? "" : s(d, "lang");
        if (!l.isEmpty() || (d != null && !s(d, "t").isEmpty())) {
            String v = "en".equals(l) ? "en" : "ar";
            if (!v.equals(p.getString("lang", ""))) p.edit().putString("lang", v).apply();
            return "en".equals(v);
        }
        String v = p.getString("lang", "");
        if (!v.isEmpty()) return "en".equals(v);
        return !"ar".equals(java.util.Locale.getDefault().getLanguage());
    }
    static String tr(boolean en, String ar, String eng) { return en ? eng : ar; }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static int callNotifId(String callId) { return 5000 + (callId.hashCode() & 0x0fff); }

    /** مكالمة واردة: إشعار مكالمة (CallStyle) + شاشة كاملة فوق القفل + نغمة الرنين تتكرّر حتى الردّ/الرفض/انتهاء المهلة.
     *  C85: كان أيّ استثناءٍ هنا يُسقط الإشعار كلّه بصمت (التجريبيّ يصل والمكالمة لا):
     *  أندرويد ١٤+ يرفض إشعار CallStyle بلا شاشةٍ كاملة مسموحة ولا خدمة أماميّة (IllegalArgumentException)،
     *  و notify() كانت تلتقط SecurityException وحدها. الآن ثلاث درجات: CallStyle ⇐ إشعار رنينٍ عاديّ بزرّين ⇐ إشعار رسالة. */
    public static void ringIncoming(Context ctx, Map<String, String> d) {
        String callId = s(d, "call");
        if (callId.isEmpty()) return;
        SharedPreferences p = prefs(ctx);
        // تكرار الخادم كلّ ٨ ثوانٍ: إن كانت ترنّ أصلاً أو رفضناها أو أُجيبت ⇒ لا شيء
        if (p.getBoolean("done_" + callId, false)) return;
        if (callId.equals(p.getString("ringing", ""))) return;
        // C142: توقيع الخادم لهذه المكالمة ⇒ «رفض» يبلغه مباشرةً ولو كان التطبيق مغلقاً
        if (!s(d, "rk").isEmpty()) p.edit().putString("rk_" + callId, s(d, "rk")).putString("rto_" + callId, s(d, "to")).apply();
        try { channels(ctx); } catch (Throwable t) { }
        String mode = "callstyle", err = "";
        boolean fsi = canFullScreen(ctx);
        try {
            postRing(ctx, d, callId, fsi, fsi);
        } catch (Throwable t1) {
            err = shortErr(t1);
            mode = "plain";
            try { postRing(ctx, d, callId, false, fsi); }
            catch (Throwable t2) {
                err += " | " + shortErr(t2);
                mode = "message";
                try { message(ctx, d, tr(en(ctx, d), "📞 مكالمة واردة — اضغط للردّ", "📞 Incoming call — tap to answer")); } catch (Throwable t3) { mode = "none"; err += " | " + shortErr(t3); }
            }
        }
        p.edit().putString("ringing", callId).putLong("lr_ts", System.currentTimeMillis()).putString("lr_mode", mode)
                .putString("lr_err", err).putBoolean("lr_fsi", fsi).apply();
    }

    static boolean canFullScreen(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        } catch (Throwable t) { return false; }
    }

    static String shortErr(Throwable t) {
        String m = t.getClass().getSimpleName() + ": " + t.getMessage();
        return m.length() > 160 ? m.substring(0, 160) : m;
    }

    private static void postRing(Context ctx, Map<String, String> d, String callId, boolean callStyle, boolean fsi) {
        final boolean en = en(ctx, d);
        String name = s(d, "title").isEmpty() ? tr(en, "سوا", "Sawa") : s(d, "title");
        boolean video = "video".equals(s(d, "type"));
        int nid = callNotifId(callId);

        Intent full = new Intent(ctx, IncomingCallActivity.class);
        full.putExtra("call", callId);
        full.putExtra("name", name);
        full.putExtra("video", video);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(ctx, nid, full, flagsImmutable());

        PendingIntent answerPi = answer(ctx, callId, nid + 1);
        PendingIntent declinePi = CallActionReceiver.pending(ctx, CallActionReceiver.ACT_DECLINE, callId, nid + 2);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_CALLS)
                .setSmallIcon(R.drawable.ic_stat_sawa)
                .setLargeIcon(logo(ctx))
                .setColor(brand(ctx))
                .setContentTitle(name)
                .setContentText(video ? tr(en, "📹 مكالمة فيديو واردة", "📹 Incoming video call") : tr(en, "📞 مكالمة صوتيّة واردة", "📞 Incoming voice call"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(50000)
                .setContentIntent(fsi ? fullPi : answerPi);
        if (fsi) b.setFullScreenIntent(fullPi, true);
        if (callStyle) {
            Person.Builder pb = new Person.Builder().setName(name).setImportant(true);
            if (logo(ctx) != null) pb.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(logo(ctx)));
            Person caller = pb.build();
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi));
        } else {
            b.addAction(0, tr(en, "رفض", "Decline"), declinePi);
            b.addAction(0, video ? tr(en, "ردّ 📹", "Answer 📹") : tr(en, "ردّ 📞", "Answer 📞"), answerPi);
        }
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;   // النغمة تتكرّر حتى يُزال الإشعار
        NotificationManagerCompat.from(ctx).notify(nid, n);   // بلا التقاط: الفشل يُنزل إلى الدرجة التالية
    }

    /** أوقف الرنين (ردّ/رفض/ألغاها المتّصل). */
    public static void stopRinging(Context ctx, String callId, boolean done) {
        if (callId == null || callId.isEmpty()) return;
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        if (callId.equals(p.getString("ringing", ""))) e.remove("ringing");
        if (done) e.putBoolean("done_" + callId, true);
        e.apply();
        NotificationManagerCompat.from(ctx).cancel(callNotifId(callId));
        IncomingCallActivity.finishFor(callId);
    }

    public static void missed(Context ctx, Map<String, String> d) {
        String callId = s(d, "call");
        stopRinging(ctx, callId, true);
        message(ctx, d, tr(en(ctx, d), "📞 مكالمة فائتة", "📞 Missed call"));
    }

    /** رسالة/طلب/اختبار: إشعارٌ عاديّ بصوت القناة، والنقر يفتح المحادثة. */
    public static void message(Context ctx, Map<String, String> d, String fallbackBody) {
        channels(ctx);
        String t = s(d, "t"), cid = s(d, "cid");
        String title = s(d, "title").isEmpty() ? tr(en(ctx, d), "سوا", "Sawa") : s(d, "title");
        String body = s(d, "body").isEmpty() ? fallbackBody : s(d, "body");
        String url = "https://www.sawaaap.com/";
        String pid = s(d, "pid");
        if ("req".equals(t)) url += "?requests=1";
        else if ("moment".equals(t) && !pid.isEmpty()) url += "?moment=" + Uri.encode(pid) + (s(d, "cm").isEmpty() ? "" : "&cm=" + Uri.encode(s(d, "cm")));   // C135
        else if (!cid.isEmpty()) url += "?chat=" + Uri.encode(cid);
        if (!cid.isEmpty() && MainActivity.isOpenChat(cid)) return;   // C227: المحادثة مفتوحةٌ أمامك ⇒ لا إشعار
        int nid = cid.isEmpty() ? 3000 + (("moment".equals(t) ? t + pid : t).hashCode() & 0x0fff) : chatNotifId(cid);
        android.graphics.Bitmap av = avatar(ctx, d);   // C152: صورة المرسِل (كإشعارات انستجرام) — وإلّا شعار سوا
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_MSGS)
                .setSmallIcon(R.drawable.ic_stat_sawa)
                .setLargeIcon(av != null ? av : logo(ctx))
                .setColor(brand(ctx))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openUrl(ctx, url, nid));
        notify(ctx, nid, b.build());
    }

    /** C152: صورة حساب المرسِل دائريّةً للإشعار. الخادم يوقّع (ak) رابط الصورة ‎/av‎ فلا يُطلب إلّا ما وصل في إشعار.
     *  تُحفظ في ذاكرة التخزين المؤقّت ٦ ساعات · مهلة ٤ ثوانٍ · أيّ فشل ⇒ null (يبقى الشعار). يعمل في خيط FCM (لا الواجهة). */
    static android.graphics.Bitmap avatar(Context ctx, Map<String, String> d) {
        String u = s(d, "uf"), k = s(d, "ak");
        if (u.isEmpty() || !k.matches("[0-9a-f]{32}") || !u.matches("[A-Za-z0-9_-]{1,128}")) return null;
        java.io.File f = new java.io.File(ctx.getCacheDir(), "av_" + u + ".png");
        try {
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600000L) {
                android.graphics.Bitmap c = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if (c != null) return c;
            }
        } catch (Throwable t) { }
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(SawaMessagingService.AV_URL + "?u=" + Uri.encode(u) + "&s=" + k).openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(4000);
            if (c.getResponseCode() != 200) return null;
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n, tot = 0;
            while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); tot += n; if (tot > 400000) return null; }
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeByteArray(bo.toByteArray(), 0, bo.size());
            if (src == null) return null;
            android.graphics.Bitmap out = circle(src, 192);
            try (java.io.FileOutputStream fo = new java.io.FileOutputStream(f)) { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fo); } catch (Throwable t) { }
            return out;
        } catch (Throwable t) { return null; }
        finally { if (c != null) c.disconnect(); }
    }

    static android.graphics.Bitmap circle(android.graphics.Bitmap src, int size) {
        int w = src.getWidth(), h = src.getHeight(), m = Math.min(w, h);
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        cv.drawBitmap(src, new android.graphics.Rect((w - m) / 2, (h - m) / 2, (w - m) / 2 + m, (h - m) / 2 + m), new android.graphics.Rect(0, 0, size, size), p);
        return out;
    }

    /** إشعار «مكالمة جارية» الثابت (تستعمله CallService). */
    public static Notification ongoing(Context ctx, String title) {
        channels(ctx);
        PendingIntent back = openUrl(ctx, "https://www.sawaaap.com/", ID_ONGOING);
        PendingIntent end = CallActionReceiver.pending(ctx, CallActionReceiver.ACT_HANGUP, "", ID_ONGOING + 1);
        return new NotificationCompat.Builder(ctx, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_sawa)
                .setColor(brand(ctx))
                .setContentTitle(title == null || title.isEmpty() ? tr(en(ctx, null), "مكالمة جارية", "Ongoing call") : title)
                .setContentText(tr(en(ctx, null), "مكالمة جارية في سوا — اضغط للعودة", "Ongoing Sawa call — tap to return"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(back)
                .addAction(0, tr(en(ctx, null), "العودة", "Return"), back)
                .addAction(0, tr(en(ctx, null), "إنهاء", "End"), end)
                .build();
    }

    /** C227: معرّف إشعار المحادثة — يُزال به عند فتحها داخل التطبيق. */
    static int chatNotifId(String cid) { return 3000 + (cid.hashCode() & 0x0fff); }
    static void notify(Context ctx, int id, Notification n) {
        try { NotificationManagerCompat.from(ctx).notify(id, n); } catch (SecurityException e) { /* لا إذن إشعارات */ }
    }
}
