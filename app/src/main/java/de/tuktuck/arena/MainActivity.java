package de.tuktuck.arena;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Arena – schlanker, stabiler WebView-Client für arena.ai.
 * Optimiert für Geräte bis Android 7, läuft ab Android 4.4.
 */
public class MainActivity extends Activity {

    public static final String HOME_URL = "https://arena.ai/";

    private static final String PREFS = "arena";
    private static final String JS_BRIDGE = "ArenaApp";
    private static final int REQ_FILE = 2001;
    private static final int ZOOM_MIN = 50;
    private static final int ZOOM_MAX = 300;

    /** Dunkler Modus für die Webseite (invertierter Filter, bewusst einfach gehalten). */
    static final String JS_DARK =
            "(function(){try{"
                    + "var old=document.getElementById('arena-dark-style');"
                    + "if(old&&old.parentNode){old.parentNode.removeChild(old);}"
                    + "var s=document.createElement('style');s.id='arena-dark-style';"
                    + "s.textContent='html{background:#111 !important;filter:invert(0.92) hue-rotate(180deg);}"
                    + "html img,html video,html canvas,html embed,html iframe,html object{filter:invert(1) hue-rotate(180deg);}';"
                    + "(document.head||document.documentElement).appendChild(s);}catch(e){}})();";

    /** target=\"_blank\"-Links im selben Fenster öffnen (z. B. Login-Popups). */
    static final String JS_KEEP_BLANK =
            "(function(){try{"
                    + "document.addEventListener('click',function(e){"
                    + "var t=e.target;while(t&&t.tagName!=='A'){t=t.parentNode;if(!t||t===document){t=null;break;}}"
                    + "if(t&&t.getAttribute('target')==='_blank'){t.setAttribute('target','_self');}"
                    + "},true);}catch(e){}})();";

    private WebView web;
    private ProgressBar progressBar;
    private ImageView btnBack;
    private ImageView btnForward;
    private ImageView btnReload;
    private TextView btnZoomOut;
    private TextView btnZoomIn;
    private TextView btnMenu;
    private View toolbar;
    private SharedPreferences prefs;

    private ValueCallback<Uri[]> fileCallback;
    private ValueCallback<Uri> legacyFileCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String pendingRetryUrl;
    private JsBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecovery();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        btnBack = (ImageView) findViewById(R.id.btn_back);
        btnForward = (ImageView) findViewById(R.id.btn_forward);
        btnReload = (ImageView) findViewById(R.id.btn_reload);
        btnZoomOut = (TextView) findViewById(R.id.btn_zoom_out);
        btnZoomIn = (TextView) findViewById(R.id.btn_zoom_in);
        btnMenu = (TextView) findViewById(R.id.btn_menu);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web != null && web.canGoBack()) {
                    web.goBack();
                }
            }
        });
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web != null && web.canGoForward()) {
                    web.goForward();
                }
            }
        });
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadCurrent();
            }
        });
        btnZoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeZoom(-25);
            }
        });
        btnZoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeZoom(25);
            }
        });
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        setupWebView();
        web.getSettings().setTextZoom(prefs.getInt("zoom", 100));
        applyUserAgent();

        boolean restored = false;
        if (savedInstanceState != null) {
            Bundle webState = savedInstanceState.getBundle("webstate");
            if (webState != null) {
                restored = web.restoreState(webState) != null;
            }
        }
        if (!restored) {
            String url = null;
            if (getIntent() != null && getIntent().getData() != null) {
                url = getIntent().getDataString();
            }
            loadMain(url != null ? url : HOME_URL);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("crashed", false)) {
            Toast.makeText(this, R.string.toast_restored, Toast.LENGTH_LONG).show();
        }
        updateNavState();
    }

    /** Bei unerwarteten Abstürzen die Seite automatisch wiederherstellen. */
    private void installCrashRecovery() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    SharedPreferences p =
                            getSharedPreferences(PREFS, MODE_PRIVATE);
                    long last = p.getLong("last_crash", 0);
                    long now = System.currentTimeMillis();
                    if (now - last > 15000) {
                        p.edit().putLong("last_crash", now).commit();
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        i.putExtra("crashed", true);
                        startActivity(i);
                    }
                } catch (Throwable ignored) {
                }
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(t, e);
                }
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "ObsoleteSdkInt"})
    private void setupWebView() {
        web = (WebView) findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new ArenaWebViewClient(this));
        web.setWebChromeClient(new ArenaChromeClient(this));
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    if (mimeType != null) {
                        req.setMimeType(mimeType);
                    }
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) {
                        req.addRequestHeader("Cookie", cookies);
                    }
                    if (userAgent != null) {
                        req.addRequestHeader("User-Agent", userAgent);
                    }
                    req.setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(req);
                    Toast.makeText(MainActivity.this, R.string.toast_download,
                            Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, R.string.toast_download_error,
                            Toast.LENGTH_SHORT).show();
                    openExternally(url);
                }
            }
        });
    }

    // ------------------------------------------------------------------ Laden

    void loadMain(String url) {
        pendingRetryUrl = url;
        removeJsBridge();
        web.loadUrl(url);
    }

    void retryLoad() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isOnline()) {
                    loadMain(pendingRetryUrl != null ? pendingRetryUrl : HOME_URL);
                } else {
                    showOfflinePage();
                }
            }
        });
    }

    private void reloadCurrent() {
        if (!isOnline()) {
            showOfflinePage();
            return;
        }
        String u = web.getUrl();
        if (u != null && u.startsWith("file:///android_asset/")) {
            loadMain(pendingRetryUrl != null ? pendingRetryUrl : HOME_URL);
        } else {
            web.reload();
        }
    }

    private void showOfflinePage() {
        addJsBridge();
        web.loadUrl("file:///android_asset/html/offline.html");
    }

    private void showErrorPage(boolean cert) {
        addJsBridge();
        web.loadUrl("file:///android_asset/html/error.html" + (cert ? "?cert=1" : ""));
    }

    void onMainFrameError(String url, boolean cert) {
        if (url != null && url.startsWith("http")) {
            pendingRetryUrl = url;
        }
        showErrorPage(cert);
    }

    void onRendererGone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recreate();
            }
        });
    }

    boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    private void addJsBridge() {
        if (bridge == null) {
            bridge = new JsBridge(this);
        }
        web.addJavascriptInterface(bridge, JS_BRIDGE);
    }

    private void removeJsBridge() {
        web.removeJavascriptInterface(JS_BRIDGE);
    }

    // ------------------------------------------------------------------ UI-Rückmeldungen (aus Clients)

    void onProgressUi(int progress) {
        if (progress >= 100) {
            progressBar.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(progress);
        }
    }

    void onPageStartedUi(String url) {
        removeJsBridge();
        updateNavState();
    }

    void onPageFinishedUi(String url) {
        updateNavState();
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            if (isDarkMode()) {
                web.evaluateJavascript(JS_DARK, null);
            }
            web.evaluateJavascript(JS_KEEP_BLANK, null);
        }
    }

    void onHistoryChanged() {
        updateNavState();
    }

    void onTitleUi(String title) {
        if (title != null && title.length() > 0) {
            setTitle(title);
        } else {
            setTitle(R.string.app_name);
        }
    }

    private void updateNavState() {
        boolean canBack = web != null && web.canGoBack();
        btnBack.setEnabled(canBack);
        btnBack.setAlpha(canBack ? 1f : 0.3f);
        boolean canFwd = web != null && web.canGoForward();
        btnForward.setEnabled(canFwd);
        btnForward.setAlpha(canFwd ? 1f : 0.3f);
    }

    private void changeZoom(int delta) {
        int cur = web.getSettings().getTextZoom() + delta;
        if (cur < ZOOM_MIN) {
            cur = ZOOM_MIN;
        }
        if (cur > ZOOM_MAX) {
            cur = ZOOM_MAX;
        }
        web.getSettings().setTextZoom(cur);
        prefs.edit().putInt("zoom", cur).commit();
        Toast.makeText(this, getString(R.string.toast_zoom, cur), Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ Menü

    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, btnMenu);
        popup.getMenuInflater().inflate(R.menu.main, popup.getMenu());
        popup.getMenu().findItem(R.id.menu_desktop).setChecked(isDesktop());
        popup.getMenu().findItem(R.id.menu_dark).setChecked(isDarkMode());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_reload) {
                    reloadCurrent();
                } else if (id == R.id.menu_browser) {
                    String u = web.getUrl();
                    openExternally(u != null ? u : HOME_URL);
                } else if (id == R.id.menu_share) {
                    shareLink();
                } else if (id == R.id.menu_desktop) {
                    toggleDesktop();
                } else if (id == R.id.menu_dark) {
                    toggleDark();
                } else if (id == R.id.menu_zoom_reset) {
                    web.getSettings().setTextZoom(100);
                    prefs.edit().putInt("zoom", 100).commit();
                } else if (id == R.id.menu_clear) {
                    confirmClear();
                } else if (id == R.id.menu_about) {
                    showAbout();
                } else {
                    return false;
                }
                return true;
            }
        });
        popup.show();
    }

    private void toggleDesktop() {
        boolean on = !isDesktop();
        prefs.edit().putBoolean("desktop", on).commit();
        applyUserAgent();
        reloadCurrent();
        Toast.makeText(this, on ? R.string.toast_desktop_on : R.string.toast_desktop_off,
                Toast.LENGTH_SHORT).show();
    }

    private void toggleDark() {
        boolean on = !isDarkMode();
        prefs.edit().putBoolean("dark", on).commit();
        reloadCurrent();
        Toast.makeText(this, on ? R.string.toast_dark_on : R.string.toast_dark_off,
                Toast.LENGTH_SHORT).show();
    }

    private boolean isDarkMode() {
        return prefs.getBoolean("dark", false);
    }

    private boolean isDesktop() {
        return prefs.getBoolean("desktop", false);
    }

    private void applyUserAgent() {
        if (isDesktop()) {
            web.getSettings().setUserAgentString(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36");
        } else {
            web.getSettings().setUserAgentString(null);
        }
    }

    private void shareLink() {
        String url = web.getUrl();
        if (url == null || url.startsWith("file:")) {
            url = HOME_URL;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        String title = web.getTitle();
        i.putExtra(Intent.EXTRA_TEXT, (title != null ? title + " – " : "") + url);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_text)
                .setPositiveButton(R.string.clear_confirm_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doClear();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void doClear() {
        try {
            web.clearCache(true);
            web.clearHistory();
            web.clearFormData();
            WebStorage.getInstance().deleteAllData();
            CookieManager cm = CookieManager.getInstance();
            if (Build.VERSION.SDK_INT >= 21) {
                cm.removeAllCookies(null);
            } else {
                CookieSyncManager.createInstance(this);
                cm.removeAllCookie();
                CookieSyncManager.getInstance().sync();
            }
            prefs.edit().remove("zoom").remove("dark").remove("desktop").commit();
            web.getSettings().setTextZoom(100);
            applyUserAgent();
            loadMain(HOME_URL);
        } catch (Throwable ignored) {
        }
    }

    private void showAbout() {
        String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (Throwable ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_text, version))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------------ URLs / Intents

    /** true = selbst behandelt (extern öffnen), false = im WebView laden. */
    boolean handleUrl(String url) {
        if (url == null) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return false;
        }
        if ("file".equals(scheme)) {
            // Eigene Fehlerseiten zulassen, alles andere blockieren.
            return !url.startsWith("file:///android_asset/");
        }
        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) {
            return false;
        }
        if ("intent".equals(scheme)) {
            try {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setComponent(null);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    void openExternally(String url) {
        if (url == null) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setComponent(null);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ Datei-Upload

    public void pickFile(ValueCallback<Uri[]> callback) {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
        }
        fileCallback = callback;
        startFilePicker();
    }

    public void pickFileLegacy(ValueCallback<Uri> callback) {
        if (legacyFileCallback != null) {
            legacyFileCallback.onReceiveValue(null);
        }
        legacyFileCallback = callback;
        startFilePicker();
    }

    private void startFilePicker() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, getString(R.string.pick_file)), REQ_FILE);
        } catch (ActivityNotFoundException e) {
            finishFilePicking(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    int n = cd.getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) {
                        results[i] = cd.getItemAt(i).getUri();
                    }
                }
            }
            finishFilePicking(results);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void finishFilePicking(Uri[] results) {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
        if (legacyFileCallback != null) {
            legacyFileCallback.onReceiveValue(results != null && results.length > 0 ? results[0] : null);
            legacyFileCallback = null;
        }
    }

    // ------------------------------------------------------------------ Vollbild-Video

    public void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        toolbar.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        web.setVisibility(View.GONE);
        getWindow().addContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    public void hideCustomView() {
        if (customView == null) {
            return;
        }
        customView.setVisibility(View.GONE);
        if (customView.getParent() instanceof ViewGroup) {
            ((ViewGroup) customView.getParent()).removeView(customView);
        }
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        toolbar.setVisibility(View.VISIBLE);
        web.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        updateNavState();
    }

    // ------------------------------------------------------------------ Lebenszyklus

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) {
            String u = intent.getDataString();
            if (u != null && (u.startsWith("http://") || u.startsWith("https://"))) {
                loadMain(u);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            outState.putBundle("webstate", web.saveState());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
        }
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                CookieManager.getInstance().flush();
            } else {
                CookieSyncManager.createInstance(this);
                CookieSyncManager.getInstance().sync();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            try {
                web.loadUrl("about:blank");
                web.destroy();
            } catch (Throwable ignored) {
            }
            web = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ JS-Brücke (nur auf lokalen Fehlerseiten)

    public static class JsBridge {
        private final MainActivity activity;

        JsBridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void retry() {
            activity.retryLoad();
        }

        @JavascriptInterface
        public void openBrowser() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.openExternally(activity.pendingRetryUrl != null
                            ? activity.pendingRetryUrl : HOME_URL);
                }
            });
        }
    }
}
