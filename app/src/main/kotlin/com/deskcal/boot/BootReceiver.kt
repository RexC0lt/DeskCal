package com.deskcal.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deskcal.MainActivity
import com.deskcal.data.SettingsRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val settings = SettingsRepository(context)
        if (!settings.settingsFlow.value.launchOnBoot) return
        try {
            val launch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } catch (_: Exception) {
        }
    }
}
