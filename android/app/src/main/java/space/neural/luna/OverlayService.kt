package space.neural.luna

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class OverlayService : Service() {
    private var overlayView: HexOverlayView? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(
            1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Luna")
                .setContentText("Overlay ativo")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        )
        overlayView = HexOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        windowManager?.addView(overlayView, params)
    }

    override fun onDestroy() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Luna Overlay")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "luna_overlay"
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
