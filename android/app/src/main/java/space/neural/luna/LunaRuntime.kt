package space.neural.luna

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel

class LunaRuntime : ViewModel() {
    private var webView: WebView? = null
    private var bridge: LunaBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: MainActivity) {
        // pede permissao de microfone se ainda nao tiver
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                2001
            )
        }

        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.databaseEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // permite acesso de file:// ao JavascriptInterface
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                }
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                }
            }
            // registra a interface ANTES de carregar a pagina
            addJavascriptInterface(LunaBridge(activity, this), "LunaNative")
        }
        bridge = LunaBridge(activity, wv)
        wv.loadUrl("file:///android_asset/www/index.html")
        webView = wv
        activity.setContentView(wv)
    }

    fun detach() {
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (_: Exception) {
        }
        webView = null
        bridge = null
    }
}
