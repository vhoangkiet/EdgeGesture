package com.edgegesture.evilgodxu.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min
import kotlin.math.pow

/**
 * Hiệu ứng vuốt cạnh trái/phải (Back Gesture Indicator):
 * - Vẽ bong bóng giọt nước (organic curve) dãn từ mép màn hình theo ngón tay.
 * - Vẽ mũi tên quay lại (< hoặc >) nở to và đổi màu sáng khi đạt ngưỡng kích hoạt.
 * - Hỗ trợ hủy vuốt mượt mà khi người dùng kéo ngược về mép.
 */
class BackGestureIndicatorView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    // Cọ vẽ bong bóng giọt nước nền
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC1E1E1E.toInt() // Màu xám đen mờ ban đầu
    }

    // Cọ vẽ mũi tên
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xCCFFFFFF.toInt()
    }

    private val bubblePath = Path()
    private val arrowPath = Path()

    private var isLeftEdge: Boolean = true
    private var touchY: Float = 0f
    private var extensionPx: Float = 0f
    private var arrowScale: Float = 1f
    private var currentAlpha: Float = 0f
    private var isTriggerReady: Boolean = false

    private val maxExtensionPx = 54f * density
    private val bubbleHeightPx = 110f * density

    private var releaseAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        visibility = INVISIBLE
    }

    fun onSwipeStart(isLeft: Boolean, startY: Float) {
        releaseAnimator?.cancel()
        this.isLeftEdge = isLeft
        this.touchY = startY
        this.extensionPx = 0f
        this.arrowScale = 1f
        this.currentAlpha = 1f
        this.isTriggerReady = false
        this.bubblePaint.color = 0xCC1E1E1E.toInt()
        this.arrowPaint.color = 0xCCFFFFFF.toInt()
        visibility = VISIBLE
        invalidate()
    }

    fun onSwipeProgress(progress: Float, triggered: Boolean) {
        if (visibility != VISIBLE) visibility = VISIBLE
        this.isTriggerReady = triggered

        // Dãn dẹp theo hàm làm mượt rubber-band
        val factor = (progress.coerceAtMost(1.6f) / 1.6f).pow(0.8f)
        this.extensionPx = maxExtensionPx * factor

        if (triggered) {
            // Khi đã đạt ngưỡng kích hoạt: Mũi tên nở to, nền chuyển màu nhấn xanh / xám sáng
            this.arrowScale = 1.25f
            this.bubblePaint.color = 0xEE1A73E8.toInt() // Xanh Google Material
            this.arrowPaint.color = Color.WHITE
        } else {
            this.arrowScale = 1.0f
            this.bubblePaint.color = 0xCC1E1E1E.toInt()
            this.arrowPaint.color = 0xCCFFFFFF.toInt()
        }
        invalidate()
    }

    fun onSwipeRelease(executed: Boolean) {
        releaseAnimator?.cancel()
        val startExtension = extensionPx
        val startAlpha = currentAlpha

        if (executed) {
            // Kích hoạt thành công: pop nhẹ rồi mờ dần
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    extensionPx = startExtension * (1f + 0.15f * (1f - fraction))
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
            // Hủy vuốt: co rút đàn hồi trở lại mép
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                interpolator = OvershootInterpolator(1.2f)
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    extensionPx = startExtension * (1f - fraction)
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
        }
    }

    private fun resetAndHide() {
        extensionPx = 0f
        currentAlpha = 0f
        isTriggerReady = false
        visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        if (currentAlpha <= 0.01f || extensionPx <= 1f) return

        val viewWidth = width.toFloat()
        val halfH = bubbleHeightPx / 2f
        val topY = touchY - halfH
        val bottomY = touchY + halfH

        // 1. Vẽ bong bóng giọt nước
        bubblePath.reset()
        if (isLeftEdge) {
            bubblePath.moveTo(0f, topY)
            bubblePath.cubicTo(
                0f, topY + halfH * 0.4f,
                extensionPx, touchY - halfH * 0.35f,
                extensionPx, touchY
            )
            bubblePath.cubicTo(
                extensionPx, touchY + halfH * 0.35f,
                0f, bottomY - halfH * 0.4f,
                0f, bottomY
            )
            bubblePath.close()
        } else {
            bubblePath.moveTo(viewWidth, topY)
            bubblePath.cubicTo(
                viewWidth, topY + halfH * 0.4f,
                viewWidth - extensionPx, touchY - halfH * 0.35f,
                viewWidth - extensionPx, touchY
            )
            bubblePath.cubicTo(
                viewWidth - extensionPx, touchY + halfH * 0.35f,
                viewWidth, bottomY - halfH * 0.4f,
                viewWidth, bottomY
            )
            bubblePath.close()
        }

        val originalBubbleAlpha = Paint(bubblePaint).alpha
        bubblePaint.alpha = (originalBubbleAlpha * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(bubblePath, bubblePaint)

        // 2. Vẽ mũi tên quay lại
        val arrowSize = 9f * density * arrowScale
        val arrowCenterX = if (isLeftEdge) {
            (extensionPx * 0.58f).coerceAtLeast(14f * density)
        } else {
            viewWidth - (extensionPx * 0.58f).coerceAtLeast(14f * density)
        }

        arrowPath.reset()
        if (isLeftEdge) {
            // Mũi tên chỉ sang trái: <
            arrowPath.moveTo(arrowCenterX + arrowSize * 0.45f, touchY - arrowSize)
            arrowPath.lineTo(arrowCenterX - arrowSize * 0.45f, touchY)
            arrowPath.lineTo(arrowCenterX + arrowSize * 0.45f, touchY + arrowSize)
        } else {
            // Mũi tên chỉ sang phải: >
            arrowPath.moveTo(arrowCenterX - arrowSize * 0.45f, touchY - arrowSize)
            arrowPath.lineTo(arrowCenterX + arrowSize * 0.45f, touchY)
            arrowPath.lineTo(arrowCenterX - arrowSize * 0.45f, touchY + arrowSize)
        }

        val originalArrowAlpha = Paint(arrowPaint).alpha
        arrowPaint.alpha = (originalArrowAlpha * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(arrowPath, arrowPaint)
    }
}
