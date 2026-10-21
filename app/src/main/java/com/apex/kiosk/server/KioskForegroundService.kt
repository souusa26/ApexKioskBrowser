package com.apex.kiosk.server

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import com.apex.kiosk.MainActivity
import com.apex.kiosk.kiosk.KioskManager
import com.apex.kiosk.receiver.BootAndPowerReceiver
import com.apex.kiosk.settings.AppSettings
import com.apex.kiosk.settings.ConfigManager
import com.apex.kiosk.settings.KioskConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class KioskForegroundService : Service(), ConfigManager.OnConfigChangeListener {

    private val binder = LocalBinder()
    private var httpServer: KioskHttpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var settings: AppSettings
    private lateinit var configManager: ConfigManager

    var activeActivity: Activity? = null
    var activeWebView: WebView? = null
    var kioskManager: KioskManager? = null

    private var watchdogJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var dynamicPowerReceiver: BootAndPowerReceiver? = null

    inner class LocalBinder : Binder() {
        fun getService(): KioskForegroundService = this@KioskForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando KioskForegroundService")
        settings = AppSettings(applicationContext)
        configManager = ConfigManager.getInstance(applicationContext)
        configManager.addListener(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        applyWifiLock()
        startHttpServer()
        startWatchdog()
        registerDynamicReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onConfigChanged(newConfig: KioskConfig) {
        Log.i(TAG, "Configurações alteradas: atualizando KioskForegroundService")
        applyWifiLock()
        restartHttpServerIfNeeded()
    }

    private fun applyWifiLock() {
        try {
            if (settings.enableWifiWakelock) {
                if (wifiLock == null || wifiLock?.isHeld == false) {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wifiManager.createWifiLock(mode, "ApexKiosk:WifiLock")
                    wifiLock?.acquire()
                    Log.i(TAG, "WifiLock de alta performance adquirido com sucesso")
                }
            } else {
                releaseWifiLock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar WifiLock", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.i(TAG, "WifiLock liberado")
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar WifiLock", e)
        }
    }

    private fun startHttpServer() {
        if (!settings.remoteAdmin) return
        try {
            httpServer?.stop()
            httpServer = KioskHttpServer(
                context = applicationContext,
                port = settings.remoteAdminPort,
                getActiveActivity = { activeActivity },
                getActiveWebView = { activeWebView },
                getKioskManager = { kioskManager }
            )
            httpServer?.start()
            Log.i(TAG, "Servidor HTTP NanoHTTPD ativo na porta ${settings.remoteAdminPort}")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar servidor HTTP na porta ${settings.remoteAdminPort}", e)
        }
    }

    private fun restartHttpServerIfNeeded() {
        if (settings.remoteAdmin) {
            startHttpServer()
        } else {
            httpServer?.stop()
            httpServer = null
        }
    }

    /**
     * Watchdog & Auto-Healing:
     * Verifica a cada 4 segundos se o KioskMode está ativo e traz a MainActivity de volta ao topo.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(4000L)
                if (settings.watchdog && settings.kioskMode) {
                    checkAndRestoreKioskTopActivity()
                }
            }
        }
    }

    private fun checkAndRestoreKioskTopActivity() {
        try {
            val isCurrentActivityAlive = activeActivity != null && !activeActivity!!.isFinishing && !activeActivity!!.isDestroyed
            if (!isCurrentActivityAlive) {
                Log.w(TAG, "Watchdog: MainActivity não está ativa. Reiniciando Kiosk...")
                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no loop do watchdog", e)
        }
    }

    private fun registerDynamicReceivers() {
        try {
            dynamicPowerReceiver = BootAndPowerReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(dynamicPowerReceiver, filter)
            Log.i(TAG, "BootAndPowerReceiver dinâmico registrado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar receiver dinâmico", e)
        }
    }

    private fun unregisterDynamicReceivers() {
        try {
            dynamicPowerReceiver?.let { unregisterReceiver(it) }
            dynamicPowerReceiver = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destruindo KioskForegroundService")
        configManager.removeListener(this)
        watchdogJob?.cancel()
        httpServer?.stop()
        releaseWifiLock()
        unregisterDynamicReceivers()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Apex Kiosk Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoramento do modo Kiosk e Servidor Remoto"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Apex Kiosk Ativo")
            .setContentText("Kiosk e Administração Remota ativos na porta ${settings.remoteAdminPort}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "KioskForegroundService"
        const val CHANNEL_ID = "apex_kiosk_foreground_channel"
        const val NOTIFICATION_ID = 2609
    }
}
