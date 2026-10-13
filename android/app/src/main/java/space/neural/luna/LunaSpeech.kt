package space.neural.luna

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object LunaSpeech {
    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var finished = false

    fun start(activity: Activity, callback: (String) -> Unit) {
        stop()
        finished = false
        onResult = callback
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            callback("__UNAVAILABLE__")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    if (finished) return
                    finished = true
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    onResult?.invoke(text)
                }
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    onResult?.invoke("__LISTENING__")
                }
                override fun onBeginningOfSpeech() {
                    onResult?.invoke("__SPEAKING__")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    onResult?.invoke("__RMS__:$rmsdB")
                }
                override fun onBufferReceived(buffer: ByteArray?) {
                }
                override fun onEndOfSpeech() {
                    // nao dispara callback vazio - espera onResults
                }
                override fun onError(error: Int) {
                    if (finished) return
                    finished = true
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "__NO_MATCH__"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "__TIMEOUT__"
                        else -> "__ERROR__:$error"
                    }
                    onResult?.invoke(msg)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    if (text.isNotBlank()) onResult?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                }
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            callback("__ERROR__:-1")
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        onResult = null
        finished = true
    }
}
