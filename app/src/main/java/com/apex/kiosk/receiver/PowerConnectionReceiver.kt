package com.apex.kiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.apex.kiosk.MainActivity
import com.apex.kiosk.settings.AppSettings

class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (settings.turnScreenOnOnPowerConnected) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                        "ApexKiosk:PowerConnectedWake"
                    )
                    wakeLock.acquire(5000L) // Acorda por 5 segundos
                }

                if (settings.autostartOnPower) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("ACTION_POWER_CONNECTED_TRIGGER", true)
                    }
                    context.startActivity(launchIntent)
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                if (settings.turnScreenOffOnPowerDisconnected) {
                    // Notifica a MainActivity para desligar / escurecer a tela
                    val dimIntent = Intent(MainActivity.ACTION_DIM_SCREEN).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(dimIntent)
                }
            }
        }
    }
}
