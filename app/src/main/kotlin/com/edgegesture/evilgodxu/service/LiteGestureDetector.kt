package com.edgegesture.evilgodxu.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import com.edgegesture.evilgodxu.data.LiteSettings
import kotlin.math.abs

class LiteGestureDetector(
    private val context: Context,
    private val callback: GestureCallback
) {

    interface GestureCallback {
        fun onBack()
        fun onHome()
        fun onRecentApps()
        fun onSwitchToPreviousApp()
    }

    interface GestureFeedbackListener {
        fun onSideSwipeStart(isLeft: Boolean, startY: Float)
        fun onSideSwipeProgress(isLeft: Boolean, progress: Float, readyToTrigger: Boolean, currentY: Float)
        fun onSideSwipeRelease(isLeft: Boolean, executed: Boolean)

        fun onBottomSwipeProgress(dx: Float, dy: Float, progress: Float, isHoldingRecents: Boolean)
        fun onBottomSwipeRelease()
    }

    var feedbackListener: GestureFeedbackListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    // Ngưỡng cử chỉ chuẩn Android 16 (tinh tế, nhạy, chuẩn tỷ lệ)
    private val sideSwipeThresholdPx = 16f * density       // 16dp cho vuốt Back
    private val bottomSwipeThresholdPx = 14f * density     // 14dp cho vuốt lên Home
    private val horizontalSwipeThresholdPx = 22f * density // 22dp cho vuốt ngang chuyển app lân cận
    private val recentsHoldDelayMs = 220L

    fun triggerVibration(settings: LiteSettings, durationMs: Long = 25) {
        if (!settings.vibrateOnTrigger) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachSideEdge(view: View, isLeft: Boolean, settingsProvider: () -> LiteSettings) {
        var startX = 0f
        var startY = 0f
        var triggered = false

        view.setOnTouchListener { _, event ->
            val settings = settingsProvider()
            if (!settings.isServiceEnabled || (if (isLeft) !settings.isLeftEnabled else !settings.isRightEnabled)) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    triggered = false
                    feedbackListener?.onSideSwipeStart(isLeft, startY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = if (isLeft) (event.rawX - startX) else (startX - event.rawX)
                    val dy = abs(event.rawY - startY)
                    val progress = (dx / sideSwipeThresholdPx).coerceAtLeast(0f)
                    val ready = (dx > sideSwipeThresholdPx && dx > dy * 0.7f)

                    if (ready && !triggered) {
                        triggered = true
                        triggerVibration(settings, 18) // Rung phản hồi nhẹ khi chạm ngưỡng
                    } else if (!ready && triggered) {
                        triggered = false
                    }

                    feedbackListener?.onSideSwipeProgress(isLeft, progress, ready, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && triggered) {
                        callback.onBack()
                        feedbackListener?.onSideSwipeRelease(isLeft, executed = true)
                    } else {
                        feedbackListener?.onSideSwipeRelease(isLeft, executed = false)
                    }
                    triggered = false
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachBottomEdge(view: View, settingsProvider: () -> LiteSettings) {
        var startX = 0f
        var startY = 0f
        var lastY = 0f
        var actionTriggered = false
        var isHolding = false

        var recentsRunnable: Runnable? = null

        view.setOnTouchListener { _, event ->
            val settings = settingsProvider()
            if (!settings.isServiceEnabled || !settings.isBottomEnabled) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    lastY = event.rawY
                    actionTriggered = false
                    isHolding = false

                    // Timer kích hoạt Recent Apps khi vuốt lên và giữ lại một nhịp (~220ms)
                    recentsRunnable?.let { handler.removeCallbacks(it) }
                    val runnable = Runnable {
                        val currentDy = (startY - (lastY.takeIf { it > 0 } ?: startY))
                        // Khi ngón tay đã kéo lên một đoạn nhỏ (> 8dp) và dừng giữ tay:
                        if (!actionTriggered && currentDy > 8f * density) {
                            actionTriggered = true
                            isHolding = true
                            triggerVibration(settings, 30)
                            feedbackListener?.onBottomSwipeProgress(0f, currentDy, 1f, isHoldingRecents = true)
                            callback.onRecentApps()
                        }
                    }
                    recentsRunnable = runnable
                    handler.postDelayed(runnable, recentsHoldDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rawDx = event.rawX - startX
                    val dy = (startY - event.rawY).coerceAtLeast(0f)
                    val absDx = abs(rawDx)
                    val progress = (dy / bottomSwipeThresholdPx).coerceAtLeast(0f)

                    lastY = event.rawY

                    // Kiểm tra vuốt ngang trên thanh Pill để chuyển sang ứng dụng vừa dùng (Quick Switch)
                    if (absDx > dy * 1.25f && absDx >= horizontalSwipeThresholdPx) {
                        if (!actionTriggered) {
                            actionTriggered = true
                            recentsRunnable?.let {
                                handler.removeCallbacks(it)
                                recentsRunnable = null
                            }
                            triggerVibration(settings, 25)
                            callback.onSwitchToPreviousApp()
                        }
                        feedbackListener?.onBottomSwipeProgress(rawDx, dy, 1f, false)
                    } else {
                        feedbackListener?.onBottomSwipeProgress(0f, dy, progress, isHolding)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    recentsRunnable?.let {
                        handler.removeCallbacks(it)
                        recentsRunnable = null
                    }
                    feedbackListener?.onBottomSwipeRelease()

                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val dy = startY - event.rawY
                        val dx = abs(event.rawX - startX)

                        // Vuốt lên và thả tay (không giữ) -> Về Home
                        if (!actionTriggered && dy > bottomSwipeThresholdPx && dy > dx * 0.6f) {
                            triggerVibration(settings, 20)
                            callback.onHome()
                        }
                    }
                    actionTriggered = false
                    isHolding = false
                    lastY = 0f
                    true
                }
                else -> false
            }
        }
    }
}
