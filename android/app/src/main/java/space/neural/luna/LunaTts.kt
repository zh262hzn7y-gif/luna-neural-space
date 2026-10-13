package space.neural.luna

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object LunaTts {
    private var tts: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        if (tts == null) {
        tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("pt", "BR")
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "luna")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "luna")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
