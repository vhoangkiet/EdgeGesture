package com.edgegesture.evilgodxu.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.edgegesture.evilgodxu.data.LiteSettings

class AccessibilityEdgeViewManager(
    private val context: Context,
    private val gestureDetector: LiteGestureDetector
) : LiteGestureDetector.GestureFeedbackListener {

    companion object {
        private const val TAG = "EdgeViewManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var leftView: View? = null
    private var rightView: View? = null
    private var bottomView: View? = null
    private var pillView: PillBarView? = null
    private var backIndicatorView: BackGestureIndicatorView? = null

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 2340
    private var density: Float = 1f

    private var isKeyboardVisible: Boolean = false
    private var isAnimationEnabled: Boolean = true

    init {
        gestureDetector.feedbackListener = this
    }

    fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            screenWidth = size.x
            screenHeight = size.y
        }
        density = context.resources.displayMetrics.density
    }

    fun applySettings(settings: LiteSettings, settingsProvider: () -> LiteSettings) {
        updateScreenDimensions()
        removeViews()

        if (!settings.isServiceEnabled) return
        this.isAnimationEnabled = settings.showSwipeAnimation

        // 1. Tạo và gắn màn hình vẽ hiệu ứng vuốt Back (mũi tên / giọt nước)
        if (settings.showSwipeAnimation) {
            val indicator = BackGestureIndicatorView(context)
            val indicatorParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            backIndicatorView = indicator
            safeAddView(indicator, indicatorParams)
        }

        // 2. Tạo và gắn cạnh trái (Back)
        if (settings.isLeftEnabled) {
            val leftWidthPx = (settings.leftWidthDp * density).toInt()
            val leftHeightPx = (screenHeight * settings.leftHeightPercent / 100)
            val leftY = ((screenHeight - leftHeightPx) * settings.leftPositionPercent / 100)

            val params = createTouchLayoutParams(
                width = leftWidthPx,
                height = leftHeightPx,
                gravity = Gravity.START or Gravity.TOP,
                x = 0,
                y = leftY
            )
            val view = View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                gestureDetector.attachSideEdge(this, isLeft = true, settingsProvider)
            }
            leftView = view
            safeAddView(view, params)
        }

        // 3. Tạo và gắn cạnh phải (Back)
        if (settings.isRightEnabled) {
            val rightWidthPx = (settings.rightWidthDp * density).toInt()
            val rightHeightPx = (screenHeight * settings.rightHeightPercent / 100)
            val rightY = ((screenHeight - rightHeightPx) * settings.rightPositionPercent / 100)

            val params = createTouchLayoutParams(
                width = rightWidthPx,
                height = rightHeightPx,
                gravity = Gravity.END or Gravity.TOP,
                x = 0,
                y = rightY
            )
            val view = View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                gestureDetector.attachSideEdge(this, isLeft = false, settingsProvider)
            }
            rightView = view
            safeAddView(view, params)
        }

        // 4. Tạo và gắn cạnh dưới (Home & Recent Apps)
        if (settings.isBottomEnabled && (!isKeyboardVisible || !settings.avoidKeyboard)) {
            val bottomWidthPx = (screenWidth * settings.bottomWidthPercent / 100)
            val bottomHeightPx = (settings.bottomHeightDp * density).toInt()

            val params = createTouchLayoutParams(
                width = bottomWidthPx,
                height = bottomHeightPx,
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                x = 0,
                y = 0
            )
            val view = View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                gestureDetector.attachBottomEdge(this, settingsProvider)
            }
            bottomView = view
            safeAddView(view, params)
        }

        // 5. Tạo và gắn thanh Pixel Navigation Pill
        if (settings.showPill && (!isKeyboardVisible || !settings.avoidKeyboard)) {
            val pillWidthPx = settings.pillWidthDp * density
            val pillHeightPx = settings.pillHeightDp * density
            val pillMarginBottomPx = settings.pillBottomMarginDp * density
            // Chiều cao container nhỏ gọn sát đáy màn hình
            val containerHeightPx = (pillHeightPx + pillMarginBottomPx + 16f * density).toInt()

            val pill = PillBarView(context).apply {
                updateDimensions(pillWidthPx, pillHeightPx, pillMarginBottomPx)
            }
            val pillParams = createPillLayoutParams(
                width = (pillWidthPx * 1.3f).toInt(),
                height = containerHeightPx,
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                x = 0,
                y = 0
            )
            pillView = pill
            safeAddView(pill, pillParams)
        }
    }

    // Callbacks từ GestureFeedbackListener
    override fun onSideSwipeStart(isLeft: Boolean, startY: Float) {
        if (isAnimationEnabled) {
            backIndicatorView?.onSwipeStart(isLeft, startY)
        }
    }

    override fun onSideSwipeProgress(isLeft: Boolean, progress: Float, readyToTrigger: Boolean, currentY: Float) {
        if (isAnimationEnabled) {
            backIndicatorView?.onSwipeProgress(progress, readyToTrigger, currentY)
        }
    }

    override fun onSideSwipeRelease(isLeft: Boolean, executed: Boolean) {
        if (isAnimationEnabled) {
            backIndicatorView?.onSwipeRelease(executed)
        }
    }

    override fun onBottomSwipeProgress(dx: Float, dy: Float, progress: Float, isHoldingRecents: Boolean) {
        if (isAnimationEnabled) {
            pillView?.onSwipeProgress(dx, dy, progress, isHoldingRecents)
        }
    }

    override fun onBottomSwipeRelease() {
        if (isAnimationEnabled) {
            pillView?.onSwipeRelease()
        }
    }

    fun onKeyboardVisibilityChanged(visible: Boolean, settingsProvider: () -> LiteSettings) {
        if (isKeyboardVisible == visible) return
        isKeyboardVisible = visible
        val settings = settingsProvider()
        if (settings.avoidKeyboard) {
            if (visible) {
                bottomView?.let { safeRemoveView(it); bottomView = null }
                pillView?.let { safeRemoveView(it); pillView = null }
            } else {
                applySettings(settings, settingsProvider)
            }
        }
    }

    private fun createTouchLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    private fun createPillLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Cảm ứng chạm xuyên qua Pill
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    fun removeViews() {
        leftView?.let { safeRemoveView(it); leftView = null }
        rightView?.let { safeRemoveView(it); rightView = null }
        bottomView?.let { safeRemoveView(it); bottomView = null }
        pillView?.let { safeRemoveView(it); pillView = null }
        backIndicatorView?.let { safeRemoveView(it); backIndicatorView = null }
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        try {
            if (view.windowToken == null && view.parent == null) {
                windowManager.addView(view, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "safeAddView failed: ${e.message}")
        }
    }

    private fun safeRemoveView(view: View) {
        try {
            if (view.parent != null) {
                windowManager.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "safeRemoveView failed: ${e.message}")
        }
    }
}
