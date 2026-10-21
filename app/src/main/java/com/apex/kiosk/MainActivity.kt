package com.apex.kiosk

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apex.kiosk.javascript.FullyJavascriptInterface
import com.apex.kiosk.kiosk.ApexDeviceAdminReceiver
import com.apex.kiosk.kiosk.KioskManager
import com.apex.kiosk.server.KioskForegroundService
import com.apex.kiosk.settings.AppSettings
import com.apex.kiosk.settings.ConfigManager
import com.apex.kiosk.settings.KioskConfig
import com.apex.kiosk.ui.SettingsActivity

class MainActivity : AppCompatActivity(), ConfigManager.OnConfigChangeListener {

    private lateinit var webView: WebView
    private lateinit var customViewContainer: FrameLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var btnKioskUnlock: FrameLayout
    private lateinit var hotspotTopLeft: View
    private lateinit var hotspotBottomRight: View

    private lateinit var settings: AppSettings
    private lateinit var configManager: ConfigManager
    private lateinit var kioskManager: KioskManager

    private var foregroundService: KioskForegroundService? = null
    private var isServiceBound = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var tapCountTopLeft = 0
    private var lastTapTimeTopLeft = 0L

    private var tapCountBottomRight = 0
    private var lastTapTimeBottomRight = 0L

    private val retryHandler = Handler(Looper.getMainLooper())
    private var isNetworkAvailable = true
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? KioskForegroundService.LocalBinder
            foregroundService = binder?.getService()
            foregroundService?.activeActivity = this@MainActivity
            foregroundService?.activeWebView = webView
            foregroundService?.kioskManager = kioskManager
            isServiceBound = true
            Log.i(TAG, "KioskForegroundService conectado à MainActivity")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
            isServiceBound = false
        }
    }

    private val screenControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DIM_SCREEN -> {
                    Log.i(TAG, "Recebido comando de escurecer tela")
                    kioskManager.turnScreenOff()
                }
                ACTION_WAKE_SCREEN -> {
                    Log.i(TAG, "Recebido comando de acordar tela")
                    kioskManager.turnScreenOn()
                }
                ACTION_UPDATE_CONFIG -> {
                    val json = intent.getStringExtra("config_json")
                    if (!json.isNullOrBlank()) {
                        configManager.importFromJsonString(json)
                    } else {
                        configManager.loadInitialConfig()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configManager = ConfigManager.getInstance(this)
        configManager.addListener(this)
        settings = AppSettings(this)
        kioskManager = KioskManager(this)

        if (settings.watchdog) {
            setupCrashWatchdog()
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        customViewContainer = findViewById(R.id.customViewContainer)
        loadingProgress = findViewById(R.id.loadingProgress)
        btnKioskUnlock = findViewById(R.id.btnKioskUnlock)
        hotspotTopLeft = findViewById(R.id.hotspotTopLeft)
        hotspotBottomRight = findViewById(R.id.hotspotBottomRight)

        setupGesturesAndUnlock()
        configureWebView()
        kioskManager.applyKioskPolicies()
        setupNetworkMonitoring()
        checkKioskPermissionsOnboarding()
        startAndBindKioskService()

        val filter = IntentFilter().apply {
            addAction(ACTION_DIM_SCREEN)
            addAction(ACTION_WAKE_SCREEN)
            addAction(ACTION_UPDATE_CONFIG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenControlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenControlReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        kioskManager.applyKioskPolicies()
        foregroundService?.activeActivity = this
        foregroundService?.activeWebView = webView
        foregroundService?.kioskManager = kioskManager
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            kioskManager.applyFullscreenAndBars()
        }
    }

    override fun onConfigChanged(newConfig: KioskConfig) {
        runOnUiThread {
            Log.i(TAG, "Configurações atualizadas dinamicamente: aplicando alterações")
            kioskManager.applyKioskPolicies()
            val headers = mapOf("ngrok-skip-browser-warning" to "true")
            webView.loadUrl(settings.startUrl, headers)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent?.getStringExtra("TARGET_URL")
        if (!newUrl.isNullOrBlank()) {
            val headers = mapOf("ngrok-skip-browser-warning" to "true")
            webView.loadUrl(newUrl, headers)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webSettings = webView.settings

        // Suporte Completo a HTML5, Armazenamento Local e Banco de Dados
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(false)

        // Resiliência de Cache Offline
        updateCacheMode()

        if (settings.clearCacheOnStart) {
            webView.clearCache(true)
        }

        webView.addJavascriptInterface(
            FullyJavascriptInterface(this, webView),
            "fully"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingProgress.visibility = View.GONE

                // Bypass automático da tela de aviso do ngrok gratuito
                val ngrokBypassScript = """
                    (function() {
                        var buttons = document.querySelectorAll('button, a');
                        for (var i = 0; i < buttons.length; i++) {
                            var text = buttons[i].innerText || buttons[i].textContent;
                            if (text && text.toLowerCase().indexOf('visit site') !== -1) {
                                buttons[i].click();
                                break;
                            }
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(ngrokBypassScript, null)
            }

            // Tratamento de Erros de Conexão: Evita a tela "chrome://network-error"
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Log.w(TAG, "Falha de conexão na página principal: ${error?.description}. Tentando reconectar...")
                    loadingProgress.visibility = View.GONE
                    scheduleAutoReconnect()
                }
            }

            // Watchdog Chromium Render Crash (onRenderProcessGone)
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e(TAG, "Chromium render process crashed! Did crash: ${detail?.didCrash()}. Auto-recovering...")
                val root = findViewById<ViewGroup>(R.id.rootContainer)
                root.removeView(webView)
                webView.destroy()

                // Recria a WebView dinamicamente
                webView = WebView(this@MainActivity)
                root.addView(webView, 0, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                configureWebView()
                foregroundService?.activeWebView = webView
                return true
            }
        }

        // Suporte a Fullscreen Video HTML5
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    loadingProgress.visibility = View.VISIBLE
                    loadingProgress.progress = newProgress
                } else {
                    loadingProgress.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                customViewContainer.visibility = View.VISIBLE
                customViewContainer.addView(view)
                kioskManager.applyFullscreenAndBars()
            }

            override fun onHideCustomView() {
                if (customView == null) return
                customViewContainer.removeView(customView)
                customViewContainer.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                kioskManager.applyFullscreenAndBars()
            }
        }

        val headers = mapOf("ngrok-skip-browser-warning" to "true")
        webView.loadUrl(settings.startUrl, headers)
    }

    private fun updateCacheMode() {
        val webSettings = webView.settings
        if (settings.enableOfflineCache) {
            if (isNetworkAvailable) {
                webSettings.cacheMode = WebSettings.LOAD_DEFAULT
            } else {
                webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
        } else {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    private fun scheduleAutoReconnect() {
        retryHandler.removeCallbacksAndMessages(null)
        retryHandler.postDelayed({
            if (isNetworkAvailable) {
                Log.i(TAG, "Tentando reconectar à URL inicial...")
                val headers = mapOf("ngrok-skip-browser-warning" to "true")
                webView.loadUrl(settings.startUrl, headers)
            }
        }, 5000L)
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkAvailable = true
                runOnUiThread {
                    updateCacheMode()
                    // Se a WebView estava em erro, recarrega
                    if (webView.url.isNullOrBlank() || webView.url?.contains("data:") == true) {
                        val headers = mapOf("ngrok-skip-browser-warning" to "true")
                        webView.loadUrl(settings.startUrl, headers)
                    }
                }
            }

            override fun onLost(network: Network) {
                isNetworkAvailable = false
                runOnUiThread {
                    updateCacheMode()
                }
            }
        }
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    // ========================================================
    // GESTOS OCULTOS E TECLADO DE PIN
    // ========================================================
    private fun setupGesturesAndUnlock() {
        btnKioskUnlock.setOnClickListener {
            showKioskPinKeypad()
        }

        // Gesto: 7 Toques Rápidos no Canto Superior Esquerdo
        hotspotTopLeft.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTimeTopLeft < 600L) {
                tapCountTopLeft++
            } else {
                tapCountTopLeft = 1
            }
            lastTapTimeTopLeft = now

            if (tapCountTopLeft >= 7) {
                tapCountTopLeft = 0
                showKioskPinKeypad()
            }
        }

        // Gesto: 7 Toques Rápidos no Canto Inferior Direito
        hotspotBottomRight.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTimeBottomRight < 600L) {
                tapCountBottomRight++
            } else {
                tapCountBottomRight = 1
            }
            lastTapTimeBottomRight = now

            if (tapCountBottomRight >= 7) {
                tapCountBottomRight = 0
                showKioskPinKeypad()
            }
        }
    }

    private fun showKioskPinKeypad() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_kiosk_pin, null)
        val txtDisplay = dialogView.findViewById<TextView>(R.id.txtPinDisplay)
        var currentPin = ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val updateDisplay = {
            txtDisplay.text = if (currentPin.isEmpty()) "••••" else "• ".repeat(currentPin.length).trim()
        }

        val appendDigit = { digit: String ->
            if (currentPin.length < 8) {
                currentPin += digit
                updateDisplay()
            }
        }

        dialogView.findViewById<Button>(R.id.btn1).setOnClickListener { appendDigit("1") }
        dialogView.findViewById<Button>(R.id.btn2).setOnClickListener { appendDigit("2") }
        dialogView.findViewById<Button>(R.id.btn3).setOnClickListener { appendDigit("3") }
        dialogView.findViewById<Button>(R.id.btn4).setOnClickListener { appendDigit("4") }
        dialogView.findViewById<Button>(R.id.btn5).setOnClickListener { appendDigit("5") }
        dialogView.findViewById<Button>(R.id.btn6).setOnClickListener { appendDigit("6") }
        dialogView.findViewById<Button>(R.id.btn7).setOnClickListener { appendDigit("7") }
        dialogView.findViewById<Button>(R.id.btn8).setOnClickListener { appendDigit("8") }
        dialogView.findViewById<Button>(R.id.btn9).setOnClickListener { appendDigit("9") }
        dialogView.findViewById<Button>(R.id.btn0).setOnClickListener { appendDigit("0") }

        dialogView.findViewById<Button>(R.id.btnClear).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updateDisplay()
            }
        }

        dialogView.findViewById<Button>(R.id.btnOk).setOnClickListener {
            if (currentPin == settings.kioskPin) {
                dialog.dismiss()
                kioskManager.stopLockTaskIfRunning()
                showAdminMenu()
            } else {
                Toast.makeText(this, "PIN Incorreto!", Toast.LENGTH_SHORT).show()
                currentPin = ""
                updateDisplay()
            }
        }

        dialog.show()
    }

    private fun showAdminMenu() {
        val menuView = layoutInflater.inflate(R.layout.dialog_admin_menu, null)
        val dialog = AlertDialog.Builder(this)
            .setView(menuView)
            .setCancelable(true)
            .create()

        menuView.findViewById<Button>(R.id.btnMenuReload).setOnClickListener {
            dialog.dismiss()
            val headers = mapOf("ngrok-skip-browser-warning" to "true")
            webView.loadUrl(settings.startUrl, headers)
            kioskManager.applyKioskPolicies()
        }

        menuView.findViewById<Button>(R.id.btnMenuSettings).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        menuView.findViewById<Button>(R.id.btnMenuClearCache).setOnClickListener {
            webView.clearCache(true)
            Toast.makeText(this, "Cache limpo!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            kioskManager.applyKioskPolicies()
        }

        menuView.findViewById<Button>(R.id.btnMenuExitKiosk).setOnClickListener {
            dialog.dismiss()
            settings.kioskMode = false
            kioskManager.stopLockTaskIfRunning()
            finishAffinity()
        }

        menuView.findViewById<Button>(R.id.btnMenuCancel).setOnClickListener {
            dialog.dismiss()
            kioskManager.applyKioskPolicies()
        }

        dialog.show()
    }

    // Interceptação de Teclas Físicas de Volume
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!settings.enableVolumeButtons && settings.kioskMode) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (settings.kioskMode) {
            if (webView.canGoBack()) {
                webView.goBack()
            }
            return
        }
        super.onBackPressed()
    }

    private fun startAndBindKioskService() {
        val serviceIntent = Intent(this, KioskForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkKioskPermissionsOnboarding() {
        if (!settings.kioskMode) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Toast.makeText(this, "Permita a Sobreposição para bloquear a tela", Toast.LENGTH_LONG).show()
            try { startActivity(intent) } catch (_: Exception) {}
            return
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, ApexDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Ative para permitir o modo quiosque seguro.")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    /**
     * Watchdog Global: reinicia MainActivity via AlarmManager em 2 segundos caso ocorra crash fatal
     */
    private fun setupCrashWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Crash fatal detectado pelo Watchdog! Agendando reinício...", throwable)
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    val pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        9999,
                        intent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.set(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + 2000L,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao agendar reinício pelo AlarmManager", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        configManager.removeListener(this)
        retryHandler.removeCallbacksAndMessages(null)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        try {
            unregisterReceiver(screenControlReceiver)
        } catch (_: Exception) {}
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        kioskManager.releaseWakeLock()
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_DIM_SCREEN = "com.apex.kiosk.ACTION_DIM_SCREEN"
        const val ACTION_WAKE_SCREEN = "com.apex.kiosk.ACTION_WAKE_SCREEN"
        const val ACTION_UPDATE_CONFIG = "com.apex.kiosk.ACTION_UPDATE_CONFIG"
    }
}
