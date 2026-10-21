package com.apex.kiosk.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.view.WindowManager
import android.webkit.WebView
import com.apex.kiosk.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class KioskHttpServer(
    private val context: Context,
    private val port: Int,
    private val getActiveActivity: () -> Activity?,
    private val getActiveWebView: () -> WebView?
) {

    private val settings = AppSettings(context)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (serverJob != null) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val clientSocket = serverSocket!!.accept()
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
        } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val rawPath = parts[1]

            // Lê headers para capturar Content-Length se houver POST
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            var postBody = ""
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                reader.read(charArray, 0, contentLength)
                postBody = String(charArray)
            }

            val uri = Uri.parse("http://localhost$rawPath")
            val params = mutableMapOf<String, String>()

            // Query params
            for (queryName in uri.queryParameterNames) {
                params[queryName] = uri.getQueryParameter(queryName) ?: ""
            }

            // Post body params (se form urlencoded ou JSON)
            if (postBody.startsWith("{")) {
                try {
                    val json = JSONObject(postBody)
                    json.keys().forEach { key ->
                        params[key] = json.optString(key)
                    }
                } catch (_: Exception) {}
            } else if (postBody.contains("=")) {
                postBody.split("&").forEach { pair ->
                    val kv = pair.split("=")
                    if (kv.size == 2) {
                        params[URLDecoder.decode(kv[0], "UTF-8")] = URLDecoder.decode(kv[1], "UTF-8")
                    }
                }
            }

            val cmd = params["cmd"]
            val pass = params["password"] ?: params["pwd"] ?: ""

            // Verificação de Senha
            val requiredPass = settings.remoteAdminPassword
            if (requiredPass.isNotEmpty() && pass != requiredPass && cmd != null) {
                sendJsonResponse(writer, 401, JSONObject().apply {
                    put("status", "Error")
                    put("message", "Senha incorreta para Administração Remota")
                })
                socket.close()
                return
            }

            // Roteamento de comandos API
            if (cmd != null) {
                handleApiCommand(cmd, params, writer)
            } else {
                // Serve o painel web HTML
                sendHtmlResponse(writer, renderAdminDashboard())
            }

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleApiCommand(cmd: String, params: Map<String, String>, writer: PrintWriter) {
        val activity = getActiveActivity()
        val webView = getActiveWebView()

        when (cmd) {
            "deviceInfo", "getDeviceInfo" -> {
                val json = getDeviceInfoJson()
                sendJsonResponse(writer, 200, json)
            }

            "screenOn" -> {
                activity?.runOnUiThread {
                    activity.window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                    val paramsAttr = activity.window.attributes
                    paramsAttr.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity.window.attributes = paramsAttr
                }
                sendSuccess(writer, "Tela ligada com sucesso")
            }

            "screenOff" -> {
                activity?.runOnUiThread {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val paramsAttr = activity.window.attributes
                    paramsAttr.screenBrightness = 0.01f
                    activity.window.attributes = paramsAttr
                }
                sendSuccess(writer, "Tela escurecida / desligada")
            }

            "loadURL", "loadUrl" -> {
                val url = params["url"] ?: settings.startUrl
                activity?.runOnUiThread {
                    webView?.loadUrl(url)
                }
                sendSuccess(writer, "URL carregada: $url")
            }

            "reloadTab", "reload" -> {
                activity?.runOnUiThread {
                    webView?.reload()
                }
                sendSuccess(writer, "Página recarregada")
            }

            "clearCache" -> {
                activity?.runOnUiThread {
                    webView?.clearCache(true)
                }
                sendSuccess(writer, "Cache limpo")
            }

            "setScreenBrightness" -> {
                val value = params["value"]?.toIntOrNull()?.coerceIn(0, 255) ?: 128
                activity?.runOnUiThread {
                    val paramsAttr = activity.window.attributes
                    paramsAttr.screenBrightness = value / 255f
                    activity.window.attributes = paramsAttr
                }
                sendSuccess(writer, "Brilho alterado para $value")
            }

            "restartApp" -> {
                sendSuccess(writer, "Reiniciando aplicativo...")
                activity?.runOnUiThread {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }

            else -> {
                sendJsonResponse(writer, 400, JSONObject().apply {
                    put("status", "Error")
                    put("message", "Comando desconhecido: $cmd")
                })
            }
        }
    }

    private fun getDeviceInfoJson(): JSONObject {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level.toFloat() / scale.toFloat()) * 100f else 100f

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val isPlugged = plugged > 0

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return JSONObject().apply {
            put("status", "OK")
            put("appVersion", "1.0.0 (Apex Kiosk)")
            put("deviceName", android.os.Build.MODEL ?: "Tablet Veicular")
            put("batteryLevel", batteryPct)
            put("isPlugged", isPlugged)
            put("screenOn", pm.isInteractive)
            put("startUrl", settings.startUrl)
            put("kioskMode", settings.kioskMode)
            put("currentUrl", getActiveWebView()?.url ?: settings.startUrl)
        }
    }

    private fun sendSuccess(writer: PrintWriter, message: String) {
        val json = JSONObject().apply {
            put("status", "OK")
            put("message", message)
        }
        sendJsonResponse(writer, 200, json)
    }

    private fun sendJsonResponse(writer: PrintWriter, code: Int, json: JSONObject) {
        val body = json.toString(2)
        writer.print("HTTP/1.1 $code OK\r\n")
        writer.print("Content-Type: application/json; charset=UTF-8\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun sendHtmlResponse(writer: PrintWriter, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=UTF-8\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(html)
        writer.flush()
    }

    private fun renderAdminDashboard(): String {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Apex Kiosk - Administração Remota</title>
              <style>
                body { background: #090B0F; color: #E2E8F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 24px; margin: 0; }
                .container { max-width: 600px; margin: 0 auto; background: #131722; padding: 24px; border-radius: 12px; border: 1px solid #1E293B; }
                h1 { color: #00E5FF; margin-top: 0; font-size: 22px; }
                .btn { display: inline-block; background: #00E5FF; color: #000; font-weight: bold; border: none; padding: 12px 20px; border-radius: 6px; cursor: pointer; margin: 6px 4px 6px 0; text-decoration: none; }
                .btn-danger { background: #EF4444; color: #FFF; }
                .card { background: #1E293B; padding: 14px; border-radius: 8px; margin: 16px 0; font-size: 14px; }
                input[type="text"] { width: calc(100% - 24px); padding: 10px; background: #090B0F; border: 1px solid #334155; border-radius: 6px; color: #FFF; margin-bottom: 12px; }
              </style>
            </head>
            <body>
              <div class="container">
                <h1>⚙️ Apex Kiosk Remote Admin</h1>
                <p>Tablet Veicular - Tela Livre (Porta ${settings.remoteAdminPort})</p>
                
                <div class="card">
                  <strong>URL Inicial:</strong> ${settings.startUrl}<br>
                  <strong>Modo Quiosque:</strong> ${if (settings.kioskMode) "ATIVADO" else "DESATIVADO"}<br>
                  <strong>PIN de Bloqueio:</strong> ${settings.kioskPin}
                </div>

                <h3>Controles Imediatos</h3>
                <a class="btn" href="/?cmd=screenOn&password=${settings.remoteAdminPassword}">💡 Ligar Tela</a>
                <a class="btn" href="/?cmd=screenOff&password=${settings.remoteAdminPassword}">🌙 Desligar Tela</a>
                <a class="btn" href="/?cmd=reload&password=${settings.remoteAdminPassword}">🔄 Recarregar Página</a>
                <a class="btn" href="/?cmd=clearCache&password=${settings.remoteAdminPassword}">🧹 Limpar Cache</a>
                <a class="btn btn-danger" href="/?cmd=restartApp&password=${settings.remoteAdminPassword}">⚠️ Reiniciar App</a>

                <h3>Carregar Nova URL</h3>
                <form action="/" method="GET">
                  <input type="hidden" name="cmd" value="loadURL">
                  <input type="hidden" name="password" value="${settings.remoteAdminPassword}">
                  <input type="text" name="url" placeholder="https://..." value="${settings.startUrl}">
                  <button class="btn" type="submit">Enviar para o Tablet</button>
                </form>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
