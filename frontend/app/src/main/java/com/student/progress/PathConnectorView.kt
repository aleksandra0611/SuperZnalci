package com.example.superznalci

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class PathConnectorView(
    context: Context,
    private val fromFrac: Float,
    private val toFrac: Float,
    private val nodeWidthPx: Int,
    lineColor: Int,
    dashed: Boolean
) : View(context) {

    private val density = resources.displayMetrics.density
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = (if (dashed) 6f else 10f) * density
        color = lineColor
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(1f, 14f * density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val usable = (w - nodeWidthPx).coerceAtLeast(0f)
        val x1 = fromFrac * usable + nodeWidthPx / 2f
        val x2 = toFrac * usable + nodeWidthPx / 2f

        path.reset()
        path.moveTo(x1, 0f)
        path.cubicTo(x1, h * 0.6f, x2, h * 0.4f, x2, h)
        canvas.drawPath(path, paint)
    }
}