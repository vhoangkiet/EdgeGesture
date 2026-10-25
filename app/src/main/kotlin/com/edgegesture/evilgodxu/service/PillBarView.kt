package com.edgegesture.evilgodxu.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator

class PillBarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val normalFillColor = 0xE6FFFFFF.toInt() // Trắng đục 90%
    private val recentsFillColor = 0xFF4285F4.toInt() // Xanh Google khi giữ mở Recents

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = normalFillColor
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000 // Viền mờ màu tối giúp nhìn rõ trên nền trắng
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val pillRect = RectF()
    private var pillWidthPx = 72f * density
    private var pillHeightPx = 4f * density
    private var cornerRadiusPx = 2f * density
    private var marginBottomPx = 6f * density

    private var currentTranslationY = 0f
    private var currentScaleX = 1f
    private var currentScaleY = 1f

    private var bounceAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    fun updateDimensions(widthPx: Float, heightPx: Float, marginBottomPx: Float) {
        this.pillWidthPx = widthPx
        this.pillHeightPx = heightPx
        this.cornerRadiusPx = heightPx / 2f
        this.marginBottomPx = marginBottomPx
        updateRect()
        invalidate()
    }

    private fun updateRect() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val halfW = (pillWidthPx * currentScaleX) / 2f
        val halfH = (pillHeightPx * currentScaleY) / 2f
        val centerX = w / 2f
        val centerY = h - marginBottomPx - (pillHeightPx / 2f) + currentTranslationY

        pillRect.set(
            centerX - halfW,
            centerY - halfH,
            centerX + halfW,
            centerY + halfH
        )
    }

    fun onSwipeProgress(dy: Float, progress: Float, isHoldingRecents: Boolean) {
        bounceAnimator?.cancel()

        // Độ dịch chuyển theo ngón tay (có hãm đà)
        currentTranslationY = - (dy * 0.45f).coerceAtMost(48f * density)

        if (isHoldingRecents) {
            // Khi giữ lâu kích hoạt Recent Apps: dãn rộng và đổi màu nổi bật
            currentScaleX = 1.35f
            currentScaleY = 1.15f
            fillPaint.color = recentsFillColor
        } else {
            // Đàn hồi theo chiều vuốt
            currentScaleX = 1f + (progress * 0.12f).coerceAtMost(0.25f)
            currentScaleY = (1f - (progress * 0.08f)).coerceAtLeast(0.75f)
            fillPaint.color = normalFillColor
        }

        updateRect()
        invalidate()
    }

    fun onSwipeRelease() {
        bounceAnimator?.cancel()

        val startY = currentTranslationY
        val startSx = currentScaleX
        val startSy = currentScaleY

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.4f) // Nảy nhẹ đặc trưng phong cách Pixel
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                currentTranslationY = startY * (1f - fraction)
                currentScaleX = startSx + (1f - startSx) * fraction
                currentScaleY = startSy + (1f - startSy) * fraction
                fillPaint.color = normalFillColor
                updateRect()
                invalidate()
            }
        }
        bounceAnimator = anim
        anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(pillRect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        canvas.drawRoundRect(pillRect, cornerRadiusPx, cornerRadiusPx, strokePaint)
    }
}
