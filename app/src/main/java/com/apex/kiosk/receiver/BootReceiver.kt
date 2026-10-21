package com.apex.kiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apex.kiosk.MainActivity
import com.apex.kiosk.settings.AppSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = AppSettings(context)
            if (settings.autostart) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
