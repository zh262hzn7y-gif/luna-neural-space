package space.neural.luna

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.abs

class HexOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pulse = 0f
    private var rotation = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulse = (Math.sin(it.animatedFraction * Math.PI * 2) * 0.5 + 0.5).toFloat()
            rotation = it.animatedFraction * 360f
            invalidate()
        }
        start()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }
    private val menuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 20, 10, 40)
        style = Paint.Style.FILL
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 73, 224, 255)
        style = Paint.Style.FILL
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 22f
    }

    var windowManager: WindowManager? = null
    var layoutParams: WindowManager.LayoutParams? = null
    var onAction: ((String) -> Unit)? = null

    private var showMenu = false
    private var lastClick = 0L
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private val menuItems = listOf("Iniciar", "Pausar", "Zerar", "Mic", "+Tamanho", "-Tamanho")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = minOf(width, height) / 2f - 10f
        val r = baseR * (0.92f + pulse * 0.08f)

        val glow = RadialGradient(cx, cy, r * 1.4f,
            intArrayOf(Color.argb((40 + pulse * 60).toInt(), 73, 224, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        glowPaint.shader = glow
        canvas.drawCircle(cx, cy, r * 1.35f, glowPaint)

        fillPaint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r,
            intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(200, 196, 181, 255),
                Color.argb(180, 141, 107, 255), Color.argb(160, 58, 26, 138)),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fillPaint)

        strokePaint.color = Color.argb(200, 73, 224, 255)
        canvas.drawCircle(cx, cy, r, strokePaint)

        val path = android.graphics.Path()
        for (i in 0..5) {
            val angle = Math.toRadians((60 * i - 90 + rotation * 0.15).toDouble())
            val x = cx + r * 0.72f * Math.cos(angle).toFloat()
            val y = cy + r * 0.72f * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        strokePaint.color = Color.argb(160, 255, 255, 255)
        canvas.drawPath(path, strokePaint)

        // Timer text
        val prefs = context.getSharedPreferences("luna", 0)
        val timerText = prefs.getString("timer_display", "00:00") ?: "00:00"
        canvas.drawText(timerText, cx, cy + 10f, textPaint)

        // Menu
        if (showMenu) {
            val menuW = width * 0.9f
            val menuH = 48f * menuItems.size + 16f
            val menuX = (width - menuW) / 2f
            val menuY = cy + r + 8f
            canvas.drawRoundRect(menuX, menuY, menuX + menuW, menuY + menuH, 16f, 16f, menuPaint)
            menuItems.forEachIndexed { i, label ->
                val by = menuY + 8f + i * 48f
                canvas.drawRoundRect(menuX + 8f, by, menuX + menuW - 8f, by + 40f, 8f, 8f, btnPaint)
                canvas.drawText(label, cx, by + 28f, btnTextPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lp = layoutParams ?: return false
        val wm = windowManager ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = lp.x
                startY = lp.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > 10 || abs(dy) > 10) {
                    dragging = true
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    val now = System.currentTimeMillis()
                    if (now - lastClick < 300) {
                        // double click → open app
                        showMenu = false
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(intent)
                        lastClick = 0
                    } else {
                        // single click → toggle menu or handle menu click
                        if (showMenu) {
                            handleMenuClick(event.x, event.y)
                        } else {
                            showMenu = true
                            invalidate()
                        }
                        lastClick = now
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleMenuClick(x: Float, y: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 10f
        val menuY = cy + r + 8f
        menuItems.forEachIndexed { i, label ->
            val by = menuY + 8f + i * 48f
            if (y >= by && y <= by + 40f) {
                when (label) {
                    "Iniciar" -> onAction?.invoke("iniciar")
                    "Pausar" -> onAction?.invoke("pausar")
                    "Zerar" -> onAction?.invoke("zerar")
                    "Mic" -> onAction?.invoke("mic")
                    "+Tamanho" -> onAction?.invoke("size_up")
                    "-Tamanho" -> onAction?.invoke("size_down")
                }
                showMenu = false
                invalidate()
                return
            }
        }
        showMenu = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
