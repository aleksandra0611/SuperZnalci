package com.example.superznalci

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val spacing = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        var usedWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += w + spacing
            usedWidth = maxOf(usedWidth, x - spacing)
            rowHeight = maxOf(rowHeight, h)
        }

        val height = y + rowHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(usedWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + spacing
            rowHeight = maxOf(rowHeight, h)
        }
    }
}
