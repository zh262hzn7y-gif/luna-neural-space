package space.neural.luna

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val runtime by viewModels<LunaRuntime>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            runtime.attach(this)
        } catch (e: Exception) {
            e.printStackTrace()
            val tv = TextView(this).apply {
                text = "Luna não conseguiu iniciar:\n${e.message}\n\nTente reinstalar ou contate o suporte."
                setPadding(40, 40, 40, 40)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF07060E.toInt())
            }
            setContentView(tv)
        }
    }

    override fun onDestroy() {
        try { runtime.detach() } catch (_: Exception) {}
        super.onDestroy()
    }
}
