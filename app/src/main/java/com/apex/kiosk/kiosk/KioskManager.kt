package com.apex.kiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.apex.kiosk.settings.AppSettings

class KioskManager(private val activity: Activity) {

    private val context: Context = activity.applicationContext
    private val settings = AppSettings(context)
    private var wakeLock: PowerManager.WakeLock? = null

    fun applyKioskPolicies() {
        applyFullscreenAndBars()
        applySleepAndWakePolicies()
        applyLockTaskIfEnabled()
    }

    fun applyFullscreenAndBars() {
        activity.runOnUiThread {
            // Se showFullscreen ou se showStatusBar/showNavigationBar forem false
            if (settings.showFullscreen || !settings.showStatusBar || !settings.showNavigationBar) {
                val window = activity.window

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
        }
    }

    fun applySleepAndWakePolicies() {
        activity.runOnUiThread {
            if (settings.keepScreenOn) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        if (settings.preventSleep) {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ApexKiosk:PreventSleepLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } else {
            releaseWakeLock()
        }
    }

    fun applyLockTaskIfEnabled() {
        if (settings.kioskMode) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, ApexDeviceAdminReceiver::class.java)

                if (dpm.isLockTaskPermitted(context.packageName)) {
                    activity.startLockTask()
                } else if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                    activity.startLockTask()
                }
            } catch (e: Exception) {
                // Fallback para dispositivos sem Device Owner: Kiosk gerenciado via flags e Accessibility
                e.printStackTrace()
            }
        }
    }

    fun stopLockTaskIfRunning() {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {}
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }
}
