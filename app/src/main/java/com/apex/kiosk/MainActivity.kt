package com.apex.kiosk

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
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
import com.apex.kiosk.server.RemoteAdminService
import com.apex.kiosk.settings.AppSettings
import com.apex.kiosk.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var btnKioskUnlock: FrameLayout
    private lateinit var settings: AppSettings
    private lateinit var kioskManager: KioskManager

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

        if (settings.watchdog) {
            setupCrashWatchdog()
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingProgress = findViewById(R.id.loadingProgress)
        btnKioskUnlock = findViewById(R.id.btnKioskUnlock)

        btnKioskUnlock.setOnClickListener {
            showKioskPinKeypad()
        }

        configureWebView()
        kioskManager.applyKioskPolicies()
        checkKioskPermissionsOnboarding()

        if (settings.remoteAdmin) {
            val serviceIntent = Intent(this, RemoteAdminService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

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

        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true

        if (settings.enableOfflineCache) {
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        } else {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

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

        // Carrega com header para pular a tela de aviso do ngrok
        val headers = mapOf("ngrok-skip-browser-warning" to "true")
        webView.loadUrl(settings.startUrl, headers)
    }

    // ========================================================
    // TECLADO TOUCH DE PIN (ESTILO FULLY KIOSK)
    // ========================================================
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
                showAdminMenu()
            } else {
                Toast.makeText(this, "PIN Incorreto!", Toast.LENGTH_SHORT).show()
                currentPin = ""
                updateDisplay()
            }
        }

        dialog.show()
    }

    // ========================================================
    // MENU ADMINISTRATIVO DE DESBLOQUEIO
    // ========================================================
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

    // ========================================================
    // ONBOARDING DE PERMISSÕES PARA BLOQUEIO TOTAL
    // ========================================================
    private fun checkKioskPermissionsOnboarding() {
        if (!settings.kioskMode) return

        // 1. Sobreposição de tela (SYSTEM_ALERT_WINDOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Toast.makeText(this, "Ative a Sobreposição para o Kiosk bloquear a tela", Toast.LENGTH_LONG).show()
            try { startActivity(intent) } catch (_: Exception) {}
            return
        }

        // 2. Administrador do Dispositivo
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, ApexDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Ative o Administrador para bloquear o aparelho")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    // Interceptação dos Botões Físicos de Volume (Veicular)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!settings.enableVolumeButtons) {
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

    // Bloqueia o botão Voltar do Android quando em Modo Quiosque
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

    private fun setupCrashWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
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
