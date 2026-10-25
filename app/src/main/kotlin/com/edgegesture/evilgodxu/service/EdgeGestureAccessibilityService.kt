package com.edgegesture.evilgodxu.service

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.edgegesture.evilgodxu.MainActivity
import com.edgegesture.evilgodxu.R
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
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "edge_gesture_foreground_channel"
        private var instanceRef: WeakReference<EdgeGestureAccessibilityService>? = null

        fun isServiceRunning(): Boolean = instanceRef?.get() != null

        fun refresh(context: Context? = null) {
            instanceRef?.get()?.reloadSettings()
            try {
                context?.let { ctx ->
                    val intent = Intent(LitePreferences.ACTION_SETTINGS_CHANGED).setPackage(ctx.packageName)
                    ctx.sendBroadcast(intent)
                }
            } catch (_: Exception) {}
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

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                LitePreferences.reload()
                reloadSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi cập nhật cấu hình từ broadcast: ${e.message}")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        LitePreferences.init(this)

        startForegroundNotification()

        gestureDetector = LiteGestureDetector(this, this)
        edgeViewManager = AccessibilityEdgeViewManager(this, gestureDetector)

        // Lắng nghe thay đổi cấu hình nội bộ
        LitePreferences.settingsFlow
            .onEach { settings ->
                edgeViewManager.applySettings(settings, settingsProvider)
            }
            .launchIn(serviceScope)

        // Đăng ký nhận Broadcast đồng bộ từ tiến trình UI (MainActivity)
        try {
            val filter = IntentFilter(LitePreferences.ACTION_SETTINGS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerReceiver error: ${e.message}")
        }

        Log.d(TAG, "EdgeGestureAccessibilityService connected")
    }

    private fun startForegroundNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Dịch vụ chạy ngầm EdgeGesture",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Duy trì cử chỉ hoạt động ổn định khi vuốt xóa app khỏi Đa nhiệm"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EdgeGesture Lite")
                .setContentText("Cử chỉ đang chạy ngầm liên tục")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground notification started")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
        }
    }

    override fun onBack() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e(TAG, "onBack failed: ${e.message}")
        }
    }

    override fun onHome() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "onHome failed: ${e.message}")
        }
    }

    override fun onRecentApps() {
        try {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        } catch (e: Exception) {
            Log.e(TAG, "onRecentApps failed: ${e.message}")
        }
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "onAccessibilityEvent error: ${e.message}")
        }
    }

    private fun checkKeyboardVisibility() {
        try {
            val windowList = windows
            var imeVisible = false
            if (windowList != null) {
                for (window in windowList) {
                    if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        imeVisible = true
                        break
                    }
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
        try {
            if (::edgeViewManager.isInitialized) {
                edgeViewManager.applySettings(settingsProvider(), settingsProvider)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onConfigurationChanged error: ${e.message}")
        }
    }

    fun reloadSettings() {
        try {
            if (::edgeViewManager.isInitialized) {
                edgeViewManager.applySettings(settingsProvider(), settingsProvider)
            }
        } catch (e: Exception) {
            Log.e(TAG, "reloadSettings error: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: app swiped from recents - scheduling auto-restart")
        try {
            if (LitePreferences.getSettings().isServiceEnabled) {
                val restartIntent = Intent(this, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_RESTART_SERVICE
                }
                val pending = PendingIntent.getBroadcast(
                    this,
                    99,
                    restartIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 500,
                    pending
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "onTaskRemoved alarm error: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(settingsReceiver)
        } catch (_: Exception) {}
        try {
            if (::edgeViewManager.isInitialized) {
                edgeViewManager.removeViews()
            }
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error: ${e.message}")
        } finally {
            instanceRef = null
            Log.d(TAG, "EdgeGestureAccessibilityService destroyed")
        }

        // Nếu service bị kill bất ngờ trong khi người dùng vẫn bật cử chỉ, tự động đánh thức và kích hoạt lại
        try {
            if (LitePreferences.getSettings().isServiceEnabled) {
                val restartIntent = Intent(this, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_RESTART_SERVICE
                }
                val pending = PendingIntent.getBroadcast(
                    this,
                    98,
                    restartIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 800,
                    pending
                )
                Log.d(TAG, "onDestroy: scheduled auto-restart alarm")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy restart alarm error: ${e.message}")
        }
    }
}

