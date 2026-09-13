package com.example.skilkavach

import android.app.Application
import com.example.skilkavach.data.SafetyRepository

class SafetyApplication : Application() {
    val repository by lazy { SafetyRepository(this) }

    override fun onCreate() {
        super.onCreate()
        if (getSharedPreferences("notifications", 0).getBoolean("enabled", false))
            runCatching { PushSetup.initialize(this) }
    }
}
