package com.edgegesture.evilgodxu

import android.app.Application
import com.edgegesture.evilgodxu.data.LitePreferences

class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LitePreferences.init(this)
    }
}
