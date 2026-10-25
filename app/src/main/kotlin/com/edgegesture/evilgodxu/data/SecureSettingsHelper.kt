package com.edgegesture.evilgodxu.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Trợ lý điều khiển các cài đặt hệ thống sâu thông qua quyền WRITE_SECURE_SETTINGS.
 * Giúp người dùng bật/tắt trực tiếp trong app sau khi cấp quyền qua ADB 1 lần duy nhất:
 * 1. Ẩn thanh 3 nút điều hướng MIUI (force_fsg_nav_bar).
 * 2. Tắt cử chỉ vuốt Back mặc định mép viền của hệ thống (back_gesture_inset_scale).
 * 3. Ẩn thanh gạch trắng gốc ở đáy của MIUI (hide_gesture_line).
 */
object SecureSettingsHelper {

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    // 1. Ẩn 3 nút điều hướng MIUI (force_fsg_nav_bar)
    fun isMiuiNavbarHidden(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "force_fsg_nav_bar", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setMiuiNavbarHidden(context: Context, hidden: Boolean): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, "force_fsg_nav_bar", if (hidden) 1 else 0)
        } catch (_: Exception) {
            false
        }
    }

    // 2. Tắt cử chỉ Back mặc định của máy (tránh xung đột 2 lần Back)
    fun isStockBackDisabled(context: Context): Boolean {
        return try {
            val left = Settings.Secure.getString(context.contentResolver, "back_gesture_inset_scale_left")
            val right = Settings.Secure.getString(context.contentResolver, "back_gesture_inset_scale_right")
            left == "-1" || right == "-1"
        } catch (_: Exception) {
            false
        }
    }

    fun setStockBackDisabled(context: Context, disabled: Boolean): Boolean {
        return try {
            val value = if (disabled) "-1" else "default"
            val successLeft = Settings.Secure.putString(context.contentResolver, "back_gesture_inset_scale_left", value)
            val successRight = Settings.Secure.putString(context.contentResolver, "back_gesture_inset_scale_right", value)
            successLeft && successRight
        } catch (_: Exception) {
            false
        }
    }

    // 3. Ẩn thanh gạch trắng mặc định ở đáy của MIUI
    fun isMiuiGestureLineHidden(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "hide_gesture_line", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setMiuiGestureLineHidden(context: Context, hidden: Boolean): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, "hide_gesture_line", if (hidden) 1 else 0)
        } catch (_: Exception) {
            false
        }
    }
}
