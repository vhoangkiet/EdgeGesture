package com.edgegesture.evilgodxu.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class PillBarView(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt() // Trắng đục 90%
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000 // Viền mờ màu tối giúp nhìn rõ trên nền trắng
        style = Paint.Style.STROKE
        strokeWidth = 1f * context.resources.displayMetrics.density
    }

    private val pillRect = RectF()
    private var pillWidthPx = 72f
    private var pillHeightPx = 4f
    private var cornerRadiusPx = 2f

    init {
        isClickable = false
        isFocusable = false
    }

    fun updateDimensions(widthPx: Float, heightPx: Float) {
        this.pillWidthPx = widthPx
        this.pillHeightPx = heightPx
        this.cornerRadiusPx = heightPx / 2f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = (w - pillWidthPx) / 2f
        val top = (h - pillHeightPx) / 2f
        pillRect.set(left, top, left + pillWidthPx, top + pillHeightPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(pillRect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        canvas.drawRoundRect(pillRect, cornerRadiusPx, cornerRadiusPx, strokePaint)
    }
}
