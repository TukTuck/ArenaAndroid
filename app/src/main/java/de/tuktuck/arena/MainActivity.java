package de.tuktuck.arena;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arena – schlanker, stabiler WebView-Client für arena.ai.
 * Optimiert für Geräte bis Android 7, läuft ab Android 4.4.
 */
public class MainActivity extends Activity {

    /** Produkt-UI, nicht die Marketing-Landing unter /. Beleg: Chrome XCover 5, 2026-09-23. */
    public static final String HOME_URL = "https://arena.ai/code";

    private static final String PREFS = "arena";
    private static final String JS_BRIDGE = "ArenaApp";
    private static final String LOCAL_PREFIX = "file:///android_asset/";
    private static final int REQ_FILE = 2001;
    /** Seiten-Zoom (CSS zoom), nicht Schriftzoom. 100 = WebView-Normalmaß, nicht „kein Zoom“. 0 % gäbe es nicht – die Seite wäre unsichtbar. */
    private static final int ZOOM_MIN = 25;
    private static final int ZOOM_MAX = 300;
    private static final int ZOOM_STEP = 25;
    private static final long ERROR_DEDUP_MS = 2500;

    /** Dunkler Modus für die Webseite (invertierter Filter, bewusst einfach gehalten). */
    static final String JS_DARK =
            "(function(){try{"
                    + "var old=document.getElementById('arena-dark-style');"
                    + "if(old&&old.parentNode){old.parentNode.removeChild(old);}"
                    + "var s=document.createElement('style');s.id='arena-dark-style';"
                    + "s.textContent='html{background:#111 !important;filter:invert(0.92) hue-rotate(180deg);}"
                    + "html img,html video,html canvas,html embed,html iframe,html object{filter:invert(1) hue-rotate(180deg);}';"
                    + "(document.head||document.documentElement).appendChild(s);}catch(e){}})();";

    /**
     * Chrome-Viewport: Breite = Gerät, Scale 1. Ohne das nimmt WebView oft ~980 px
     * (Desktop) und zeigt bei Overview=off nur einen Crop — Cookies/Hero wirken
     * 4× zu groß, CSS-Zoom 25 % „rettet“ visuell, ändert innerWidth aber nicht,
     * deshalb scrollt die Sidebar nicht zum Login.
     */
    static final String JS_VIEWPORT =
            "(function(){try{"
                    + "var c='width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes';"
                    + "var list=document.getElementsByName('viewport');"
                    + "var m=null;"
                    + "for(var i=0;i<list.length;i++){if(list[i].tagName==='META'){m=list[i];}}"
                    + "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');"
                    + "(document.head||document.documentElement).appendChild(m);}"
                    + "var cur=m.getAttribute('content')||'';"
                    + "if(cur.indexOf('device-width')===-1){m.setAttribute('content',c);}"
                    + "}catch(e){}})();";

    /** Klickt den Login-Knopf der Seite (liegt oft unterhalb der sichtbaren Sidebar). */
    static final String JS_CLICK_LOGIN =
            "(function(){try{"
                    + "var nodes=document.querySelectorAll('a,button,[role=button]');"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var t=(nodes[i].innerText||nodes[i].textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                    + "if(t==='login'||t==='log in'||t==='sign in'||t==='anmelden'){"
                    + "nodes[i].click();return 'ok';}}"
                    + "return 'none';}catch(e){return 'err';}})();";

    /** target=\"_blank\"-Links im selben Fenster öffnen (z. B. Login-Popups). Einmal pro Dokument. */
    static final String JS_KEEP_BLANK =
            "(function(){try{"
                    + "if(window.__arenaKeepBlank){return;}"
                    + "window.__arenaKeepBlank=true;"
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
    private TextView lblVersion;
    private View toolbar;
    private SharedPreferences prefs;

    private ValueCallback<Uri[]> fileCallback;
    private ValueCallback<Uri> legacyFileCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String pendingRetryUrl;
    private JsBridge bridge;

    private String lastErrorUrl;
    private long lastErrorAt;
    private BroadcastReceiver netReceiver;
    /** Unveränderte WebView-UA, merken wir uns zum Chrome-ähnlich-Machen. */
    private String stockUserAgent;
    /** true, sobald der Nutzer den Zoom vom Default weggedreht hat – dann CSS-zoom setzen/löschen. */
    private boolean zoomTouched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecovery();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // 0.2.5: Nutzer hat A− auf 25 % gestellt, um den Desktop-Crop zu sehen.
        // CSS-zoom ändert innerWidth nicht → Sidebar scrollt nicht. Einmal zurück.
        if (!prefs.getBoolean("zoom_cleared_026", false)) {
            prefs.edit().putInt("zoom", 100).putBoolean("zoom_cleared_026", true).commit();
        }
        zoomTouched = false;
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        btnBack = (ImageView) findViewById(R.id.btn_back);
        btnForward = (ImageView) findViewById(R.id.btn_forward);
        btnReload = (ImageView) findViewById(R.id.btn_reload);
        btnZoomOut = (TextView) findViewById(R.id.btn_zoom_out);
        btnZoomIn = (TextView) findViewById(R.id.btn_zoom_in);
        btnMenu = (TextView) findViewById(R.id.btn_menu);
        lblVersion = (TextView) findViewById(R.id.lbl_version);
        bindVersionBadge();

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
                changeZoom(-ZOOM_STEP);
            }
        });
        btnZoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeZoom(ZOOM_STEP);
            }
        });
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        setupWebView();
        applyLite();

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
            loadMain(normalizeStartUrl(url));
        }

        if (getIntent() != null && getIntent().getBooleanExtra("crashed", false)) {
            Toast.makeText(this, R.string.toast_restored, Toast.LENGTH_SHORT).show();
        }
        updateNavState();
    }

    /** Bei unerwarteten Abstürzen still wiederherstellen (ohne Absturzdialog-Serie). */
    private void installCrashRecovery() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                    long last = p.getLong("last_crash", 0);
                    long now = System.currentTimeMillis();
                    if (now - last > 15000) {
                        p.edit().putLong("last_crash", now).commit();
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        i.putExtra("crashed", true);
                        startActivity(i);
                        // Prozess direkt beenden – kein „App wurde beendet"-Dialog, keine Schleife.
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(10);
                    }
                } catch (Throwable ignored) {
                }
                // Innerhalb von 15 s erneut abgestürzt: echter Fehler, normal sterben lassen.
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(11);
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
        // Overview AN: ohne Viewport-Meta legt WebView ~980 px Desktop an und zeigt
        // bei Overview=off nur den Crop (Cookies riesig). Mit device-width (JS_VIEWPORT)
        // ist die Seite schon bildschirmbreit → Overview-Scale ≈ 1, Chats nicht extra klein.
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        try {
            s.setRenderPriority(WebSettings.RenderPriority.HIGH);
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                s.setOffscreenPreRaster(true);
            } catch (Throwable ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        stockUserAgent = s.getUserAgentString();
        applyUserAgent();
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

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
                loadMain(pendingRetryUrl != null ? pendingRetryUrl : HOME_URL);
            }
        });
    }

    private void reloadCurrent() {
        String u = web.getUrl();
        if (u != null && u.startsWith(LOCAL_PREFIX)) {
            loadMain(pendingRetryUrl != null ? pendingRetryUrl : HOME_URL);
        } else {
            web.reload();
        }
    }

    /**
     * Bare / ist die Marketing-Schale („Experience the frontier“).
     * Das Produkt (Sidebar, New Chat, Code) liegt unter /code.
     */
    static String normalizeStartUrl(String url) {
        if (url == null || url.length() == 0) {
            return HOME_URL;
        }
        String t = url.trim();
        if ("https://arena.ai".equals(t) || "https://arena.ai/".equals(t)
                || "https://www.arena.ai".equals(t) || "https://www.arena.ai/".equals(t)
                || "http://arena.ai".equals(t) || "http://arena.ai/".equals(t)
                || "http://www.arena.ai".equals(t) || "http://www.arena.ai/".equals(t)) {
            return HOME_URL;
        }
        return t;
    }

    private static boolean isLocalPage(String url) {
        return url != null && url.startsWith(LOCAL_PREFIX);
    }

    private void showOfflinePage() {
        onProgressUi(100);
        addJsBridge();
        web.loadUrl(LOCAL_PREFIX + "html/offline.html");
    }

    private void showErrorPage(boolean cert, int code, String url) {
        onProgressUi(100);
        addJsBridge();
        StringBuilder q = new StringBuilder(LOCAL_PREFIX + "html/error.html?");
        if (cert) {
            q.append("cert=1&");
        }
        q.append("code=").append(code);
        if (url != null) {
            q.append("&url=").append(Uri.encode(url));
        }
        web.loadUrl(q.toString());
    }

    void onMainFrameError(String url, boolean cert, int code) {
        long now = System.currentTimeMillis();
        if (url != null && url.equals(lastErrorUrl) && now - lastErrorAt < ERROR_DEDUP_MS) {
            return; // keine Fehlerflut für dieselbe Adresse
        }
        lastErrorUrl = url;
        lastErrorAt = now;
        if (url != null && url.startsWith("http")) {
            pendingRetryUrl = url;
        }
        if (!cert && !isOnline()) {
            showOfflinePage();
        } else {
            showErrorPage(cert, code, url);
        }
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

    String getPendingUrl() {
        return pendingRetryUrl;
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
        if (progress >= 10) {
            applyMobileViewport();
        }
    }

    /** Viewport so früh wie möglich, sonst hydriert die SPA auf Desktop-Breite. */
    void applyMobileViewport() {
        if (web == null) {
            return;
        }
        String url = web.getUrl();
        if (isLocalPage(url)) {
            return;
        }
        web.evaluateJavascript(JS_VIEWPORT, null);
    }

    void onPageStartedUi(String url) {
        // WICHTIG: Auf lokalen Fehlerseiten muss die JS-Brücke bestehen bleiben,
        // sonst reagieren „Erneut versuchen"/„Im Browser öffnen" nicht.
        if (isLocalPage(url)) {
            addJsBridge();
        } else {
            removeJsBridge();
        }
        updateNavState();
    }

    void onPageFinishedUi(String url) {
        if (isLocalPage(url)) {
            addJsBridge();
        }
        updateNavState();
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            applyMobileViewport();
            if (isDarkMode()) {
                web.evaluateJavascript(JS_DARK, null);
            }
            web.evaluateJavascript(JS_KEEP_BLANK, null);
            applyPageZoom();
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

    private int currentZoom() {
        int z = prefs.getInt("zoom", 100);
        if (z < ZOOM_MIN) {
            z = ZOOM_MIN;
        }
        if (z > ZOOM_MAX) {
            z = ZOOM_MAX;
        }
        return z;
    }

    /**
     * CSS-Zoom nur, wenn der Nutzer A−/A+ benutzt hat.
     * Bei 100 % und unberührtem Zoom: nichts injizieren – arena.ai soll sein
     * eigenes Layout ungestört fahren (sonst Relayout bei jedem pageFinished).
     */
    private void applyPageZoom() {
        if (web == null) {
            return;
        }
        web.getSettings().setTextZoom(100);
        String url = web.getUrl();
        if (isLocalPage(url)) {
            return;
        }
        int user = currentZoom();
        if (!zoomTouched && user == 100) {
            return;
        }
        float fontScale = 1f;
        try {
            fontScale = getResources().getConfiguration().fontScale;
        } catch (Throwable ignored) {
        }
        if (fontScale < 0.5f || fontScale > 3f) {
            fontScale = 1f;
        }
        int effective = Math.round(user / fontScale);
        if (effective < 10) {
            effective = 10;
        }
        if (effective > ZOOM_MAX) {
            effective = ZOOM_MAX;
        }
        String js;
        if (effective == 100) {
            js = "(function(){try{document.documentElement.style.zoom='';}catch(e){}})();";
        } else {
            js = "(function(){try{"
                    + "document.documentElement.style.zoom=" + effective + "/100;"
                    + "}catch(e){}})();";
        }
        web.evaluateJavascript(js, null);
    }

    private void changeZoom(int delta) {
        zoomTouched = true;
        int cur = currentZoom() + delta;
        if (cur < ZOOM_MIN) {
            cur = ZOOM_MIN;
        }
        if (cur > ZOOM_MAX) {
            cur = ZOOM_MAX;
        }
        prefs.edit().putInt("zoom", cur).commit();
        applyPageZoom();
        Toast.makeText(this, getString(R.string.toast_zoom, cur), Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ Menü

    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, btnMenu);
        popup.getMenuInflater().inflate(R.menu.main, popup.getMenu());
        popup.getMenu().findItem(R.id.menu_desktop).setChecked(isDesktop());
        popup.getMenu().findItem(R.id.menu_dark).setChecked(isDarkMode());
        popup.getMenu().findItem(R.id.menu_lite).setChecked(isLite());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_reload) {
                    reloadCurrent();
                } else if (id == R.id.menu_login) {
                    clickSiteLogin();
                } else if (id == R.id.menu_browser) {
                    String u = web.getUrl();
                    openExternally(u != null ? u : HOME_URL);
                } else if (id == R.id.menu_share) {
                    shareLink();
                } else if (id == R.id.menu_desktop) {
                    toggleDesktop();
                } else if (id == R.id.menu_dark) {
                    toggleDark();
                } else if (id == R.id.menu_lite) {
                    toggleLite();
                } else if (id == R.id.menu_zoom_reset) {
                    prefs.edit().putInt("zoom", 100).commit();
                    zoomTouched = true;
                    applyPageZoom();
                    zoomTouched = false;
                    Toast.makeText(MainActivity.this, getString(R.string.toast_zoom, 100),
                            Toast.LENGTH_SHORT).show();
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

    /** Login der Website auslösen – der Knopf sitzt oft unter dem Promo-Block der Sidebar. */
    private void clickSiteLogin() {
        if (web == null) {
            return;
        }
        String url = web.getUrl();
        if (isLocalPage(url)) {
            Toast.makeText(this, R.string.toast_login_none, Toast.LENGTH_SHORT).show();
            return;
        }
        web.evaluateJavascript(JS_CLICK_LOGIN, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.indexOf("ok") < 0) {
                    Toast.makeText(MainActivity.this, R.string.toast_login_none,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
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

    private void toggleLite() {
        boolean on = !isLite();
        prefs.edit().putBoolean("lite", on).commit();
        applyLite();
        reloadCurrent();
        Toast.makeText(this, on ? R.string.toast_lite_on : R.string.toast_lite_off,
                Toast.LENGTH_SHORT).show();
    }

    private boolean isDarkMode() {
        return prefs.getBoolean("dark", false);
    }

    private boolean isDesktop() {
        return prefs.getBoolean("desktop", false);
    }

    private boolean isLite() {
        return prefs.getBoolean("lite", false);
    }

    /** Sparmodus: keine Bilder – spart Speicher/Fluss auf alten Geräten enorm. */
    private void applyLite() {
        boolean lite = isLite();
        web.getSettings().setBlockNetworkImage(lite);
        web.getSettings().setLoadsImagesAutomatically(!lite);
    }

    /**
     * Chrome-ähnliche UA: `; wv` und `Version/4.0` entfernen.
     * Viele SPAs (inkl. arena.ai) erkennen WebView und schalten auf langsamere
     * oder kaputte Pfade. Desktop: echte Chrome-Version der WebView, nicht Chrome/60.
     */
    private void applyUserAgent() {
        String stock = stockUserAgent;
        if (stock == null || stock.length() == 0) {
            stock = web.getSettings().getUserAgentString();
            stockUserAgent = stock;
        }
        if (stock == null) {
            stock = "";
        }
        if (isDesktop()) {
            String ver = "120.0.0.0";
            Matcher m = Pattern.compile("Chrome/([0-9.]+)").matcher(stock);
            if (m.find()) {
                ver = m.group(1);
            }
            web.getSettings().setUserAgentString(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                            + ver + " Safari/537.36");
        } else {
            String ua = stock.replace("; wv", "").replace(" Version/4.0", "");
            web.getSettings().setUserAgentString(ua);
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
            prefs.edit().remove("zoom").remove("dark").remove("desktop").remove("lite").commit();
            zoomTouched = false;
            web.getSettings().setTextZoom(100);
            applyUserAgent();
            applyLite();
            applyPageZoom();
            loadMain(HOME_URL);
        } catch (Throwable ignored) {
        }
    }

    /** Installierte versionName aus dem APK-Manifest – nie hardcodiert. */
    private String installedVersionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi != null && pi.versionName != null && pi.versionName.length() > 0) {
                return pi.versionName;
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    private void bindVersionBadge() {
        if (lblVersion == null) {
            return;
        }
        lblVersion.setText(installedVersionName());
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_text, installedVersionName()))
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
            return !url.startsWith(LOCAL_PREFIX);
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
                loadMain(normalizeStartUrl(u));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            Bundle state = new Bundle();
            web.saveState(state);
            outState.putBundle("webstate", state);
        }
    }

    /** Bei wiederkehrender Verbindung die Fehler-/Offlineseite automatisch neu laden. */
    private void ensureNetReceiver() {
        if (netReceiver != null) {
            return;
        }
        netReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String u = (web != null) ? web.getUrl() : null;
                    if (isOnline() && isLocalPage(u)) {
                        retryLoad();
                    }
                } catch (Throwable ignored) {
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            try {
                web.resumeTimers();
            } catch (Throwable ignored) {
            }
        }
        ensureNetReceiver();
        registerReceiver(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        try {
            if (netReceiver != null) {
                unregisterReceiver(netReceiver);
            }
        } catch (Throwable ignored) {
        }
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
