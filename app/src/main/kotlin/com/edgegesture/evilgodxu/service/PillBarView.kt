package com.edgegesture.evilgodxu.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Thanh điều hướng Pixel Navigation Pill (chuẩn Google Pixel / Android gốc):
 * - Neo cố định sát đáy màn hình, luôn giữ màu trắng đục tinh tế (không đổi màu xanh to đùng).
 * - Khi vuốt lên: chỉ nhấc nhẹ đàn hồi 8dp - 10dp (tinh tế, không bay lên cao giữa màn hình).
 * - Khi vuốt ngang: trượt nhẹ 14dp theo ngón tay để chuyển nhanh ứng dụng lân cận.
 */
class PillBarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val normalFillColor = 0xF2FFFFFF.toInt() // Trắng đục 95% chuẩn Pixel
    private val shadowColor = 0x4D000000            // Bóng đổ nhẹ

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = normalFillColor
        style = Paint.Style.FILL
        setShadowLayer(2.5f * density, 0f, 1f * density, shadowColor)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2E000000 // Viền mờ chống lẫn trên nền trắng sáng
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val pillRect = RectF()
    private var pillWidthPx = 108f * density
    private var pillHeightPx = 4f * density
    private var cornerRadiusPx = 2f * density
    private var marginBottomPx = 8f * density

    private var currentTranslationX = 0f
    private var currentTranslationY = 0f
    private var currentScaleX = 1f
    private var currentScaleY = 1f

    private var bounceAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)
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
        val centerX = w / 2f + currentTranslationX
        val centerY = h - marginBottomPx - (pillHeightPx / 2f) + currentTranslationY

        pillRect.set(
            centerX - halfW,
            centerY - halfH,
            centerX + halfW,
            centerY + halfH
        )
    }

    fun onSwipeProgress(dx: Float, dy: Float, progress: Float, isHoldingRecents: Boolean) {
        bounceAnimator?.cancel()

        if (abs(dx) > dy * 1.2f) {
            // 1. Vuốt ngang chuyển app lân cận (Quick Switch): trượt nhẹ tinh tế tối đa 16dp
            currentTranslationX = (dx * 0.35f).coerceIn(-16f * density, 16f * density)
            currentTranslationY = 0f
            currentScaleX = 1f
            currentScaleY = 1f
        } else {
            // 2. Vuốt dọc: chỉ nhấc nhẹ 8dp - 10dp khỏi đáy, không bao giờ bay lên cao giữa màn hình
            currentTranslationX = 0f
            currentTranslationY = - (dy * 0.16f).coerceAtMost(10f * density)

            if (isHoldingRecents) {
                currentScaleX = 1.08f
                currentScaleY = 1f
            } else {
                currentScaleX = 1f + (progress * 0.04f).coerceAtMost(0.08f)
                currentScaleY = 1f
            }
        }

        // Luôn giữ màu trắng đục thanh lịch của Pixel
        fillPaint.color = normalFillColor

        updateRect()
        invalidate()
    }

    fun onSwipeRelease() {
        bounceAnimator?.cancel()

        val startX = currentTranslationX
        val startY = currentTranslationY
        val startSx = currentScaleX

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 190
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                currentTranslationX = startX * (1f - fraction)
                currentTranslationY = startY * (1f - fraction)
                currentScaleX = startSx + (1f - startSx) * fraction
                currentScaleY = 1f
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
