package com.edgegesture.evilgodxu.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.pow

/**
 * Hiệu ứng cử chỉ Back chuẩn phong cách Android 15 / 16 (Predictive Back Gesture):
 * - Viên thuốc nổi (Floating Pill) với bóng đổ Material 3 Expressive.
 * - Hiệu ứng biến hình đặc trưng Android 16: Mũi tên góc '<' tự mọc thân ngang biến thành mũi tên hoàn chỉnh '←' khi chạm ngưỡng kích hoạt.
 * - Co dãn đàn hồi (squash & stretch) theo lực kéo ngón tay.
 * - Chuyển màu mượt mà sang màu nhấn Material Blue khi sẵn sàng kích hoạt.
 */
class BackGestureIndicatorView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    // Màu sắc theo chuẩn Material 3
    private val uncommittedBgColor = 0xF0202124.toInt() // Dark Surface Container High
    private val committedBgColor = 0xFF1A73E8.toInt()   // Google Primary Blue
    private val shadowColor = 0x66000000                // Bóng đổ nổi bật

    // Nền viên thuốc
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = uncommittedBgColor
        setShadowLayer(5f * density, 0f, 2f * density, shadowColor)
    }

    // Viền tinh tế
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x26FFFFFF // 15% trắng mờ
    }

    // Mũi tên Material 3
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val bubblePath = Path()
    private val arrowPath = Path()
    private val capsuleRect = RectF()

    private var isLeftEdge: Boolean = true
    private var touchY: Float = 0f
    private var currentExtensionPx: Float = 0f
    private var arrowScale: Float = 1f
    private var currentAlpha: Float = 0f
    private var isTriggerReady: Boolean = false
    private var morphStemProgress: Float = 0f // 0f = '<' (chưa có thân), 1f = '←' (mọc đầy đủ thân)

    private val maxExtensionPx = 52f * density
    private val bubbleRadiusPx = 22f * density

    private var releaseAnimator: ValueAnimator? = null
    private var morphAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Cho phép vẽ ShadowLayer mượt mà
        visibility = INVISIBLE
    }

    fun onSwipeStart(isLeft: Boolean, startY: Float) {
        releaseAnimator?.cancel()
        morphAnimator?.cancel()
        this.isLeftEdge = isLeft
        this.touchY = startY
        this.currentExtensionPx = 0f
        this.arrowScale = 1f
        this.currentAlpha = 1f
        this.isTriggerReady = false
        this.morphStemProgress = 0f
        this.bubblePaint.color = uncommittedBgColor
        this.arrowPaint.color = Color.WHITE
        visibility = VISIBLE
        invalidate()
    }

    fun onSwipeProgress(progress: Float, triggered: Boolean, currentY: Float = touchY) {
        if (visibility != VISIBLE) visibility = VISIBLE
        this.touchY = currentY

        // Dãn nhẹ theo ngón tay (rubber-band)
        val factor = (progress.coerceAtMost(1.5f) / 1.5f).pow(0.82f)
        this.currentExtensionPx = maxExtensionPx * factor

        if (triggered != isTriggerReady) {
            isTriggerReady = triggered
            animateMorph(triggered)
        }

        invalidate()
    }

    private fun animateMorph(toTriggered: Boolean) {
        morphAnimator?.cancel()
        val startScale = arrowScale
        val targetScale = if (toTriggered) 1.22f else 1.0f
        val startStem = morphStemProgress
        val targetStem = if (toTriggered) 1.0f else 0.0f
        val startColor = bubblePaint.color
        val targetColor = if (toTriggered) committedBgColor else uncommittedBgColor

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = if (toTriggered) OvershootInterpolator(1.4f) else DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                arrowScale = startScale + (targetScale - startScale) * fraction
                morphStemProgress = startStem + (targetStem - startStem) * fraction
                bubblePaint.color = blendColors(startColor, targetColor, fraction)
                invalidate()
            }
        }
        morphAnimator = anim
        anim.start()
    }

    fun onSwipeRelease(executed: Boolean) {
        releaseAnimator?.cancel()
        morphAnimator?.cancel()
        val startExtension = currentExtensionPx
        val startAlpha = currentAlpha

        if (executed) {
            // Android 16 Commit Pop: Mũi tên phóng nhẹ về phía trước rồi biến mất
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    currentExtensionPx = startExtension * (1f + 0.18f * (1f - fraction))
                    currentAlpha = startAlpha * (1f - fraction)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetAndHide()
                    }
                })
            }
            releaseAnimator = anim
            anim.start()
        } else {
            // Hủy vuốt: Co rút đàn hồi về mép
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 190
                interpolator = OvershootInterpolator(1.3f)
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    currentExtensionPx = startExtension * (1f - fraction)
                    currentAlpha = startAlpha * (1f - fraction)
                    morphStemProgress = (1f - fraction)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetAndHide()
                    }
                })
            }
            releaseAnimator = anim
            anim.start()
        }
    }

    private fun resetAndHide() {
        currentExtensionPx = 0f
        currentAlpha = 0f
        isTriggerReady = false
        morphStemProgress = 0f
        visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        if (currentAlpha <= 0.01f || currentExtensionPx <= 2f) return

        val viewWidth = width.toFloat()
        val r = bubbleRadiusPx
        // Tọa độ tâm viên thuốc
        val centerX = if (isLeftEdge) {
            (currentExtensionPx - r * 0.35f).coerceAtLeast(r)
        } else {
            viewWidth - (currentExtensionPx - r * 0.35f).coerceAtLeast(r)
        }
        // Áp dụng mFingerOffset chuẩn AOSP: nâng nhẹ 8dp để ngón cái không che khuất mũi tên
        val centerY = touchY - 8f * density

        // 1. Vẽ cuống dẻo kết nối với mép viền (Fluid Stem)
        bubblePath.reset()
        val stemHalfH = r * 1.35f
        if (isLeftEdge) {
            bubblePath.moveTo(0f, centerY - stemHalfH)
            bubblePath.cubicTo(
                centerX * 0.45f, centerY - stemHalfH * 0.85f,
                centerX - r, centerY - r,
                centerX, centerY - r
            )
            capsuleRect.set(centerX - r, centerY - r, centerX + r, centerY + r)
            bubblePath.arcTo(capsuleRect, -90f, 180f, false)
            bubblePath.cubicTo(
                centerX - r, centerY + r,
                centerX * 0.45f, centerY + stemHalfH * 0.85f,
                0f, centerY + stemHalfH
            )
            bubblePath.close()
        } else {
            bubblePath.moveTo(viewWidth, centerY - stemHalfH)
            bubblePath.cubicTo(
                viewWidth - (viewWidth - centerX) * 0.45f, centerY - stemHalfH * 0.85f,
                centerX + r, centerY - r,
                centerX, centerY - r
            )
            capsuleRect.set(centerX - r, centerY - r, centerX + r, centerY + r)
            bubblePath.arcTo(capsuleRect, -90f, -180f, false)
            bubblePath.cubicTo(
                centerX + r, centerY + r,
                viewWidth - (viewWidth - centerX) * 0.45f, centerY + stemHalfH * 0.85f,
                viewWidth, centerY + stemHalfH
            )
            bubblePath.close()
        }

        val originalBubbleAlpha = Color.alpha(bubblePaint.color)
        bubblePaint.alpha = (originalBubbleAlpha * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(bubblePath, bubblePaint)
        canvas.drawPath(bubblePath, strokePaint)

        // 2. Vẽ mũi tên Android 16 (Chevron '<' mọc thân ngang biến thành '←')
        val arrowWing = 7.5f * density * arrowScale
        val maxStemLength = 11f * density * arrowScale
        val currentStemLength = maxStemLength * morphStemProgress

        arrowPath.reset()
        if (isLeftEdge) {
            // Mũi tên chỉ sang trái: vertex tại (arrowVertexX, centerY)
            val arrowVertexX = centerX - 3.5f * density * arrowScale
            // Hai cánh chevron '<'
            arrowPath.moveTo(arrowVertexX + arrowWing * 0.55f, centerY - arrowWing)
            arrowPath.lineTo(arrowVertexX, centerY)
            arrowPath.lineTo(arrowVertexX + arrowWing * 0.55f, centerY + arrowWing)

            // Thân ngang '—' mọc dài dần ra sau
            if (currentStemLength > 0.5f) {
                arrowPath.moveTo(arrowVertexX, centerY)
                arrowPath.lineTo(arrowVertexX + currentStemLength, centerY)
            }
        } else {
            // Mũi tên chỉ sang phải: vertex tại (arrowVertexX, centerY)
            val arrowVertexX = centerX + 3.5f * density * arrowScale
            // Hai cánh chevron '>'
            arrowPath.moveTo(arrowVertexX - arrowWing * 0.55f, centerY - arrowWing)
            arrowPath.lineTo(arrowVertexX, centerY)
            arrowPath.lineTo(arrowVertexX - arrowWing * 0.55f, centerY + arrowWing)

            // Thân ngang '—' mọc dài dần ra trước
            if (currentStemLength > 0.5f) {
                arrowPath.moveTo(arrowVertexX, centerY)
                arrowPath.lineTo(arrowVertexX - currentStemLength, centerY)
            }
        }

        arrowPaint.alpha = (255 * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inverseRatio + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inverseRatio + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inverseRatio + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}
