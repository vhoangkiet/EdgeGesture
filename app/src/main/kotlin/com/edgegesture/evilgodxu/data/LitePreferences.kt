package com.edgegesture.evilgodxu.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiteSettings(
    val isServiceEnabled: Boolean = true,
    // Cạnh trái
    val isLeftEnabled: Boolean = true,
    val leftHeightPercent: Int = 60,      // 20% .. 100%
    val leftPositionPercent: Int = 50,    // 0% (đỉnh) .. 100% (đáy)
    val leftWidthDp: Int = 22,            // 12 .. 48 dp
    // Cạnh phải
    val isRightEnabled: Boolean = true,
    val rightHeightPercent: Int = 60,
    val rightPositionPercent: Int = 50,
    val rightWidthDp: Int = 22,
    // Cạnh dưới
    val isBottomEnabled: Boolean = true,
    val bottomWidthPercent: Int = 70,     // 30% .. 100%
    val bottomHeightDp: Int = 24,         // 12 .. 48 dp
    // Thanh Pill phong cách Pixel
    val showPill: Boolean = true,
    val pillWidthDp: Int = 72,            // 40 .. 120 dp
    val pillHeightDp: Int = 4,            // 3 .. 8 dp
    val pillBottomMarginDp: Int = 6,      // 2 .. 16 dp
    // Phản hồi rung
    val vibrateOnTrigger: Boolean = true,
    // Tự động tránh bàn phím (tránh cản trở Laban Key)
    val avoidKeyboard: Boolean = true,
    // Hiệu ứng vuốt (mũi tên giọt nước & đàn hồi thanh Pill)
    val showSwipeAnimation: Boolean = true
)

object LitePreferences {
    private const val PREF_NAME = "edge_gesture_lite_prefs"

    private lateinit var prefs: SharedPreferences
    private val _settingsFlow = MutableStateFlow(LiteSettings())
    val settingsFlow: StateFlow<LiteSettings> = _settingsFlow.asStateFlow()

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            _settingsFlow.value = loadSettings()
        }
    }

    fun getSettings(): LiteSettings {
        if (!::prefs.isInitialized) return LiteSettings()
        return _settingsFlow.value
    }

    fun update(transform: (LiteSettings) -> LiteSettings) {
        val current = getSettings()
        val updated = transform(current)
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    private fun loadSettings(): LiteSettings {
        return LiteSettings(
            isServiceEnabled = prefs.getBoolean("isServiceEnabled", true),
            isLeftEnabled = prefs.getBoolean("isLeftEnabled", true),
            leftHeightPercent = prefs.getInt("leftHeightPercent", 60),
            leftPositionPercent = prefs.getInt("leftPositionPercent", 50),
            leftWidthDp = prefs.getInt("leftWidthDp", 22),
            isRightEnabled = prefs.getBoolean("isRightEnabled", true),
            rightHeightPercent = prefs.getInt("rightHeightPercent", 60),
            rightPositionPercent = prefs.getInt("rightPositionPercent", 50),
            rightWidthDp = prefs.getInt("rightWidthDp", 22),
            isBottomEnabled = prefs.getBoolean("isBottomEnabled", true),
            bottomWidthPercent = prefs.getInt("bottomWidthPercent", 70),
            bottomHeightDp = prefs.getInt("bottomHeightDp", 24),
            showPill = prefs.getBoolean("showPill", true),
            pillWidthDp = prefs.getInt("pillWidthDp", 72),
            pillHeightDp = prefs.getInt("pillHeightDp", 4),
            pillBottomMarginDp = prefs.getInt("pillBottomMarginDp", 6),
            vibrateOnTrigger = prefs.getBoolean("vibrateOnTrigger", true),
            avoidKeyboard = prefs.getBoolean("avoidKeyboard", true),
            showSwipeAnimation = prefs.getBoolean("showSwipeAnimation", true)
        )
    }

    private fun saveSettings(s: LiteSettings) {
        prefs.edit()
            .putBoolean("isServiceEnabled", s.isServiceEnabled)
            .putBoolean("isLeftEnabled", s.isLeftEnabled)
            .putInt("leftHeightPercent", s.leftHeightPercent)
            .putInt("leftPositionPercent", s.leftPositionPercent)
            .putInt("leftWidthDp", s.leftWidthDp)
            .putBoolean("isRightEnabled", s.isRightEnabled)
            .putInt("rightHeightPercent", s.rightHeightPercent)
            .putInt("rightPositionPercent", s.rightPositionPercent)
            .putInt("rightWidthDp", s.rightWidthDp)
            .putBoolean("isBottomEnabled", s.isBottomEnabled)
            .putInt("bottomWidthPercent", s.bottomWidthPercent)
            .putInt("bottomHeightDp", s.bottomHeightDp)
            .putBoolean("showPill", s.showPill)
            .putInt("pillWidthDp", s.pillWidthDp)
            .putInt("pillHeightDp", s.pillHeightDp)
            .putInt("pillBottomMarginDp", s.pillBottomMarginDp)
            .putBoolean("vibrateOnTrigger", s.vibrateOnTrigger)
            .putBoolean("avoidKeyboard", s.avoidKeyboard)
            .putBoolean("showSwipeAnimation", s.showSwipeAnimation)
            .apply()
    }
}
