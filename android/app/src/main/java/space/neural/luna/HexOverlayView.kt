package space.neural.luna

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolatoR

class HexOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pulse = 0f
    private var rotation = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolatoR()
        addUpdateListener {
            pulse = (Math.sin(it.animatedFraction * Math.PI * 2) * 0.5 + 0.5).toFloat()
            rotation = it.animatedFraction * 360f
            invalidate()
        }
        start()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = minOf(width, height) / 2f - 10f
        val r = baseR * (0.92f + pulse * 0.08f)

        val glow = RadialGradient(
            cx, cy, r * 1.4f,
            intArrayOf(Color.argb((40 + pulse * 60).toInt(), 73, 224, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = glow
        canvas.drawCircle(cx, cy, r * 1.35f, glowPaint)

        fillPaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.3f, r,
            intArrayOf(
                Color.argb(230, 255, 255, 255),
                Color.argb(200, 196, 181, 255),
                Color.argb(180, 141, 107, 255),
                Color.argb(160, 58, 26, 138)
            ),
            floatArrayOf(0f, 0.25f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
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

        strokePaint.color = Color.argb((120 + pulse * 80).toInt(), 255, 138, 76)
        strokePaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, r * 0.55f, strokePaint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
