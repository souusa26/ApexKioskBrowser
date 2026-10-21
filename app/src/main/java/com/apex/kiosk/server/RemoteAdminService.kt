package com.apex.kiosk.server

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import com.apex.kiosk.settings.AppSettings

class RemoteAdminService : Service() {

    private val binder = LocalBinder()
    private var httpServer: KioskHttpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var settings: AppSettings

    var activeActivity: Activity? = null
    var activeWebView: WebView? = null

    inner class LocalBinder : Binder() {
        fun getService(): RemoteAdminService = this@RemoteAdminService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(applicationContext)

        createNotificationChannel()
        startForeground(1001, buildNotification())

        if (settings.enableWifiWakelock) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ApexKiosk:WifiLock")
            wifiLock?.acquire()
        }

        if (settings.remoteAdmin) {
            httpServer = KioskHttpServer(
                context = applicationContext,
                port = settings.remoteAdminPort,
                getActiveActivity = { activeActivity },
                getActiveWebView = { activeWebView }
            )
            httpServer?.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Apex Kiosk Remote Admin",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor Web Ativo na porta ${settings.remoteAdminPort}"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Apex Kiosk Ativo")
            .setContentText("Servidor de Administração Remota ativo na porta ${settings.remoteAdminPort}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "apex_kiosk_service_channel"
    }
}
