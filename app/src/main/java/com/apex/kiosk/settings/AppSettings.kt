package com.apex.kiosk.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.InputStreamReader

class AppSettings(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apex_kiosk_prefs", Context.MODE_PRIVATE)

    init {
        // Se for a primeira inicialização, carrega o default_config.json dos assets
        if (!prefs.contains("initialized")) {
            loadFromDefaultAsset()
        }
    }

    private fun loadFromDefaultAsset() {
        try {
            val inputStream = context.assets.open("default_config.json")
            val jsonStr = InputStreamReader(inputStream).readText()
            inputStream.close()
            importFromJson(JSONObject(jsonStr))
            prefs.edit().putBoolean("initialized", true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun importFromJson(json: JSONObject) {
        val editor = prefs.edit()
        if (json.has("startURL")) editor.putString("startURL", json.getString("startURL"))
        if (json.has("kioskMode")) editor.putBoolean("kioskMode", json.getBoolean("kioskMode"))
        if (json.has("kioskPin")) editor.putString("kioskPin", json.getString("kioskPin"))
        if (json.has("autostart")) editor.putBoolean("autostart", json.getBoolean("autostart"))
        if (json.has("autostartOnPower")) editor.putBoolean("autostartOnPower", json.getBoolean("autostartOnPower"))
        if (json.has("turnScreenOnOnPowerConnected")) editor.putBoolean("turnScreenOnOnPowerConnected", json.getBoolean("turnScreenOnOnPowerConnected"))
        if (json.has("turnScreenOffOnPowerDisconnected")) editor.putBoolean("turnScreenOffOnPowerDisconnected", json.getBoolean("turnScreenOffOnPowerDisconnected"))
        if (json.has("keepScreenOn")) editor.putBoolean("keepScreenOn", json.getBoolean("keepScreenOn"))
        if (json.has("showActionBar")) editor.putBoolean("showActionBar", json.getBoolean("showActionBar"))
        if (json.has("showStatusBar")) editor.putBoolean("showStatusBar", json.getBoolean("showStatusBar"))
        if (json.has("showNavigationBar")) editor.putBoolean("showNavigationBar", json.getBoolean("showNavigationBar"))
        if (json.has("enableVolumeButtons")) editor.putBoolean("enableVolumeButtons", json.getBoolean("enableVolumeButtons"))
        if (json.has("preventSleep")) editor.putBoolean("preventSleep", json.getBoolean("preventSleep"))
        if (json.has("showFullscreen")) editor.putBoolean("showFullscreen", json.getBoolean("showFullscreen"))
        if (json.has("watchdog")) editor.putBoolean("watchdog", json.getBoolean("watchdog"))
        if (json.has("remoteAdmin")) editor.putBoolean("remoteAdmin", json.getBoolean("remoteAdmin"))
        if (json.has("remoteAdminPort")) editor.putInt("remoteAdminPort", json.getInt("remoteAdminPort"))
        if (json.has("remoteAdminPassword")) editor.putString("remoteAdminPassword", json.getString("remoteAdminPassword"))
        if (json.has("enableWifiWakelock")) editor.putBoolean("enableWifiWakelock", json.getBoolean("enableWifiWakelock"))
        if (json.has("clearCacheOnStart")) editor.putBoolean("clearCacheOnStart", json.getBoolean("clearCacheOnStart"))
        if (json.has("enableOfflineCache")) editor.putBoolean("enableOfflineCache", json.getBoolean("enableOfflineCache"))
        if (json.has("description")) editor.putString("description", json.getString("description"))
        editor.apply()
    }

    var startUrl: String
        get() = prefs.getString("startURL", "https://vividness-reggae-sizably.ngrok-free.dev/player/") ?: "https://vividness-reggae-sizably.ngrok-free.dev/player/"
        set(value) = prefs.edit().putString("startURL", value).apply()

    var kioskMode: Boolean
        get() = prefs.getBoolean("kioskMode", true)
        set(value) = prefs.edit().putBoolean("kioskMode", value).apply()

    var kioskPin: String
        get() = prefs.getString("kioskPin", "2609") ?: "2609"
        set(value) = prefs.edit().putString("kioskPin", value).apply()

    var autostart: Boolean
        get() = prefs.getBoolean("autostart", true)
        set(value) = prefs.edit().putBoolean("autostart", value).apply()

    var autostartOnPower: Boolean
        get() = prefs.getBoolean("autostartOnPower", true)
        set(value) = prefs.edit().putBoolean("autostartOnPower", value).apply()

    var turnScreenOnOnPowerConnected: Boolean
        get() = prefs.getBoolean("turnScreenOnOnPowerConnected", true)
        set(value) = prefs.edit().putBoolean("turnScreenOnOnPowerConnected", value).apply()

    var turnScreenOffOnPowerDisconnected: Boolean
        get() = prefs.getBoolean("turnScreenOffOnPowerDisconnected", true)
        set(value) = prefs.edit().putBoolean("turnScreenOffOnPowerDisconnected", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keepScreenOn", true)
        set(value) = prefs.edit().putBoolean("keepScreenOn", value).apply()

    var showActionBar: Boolean
        get() = prefs.getBoolean("showActionBar", false)
        set(value) = prefs.edit().putBoolean("showActionBar", value).apply()

    var showStatusBar: Boolean
        get() = prefs.getBoolean("showStatusBar", false)
        set(value) = prefs.edit().putBoolean("showStatusBar", value).apply()

    var showNavigationBar: Boolean
        get() = prefs.getBoolean("showNavigationBar", false)
        set(value) = prefs.edit().putBoolean("showNavigationBar", value).apply()

    var enableVolumeButtons: Boolean
        get() = prefs.getBoolean("enableVolumeButtons", false)
        set(value) = prefs.edit().putBoolean("enableVolumeButtons", value).apply()

    var preventSleep: Boolean
        get() = prefs.getBoolean("preventSleep", true)
        set(value) = prefs.edit().putBoolean("preventSleep", value).apply()

    var showFullscreen: Boolean
        get() = prefs.getBoolean("showFullscreen", true)
        set(value) = prefs.edit().putBoolean("showFullscreen", value).apply()

    var watchdog: Boolean
        get() = prefs.getBoolean("watchdog", true)
        set(value) = prefs.edit().putBoolean("watchdog", value).apply()

    var remoteAdmin: Boolean
        get() = prefs.getBoolean("remoteAdmin", true)
        set(value) = prefs.edit().putBoolean("remoteAdmin", value).apply()

    var remoteAdminPort: Int
        get() = prefs.getInt("remoteAdminPort", 2323)
        set(value) = prefs.edit().putInt("remoteAdminPort", value).apply()

    var remoteAdminPassword: String
        get() = prefs.getString("remoteAdminPassword", "admin") ?: "admin"
        set(value) = prefs.edit().putString("remoteAdminPassword", value).apply()

    var enableWifiWakelock: Boolean
        get() = prefs.getBoolean("enableWifiWakelock", true)
        set(value) = prefs.edit().putBoolean("enableWifiWakelock", value).apply()

    var clearCacheOnStart: Boolean
        get() = prefs.getBoolean("clearCacheOnStart", false)
        set(value) = prefs.edit().putBoolean("clearCacheOnStart", value).apply()

    var enableOfflineCache: Boolean
        get() = prefs.getBoolean("enableOfflineCache", true)
        set(value) = prefs.edit().putBoolean("enableOfflineCache", value).apply()
}
