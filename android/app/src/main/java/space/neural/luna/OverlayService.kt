package space.neural.luna

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class OverlayService : Service() {
    private var overlayView: HexOverlayView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bubbleSize = 140
    private var timerRunning = false
    private var timerStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {}
                stopSelf()
                return
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createNotificationChannel()
            startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Luna")
                .setContentText("Bolha de turno ativa")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build())

            val prefs = getSharedPreferences("luna", 0)
            bubbleSize = prefs.getInt("bubble_size", 140)
            val sizePx = (bubbleSize * resources.displayMetrics.density).toInt()

            overlayView = HexOverlayView(this).apply {
                windowManager = this@OverlayService.windowManager
                onAction = { action -> handleAction(action) }
                onMenuExpand = { expand -> expandForMenu(expand) }
            }

            layoutParams = WindowManager.LayoutParams(
                sizePx, sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.getInt("bubble_x", 40)
                y = prefs.getInt("bubble_y", 200)
            }
            overlayView?.layoutParams = layoutParams
            windowManager?.addView(overlayView, layoutParams)

            // restore timer if running
            timerRunning = prefs.getBoolean("timer_running", false)
            timerStart = prefs.getLong("timer_start", 0L)
            if (timerRunning && timerStart > 0) startTimerTick()
        } catch (e: Exception) {
            e.printStackTrace()
            try { stopSelf() } catch (_: Exception) {}
        }
    }

    private fun handleAction(action: String) {
        val prefs = getSharedPreferences("luna", 0)
        when (action) {
            "iniciar" -> {
                // signal JS to handle
                prefs.edit().putString("pending_action", "iniciar").apply()
                timerRunning = true
                startTimerTick()
            }
            "pausar" -> {
                prefs.edit().putString("pending_action", "pausar").apply()
                timerRunning = false
            }
            "cafe" -> {
                prefs.edit().putString("pending_action", "cafe").apply()
                timerRunning = false
            }
            "fechar" -> {
                prefs.edit().putString("pending_action", "fechar").apply()
                timerRunning = false
            }
            "concluir" -> {
                prefs.edit().putString("pending_action", "concluir").apply()
                timerRunning = false
            }
            "zerar" -> {
                timerRunning = false
                timerStart = 0
                prefs.edit().putBoolean("timer_running", false).putLong("timer_start", 0).putString("timer_display", "00:00").apply()
                overlayView?.invalidate()
            }
            "mic" -> {
                // open app and signal mic
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("start_mic", true)
                }
                startActivity(intent)
            }
            "size_up" -> resize(bubbleSize + 20)
            "size_down" -> resize((bubbleSize - 20).coerceAtLeast(80))
        }
    }

    private fun resize(newSize: Int) {
        bubbleSize = newSize
        getSharedPreferences("luna", 0).edit().putInt("bubble_size", bubbleSize).apply()
        val sizePx = (bubbleSize * resources.displayMetrics.density).toInt()
        layoutParams?.width = sizePx
        layoutParams?.height = sizePx
        try { windowManager?.updateViewLayout(overlayView, layoutParams) } catch (_: Exception) {}
    }

    private fun startTimerTick() {
        handler.post(object : Runnable {
            override fun run() {
                if (!timerRunning) return
                val elapsed = System.currentTimeMillis() - timerStart
                val s = (elapsed / 1000).toInt()
                val h = s / 3600
                val m = (s % 3600) / 60
                val sec = s % 60
                val text = if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
                getSharedPreferences("luna", 0).edit().putString("timer_display", text).apply()
                overlayView?.invalidate()
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Luna Overlay").build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }


    private fun expandForMenu(expand: Boolean) {
        val lp = layoutParams ?: return
        val sizePx = (bubbleSize * resources.displayMetrics.density).toInt()
        lp.height = if (expand) sizePx + (48 * 9 + 60) else sizePx
        try { windowManager?.updateViewLayout(overlayView, lp) } catch (_: Exception) {}
    }

    companion object {
        private const val CHANNEL_ID = "luna_overlay"
        fun start(context: Context) {
            try {
                val intent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) { e.printStackTrace() }
        }
        fun stop(context: Context) {
            try { context.stopService(Intent(context, OverlayService::class.java)) } catch (_: Exception) {}
        }
    }
}
