package space.neural.luna

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object LunaSpeech {
    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null

    fun start(activity: Activity, callback: (String) -> Unit) {
        onResult = callback
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            callback("")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    onResult?.invoke(text)
                }
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onResult?.invoke("")
                }
                override fun onError(error: Int) {
                    onResult?.invoke("")
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    onResult?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onResult = null
    }
}
