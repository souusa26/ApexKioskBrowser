package com.apex.kiosk

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apex.kiosk.javascript.FullyJavascriptInterface
import com.apex.kiosk.kiosk.KioskManager
import com.apex.kiosk.server.RemoteAdminService
import com.apex.kiosk.settings.AppSettings
import com.apex.kiosk.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var secretSettingsTrigger: View
    private lateinit var settings: AppSettings
    private lateinit var kioskManager: KioskManager

    private var tapCount = 0
    private var lastTapTime = 0L

    private var remoteService: RemoteAdminService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RemoteAdminService.LocalBinder
            remoteService = binder?.getService()
            remoteService?.activeActivity = this@MainActivity
            remoteService?.activeWebView = webView
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            isServiceBound = false
        }
    }

    private val dimScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DIM_SCREEN) {
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val params = window.attributes
                    params.screenBrightness = 0.01f
                    window.attributes = params
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = AppSettings(this)
        kioskManager = KioskManager(this)

        // Configuração do Watchdog contra travamentos e crashes
        if (settings.watchdog) {
            setupCrashWatchdog()
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingProgress = findViewById(R.id.loadingProgress)
        secretSettingsTrigger = findViewById(R.id.secretSettingsTrigger)

        setupSecretSettingsTrigger()
        configureWebView()
        kioskManager.applyKioskPolicies()

        // Inicia o serviço em segundo plano para Administração Remota (Porta 2323)
        if (settings.remoteAdmin) {
            val serviceIntent = Intent(this, RemoteAdminService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Registra receptor para escurecer a tela ao desconectar a ignição do veículo
        val filter = IntentFilter(ACTION_DIM_SCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dimScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dimScreenReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        kioskManager.applyKioskPolicies()
        remoteService?.activeActivity = this
        remoteService?.activeWebView = webView
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            kioskManager.applyFullscreenAndBars()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webSettings = webView.settings

        // Habilita JavaScript e recursos modernos
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true

        // Cache Offline
        if (settings.enableOfflineCache) {
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        } else {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        if (settings.clearCacheOnStart) {
            webView.clearCache(true)
        }

        // Injeção da API window.fully (compatível com Fully Kiosk)
        webView.addJavascriptInterface(
            FullyJavascriptInterface(this, webView),
            "fully"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Mantém a navegação sempre dentro do WebView
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingProgress.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    loadingProgress.visibility = View.VISIBLE
                    loadingProgress.progress = newProgress
                } else {
                    loadingProgress.visibility = View.GONE
                }
            }
        }

        // Carrega a URL inicial de produção configurada no perfil
        webView.loadUrl(settings.startUrl)
    }

    private fun setupSecretSettingsTrigger() {
        // Toque 4 vezes rapidamente no canto superior direito para abrir o diálogo de PIN
        secretSettingsTrigger.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 800) {
                tapCount++
                if (tapCount >= 4) {
                    tapCount = 0
                    showPinDialog()
                }
            } else {
                tapCount = 1
            }
            lastTapTime = now
        }
    }

    private fun showPinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin, null)
        val editPin = dialogView.findViewById<EditText>(R.id.editPin)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Acessar") { dialog, _ ->
                val inputPin = editPin.text.toString().trim()
                if (inputPin == settings.kioskPin) {
                    dialog.dismiss()
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, "PIN Incorreto!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                kioskManager.applyFullscreenAndBars()
            }
            .show()
    }

    // Interceptação dos Botões Físicos de Volume (Veicular)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!settings.enableVolumeButtons) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    return true // Bloqueia e consome o evento de volume
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }



    // Bloqueia o botão Voltar do Android quando em Modo Quiosque
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (settings.kioskMode) {
            if (webView.canGoBack()) {
                webView.goBack()
            }
            // Não fecha a Activity
            return
        }
        super.onBackPressed()
    }

    private fun setupCrashWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            // Reinicia imediatamente o app em caso de crash
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        try {
            unregisterReceiver(dimScreenReceiver)
        } catch (_: Exception) {}
        kioskManager.releaseWakeLock()
    }

    companion object {
        const val ACTION_DIM_SCREEN = "com.apex.kiosk.ACTION_DIM_SCREEN"
    }
}
