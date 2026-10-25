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
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    // Ngưỡng phát hiện cử chỉ (dp -> px)
    private val swipeThresholdPx = 36f * density
    private val longPressRecentsDelayMs = 280L

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
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!triggered) {
                        val dx = if (isLeft) (event.rawX - startX) else (startX - event.rawX)
                        val dy = abs(event.rawY - startY)

                        // Vuốt vào phía trong màn hình vượt ngưỡng và góc vuốt hợp lý
                        if (dx > swipeThresholdPx && dx > dy * 0.8f) {
                            triggered = true
                            triggerVibration(settings, 20)
                            callback.onBack()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                    actionTriggered = false
                    isHolding = false

                    // Lên lịch kiểm tra vuốt lên và giữ (Recent Apps)
                    recentsRunnable = Runnable {
                        if (!actionTriggered && isHolding) {
                            actionTriggered = true
                            triggerVibration(settings, 35)
                            callback.onRecentApps()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = startY - event.rawY // Vuốt lên -> dy > 0
                    val dx = abs(event.rawX - startX)

                    if (!actionTriggered) {
                        if (dy > swipeThresholdPx && dy > dx * 0.7f) {
                            if (!isHolding) {
                                isHolding = true
                                recentsRunnable?.let { handler.postDelayed(it, longPressRecentsDelayMs) }
                            }
                        } else {
                            if (isHolding) {
                                isHolding = false
                                recentsRunnable?.let { handler.removeCallbacks(it) }
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    recentsRunnable?.let { handler.removeCallbacks(it) }

                    val dy = startY - event.rawY
                    val dx = abs(event.rawX - startX)

                    if (!actionTriggered) {
                        if (dy > swipeThresholdPx && dy > dx * 0.7f) {
                            // Vuốt lên thả tay nhanh -> Home
                            actionTriggered = true
                            triggerVibration(settings, 25)
                            callback.onHome()
                        }
                    }
                    actionTriggered = false
                    isHolding = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    recentsRunnable?.let { handler.removeCallbacks(it) }
                    actionTriggered = false
                    isHolding = false
                    true
                }
                else -> false
            }
        }
    }
}
