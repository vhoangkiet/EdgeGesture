package com.edgegesture.evilgodxu.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.edgegesture.evilgodxu.data.LitePreferences
import com.edgegesture.evilgodxu.data.LiteSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.ref.WeakReference

class EdgeGestureAccessibilityService : AccessibilityService(), LiteGestureDetector.GestureCallback {

    companion object {
        private const val TAG = "EdgeGestureService"
        private var instanceRef: WeakReference<EdgeGestureAccessibilityService>? = null

        fun isServiceRunning(): Boolean = instanceRef?.get() != null

        fun refresh() {
            instanceRef?.get()?.reloadSettings()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var gestureDetector: LiteGestureDetector
    private lateinit var edgeViewManager: AccessibilityEdgeViewManager

    private var currentApp: String? = null
    private var previousApp: String? = null

    private val settingsProvider: () -> LiteSettings = {
        LitePreferences.getSettings()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        LitePreferences.init(this)

        gestureDetector = LiteGestureDetector(this, this)
        edgeViewManager = AccessibilityEdgeViewManager(this, gestureDetector)

        // Lắng nghe thay đổi cấu hình và cập nhật overlay ngay lập tức
        LitePreferences.settingsFlow
            .onEach { settings ->
                edgeViewManager.applySettings(settings, settingsProvider)
            }
            .launchIn(serviceScope)

        Log.d(TAG, "EdgeGestureAccessibilityService connected")
    }

    override fun onBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onRecentApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    override fun onSwitchToPreviousApp() {
        val target = previousApp ?: return
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(target)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                startActivity(launchIntent)
                // Đảo ngược để lần vuốt tiếp theo chuyển qua lại giữa 2 ứng dụng
                val temp = currentApp
                currentApp = target
                previousApp = temp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Switch to previous app failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() &&
                pkg != packageName &&
                pkg != "com.android.systemui" &&
                !pkg.contains("launcher") &&
                !pkg.contains("inputmethod")
            ) {
                if (pkg != currentApp) {
                    previousApp = currentApp
                    currentApp = pkg
                }
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            checkKeyboardVisibility()
        }
    }

    private fun checkKeyboardVisibility() {
        try {
            val windowList = windows
            var imeVisible = false
            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    imeVisible = true
                    break
                }
            }
            if (::edgeViewManager.isInitialized) {
                edgeViewManager.onKeyboardVisibilityChanged(imeVisible, settingsProvider)
            }
        } catch (_: Exception) {
            // Một số phiên bản MIUI có thể hạn chế truy cập windows, bỏ qua an toàn
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "EdgeGestureAccessibilityService interrupted")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::edgeViewManager.isInitialized) {
            edgeViewManager.applySettings(settingsProvider(), settingsProvider)
        }
    }

    fun reloadSettings() {
        if (::edgeViewManager.isInitialized) {
            edgeViewManager.applySettings(settingsProvider(), settingsProvider)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::edgeViewManager.isInitialized) {
            edgeViewManager.removeViews()
        }
        serviceScope.cancel()
        instanceRef = null
        Log.d(TAG, "EdgeGestureAccessibilityService destroyed")
    }
}
