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

    interface GestureFeedbackListener {
        fun onSideSwipeStart(isLeft: Boolean, startY: Float)
        fun onSideSwipeProgress(isLeft: Boolean, progress: Float, readyToTrigger: Boolean)
        fun onSideSwipeRelease(isLeft: Boolean, executed: Boolean)

        fun onBottomSwipeProgress(dy: Float, progress: Float, isHoldingRecents: Boolean)
        fun onBottomSwipeRelease()
    }

    var feedbackListener: GestureFeedbackListener? = null

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
                    feedbackListener?.onSideSwipeStart(isLeft, startY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = if (isLeft) (event.rawX - startX) else (startX - event.rawX)
                    val dy = abs(event.rawY - startY)
                    val progress = (dx / swipeThresholdPx).coerceAtLeast(0f)
                    val ready = (dx > swipeThresholdPx && dx > dy * 0.8f)

                    if (ready && !triggered) {
                        triggered = true
                        triggerVibration(settings, 18) // Rung nhẹ phản hồi khi chạm ngưỡng
                    } else if (!ready && triggered) {
                        triggered = false
                    }

                    feedbackListener?.onSideSwipeProgress(isLeft, progress, ready)
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

                    recentsRunnable?.let { handler.removeCallbacks(it) }
                    val runnable = Runnable {
                        if (!actionTriggered) {
                            isHolding = true
                            actionTriggered = true
                            triggerVibration(settings, 30)
                            feedbackListener?.onBottomSwipeProgress(swipeThresholdPx, 1f, isHoldingRecents = true)
                            callback.onRecentApps()
                        }
                    }
                    recentsRunnable = runnable
                    handler.postDelayed(runnable, longPressRecentsDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (startY - event.rawY).coerceAtLeast(0f)
                    val dx = abs(event.rawX - startX)
                    val progress = (dy / swipeThresholdPx).coerceAtLeast(0f)

                    feedbackListener?.onBottomSwipeProgress(dy, progress, isHolding)

                    // Nếu ngón tay vuốt dứt khoát lên trên -> hủy đếm Recents để kích hoạt Home khi nhả tay
                    if (dy > swipeThresholdPx * 1.4f || dx > swipeThresholdPx) {
                        recentsRunnable?.let {
                            handler.removeCallbacks(it)
                            recentsRunnable = null
                        }
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

                        // Nếu chưa kích hoạt Recents và vuốt lên vượt ngưỡng -> Kích hoạt Home
                        if (!actionTriggered && dy > swipeThresholdPx && dy > dx * 0.8f) {
                            triggerVibration(settings, 20)
                            callback.onHome()
                        }
                    }
                    actionTriggered = false
                    isHolding = false
                    true
                }
                else -> false
            }
        }
    }
}
