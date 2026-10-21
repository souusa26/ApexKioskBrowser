package com.apex.kiosk.receiver

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.apex.kiosk.MainActivity
import com.apex.kiosk.kiosk.ApexDeviceAdminReceiver
import com.apex.kiosk.settings.AppSettings

class BootAndPowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Broadcast recebido: $action")
        val settings = AppSettings(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON -> {
                if (settings.autostart) {
                    Log.i(TAG, "Autostart ativado: iniciando MainActivity no boot")
                    launchMainActivity(context, "BOOT_COMPLETED")
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                Log.i(TAG, "Energia conectada (carregador/veículo)")
                
                // 1. Acorda o dispositivo se turnScreenOnOnPowerConnected for true
                if (settings.turnScreenOnOnPowerConnected) {
                    wakeUpScreen(context)
                }

                // 2. Inicia o app se autostartOnPower for true
                if (settings.autostartOnPower) {
                    launchMainActivity(context, "ACTION_POWER_CONNECTED")
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i(TAG, "Energia desconectada (desligamento do veículo)")
                if (settings.turnScreenOffOnPowerDisconnected) {
                    turnOffOrDimScreen(context)
                }
            }
        }
    }

    private fun launchMainActivity(context: Context, trigger: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TRIGGER_REASON", trigger)
        }
        context.startActivity(launchIntent)
    }

    private fun wakeUpScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ApexKiosk:PowerConnectedWake"
            )
            wakeLock.acquire(5000L) // Acorda por 5 segundos para exibir o player

            // Notifica MainActivity para aplicar flags de tela acesa
            val wakeIntent = Intent(MainActivity.ACTION_WAKE_SCREEN).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(wakeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao acordar tela em conexão de energia", e)
        }
    }

    private fun turnOffOrDimScreen(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, ApexDeviceAdminReceiver::class.java)

            // Se for Device Owner ou Administrador ativo, bloqueia a tela imediatamente
            if (dpm.isAdminActive(adminComponent)) {
                Log.i(TAG, "Bloqueando tela imediatamente via DevicePolicyManager.lockNow()")
                dpm.lockNow()
            } else {
                // Fallback: notifica a MainActivity para reduzir o brilho a zero e liberar wake locks
                Log.i(TAG, "Enviando broadcast para escurecer tela (fallback)")
                val dimIntent = Intent(MainActivity.ACTION_DIM_SCREEN).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(dimIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao apagar tela na desconexão de energia", e)
        }
    }

    companion object {
        private const val TAG = "BootAndPowerReceiver"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
