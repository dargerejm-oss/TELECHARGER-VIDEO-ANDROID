package com.jmdargere.telechargervideo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/** Barre de progression avec le petit coureur. Bras en l'air à l'arrivée. */
class RunnerView(ctx: Context) : View(ctx) {
    var progress = 0f          // 0..1
    var actif = false          // le coureur court
    private var phase = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dm = ctx.resources.displayMetrics.density

    private fun dp(v: Float) = v * dm

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), dp(86f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val barH = dp(18f)
        val top = height - barH - dp(6f)
        val left = dp(14f)
        val right = w - dp(14f)
        // fond
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#E0E0E0")
        c.drawRoundRect(RectF(left, top, right, top + barH), barH / 2, barH / 2, p)
        // rempli
        val x = left + (right - left) * progress.coerceIn(0f, 1f)
        if (x > left + 2) {
            p.color = if (progress >= 1f) Color.parseColor("#2E7D32") else Color.parseColor("#43A047")
            c.drawRoundRect(RectF(left, top, x, top + barH), barH / 2, barH / 2, p)
        }
        // coureur
        val arrive = progress >= 1f
        val cx = x.coerceIn(left + dp(10f), right - dp(10f))
        val foot = top - dp(2f)
        val body = dp(20f)
        val hip = foot - dp(20f)
        val shoulder = hip - body
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(3f)
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.parseColor("#1B5E20")
        c.drawLine(cx, hip, cx, shoulder, p)                       // tronc
        p.style = Paint.Style.FILL
        c.drawCircle(cx, shoulder - dp(7f), dp(6f), p)             // tête
        p.style = Paint.Style.STROKE
        val s = if (actif && !arrive) sin(phase) else 0f
        if (arrive) {
            c.drawLine(cx, shoulder + dp(3f), cx - dp(11f), shoulder - dp(12f), p) // bras en l'air
            c.drawLine(cx, shoulder + dp(3f), cx + dp(11f), shoulder - dp(12f), p)
            c.drawLine(cx, hip, cx - dp(6f), foot, p)
            c.drawLine(cx, hip, cx + dp(6f), foot, p)
        } else {
            c.drawLine(cx, shoulder + dp(4f), cx + dp(10f) * s, shoulder + dp(14f), p)  // bras
            c.drawLine(cx, shoulder + dp(4f), cx - dp(10f) * s, shoulder + dp(14f), p)
            c.drawLine(cx, hip, cx + dp(10f) * s, foot, p)                              // jambes
            c.drawLine(cx, hip, cx - dp(10f) * s, foot, p)
        }
        if (actif && !arrive) {
            phase += 0.45f
            postInvalidateOnAnimation()
        }
    }
}
