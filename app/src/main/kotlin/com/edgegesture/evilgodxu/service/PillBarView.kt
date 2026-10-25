package com.edgegesture.evilgodxu.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Thanh điều hướng Pixel Navigation Pill (chuẩn Android 16 Expressive):
 * - Hỗ trợ vuốt ngang chuyển nhanh ứng dụng (Quick Switch app lân cận).
 * - Hỗ trợ vuốt dọc lên Home và kéo giữ mở Recent Apps với cự ly thanh thoát (~60dp).
 * - Hiệu ứng căng dây thun (Elastic tension) và nảy lò xo (Spring bounce).
 */
class PillBarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val normalFillColor = 0xF2FFFFFF.toInt() // Trắng đục 95%
    private val recentsFillColor = 0xFF4285F4.toInt() // Xanh Google Material Accent
    private val shadowColor = 0x55000000

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = normalFillColor
        style = Paint.Style.FILL
        setShadowLayer(3f * density, 0f, 1.5f * density, shadowColor)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000 // Viền mờ chống lẫn trên nền sáng
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val pillRect = RectF()
    private var pillWidthPx = 72f * density
    private var pillHeightPx = 4.2f * density
    private var cornerRadiusPx = 2.1f * density
    private var marginBottomPx = 6f * density

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

        if (abs(dx) > dy) {
            // 1. Vuốt ngang chuyển app lân cận (Quick Switch): trượt nhẹ theo chiều vuốt
            currentTranslationX = (dx * 0.65f).coerceIn(-48f * density, 48f * density)
            currentTranslationY = 0f
            currentScaleX = 1f + (abs(dx) / 320f).coerceAtMost(0.25f)
            currentScaleY = 1f
            fillPaint.color = normalFillColor
        } else {
            // 2. Vuốt dọc chuẩn Android 16 (cự ly vừa vặn ~60dp, không bị bay quá cao)
            currentTranslationX = 0f
            currentTranslationY = - (dy * 0.55f).coerceAtMost(64f * density)

            if (isHoldingRecents) {
                // Dock expansion khi vào Recent Apps
                currentScaleX = 1.35f
                currentScaleY = 1.2f
                fillPaint.color = recentsFillColor
            } else {
                // Dãn dẹp đàn hồi cao su theo ngón tay
                currentScaleX = 1f + (progress * 0.2f).coerceAtMost(0.32f)
                currentScaleY = (1f - (progress * 0.08f)).coerceAtLeast(0.8f)
                fillPaint.color = normalFillColor
            }
        }

        updateRect()
        invalidate()
    }

    fun onSwipeRelease() {
        bounceAnimator?.cancel()

        val startX = currentTranslationX
        val startY = currentTranslationY
        val startSx = currentScaleX
        val startSy = currentScaleY
        val startColor = fillPaint.color

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.4f) // Spring bounce đặc trưng Android 16
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                currentTranslationX = startX * (1f - fraction)
                currentTranslationY = startY * (1f - fraction)
                currentScaleX = startSx + (1f - startSx) * fraction
                currentScaleY = startSy + (1f - startSy) * fraction
                fillPaint.color = blendColors(startColor, normalFillColor, fraction)
                updateRect()
                invalidate()
            }
        }
        bounceAnimator = anim
        anim.start()
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inverseRatio + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inverseRatio + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inverseRatio + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(pillRect, cornerRadiusPx * currentScaleY, cornerRadiusPx * currentScaleY, fillPaint)
        canvas.drawRoundRect(pillRect, cornerRadiusPx * currentScaleY, cornerRadiusPx * currentScaleY, strokePaint)
    }
}
