package space.neural.luna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val runtime by viewModels<LunaRuntime>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(android.R.id.content)
        // WebView host + bridge initialized by LunaRuntime
        runtime.attach(this)
    }

    override fun onDestroy() {
        runtime.detach()
        super.onDestroy()
    }
}
