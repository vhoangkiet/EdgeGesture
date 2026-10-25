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
 * Hiệu ứng vuốt Back (chuẩn phong cách MIUI 12 / Android 10):
 * - Vòng tròn viên thuốc tinh tế nối với mép viền bằng cuống dẻo.
 * - Mũi tên chevron góc 45 độ cân đối, sắc nét.
 * - Tự động bám theo tọa độ ngón tay di chuyển cả trục X và trục Y.
 * - Khi đạt ngưỡng kích hoạt: vòng tròn phóng to nhẹ và đổi màu xanh Material kèm rung phản hồi.
 */
class BackGestureIndicatorView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    // Nền viên thuốc
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD252525.toInt() // Xám đen bóng bẩy
    }

    // Viền nhẹ cho viên thuốc để nổi bật trên nền đen/trắng
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x33FFFFFF
    }

    // Mũi tên chevron
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val bubblePath = Path()
    private val arrowPath = Path()
    private val circleRect = RectF()

    private var isLeftEdge: Boolean = true
    private var touchY: Float = 0f
    private var currentExtensionPx: Float = 0f
    private var arrowScale: Float = 1f
    private var currentAlpha: Float = 0f
    private var isTriggerReady: Boolean = false

    private val maxExtensionPx = 48f * density
    private val bubbleRadiusPx = 20f * density

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
        this.currentExtensionPx = 0f
        this.arrowScale = 1f
        this.currentAlpha = 1f
        this.isTriggerReady = false
        this.bubblePaint.color = 0xDD252525.toInt()
        this.arrowPaint.color = Color.WHITE
        visibility = VISIBLE
        invalidate()
    }

    fun onSwipeProgress(progress: Float, triggered: Boolean, currentY: Float = touchY) {
        if (visibility != VISIBLE) visibility = VISIBLE
        this.touchY = currentY
        this.isTriggerReady = triggered

        // Độ trượt theo ngón tay (rubber-band)
        val factor = (progress.coerceAtMost(1.5f) / 1.5f).pow(0.85f)
        this.currentExtensionPx = maxExtensionPx * factor

        if (triggered) {
            // Khi đạt ngưỡng: vòng tròn nảy nhẹ lên 1.2x và đổi màu xanh nổi bật
            this.arrowScale = 1.2f
            this.bubblePaint.color = 0xEE1A73E8.toInt() // Xanh Google Material
        } else {
            this.arrowScale = 1.0f
            this.bubblePaint.color = 0xDD252525.toInt()
        }
        invalidate()
    }

    fun onSwipeRelease(executed: Boolean) {
        releaseAnimator?.cancel()
        val startExtension = currentExtensionPx
        val startAlpha = currentAlpha

        if (executed) {
            // Kích hoạt thành công: pop nảy nhẹ rồi biến mất
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    currentExtensionPx = startExtension * (1f + 0.15f * (1f - fraction))
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
            // Hủy vuốt: co rút đàn hồi về mép
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                interpolator = OvershootInterpolator(1.2f)
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    currentExtensionPx = startExtension * (1f - fraction)
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
        currentExtensionPx = 0f
        currentAlpha = 0f
        isTriggerReady = false
        visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        if (currentAlpha <= 0.01f || currentExtensionPx <= 2f) return

        val viewWidth = width.toFloat()
        val r = bubbleRadiusPx * arrowScale
        val circleCenterX = if (isLeftEdge) {
            currentExtensionPx - r * 0.4f
        } else {
            viewWidth - currentExtensionPx + r * 0.4f
        }
        val circleCenterY = touchY

        // 1. Vẽ cuống dẻo nối từ mép tới vòng tròn (Fluid teardrop stem)
        bubblePath.reset()
        val stemHeight = r * 2.4f
        if (isLeftEdge) {
            bubblePath.moveTo(0f, circleCenterY - stemHeight / 2f)
            bubblePath.quadTo(
                circleCenterX * 0.5f, circleCenterY - r * 0.6f,
                circleCenterX, circleCenterY - r
            )
            circleRect.set(circleCenterX - r, circleCenterY - r, circleCenterX + r, circleCenterY + r)
            bubblePath.arcTo(circleRect, -90f, 180f, false)
            bubblePath.quadTo(
                circleCenterX * 0.5f, circleCenterY + r * 0.6f,
                0f, circleCenterY + stemHeight / 2f
            )
            bubblePath.close()
        } else {
            bubblePath.moveTo(viewWidth, circleCenterY - stemHeight / 2f)
            bubblePath.quadTo(
                viewWidth - (viewWidth - circleCenterX) * 0.5f, circleCenterY - r * 0.6f,
                circleCenterX, circleCenterY - r
            )
            circleRect.set(circleCenterX - r, circleCenterY - r, circleCenterX + r, circleCenterY + r)
            bubblePath.arcTo(circleRect, -90f, -180f, false)
            bubblePath.quadTo(
                viewWidth - (viewWidth - circleCenterX) * 0.5f, circleCenterY + r * 0.6f,
                viewWidth, circleCenterY + stemHeight / 2f
            )
            bubblePath.close()
        }

        val originalBubbleAlpha = Paint(bubblePaint).alpha
        bubblePaint.alpha = (originalBubbleAlpha * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(bubblePath, bubblePaint)
        canvas.drawPath(bubblePath, strokePaint)

        // 2. Vẽ mũi tên chevron chuẩn 45 độ sắc nét
        val arrowWing = 7.5f * density * arrowScale
        arrowPath.reset()
        if (isLeftEdge) {
            // Mũi tên chỉ sang trái: <
            arrowPath.moveTo(circleCenterX + arrowWing * 0.5f, circleCenterY - arrowWing)
            arrowPath.lineTo(circleCenterX - arrowWing * 0.5f, circleCenterY)
            arrowPath.lineTo(circleCenterX + arrowWing * 0.5f, circleCenterY + arrowWing)
        } else {
            // Mũi tên chỉ sang phải: >
            arrowPath.moveTo(circleCenterX - arrowWing * 0.5f, circleCenterY - arrowWing)
            arrowPath.lineTo(circleCenterX + arrowWing * 0.5f, circleCenterY)
            arrowPath.lineTo(circleCenterX - arrowWing * 0.5f, circleCenterY + arrowWing)
        }

        arrowPaint.alpha = (255 * currentAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(arrowPath, arrowPaint)
    }
}
