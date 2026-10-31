package com.sawaaap.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.os.PowerManager;
import android.provider.Settings;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.view.Gravity;
import android.graphics.Color;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** سوا: واجهة www.sawaaap.com داخل WebView + جسرٌ أصليّ (SawaNativeBridge) للإشعارات والمكالمات والدخول بـGoogle. */
public class MainActivity extends AppCompatActivity {
    private static WeakReference<MainActivity> inst = new WeakReference<>(null);
    private static volatile boolean foreground = false;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int RC_MEDIA = 11, RC_NOTIF = 12, RC_GEO = 13, RC_CONTACTS = 14, RC_FILE = 21, RC_SCREEN = 31;
    private String pendingContactsCb;

    private WebView web;
    private FrameLayout root;
    AppLock lock;
    private boolean pageReady = false;
    private PermissionRequest pendingMedia;
    private GeolocationPermissions.Callback pendingGeoCb;
    private String pendingGeoOrigin;
    private ValueCallback<Uri[]> fileCb;

    static boolean isForeground() { return foreground && inst.get() != null; }

    /** تنفيذ JS في الواجهة من أيّ خيط (يُتجاهل إن لم يكن التطبيق حيّاً). */
    static void js(final String code) {
        MAIN.post(() -> {
            MainActivity a = inst.get();
            if (a != null && a.web != null) a.web.evaluateJavascript(code, null);
        });
    }

    static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        inst = new WeakReference<>(this);
        Notifier.channels(this);

        web = new WebView(this);
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        edgeToEdge();   // C115: Android 15/16 يرسم التطبيق تحت أشرطة النظام إجباريّاً
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }   // C115: Android 16 لا يستدعي onBackPressed
        });
        lock = new AppLock(this);
        Intent first = getIntent();
        if (first != null && first.getStringExtra("answer_call") != null) lock.callBypass = true;   // الردّ على مكالمة لا ينتظر القفل
        lock.onCreate();
        MAIN.postDelayed(() -> NativeRtc.warm(MainActivity.this), 3000);   // C113: أوّل مكالمة أسرع
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " SawaAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "SawaNativeBridge");
        // C99: عارض الصفحة يبقى «مهمّاً» والتطبيق في الخلفيّة (لا يُخفَّض فيُقتل أو يُجمَّد أثناء مكالمة)
        if (Build.VERSION.SDK_INT >= 26) { try { web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false); } catch (Throwable t) { } }
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost();
                if (("https".equals(scheme) || "http".equals(scheme)) && (host.equals("www.sawaaap.com") || host.equals("sawaaap.com"))) return false;
                if (("https".equals(scheme)) && (host.endsWith(".firebaseapp.com") || host.endsWith(".googleapis.com"))) return false;
                openExternal(u.toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverShare();
            }

            // C82: إن قتل النظام عارض الصفحة (ذاكرة) كانت الشاشة تبقى سوداء — نعيد بناء النشاط بدل ذلك
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                try { if (web != null) { root.removeView(web); web.destroy(); web = null; } } catch (Exception e) { }
                MAIN.post(MainActivity.this::recreate);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                MAIN.post(() -> handleMediaPermission(r));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) { cb.invoke(origin, true, false); return; }
                pendingGeoOrigin = origin; pendingGeoCb = cb;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RC_GEO);
            }

            // C140: ملء الشاشة للفيديو (زرّ ⛶ في مشغّل الفيديو) — كان WebView يتجاهله لأنّ onShowCustomView غير مُنفَّذ
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fsView != null) { cb.onCustomViewHidden(); return; }
                fsView = view; fsCb = cb; fsOrient = getRequestedOrientation();
                view.setBackgroundColor(Color.BLACK);
                root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                try {
                    WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), view);
                    c.hide(WindowInsetsCompat.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } catch (Exception ignored) { }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);   // يدور مع الهاتف (فيديو أفقيّ ⇒ الشاشة كلّها)
            }

            @Override
            public void onHideCustomView() { exitVideoFullscreen(); }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                lock.suppressNextStop = true;
                try { startActivityForResult(FileBridge.chooser(MainActivity.this, p), RC_FILE); }   // C89: كلّ الأنواع + متعدّد + كاميرا
                catch (ActivityNotFoundException e) { fileCb = null; lock.suppressNextStop = false; return false; }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
        }
        fetchToken();

        String start = urlFrom(getIntent());
        handleAnswerExtra(getIntent());
        if (FileBridge.isShare(getIntent())) importShareAsync(getIntent());   // C89: «مشاركة إلى سوا»
        new Thread(() -> FileBridge.cleanup(this)).start();
        web.loadUrl(start != null ? start : BuildConfig.HOME_URL);
    }

    private static String urlFrom(Intent i) {
        if (i == null || i.getData() == null) return null;
        Uri u = i.getData();
        String host = u.getHost() == null ? "" : u.getHost();
        if (!"https".equals(u.getScheme()) || !(host.equals("www.sawaaap.com") || host.equals("sawaaap.com"))) return null;
        return u.toString();
    }

    private void handleAnswerExtra(Intent i) {
        if (i == null) return;
        String ans = i.getStringExtra("answer_call");
        if (ans != null && !ans.isEmpty()) {
            Notifier.stopRinging(this, ans, true);
            if (lock != null && !lock.isCovering()) lock.callBypass = true;
            overLock(true);   // C142: «ردّ» والهاتف مقفل ⇒ المكالمة تظهر فوق شاشة القفل (كواتساب) بدل طلب فتح القفل أوّلاً
        }
    }
    @SuppressWarnings("deprecation")
    private void overLock(boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(on); setTurnScreenOn(on); }
            else if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } catch (Exception e) { }
    }

    // ---------- C142: «صورة داخل صورة» لمكالمات الفيديو والغرف ----------
    // الواجهة تبلغنا متى تكون مطلوبة (setPip) ⇒ الخروج من سوا (زرّ الرئيسيّة/تطبيقٌ آخر) يصغّرها نافذةً عائمة فوق التطبيقات الأخرى،
    // والصفحة تعرض فيها صورة المكالمة وحدها (window.__sawaPip). أندرويد ١٢+: تدخلها تلقائيّاً مع إيماءة الرئيسيّة.
    private volatile boolean pipWanted = false;
    private android.app.PictureInPictureParams pipParams() {
        android.app.PictureInPictureParams.Builder b = new android.app.PictureInPictureParams.Builder()
                .setAspectRatio(new android.util.Rational(3, 4));
        if (Build.VERSION.SDK_INT >= 31) { b.setAutoEnterEnabled(pipWanted); b.setSeamlessResizeEnabled(true); }
        return b.build();
    }
    private void setPipWanted(boolean on) {
        pipWanted = on;
        if (Build.VERSION.SDK_INT < 26) return;
        try { if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) setPictureInPictureParams(pipParams()); } catch (Exception e) { }
    }
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!pipWanted || Build.VERSION.SDK_INT < 26 || (Build.VERSION.SDK_INT >= 31)) return;   // ١٢+ تلقائيّ (setAutoEnterEnabled)
        try { if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) enterPictureInPictureMode(pipParams()); } catch (Exception e) { }
    }
    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (web != null) web.evaluateJavascript("window.__sawaPip&&window.__sawaPip(" + inPip + ")", null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAnswerExtra(intent);
        if (FileBridge.isShare(intent)) { importShareAsync(intent); return; }
        String url = urlFrom(intent);
        if (url == null) return;
        if (!pageReady) { web.loadUrl(url); return; }
        // الواجهة تعالج الرابط في مكانها (مكالمة/غرفة/محادثة) بلا إعادة تحميلٍ تقطع مكالمةً جارية
        web.evaluateJavascript("(window.__sawaNativeOpen&&window.__sawaNativeOpen(" + q(url) + "))?1:0", v -> {
            if (!"1".equals(v)) web.loadUrl(url);
        });
    }

    // C89: ما شورِك إلى سوا يُنسخ في خيطٍ خلفيّ ثمّ يُسلَّم للواجهة (أو تأخذه بنفسها عند جاهزيّتها)
    private void importShareAsync(final Intent i) {
        new Thread(() -> {
            String j = FileBridge.importShare(this, i);
            if (j != null) MAIN.post(this::deliverShare);
        }).start();
    }
    private void deliverShare() {
        if (!pageReady || web == null) return;
        web.evaluateJavascript("window.__sawaNativeShareIn?(window.__sawaNativeShareIn(),1):0", v -> { });
    }

    FrameLayout root() { return root; }

    @Override protected void onResume() { super.onResume(); foreground = true; inst = new WeakReference<>(this); if (lock != null) lock.onResume(); }
    @Override protected void onPause() { foreground = false; super.onPause(); }

    @Override
    protected void onStop() {
        super.onStop();
        if (lock != null) {
            if (lock.callBypass && !CallService.running) lock.callBypass = false;
            lock.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        setProximity(false);
        if (inst.get() == this) inst = new WeakReference<>(null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** C115: الواجهة داخل حدود أشرطة النظام ولوحة المفاتيح (هوامش من insets)، وشريط الحالة بلون سوا. */
    private View statusScrim;
    private void edgeToEdge() {
        try {
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.sawa_bg));
            statusScrim = new View(this);
            statusScrim.setBackgroundColor(ContextCompat.getColor(this, R.color.sawa_primary));
            root.addView(statusScrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                if (web != null) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
                    lp.setMargins(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                    web.setLayoutParams(lp);
                }
                FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) statusScrim.getLayoutParams();
                sp.height = bars.top; statusScrim.setLayoutParams(sp);
                return WindowInsetsCompat.CONSUMED;
            });
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), root);
            c.setAppearanceLightStatusBars(false);   // أيقونات بيضاء فوق الأزرق
            c.setAppearanceLightNavigationBars(true);
        } catch (Throwable t) { }
    }

    private View fsView;
    private WebChromeClient.CustomViewCallback fsCb;
    private int fsOrient = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private void exitVideoFullscreen() {
        if (fsView == null) return;
        View v = fsView; WebChromeClient.CustomViewCallback cb = fsCb;
        fsView = null; fsCb = null;
        try { root.removeView(v); } catch (Exception ignored) { }
        try { WindowCompat.getInsetsController(getWindow(), web).show(WindowInsetsCompat.Type.systemBars()); } catch (Exception ignored) { }
        setRequestedOrientation(fsOrient);
        if (cb != null) try { cb.onCustomViewHidden(); } catch (Exception ignored) { }
    }
    private void handleBack() {
        if (fsView != null) { exitVideoFullscreen(); return; }   // C140: الرجوع يخرج من ملء الشاشة أوّلاً
        if (lock != null && lock.isCovering()) { moveTaskToBack(true); return; }
        if (web == null) { moveTaskToBack(true); return; }
        // C99: الواجهة أوّلاً — تُغلق الورقة/الشاشة العليا (زرّ «رجوع» فيها). كان الرجوع من شاشةٍ بلا خطوة في السجلّ يُخرج من التطبيق
        web.evaluateJavascript("(function(){try{return window.__sawaBack&&window.__sawaBack()?1:0}catch(e){return 0}})()", v -> {
            if ("1".equals(v)) return;
            if (web != null && web.canGoBack()) { web.goBack(); return; }
            moveTaskToBack(true);   // كتطبيقات المكالمات: الرجوع لا يُغلق التطبيق
        });
    }

    // ---------- الأذونات ----------
    private boolean has(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    private void handleMediaPermission(PermissionRequest r) {
        List<String> need = new ArrayList<>();
        for (String res : r.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && !has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && !has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
        }
        if (need.isEmpty()) { r.grant(r.getResources()); return; }
        if (pendingMedia != null) { try { pendingMedia.deny(); } catch (Exception e) { } }
        pendingMedia = r;
        ActivityCompat.requestPermissions(this, need.toArray(new String[0]), RC_MEDIA);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == RC_MEDIA && pendingMedia != null) {
            PermissionRequest r = pendingMedia; pendingMedia = null;
            List<String> ok = new ArrayList<>();
            for (String x : r.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x) && has(Manifest.permission.RECORD_AUDIO)) ok.add(x);
                else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x) && has(Manifest.permission.CAMERA)) ok.add(x);
            }
            if (ok.isEmpty()) r.deny(); else r.grant(ok.toArray(new String[0]));
        } else if (code == RC_GEO && pendingGeoCb != null) {
            boolean g = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
            pendingGeoCb.invoke(pendingGeoOrigin, g, false);
            pendingGeoCb = null;
        } else if (code == RC_CONTACTS && pendingContactsCb != null) {
            String cb = pendingContactsCb; pendingContactsCb = null;
            if (has(Manifest.permission.READ_CONTACTS)) readContactsNow(cb);
            else {
                boolean never = !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS);
                js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + ",{\"ok\":false,\"denied\":true,\"never\":" + never + "})");
            }
        } else if (code == RC_NOTIF) {
            js("window.__sawaNativePerm&&window.__sawaNativePerm(" + q(notifState()) + ")");
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == RC_FILE && fileCb != null) {
            fileCb.onReceiveValue(FileBridge.result(this, result, data));
            fileCb = null;
        }
        if (code == RC_SCREEN) {   // C141: موافقة «بدء التسجيل/البثّ» من النظام
            if (result == RESULT_OK && data != null) ScreenService.start(this, result, data);
            else screenEvent("denied");
        }
    }

    // ---------- C141: مشاركة الشاشة (الإطارات من ScreenService إلى الواجهة) ----------
    private static volatile boolean scrInflight = false;
    private static volatile long scrSentAt = 0;
    /** الواجهة لم تنتهِ من الإطار السابق ⇒ لا نرسل غيره (حدٌّ أقصى ٢ ث احتياطاً) */
    static boolean scrBusy() { return scrInflight && SystemClock.uptimeMillis() - scrSentAt < 2000; }
    static void screenFrame(final String b64, final int w, final int h) {
        final MainActivity a = inst.get();
        if (a == null || a.web == null) return;
        scrInflight = true; scrSentAt = SystemClock.uptimeMillis();
        MAIN.post(() -> {
            try { a.web.evaluateJavascript("window.__sawaScrFrame&&window.__sawaScrFrame('" + b64 + "'," + w + "," + h + ")", v -> scrInflight = false); }
            catch (Exception e) { scrInflight = false; }
        });
    }
    static void screenEvent(final String st) {
        final MainActivity a = inst.get();
        if (a == null || a.web == null) return;
        MAIN.post(() -> { try { a.web.evaluateJavascript("window.__sawaScr&&window.__sawaScr('" + st + "')", null); } catch (Exception e) { } });
    }
    private void screenAsk() {
        if (ScreenService.inst != null) { screenEvent("started"); return; }
        try {
            android.media.projection.MediaProjectionManager mpm = (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm == null) { screenEvent("error"); return; }
            if (lock != null) lock.suppressNextStop = true;
            startActivityForResult(mpm.createScreenCaptureIntent(), RC_SCREEN);
        } catch (Exception e) { if (lock != null) lock.suppressNextStop = false; screenEvent("error"); }
    }

    private String notifState() {
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (enabled && (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))) return "granted";
        return Notifier.prefs(this).getBoolean("notif_asked", false) ? "denied" : "default";
    }

    private void fetchToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
                if (!t.isSuccessful() || t.getResult() == null) return;
                String tok = t.getResult();
                Notifier.prefs(this).edit().putString("fcm", tok).apply();
                js("window.__sawaNativeToken&&window.__sawaNativeToken(" + q(tok) + ")");
            });
        } catch (Exception e) { /* Firebase غير مهيّأ */ }
    }

    private PowerManager.WakeLock proxLock;
    boolean proximitySupported() {
        try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); return pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK); }
        catch (Exception e) { return false; }
    }
    void setProximity(boolean on) {
        try {
            if (on) {
                if (proxLock == null && proximitySupported()) {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    proxLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "sawa:proximity");
                    proxLock.setReferenceCounted(false);
                }
                if (proxLock != null && !proxLock.isHeld()) proxLock.acquire(4 * 60 * 60 * 1000L);
            } else if (proxLock != null && proxLock.isHeld()) {
                proxLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            }
        } catch (Exception e) { }
    }

    // C82: صفحات إعدادات الهاتف التي تقرّر هل يصل الإشعار والتطبيق مغلق
    private void openSetting(String kind) {
        String pkg = getPackageName();
        List<Intent> tries = new ArrayList<>();
        if ("battery".equals(kind)) {
            if (Build.VERSION.SDK_INT >= 23) tries.add(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg)));
            tries.add(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } else if ("fullscreen".equals(kind)) {
            if (Build.VERSION.SDK_INT >= 34) tries.add(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkg)));
        } else if ("notif".equals(kind)) {
            Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
            i.putExtra("android.provider.extra.APP_PACKAGE", pkg);
            tries.add(i);
        } else if ("calls".equals(kind)) {
            if (Build.VERSION.SDK_INT >= 26) {
                Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                i.putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_CALLS);
                tries.add(i);
            }
        } else if ("launch".equals(kind)) {
            // «تشغيل التطبيقات» في Honor/Huawei، و«التشغيل التلقائيّ» في Xiaomi/Oppo/Vivo
            String[][] comps = {
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"},
            };
            for (String[] c : comps) { Intent i = new Intent(); i.setComponent(new ComponentName(c[0], c[1])); tries.add(i); }
        }
        tries.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        for (Intent i : tries) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); lock.suppressNextStop = true; startActivity(i); return; } catch (Exception e) { lock.suppressNextStop = false; }
        }
    }

    // C88: قراءة جهات الهاتف في خيطٍ خلفيّ ثمّ إعادتها للواجهة (لا تُحفظ ولا تُرفع)
    private void readContactsNow(final String cb) {
        new Thread(() -> {
            String json;
            try {
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("list", PhoneContacts.read(MainActivity.this, 5000));
                json = o.toString();
            } catch (Exception e) { json = "{\"ok\":false}"; }
            js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + "," + json + ")");
        }).start();
    }

    // C92: «وضع الاتّصال» أثناء المكالمة — يُفعّل مُلغي الصدى ومزيل الضوضاء في عتاد الهاتف ويوجّه الصوت كمكالمة هاتفيّة
    //      (كان الصوت يعمل في الوضع العاديّ كوسائط: صدى وتكرارٌ للكلام على المكبّر في هواتف كثيرة)، مع تركيز الصوت لإيقاف الموسيقى
    private android.media.AudioFocusRequest focusReq;
    private boolean inCallAudio = false;
    private int prevCallVol = -1;
    private void callAudio(boolean on) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null || on == inCallAudio) return;
            inCallAudio = on;
            if (on) {
                am.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                // C96: أزرار الصوت تتحكّم بصوت المكالمة (لا الوسائط) + رفع صوت المكالمة إن كان أقلّ من النصف (يُعاد بعدها)
                setVolumeControlStream(android.media.AudioManager.STREAM_VOICE_CALL);
                try {
                    int mx = am.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL), v = am.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
                    prevCallVol = -1;
                    if (mx > 0 && v < mx / 2) { prevCallVol = v; am.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, Math.max(1, mx * 4 / 5), 0); }
                } catch (Throwable t) { }
                if (Build.VERSION.SDK_INT >= 26) {
                    focusReq = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()).build();
                    am.requestAudioFocus(focusReq);
                }
            } else {
                watchDevices(false); wantSpeaker = false;
                if (Build.VERSION.SDK_INT >= 31) { try { am.clearCommunicationDevice(); } catch (Throwable t) { } }
                else { stopSco(am); am.setSpeakerphoneOn(false); }
                try { if (prevCallVol >= 0) am.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, prevCallVol, 0); } catch (Throwable t) { }
                prevCallVol = -1;
                setVolumeControlStream(android.media.AudioManager.USE_DEFAULT_STREAM_TYPE);
                am.setMode(android.media.AudioManager.MODE_NORMAL);
                if (focusReq != null && Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(focusReq);
                focusReq = null;
            }
        } catch (Throwable t) { }
    }
    // C150: سمّاعات الرأس (سلكيّة/USB/بلوتوث) — كان «سمّاعة الأذن» يُفرض صراحةً (setCommunicationDevice) فيتجاوز السمّاعة الموصولة
    //       فلا يُسمع شيءٌ فيها. الآن: المكبّر عند طلبه · وإلّا سمّاعة الرأس إن وُجدت · وإلّا سمّاعة الأذن. ويُعاد عند توصيلها أو فصلها أثناء المكالمة.
    private boolean wantSpeaker = false;
    private android.media.AudioDeviceCallback devCb;
    private static final int[] HEADSETS = {
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            22 /* TYPE_USB_HEADSET */, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            26 /* TYPE_BLE_HEADSET */, 23 /* TYPE_HEARING_AID */ };
    private static boolean isHeadset(int t) { for (int h : HEADSETS) if (h == t) return true; return false; }
    private void setSpeaker(boolean on) {
        wantSpeaker = on;
        NativeRtc.speaker(on);   // C113
        applyRoute();
        watchDevices(true);
    }
    @SuppressWarnings("deprecation")
    private void applyRoute() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            // C177: بعض الهواتف (ومعها WebView حين يعيد فتح الميكروفون) تُرجع الوضع إلى «عاديّ» فيخرج الصوت وسائطَ من المكبّر
            //       مهما اخترنا سمّاعة الأذن — نعيد «وضع الاتّصال» قبل كلّ توجيه
            if (inCallAudio && am.getMode() != android.media.AudioManager.MODE_IN_COMMUNICATION) am.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.List<android.media.AudioDeviceInfo> devs = am.getAvailableCommunicationDevices();
                android.media.AudioDeviceInfo pick = null, ear = null, spk = null;
                for (android.media.AudioDeviceInfo d : devs) {
                    int t = d.getType();
                    if (t == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) spk = d;
                    else if (t == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) ear = d;
                    else if (isHeadset(t) && (pick == null || t != android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) pick = d;   // السلكيّة أوّلاً ثمّ البلوتوث
                }
                android.media.AudioDeviceInfo want = wantSpeaker ? spk : (pick != null ? pick : ear);
                if (want != null) am.setCommunicationDevice(want); else am.clearCommunicationDevice();
            } else {
                boolean wired = false, bt = false;
                for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
                    int t = d.getType();
                    if (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) bt = true;
                    else if (isHeadset(t)) wired = true;
                }
                if (wantSpeaker) { stopSco(am); am.setSpeakerphoneOn(true); }
                else if (bt && !wired) { am.setSpeakerphoneOn(false); if (!am.isBluetoothScoOn()) { am.startBluetoothSco(); am.setBluetoothScoOn(true); } }
                else { stopSco(am); am.setSpeakerphoneOn(false); }   // السلكيّة يوجّهها النظام تلقائيّاً
            }
        } catch (Throwable t) { }
    }
    @SuppressWarnings("deprecation")
    private void stopSco(android.media.AudioManager am) { try { if (am.isBluetoothScoOn()) { am.setBluetoothScoOn(false); am.stopBluetoothSco(); } } catch (Throwable t) { } }
    private void watchDevices(boolean on) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            if (on && devCb == null) {
                devCb = new android.media.AudioDeviceCallback() {
                    @Override public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] a) { if (inCallAudio) MAIN.postDelayed(MainActivity.this::applyRoute, 400); }
                    @Override public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] a) { if (inCallAudio) MAIN.postDelayed(MainActivity.this::applyRoute, 400); }
                };
                am.registerAudioDeviceCallback(devCb, MAIN);
            } else if (!on && devCb != null) { am.unregisterAudioDeviceCallback(devCb); devCb = null; }
        } catch (Throwable t) { }
    }
    /** للواجهة: «headset» إن كانت سمّاعة رأس موصولة (يظهر 🎧 بدل زرّ المكبّر/الأذن) */
    /** C177: المسار الفعليّ للتشخيص — الجهاز الذي يخرج منه صوت المكالمة ووضع الصوت (٣ = وضع الاتّصال) */
    @SuppressWarnings("deprecation")
    String audioRoute() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            String dev;
            if (Build.VERSION.SDK_INT >= 31) {
                android.media.AudioDeviceInfo d = am.getCommunicationDevice();
                int t = d == null ? -1 : d.getType();
                dev = t == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ? "earpiece" : t == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ? "speaker" : isHeadset(t) ? "headset" : ("type " + t);
            } else dev = am.isSpeakerphoneOn() ? "speaker" : am.isBluetoothScoOn() ? "bluetooth" : "earpiece";
            return dev + " · mode " + am.getMode();
        } catch (Throwable t) { return "?"; }
    }
    String audioOut() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) if (isHeadset(d.getType()) || d.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return "headset";
        } catch (Throwable t) { }
        return wantSpeaker ? "speaker" : "earpiece";
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { /* لا تطبيق يفتحه */ }
    }

    // ---------- الدخول بـGoogle أصليّاً (Google تمنع الدخول من داخل WebView) ----------
    private void googleSignIn(final String cb) {
        try {
            GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build();
            GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
            CredentialManager cm = CredentialManager.create(this);
            cm.getCredentialAsync(this, req, new CancellationSignal(), ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse r) {
                            Credential c = r.getCredential();
                            String token = null;
                            if (c instanceof CustomCredential && "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL".equals(c.getType())) {
                                token = c.getData().getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN");
                            }
                            reply(cb, token != null, token, token != null ? "" : "no-token");
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            reply(cb, false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            reply(cb, false, null, String.valueOf(e));
        }
    }

    private void reply(String cb, boolean ok, String token, String err) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            if (token != null) o.put("idToken", token);
            if (err != null && !err.isEmpty()) o.put("error", err);
            js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + "," + o.toString() + ")");
        } catch (Exception e) { }
    }

    /** الجسر الذي تراه الواجهة: window.SawaNativeBridge */
    class Bridge {
        /** C130b: الواجهة تُظهر «الألبومات» في صورة الحساب فقط حين يدعمها هذا البناء. */
        @JavascriptInterface public boolean albumsOk() { return true; }
        @JavascriptInterface public boolean screenOk() { return true; }
        @JavascriptInterface public void setPip(final boolean on) { MAIN.post(() -> setPipWanted(on)); }   // C142   // C141: مشاركة الشاشة في الغرف
        @JavascriptInterface public void screenStart() { MAIN.post(MainActivity.this::screenAsk); }
        @JavascriptInterface public void screenStop() { MAIN.post(() -> ScreenService.stop(MainActivity.this)); }
        @JavascriptInterface public String platform() { return "android/" + BuildConfig.VERSION_NAME; }
        @JavascriptInterface public String fcmToken() { return Notifier.prefs(MainActivity.this).getString("fcm", ""); }
        @JavascriptInterface public String notifPermission() { return notifState(); }

        @JavascriptInterface public void requestNotifPermission() {
            MAIN.post(() -> {
                Notifier.prefs(MainActivity.this).edit().putBoolean("notif_asked", true).apply();
                if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
                } else if (!NotificationManagerCompat.from(MainActivity.this).areNotificationsEnabled()) {
                    // محظورة من الإعدادات ⇒ نفتح صفحة إشعارات التطبيق
                    Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                    i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                    try { startActivity(i); } catch (Exception e) { }
                } else {
                    js("window.__sawaNativePerm&&window.__sawaNativePerm('granted')");
                }
                fetchToken();
            });
        }

        @JavascriptInterface public void googleSignIn(final String cb) { MAIN.post(() -> MainActivity.this.googleSignIn(cb)); }

        @JavascriptInterface public void setInCall(final boolean on, final String title) {
            MAIN.post(() -> {
                // C82: الشاشة تبقى مضاءة طوال المكالمة (كانت تُطفأ بمهلة الشاشة بعد ثوانٍ فتبدو سوداء)
                try { if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Exception e) { }
                if (on) CallService.start(MainActivity.this, title);
                else { CallService.stop(MainActivity.this); setProximity(false); if (lock != null) lock.callEnded(); overLock(false); setPipWanted(false); }
                callAudio(on);   // C92
            });
        }

        // C92: مكبّر الصوت ↔ سمّاعة الأذن من زرّ المكالمة (مع وضع الاتّصال)
        @JavascriptInterface public void setSpeaker(final boolean on) { MAIN.post(() -> MainActivity.this.setSpeaker(on)); }
        @JavascriptInterface public String audioOut() { return MainActivity.this.audioOut(); }   // C150
        @JavascriptInterface public String audioRoute() { return MainActivity.this.audioRoute(); }   // C177

        // C95: محرّك المكالمات الصوتيّة الأصليّ (NativeRtc) — سقفٌ لمخزن الصوت بدل تضخّمه في WebView
        @JavascriptInterface public int rtcVersion() { return 2; }
        @JavascriptInterface public void rtcAudioBr(String id, int bps) { NativeRtc.audioBitrate(id, bps); }   // C110
        @JavascriptInterface public boolean rtcMicOk() { return has(Manifest.permission.RECORD_AUDIO); }
        @JavascriptInterface public void rtcNew(String id, String iceJson, String cb) { NativeRtc.create(MainActivity.this, id, iceJson, cb); }
        @JavascriptInterface public void rtcAddAudio(String id) { NativeRtc.addAudio(id); }
        @JavascriptInterface public void rtcCreate(String id, boolean offer, boolean iceRestart, String cb) { NativeRtc.createSdp(id, offer, iceRestart, cb); }
        @JavascriptInterface public void rtcSet(String id, boolean local, String type, String sdp, String cb) { NativeRtc.setSdp(id, local, type, sdp, cb); }
        @JavascriptInterface public void rtcIce(String id, String json) { NativeRtc.addIce(id, json); }
        @JavascriptInterface public void rtcStats(String id, String cb) { NativeRtc.stats(id, cb); }
        @JavascriptInterface public void rtcMute(boolean m) { NativeRtc.setMuted(m); }
        @JavascriptInterface public void rtcClose(String id) { NativeRtc.close(id); }

        @JavascriptInterface public void openExternal(final String url) { MAIN.post(() -> MainActivity.this.openExternal(url)); }

        @JavascriptInterface public void callDone(final String callId) { Notifier.stopRinging(MainActivity.this, callId, true); }
        // C186: فتح المحادثة من داخل التطبيق يزيل إشعارها من شريط الإشعارات (المعرّف نفسه في Notifier.notifyMessage)
        @JavascriptInterface public void clearChatNotif(final String cid) {
            if (cid == null || cid.isEmpty()) return;
            try { NotificationManagerCompat.from(MainActivity.this).cancel(3000 + (cid.hashCode() & 0x0fff)); } catch (Exception e) { }
        }

        // C82: ما يمنع وصول المكالمات والتطبيق مغلق — للعرض في «فحص الإشعارات» مع أزرار الإصلاح
        @JavascriptInterface public String bgStatus() {
            try {
                JSONObject o = new JSONObject();
                o.put("notif", NotificationManagerCompat.from(MainActivity.this).areNotificationsEnabled());
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                    android.app.NotificationChannel ch = nm.getNotificationChannel(Notifier.CH_CALLS);
                    o.put("callsChannel", ch == null ? -1 : ch.getImportance());
                }
                if (Build.VERSION.SDK_INT >= 34 && nm != null) o.put("fullScreen", nm.canUseFullScreenIntent());
                else o.put("fullScreen", true);
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                o.put("battery", Build.VERSION.SDK_INT < 23 || (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())));
                o.put("maker", String.valueOf(Build.MANUFACTURER).toLowerCase());
                o.put("sdk", Build.VERSION.SDK_INT);
                o.put("fcm", Notifier.prefs(MainActivity.this).getString("fcm", "").length() > 0);
                android.content.SharedPreferences pp = Notifier.prefs(MainActivity.this);
                if (pp.getLong("lp_ts", 0) > 0) {   // C84: آخر إشعارٍ وصل فعلاً
                    JSONObject lp = new JSONObject();
                    lp.put("ts", pp.getLong("lp_ts", 0)); lp.put("t", pp.getString("lp_t", "")); lp.put("fg", pp.getBoolean("lp_fg", false));
                    lp.put("shown", pp.getBoolean("lp_shown", true)); lp.put("pri", pp.getInt("lp_pri", 0)); lp.put("n", pp.getInt("lp_n", 0));
                    o.put("lastPush", lp);
                }
                if (pp.getLong("lr_ts", 0) > 0) {   // C85: آخر مكالمةٍ واردة وكيف عُرضت (أو لماذا فشل عرضها)
                    JSONObject lr = new JSONObject();
                    lr.put("ts", pp.getLong("lr_ts", 0)); lr.put("mode", pp.getString("lr_mode", "")); lr.put("err", pp.getString("lr_err", "")); lr.put("fsi", pp.getBoolean("lr_fsi", true));
                    o.put("lastRing", lr);
                }
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        // C82: حسّاس القرب الحقيقيّ في المكالمة الصوتيّة على السمّاعة — الشاشة تنطفئ عند الأذن وتعود حين تبعد الهاتف
        //      (بدل «وضع الأذن» في الويب: شاشةٌ سوداء بعد ٥ ثوانٍ لا تعود إلّا بالضغط المطوّل)
        @JavascriptInterface public boolean setProximity(final boolean on) {
            MAIN.post(() -> MainActivity.this.setProximity(on));
            return proximitySupported();
        }

        // C84: قائمة المشاركة الأصليّة (واتساب، تيليجرام…) — WebView لا يعرف navigator.share
        @JavascriptInterface public void share(final String title, final String text) {
            MAIN.post(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                    if (title != null && !title.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, title);
                    startActivity(Intent.createChooser(i, title == null || title.isEmpty() ? "مشاركة" : title));
                } catch (Exception e) { }
            });
        }

        // C89: ملفٌّ من المحادثة ⇒ فتح/حفظ/مشاركة (قطع base64) · وما شورِك إلى سوا ⇒ تسحبه الواجهة قطعاً
        @JavascriptInterface public String fileBegin(String name, String mime) { return FileBridge.begin(MainActivity.this, name, mime); }
        @JavascriptInterface public boolean fileChunk(String id, String b64) { return FileBridge.chunk(id, b64); }
        @JavascriptInterface public void fileEnd(final String id, final String action, final String cb) {
            MAIN.post(() -> {
                if (!"save".equals(action)) lock.suppressNextStop = true;
                String r = FileBridge.end(MainActivity.this, id, action);
                js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + "," + r + ")");
            });
        }
        @JavascriptInterface public String shareInTake() { return FileBridge.take(); }
        @JavascriptInterface public String shareInRead(String id, int off, int len) { return FileBridge.read(id, off, len); }
        @JavascriptInterface public void shareInDone(String id) { FileBridge.done(id); }

        // C88: جهات الهاتف (بإذن READ_CONTACTS) + رسالة نصّيّة جاهزة
        @JavascriptInterface public void readContacts(final String cb) {
            MAIN.post(() -> {
                if (has(Manifest.permission.READ_CONTACTS)) { readContactsNow(cb); return; }
                pendingContactsCb = cb;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, RC_CONTACTS);
            });
        }

        @JavascriptInterface public void sms(final String phone, final String body) {
            MAIN.post(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone == null ? "" : phone)));
                    i.putExtra("sms_body", body == null ? "" : body);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
                        startActivity(Intent.createChooser(i, "إرسال"));
                    } catch (Exception e2) { }
                }
            });
        }

        @JavascriptInterface public void openSetting(final String kind) {
            MAIN.post(() -> MainActivity.this.openSetting(kind));
        }

        // C73: قفل التطبيق
        @JavascriptInterface public String appLockState() { return lock != null ? lock.stateJson(null) : "{}"; }

        @JavascriptInterface public void setAppLock(final boolean on, final double afterMs, final String cb) {
            MAIN.post(() -> lock.set(on, (long) afterMs, (ok, err) -> {
                try {
                    JSONObject o = new JSONObject(lock.stateJson(null));
                    o.put("ok", ok);
                    if (err != null) o.put("error", err);
                    js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + "," + o.toString() + ")");
                } catch (Exception e) { }
            }));
        }

        // C73: ماسح QR الأصليّ
        @JavascriptInterface public void scanQr(final String cb) {
            MAIN.post(() -> {
                lock.suppressNextStop = true;
                QrScan.start(MainActivity.this, (value, cancelled, err) -> {
                    lock.suppressNextStop = false;
                    try {
                        JSONObject o = new JSONObject();
                        o.put("ok", value != null);
                        if (value != null) o.put("value", value);
                        if (cancelled) o.put("cancelled", true);
                        if (err != null) o.put("error", err);
                        js("window.__sawaNativeCb&&window.__sawaNativeCb(" + q(cb) + "," + o.toString() + ")");
                    } catch (Exception e) { }
                });
            });
        }
    }
}
