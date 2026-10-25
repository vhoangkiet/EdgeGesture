package com.edgegesture.evilgodxu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.edgegesture.evilgodxu.data.LitePreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            LitePreferences.init(context)
            EdgeGestureAccessibilityService.refresh()
        }
    }
}
