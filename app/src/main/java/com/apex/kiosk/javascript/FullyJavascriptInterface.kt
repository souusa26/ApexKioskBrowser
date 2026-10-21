package com.apex.kiosk.javascript

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.net.NetworkInterface
import java.util.Collections

/**
 * Interface JavaScript 100% compatível com a API window.fully do Fully Kiosk Browser.
 * Permite que páginas web (ex: Home Assistant, Dashboards) controlem o dispositivo nativamente.
 */
class FullyJavascriptInterface(
    private val activity: Activity,
    private val webView: WebView
) {

    private val context: Context = activity.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var isScreensaverActive: Boolean = false

    // ==========================================
    // CONTROLE DE TELA E ENERGIA
    // ==========================================

    @JavascriptInterface
    fun turnScreenOn() {
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    @JavascriptInterface
    fun turnScreenOff() {
        activity.runOnUiThread {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Simula tela desligada escurecendo ou desativando flags
            val params = activity.window.attributes
            params.screenBrightness = 0.01f
            activity.window.attributes = params
        }
    }

    @JavascriptInterface
    fun isScreenOn(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    @JavascriptInterface
    fun setScreenBrightness(brightness: Int) {
        // brightness de 0 a 255
        activity.runOnUiThread {
            val clamped = brightness.coerceIn(0, 255)
            val params = activity.window.attributes
            params.screenBrightness = clamped / 255f
            activity.window.attributes = params
        }
    }

    @JavascriptInterface
    fun getScreenBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
    }

    // ==========================================
    // BATERIA E HARDWARE
    // ==========================================

    @JavascriptInterface
    fun getBatteryLevel(): Float {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level.toFloat() / scale.toFloat()) * 100f
        } else {
            100f
        }
    }

    @JavascriptInterface
    fun isPlugged(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
               plugged == BatteryManager.BATTERY_PLUGGED_USB ||
               plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    @JavascriptInterface
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "apex_kiosk_device"
    }

    @JavascriptInterface
    fun getSerialNumber(): String {
        return Build.SERIAL ?: "UNKNOWN"
    }

    // ==========================================
    // REDE E CONECTIVIDADE
    // ==========================================

    @JavascriptInterface
    fun getIp4Address(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    @JavascriptInterface
    fun getMacAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (network in interfaces) {
                if (network.name.equals("wlan0", ignoreCase = true)) {
                    val macBytes = network.hardwareAddress ?: continue
                    val res = StringBuilder()
                    for (b in macBytes) {
                        res.append(String.format("%02X:", b))
                    }
                    if (res.isNotEmpty()) res.deleteCharAt(res.length - 1)
                    return res.toString()
                }
            }
        } catch (_: Exception) {}
        return "02:00:00:00:00:00"
    }

    @JavascriptInterface
    fun getHostname(): String {
        return Build.MODEL ?: "ApexKiosk"
    }

    @JavascriptInterface
    fun getWifiSignalLevel(): Int {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        return WifiManager.calculateSignalLevel(info.rssi, 5)
    }

    // ==========================================
    // NAVEGAÇÃO E WEBVIEW
    // ==========================================

    @JavascriptInterface
    fun loadUrl(url: String) {
        activity.runOnUiThread {
            webView.loadUrl(url)
        }
    }

    @JavascriptInterface
    fun reloadPage() {
        activity.runOnUiThread {
            webView.reload()
        }
    }

    @JavascriptInterface
    fun clearCache() {
        activity.runOnUiThread {
            webView.clearCache(true)
        }
    }

    // ==========================================
    // MULTIMÍDIA E ÁUDIO
    // ==========================================

    @JavascriptInterface
    fun playRingtone(url: String?, loop: Boolean) {
        stopSound()
        try {
            val soundUri = if (!url.isNullOrBlank()) {
                Uri.parse(url)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                isLooping = loop
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }

    // ==========================================
    // PROTETOR DE TELA E CICLO DE VIDA
    // ==========================================

    @JavascriptInterface
    fun startScreensaver() {
        isScreensaverActive = true
        activity.runOnUiThread {
            val params = activity.window.attributes
            params.screenBrightness = 0.05f
            activity.window.attributes = params
        }
    }

    @JavascriptInterface
    fun stopScreensaver() {
        isScreensaverActive = false
        activity.runOnUiThread {
            val params = activity.window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = params
        }
    }

    @JavascriptInterface
    fun isInScreensaver(): Boolean = isScreensaverActive

    @JavascriptInterface
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    @JavascriptInterface
    fun exit() {
        activity.finishAffinity()
    }
}
