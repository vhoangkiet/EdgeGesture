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
) {
    companion object {
        private const val TAG = "EdgeViewManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var leftView: View? = null
    private var rightView: View? = null
    private var bottomView: View? = null
    private var pillView: PillBarView? = null

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 2340
    private var density: Float = 1f

    private var isKeyboardVisible: Boolean = false

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

        // 1. Tạo và gắn cạnh trái (Back)
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

        // 2. Tạo và gắn cạnh phải (Back)
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

        // 3. Tạo và gắn cạnh dưới (Home & Recent Apps)
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

        // 4. Tạo và gắn thanh Pixel Navigation Pill
        if (settings.showPill && (!isKeyboardVisible || !settings.avoidKeyboard)) {
            val pillWidthPx = settings.pillWidthDp * density
            val pillHeightPx = settings.pillHeightDp * density
            val pillMarginBottomPx = (settings.pillBottomMarginDp * density).toInt()
            val containerHeightPx = (pillHeightPx + pillMarginBottomPx * 2).toInt()

            val pill = PillBarView(context).apply {
                updateDimensions(pillWidthPx, pillHeightPx)
            }
            val pillParams = createPillLayoutParams(
                width = (pillWidthPx * 1.4f).toInt(),
                height = containerHeightPx,
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                x = 0,
                y = 0
            )
            pillView = pill
            safeAddView(pill, pillParams)
        }
    }

    fun onKeyboardVisibilityChanged(visible: Boolean, settingsProvider: () -> LiteSettings) {
        if (isKeyboardVisible == visible) return
        isKeyboardVisible = visible
        val settings = settingsProvider()
        if (settings.avoidKeyboard) {
            // Khi bàn phím (Laban Key) mở, tạm thời ẩn cạnh dưới & pill để tránh chạm nhầm phím Space
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Touches pass through pill
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
