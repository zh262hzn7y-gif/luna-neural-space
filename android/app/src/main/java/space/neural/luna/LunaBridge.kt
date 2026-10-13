package space.neural.luna

import android.webkit.JavascriptInterface
import android.webkit.WebView

class LunaBridge(private val activity: MainActivity, private val webView: WebView) {

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun speak(text: String) {
        LunaTts.speak(activity, text)
    }

    @JavascriptInterface
    fun startListening() {
        LunaSpeech.start(activity) { result ->
            webView.post {
                webView.evaluateJavascript("window.onLunaSpeechResult(${org.json.JSONObject.quote(result)})", null)
            }
        }
    }

    @JavascriptInterface
    fun stopListening() {
        LunaSpeech.stop()
    }

    @JavascriptInterface
    fun getLocation(callback: String) {
        LunaLocation.get(activity) { lat, lon ->
            webView.post {
                webView.evaluateJavascript("$callback($lat, $lon)", null)
            }
        }
    }

    @JavascriptInterface
    fun showOverlay() {
        OverlayService.start(activity)
    }

    @JavascriptInterface
    fun hideOverlay() {
        OverlayService.stop(activity)
    }

    @JavascriptInterface
    fun notify(title: String, body: String) {
        LunaNotifier.show(activity, title, body)
    }
}
