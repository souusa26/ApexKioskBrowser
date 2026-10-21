package com.apex.kiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.apex.kiosk.settings.AppSettings

class KioskManager(private val activity: Activity) {

    private val context: Context = activity.applicationContext
    private val settings = AppSettings(context)
    private var partialWakeLock: PowerManager.WakeLock? = null
    private val adminComponent = ComponentName(context, ApexDeviceAdminReceiver::class.java)

    fun applyKioskPolicies() {
        applyFullscreenAndBars()
        applySleepAndWakePolicies()
        applyLockTaskIfEnabled()
    }

    /**
     * Aplica o Sticky Immersive Mode ocultando StatusBar, NavigationBar e ActionBar.
     */
    fun applyFullscreenAndBars() {
        activity.runOnUiThread {
            try {
                val window = activity.window

                // Oculta ActionBar se configurado
                if (!settings.showActionBar) {
                    if (activity is androidx.appcompat.app.AppCompatActivity) {
                        activity.supportActionBar?.hide()
                    }
                    activity.actionBar?.hide()
                }

                if (settings.showFullscreen || !settings.showStatusBar || !settings.showNavigationBar) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                        var typesToHide = 0
                        if (!settings.showStatusBar) typesToHide = typesToHide or WindowInsetsCompat.Type.statusBars()
                        if (!settings.showNavigationBar) typesToHide = typesToHide or WindowInsetsCompat.Type.navigationBars()
                        if (settings.showFullscreen) typesToHide = typesToHide or WindowInsetsCompat.Type.systemBars()

                        controller.hide(typesToHide)
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao aplicar barras e tela cheia", e)
            }
        }
    }

    /**
     * Aplica FLAG_KEEP_SCREEN_ON e WakeLocks de acordo com o JSON.
     */
    fun applySleepAndWakePolicies() {
        activity.runOnUiThread {
            try {
                if (settings.keepScreenOn) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao configurar FLAG_KEEP_SCREEN_ON", e)
            }
        }

        if (settings.preventSleep) {
            try {
                if (partialWakeLock == null) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ApexKiosk:PreventSleepLock")
                }
                if (partialWakeLock?.isHeld == false) {
                    partialWakeLock?.acquire()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao adquirir PARTIAL_WAKE_LOCK", e)
            }
        } else {
            releaseWakeLock()
        }
    }

    /**
     * Aplica o LockTask (Screen Pinning / Kiosk Mode).
     * Se for Device Owner, ativa o Kiosk de forma 100% blindada sem diálogos do Android.
     */
    fun applyLockTaskIfEnabled() {
        if (!settings.kioskMode) return

        activity.runOnUiThread {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                    activity.startLockTask()
                    Log.i(TAG, "startLockTask executado como Device Owner com sucesso")
                } else if (dpm.isLockTaskPermitted(context.packageName)) {
                    activity.startLockTask()
                    Log.i(TAG, "startLockTask executado com permissão prévia")
                } else {
                    // Fallback para aparelhos onde ainda não foi setado Device Owner via ADB
                    try {
                        activity.startLockTask()
                        Log.i(TAG, "startLockTask padrão ativado")
                    } catch (e: Exception) {
                        Log.w(TAG, "startLockTask padrão não suportado sem permissões de sistema: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao aplicar startLockTask", e)
            }
        }
    }

    fun stopLockTaskIfRunning() {
        activity.runOnUiThread {
            try {
                activity.stopLockTask()
                Log.i(TAG, "stopLockTask executado")
            } catch (e: Exception) {
                Log.w(TAG, "stopLockTask não estava ativo ou falhou: ${e.message}")
            }
        }
    }

    /**
     * Desliga ou escurece a tela ao desconectar a energia.
     */
    fun turnScreenOff() {
        activity.runOnUiThread {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow()
                } else {
                    // Fallback: remove keep screen on e escurece brilho
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val lp = activity.window.attributes
                    lp.screenBrightness = 0.01f
                    activity.window.attributes = lp
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao desligar a tela", e)
            }
        }
    }

    /**
     * Liga a tela e acorda o dispositivo ao conectar energia.
     */
    fun turnScreenOn() {
        activity.runOnUiThread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                }
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
                val lp = activity.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = lp
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao ligar a tela", e)
            }
        }
    }

    fun releaseWakeLock() {
        try {
            if (partialWakeLock?.isHeld == true) {
                partialWakeLock?.release()
            }
            partialWakeLock = null
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "KioskManager"
    }
}
