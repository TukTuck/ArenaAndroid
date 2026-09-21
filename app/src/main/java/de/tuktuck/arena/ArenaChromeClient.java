package de.tuktuck.arena;

import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Fortschritt, Datei-Upload, Vollbild-Video und Popups (Login-Fenster).
 */
public class ArenaChromeClient extends WebChromeClient {

    private final MainActivity activity;

    public ArenaChromeClient(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        activity.onProgressUi(newProgress);
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        activity.onTitleUi(title);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback) {
        // Bewusst kein Standortzugriff.
        callback.invoke(origin, false, false);
    }

    // ---- Datei-Upload (ab Android 5) ----

    @Override
    public boolean onShowFileChooser(WebView webView,
                                     ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        activity.pickFile(filePathCallback);
        return true;
    }

    // ---- Datei-Upload (Android 4.x, damals versteckte Methoden) ----

    @SuppressWarnings("unused")
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        activity.pickFileLegacy(uploadFile);
    }

    @SuppressWarnings("unused")
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType) {
        activity.pickFileLegacy(uploadFile);
    }

    @SuppressWarnings("unused")
    public void openFileChooser(ValueCallback<Uri> uploadFile) {
        activity.pickFileLegacy(uploadFile);
    }

    // ---- Vollbild-Video ----

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        activity.showCustomView(view, callback);
    }

    @Override
    public void onHideCustomView() {
        activity.hideCustomView();
    }

    // ---- Popup-Fenster (target=_blank, OAuth-Login) im selben WebView öffnen ----

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg) {
        WebView temp = new WebView(view.getContext());
        temp.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                activity.loadMain(url);
                return true;
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(temp);
        resultMsg.sendToTarget();
        return true;
    }
}
