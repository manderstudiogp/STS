package com.manderstudio.shootthescene;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    // The game is served from this secure local address so localStorage,
    // clipboard and other web features behave like they do on Netlify.
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + APP_HOST + "/assets/index.html";

    // Optional: set this to a hosted URL (e.g. your Netlify site) to load the
    // live web version instead of the copy bundled inside the app.
    private static final String REMOTE_URL = null;

    private static final String BG_COLOR = "#0c0f14"; // matches the game's background

    /*
     * Android-only display fixes, added to the page as a <style> tag.
     * The Netlify/web version is not affected.
     *  - Blurred "glass" panels (backdrop-filter) over the constantly redrawing
     *    marketplace / lounge scenes make Android's WebView flash. Their
     *    backgrounds are already ~75-85% opaque, so they look almost the same.
     *  - The marketplace forces each full-screen scene layer onto its own GPU
     *    layer (will-change) and then blends them; on Android that flickers.
     *  - Glowing drop-shadow filters on animated fireflies redraw every frame.
     */
    private static final String APP_FIXES_CSS =
            "*,*::before,*::after{-webkit-backdrop-filter:none!important;backdrop-filter:none!important}" +
            ".mkt-scene .mkt-svg{will-change:auto!important}" +
            ".hub-scene .hub-fly{filter:none!important}";

    private static final String APP_FIXES_JS =
            "(function(){" +
            // navigator.share -> Android share sheet (WebView has none built in)
            "if(window.AndroidApp&&!navigator.__androidShare){navigator.__androidShare=1;" +
            "navigator.share=function(o){o=o||{};" +
            "window.AndroidApp.share(String(o.title||''),String(o.text||''),String(o.url||''));" +
            "return Promise.resolve();};" +
            "navigator.canShare=function(){return true;};}" +
            // display fixes
            "function add(){var d=document.documentElement;if(!d){return setTimeout(add,0);}" +
            "d.classList.add('android-app');" +
            "if(document.getElementById('android-app-fixes'))return;" +
            "var s=document.createElement('style');s.id='android-app-fixes';" +
            "s.textContent='" + APP_FIXES_CSS + "';" +
            "(document.head||d).appendChild(s);}" +
            "add();" +
            "})();";

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(BG_COLOR));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor(BG_COLOR));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Draw edge-to-edge on every Android version (Android 15+ forces this),
        // then pad by the system bars below so the layout is identical everywhere.
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        // Keep the game clear of the status bar, notch and navigation bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(APP_HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                   // localStorage (saved progress)
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // game sounds
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setTextZoom(100);                             // ignore system font scaling so layouts don't break

        // Debug builds can be inspected from desktop Chrome at chrome://inspect
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        webView.addJavascriptInterface(new NativeBridge(), "AndroidApp");
        webView.setWebChromeClient(new WebChromeClient()); // enables alert()/confirm()
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInAppUrl(uri)) return false;
                // Social links, mailto:, etc. open in the right app or browser.
                openExternally(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Fallback for older WebViews without document-start scripts (safe to run twice).
                view.evaluateJavascript(APP_FIXES_JS, null);
            }
        });

        // Apply the Android fixes before the game's own scripts run.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = new HashSet<>();
            origins.add("https://" + APP_HOST);
            if (REMOTE_URL != null) {
                Uri r = Uri.parse(REMOTE_URL);
                origins.add(r.getScheme() + "://" + r.getHost());
            }
            WebViewCompat.addDocumentStartJavaScript(webView, APP_FIXES_JS, origins);
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(REMOTE_URL != null ? REMOTE_URL : START_URL);
        }
    }

    private boolean isInAppUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        if (host.equals(APP_HOST)) return true;
        return REMOTE_URL != null && host.equals(Uri.parse(REMOTE_URL).getHost());
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            // No app can open this link; ignore.
        }
    }

    /** Methods the game's JavaScript can call as window.AndroidApp.* */
    private class NativeBridge {
        @JavascriptInterface
        public void share(String title, String text, String url) {
            StringBuilder body = new StringBuilder();
            if (text != null && !text.isEmpty()) body.append(text);
            if (url != null && !url.isEmpty()) {
                if (body.length() > 0) body.append(' ');
                body.append(url);
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            if (title != null && !title.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title);
            send.putExtra(Intent.EXTRA_TEXT, body.toString());
            runOnUiThread(() -> startActivity(Intent.createChooser(send, null)));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
