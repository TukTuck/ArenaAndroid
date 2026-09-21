package de.tuktuck.arena;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Ladeverhalten: Fehlerseiten nur bei echten Problemen, fail-closed bei SSL,
 * Popup-Links im selben Fenster. Bewusst defensiv, damit keine
 * Fehlerkaskade bei Redirects/Teilfehlern entsteht.
 */
public class ArenaWebViewClient extends WebViewClient {

    /** Chromium/Net-Fehlercode für „abgebrochen" (Redirects, neue Eingaben) – kein echter Fehler. */
    private static final int ERR_ABORTED = -3;

    private final MainActivity activity;

    public ArenaWebViewClient(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return activity.handleUrl(url);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        activity.onPageStartedUi(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        activity.onPageFinishedUi(url);
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        super.doUpdateVisitedHistory(view, url, isReload);
        activity.onHistoryChanged();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        // Ab API 23 übernimmt die neue Signatur – sonst käme jeder Fehler doppelt.
        if (Build.VERSION.SDK_INT >= 23) {
            return;
        }
        handleMainFrameError(view, failingUrl, errorCode, false);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (Build.VERSION.SDK_INT < 23 || request == null || !request.isForMainFrame()) {
            return; // Teilfehler (Bilder, XHR, Favicon) sollen die Seite nicht zerstören
        }
        int code = (error != null) ? error.getErrorCode() : 0;
        handleMainFrameError(view, request.getUrl().toString(), code, false);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // Fail-closed: abbrechen. Aber nur wenn das Hauptdokument betroffen ist,
        // gibt es eine Fehlerseite – einzelne Werbe-/CDN-Ressourcen dürfen die
        // Seite nicht kaputt machen.
        handler.cancel();
        String url = null;
        try {
            url = error.getUrl();
        } catch (Throwable ignored) {
        }
        String current = view.getUrl();
        if (sameDocument(url, current)) {
            activity.onMainFrameError(current, true, -1);
        }
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        // Ab Android 8: Renderer-Absturz abfangen statt die ganze App sterben zu lassen.
        if (Build.VERSION.SDK_INT >= 26) {
            activity.onRendererGone();
            return true;
        }
        return super.onRenderProcessGone(view, detail);
    }

    @Override
    public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType,
                                  SafeBrowsingResponse response) {
        // arena.ai ist ausdrücklich gewünscht – Safe-Browsing-Blockade umgehen.
        response.proceed(false);
    }

    private void handleMainFrameError(WebView view, String failingUrl, int errorCode, boolean cert) {
        if (errorCode == ERR_ABORTED) {
            return; // typisch bei Redirects/Spa-Navigation – kein echter Fehler
        }
        if (failingUrl == null) {
            return;
        }
        String current = view.getUrl();
        // Nur reagieren, wenn die gerade gewünschte Hauptadresse betroffen ist.
        if (!sameDocument(failingUrl, current) && !sameDocument(failingUrl, activity.getPendingUrl())) {
            return;
        }
        activity.onMainFrameError(failingUrl, cert, errorCode);
    }

    private static boolean sameDocument(String a, String b) {
        return a != null && b != null && strip(a).equals(strip(b));
    }

    private static String strip(String u) {
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }
}
