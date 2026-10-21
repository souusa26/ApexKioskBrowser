package com.apex.kiosk.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.text.format.Formatter
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import com.apex.kiosk.kiosk.KioskManager
import com.apex.kiosk.settings.AppSettings
import com.apex.kiosk.settings.ConfigManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class KioskHttpServer(
    private val context: Context,
    port: Int,
    private val getActiveActivity: () -> Activity?,
    private val getActiveWebView: () -> WebView?,
    private val getKioskManager: () -> KioskManager?
) : NanoHTTPD(port) {

    private val settings = AppSettings(context)
    private val configManager = ConfigManager.getInstance(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val parms = session.parms

        // Suporte a body em POST (para cmd=setConfig)
        val files = HashMap<String, String>()
        if (method == Method.POST || method == Method.PUT) {
            try {
                session.parseBody(files)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar corpo da requisição POST", e)
            }
        }

        val cmd = parms["cmd"]
        val password = parms["password"] ?: parms["pwd"] ?: ""
        val requiredPass = settings.remoteAdminPassword

        // Verificação de autenticação por senha
        if (requiredPass.isNotEmpty() && password != requiredPass) {
            val errorJson = JSONObject().apply {
                put("status", "Error")
                put("message", "Acesso não autorizado: senha incorreta ou ausente")
            }
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json; charset=UTF-8",
                errorJson.toString()
            )
        }

        // Execução de comandos REST
        if (cmd != null) {
            return handleApiCommand(cmd, parms, files)
        }

        // Painel Web Administrativo HTML na raiz (GET /)
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=UTF-8",
            renderAdminDashboard()
        )
    }

    private fun handleApiCommand(
        cmd: String,
        params: Map<String, String>,
        files: Map<String, String>
    ): Response {
        val activity = getActiveActivity()
        val webView = getActiveWebView()
        val kioskManager = getKioskManager()

        when (cmd) {
            "loadStartUrl" -> {
                activity?.runOnUiThread {
                    val headers = mapOf("ngrok-skip-browser-warning" to "true")
                    webView?.loadUrl(settings.startUrl, headers)
                }
                return createSuccessResponse("Carregando startURL: ${settings.startUrl}")
            }

            "reload", "reloadTab" -> {
                activity?.runOnUiThread {
                    webView?.reload()
                }
                return createSuccessResponse("Página recarregada com sucesso")
            }

            "loadUrl", "loadURL" -> {
                val url = params["url"]
                if (url.isNullOrBlank()) {
                    return createErrorResponse("Parâmetro 'url' obrigatório")
                }
                activity?.runOnUiThread {
                    val headers = mapOf("ngrok-skip-browser-warning" to "true")
                    webView?.loadUrl(url, headers)
                }
                return createSuccessResponse("URL alterada para: $url")
            }

            "clearCache" -> {
                activity?.runOnUiThread {
                    webView?.clearCache(true)
                }
                return createSuccessResponse("Cache da WebView limpo")
            }

            "screenOn" -> {
                activity?.runOnUiThread {
                    activity.window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                    val lp = activity.window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity.window.attributes = lp
                }
                return createSuccessResponse("Tela ligada")
            }

            "screenOff" -> {
                activity?.runOnUiThread {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val lp = activity.window.attributes
                    lp.screenBrightness = 0.01f
                    activity.window.attributes = lp
                }
                return createSuccessResponse("Tela desligada / escurecida")
            }

            "getConfig" -> {
                val jsonString = configManager.exportToJsonString()
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=UTF-8",
                    jsonString
                )
            }

            "setConfig" -> {
                // Conteúdo pode vir no body do POST (postData) ou no parâmetro configJson
                val rawJson = files["postData"] ?: params["configJson"] ?: params["json"] ?: ""
                if (rawJson.isBlank()) {
                    return createErrorResponse("Corpo JSON ausente na requisição")
                }

                val success = configManager.importFromJsonString(rawJson)
                return if (success) {
                    activity?.runOnUiThread {
                        kioskManager?.applyKioskPolicies()
                        val headers = mapOf("ngrok-skip-browser-warning" to "true")
                        webView?.loadUrl(settings.startUrl, headers)
                    }
                    createSuccessResponse("Configurações atualizadas e aplicadas com sucesso")
                } else {
                    createErrorResponse("Formato JSON inválido para KioskConfig")
                }
            }

            "deviceInfo", "getDeviceInfo" -> {
                val json = getDeviceInfoJson()
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=UTF-8",
                    json.toString(2)
                )
            }

            else -> {
                return createErrorResponse("Comando desconhecido: $cmd")
            }
        }
    }

    private fun createSuccessResponse(msg: String): Response {
        val json = JSONObject().apply {
            put("status", "OK")
            put("message", msg)
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=UTF-8",
            json.toString()
        )
    }

    private fun createErrorResponse(msg: String): Response {
        val json = JSONObject().apply {
            put("status", "Error")
            put("message", msg)
        }
        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "application/json; charset=UTF-8",
            json.toString()
        )
    }

    private fun getDeviceInfoJson(): JSONObject {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100f).toInt() else 100

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val isPlugged = plugged > 0

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return JSONObject().apply {
            put("status", "OK")
            put("app", "Apex Kiosk Browser")
            put("version", "1.0.0")
            put("deviceModel", android.os.Build.MODEL ?: "Tablet Veicular")
            put("batteryLevel", batteryPct)
            put("isPlugged", isPlugged)
            put("isScreenInteractive", pm.isInteractive)
            put("kioskMode", settings.kioskMode)
            put("ipAddress", getDeviceIpAddress())
            put("startURL", settings.startUrl)
            put("currentURL", getActiveWebView()?.url ?: settings.startUrl)
        }
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        val isIPv4 = host.indexOf(':') < 0
                        if (isIPv4) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun renderAdminDashboard(): String {
        val info = getDeviceInfoJson()
        val ip = info.optString("ipAddress")
        val battery = info.optInt("batteryLevel")
        val isPlugged = info.optBoolean("isPlugged")
        val kioskStatus = if (settings.kioskMode) "ATIVO (Bloqueado)" else "DESATIVADO"
        val currentUrl = info.optString("currentURL")
        val pass = settings.remoteAdminPassword

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Apex Kiosk - Administração Remota</title>
              <style>
                * { box-sizing: border-box; }
                body { background: #0A0D14; color: #E2E8F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 20px; margin: 0; }
                .container { max-width: 650px; margin: 0 auto; background: #131722; padding: 24px; border-radius: 12px; border: 1px solid #1E293B; box-shadow: 0 8px 30px rgba(0,0,0,0.5); }
                h1 { color: #00E5FF; margin-top: 0; font-size: 22px; display: flex; align-items: center; gap: 8px; }
                .badge { font-size: 11px; padding: 4px 8px; border-radius: 4px; background: #00E5FF; color: #000; font-weight: bold; }
                .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 16px 0; }
                .card { background: #1A202C; padding: 12px 16px; border-radius: 8px; border: 1px solid #2D3748; font-size: 13px; }
                .card strong { color: #A0AEC0; display: block; font-size: 11px; text-transform: uppercase; margin-bottom: 4px; }
                .btn { display: inline-flex; align-items: center; justify-content: center; background: #00E5FF; color: #000; font-weight: bold; border: none; padding: 12px 18px; border-radius: 6px; cursor: pointer; text-decoration: none; font-size: 13px; margin: 4px; transition: 0.2s; }
                .btn:hover { background: #33ECFF; }
                .btn-danger { background: #EF4444; color: #FFF; }
                .btn-danger:hover { background: #DC2626; }
                .btn-secondary { background: #334155; color: #FFF; }
                .actions { margin-top: 20px; }
                input[type="text"], textarea { width: 100%; padding: 10px; background: #0A0D14; border: 1px solid #334155; border-radius: 6px; color: #FFF; margin-top: 6px; margin-bottom: 12px; font-family: monospace; font-size: 13px; }
              </style>
            </head>
            <body>
              <div class="container">
                <h1>⚙️ Apex Kiosk Remote Admin <span class="badge">Porta ${settings.remoteAdminPort}</span></h1>
                <p style="color: #94A3B8; font-size: 13px; margin-top: -6px;">Painel de Controle e REST API para Dispositivos Embarcados e Veiculares</p>

                <div class="grid">
                  <div class="card">
                    <strong>Dispositivo / IP</strong>
                    ${info.optString("deviceModel")} (${ip})
                  </div>
                  <div class="card">
                    <strong>Bateria</strong>
                    ${battery}% ${if (isPlugged) "⚡ (Carregando / Veículo)" else "🔋 (Bateria)"}
                  </div>
                  <div class="card">
                    <strong>Modo Quiosque</strong>
                    ${kioskStatus} (PIN: ${settings.kioskPin})
                  </div>
                  <div class="card">
                    <strong>URL Atual</strong>
                    <span style="word-break: break-all;">${currentUrl}</span>
                  </div>
                </div>

                <h3>Comandos Imediatos</h3>
                <div class="actions">
                  <a class="btn" href="/?cmd=loadStartUrl&password=${pass}">🏠 Voltar StartURL</a>
                  <a class="btn" href="/?cmd=reload&password=${pass}">🔄 Recarregar Player</a>
                  <a class="btn" href="/?cmd=clearCache&password=${pass}">🧹 Limpar Cache</a>
                  <a class="btn btn-secondary" href="/?cmd=screenOn&password=${pass}">💡 Ligar Tela</a>
                  <a class="btn btn-secondary" href="/?cmd=screenOff&password=${pass}">🌙 Desligar Tela</a>
                  <a class="btn btn-secondary" target="_blank" href="/?cmd=getConfig&password=${pass}">📄 Ver JSON Ativo</a>
                </div>

                <h3 style="margin-top: 24px;">Alterar URL em Tempo Real</h3>
                <form action="/" method="GET">
                  <input type="hidden" name="cmd" value="loadUrl">
                  <input type="hidden" name="password" value="${pass}">
                  <input type="text" name="url" placeholder="https://..." value="${settings.startUrl}">
                  <button class="btn" type="submit">Enviar URL para o Tablet</button>
                </form>

                <h3 style="margin-top: 24px;">Provisionar Novo JSON (setConfig)</h3>
                <form action="/?cmd=setConfig&password=${pass}" method="POST">
                  <textarea name="configJson" rows="6" placeholder="Cole o novo fully-auto-settings.json">${configManager.exportToJsonString()}</textarea>
                  <button class="btn btn-danger" type="submit">Salvar e Aplicar Imediatamente</button>
                </form>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "KioskHttpServer"
    }
}
