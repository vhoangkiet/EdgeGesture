package com.edgegesture.evilgodxu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.edgegesture.evilgodxu.data.LitePreferences
import com.edgegesture.evilgodxu.data.SecureSettingsHelper

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESTART_SERVICE = "com.edgegesture.evilgodxu.ACTION_RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_RESTART_SERVICE
        ) {
            LitePreferences.init(context)
            SecureSettingsHelper.enableAccessibilityService(context)
            EdgeGestureAccessibilityService.refresh(context)
        }
    }
}
