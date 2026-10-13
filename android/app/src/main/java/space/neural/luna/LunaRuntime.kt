package space.neural.luna

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel

class LunaRuntime : ViewModel() {
    private var webView: WebView? = null
    private var bridge: LunaBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: MainActivity) {
        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(LunaBridge(activity, this), "LunaNative")
        }
        bridge = LunaBridge(activity, wv)
        wv.loadUrl("file:///android_asset/www/index.html")
        webView = wv
        activity.setContentView(wv)
    }

    fun detach() {
        webView?.destroy()
        webView = null
        bridge = null
    }
}
