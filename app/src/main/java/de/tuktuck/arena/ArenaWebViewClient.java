package de.tuktuck.arena;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Ladeverhalten: Fehlerseiten, fail-closed bei SSL-Problemen,
 * Popup-Links im selben Fenster.
 */
public class ArenaWebViewClient extends WebViewClient {

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
        super.onReceivedError(view, errorCode, description, failingUrl);
        // Vor API 23 gilt dieser Callback nur für den Haupt-Frame.
        if (failingUrl != null && failingUrl.equals(view.getUrl())) {
            activity.onMainFrameError(failingUrl, false);
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (Build.VERSION.SDK_INT >= 23 && request != null && request.isForMainFrame()) {
            activity.onMainFrameError(request.getUrl().toString(), false);
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // Fail-closed: ungültige Zertifikate niemals akzeptieren.
        handler.cancel();
        String url = null;
        try {
            url = error.getUrl();
        } catch (Throwable ignored) {
        }
        activity.onMainFrameError(url, true);
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
}
