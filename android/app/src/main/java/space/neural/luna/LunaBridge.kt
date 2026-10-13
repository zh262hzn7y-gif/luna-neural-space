package space.neural.luna

import android.webkit.JavascriptInterface
import android.webkit.WebView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class LunaBridge(private val activity: MainActivity, private val webView: WebView) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dataDir: File by lazy {
        File(activity.filesDir, "luna").apply { mkdirs() }
    }

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
                webView.evaluateJavascript(
                    "window.onLunaSpeechResult(${JSONObject.quote(result)})", null
                )
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

    @JavascriptInterface
    fun geminiQuery(prompt: String): String {
        val key = activity.getSharedPreferences("luna", 0).getString("gemini_key", "") ?: ""
        if (key.isBlank()) return "__NO_KEY__"
        val models = listOf(
            "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash-lite"
        )
        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }))
                }.toString()
                val req = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = http.newCall(req).execute()
                val json = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val text = JSONObject(json)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    saveSearch(prompt, text)
                    return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return "__API_ERROR__"
    }

    @JavascriptInterface
    fun saveConversation(role: String, text: String) {
        try {
            val f = File(dataDir, "conversas.jsonl")
            f.appendText(JSONObject().apply {
                put("role", role)
                put("text", text)
                put("ts", System.currentTimeMillis())
            }.toString() + "\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun loadConversations(limit: Int): String {
        return try {
            val f = File(dataDir, "conversas.jsonl")
            if (!f.exists()) return "[]"
            val lines = f.readLines().takeLast(limit.coerceAtLeast(1))
            JSONArray(lines.joinToString(",", "[", "]")).toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun saveSearch(query: String, result: String) {
        try {
            val f = File(dataDir, "buscas.jsonl")
            f.appendText(JSONObject().apply {
                put("query", query)
                put("result", result)
                put("ts", System.currentTimeMillis())
            }.toString() + "\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun loadSearches(limit: Int): String {
        return try {
            val f = File(dataDir, "buscas.jsonl")
            if (!f.exists()) return "[]"
            val lines = f.readLines().takeLast(limit.coerceAtLeast(1))
            JSONArray(lines.joinToString(",", "[", "]")).toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun saveKey(key: String) {
        activity.getSharedPreferences("luna", 0).edit().putString("gemini_key", key).apply()
    }

    @JavascriptInterface
    fun getKey(): String {
        return activity.getSharedPreferences("luna", 0).getString("gemini_key", "") ?: ""
    }
}
