package com.apex.kiosk.settings

import com.google.gson.annotations.SerializedName

data class KioskConfig(
    @SerializedName("startURL") var startURL: String = "https://vividness-reggae-sizably.ngrok-free.dev/player/",
    @SerializedName("kioskMode") var kioskMode: Boolean = true,
    @SerializedName("kioskPin") var kioskPin: String = "2609",
    @SerializedName("autostart") var autostart: Boolean = true,
    @SerializedName("autostartOnPower") var autostartOnPower: Boolean = true,
    @SerializedName("turnScreenOnOnPowerConnected") var turnScreenOnOnPowerConnected: Boolean = true,
    @SerializedName("turnScreenOffOnPowerDisconnected") var turnScreenOffOnPowerDisconnected: Boolean = true,
    @SerializedName("keepScreenOn") var keepScreenOn: Boolean = true,
    @SerializedName("showActionBar") var showActionBar: Boolean = false,
    @SerializedName("showStatusBar") var showStatusBar: Boolean = false,
    @SerializedName("showNavigationBar") var showNavigationBar: Boolean = false,
    @SerializedName("enableVolumeButtons") var enableVolumeButtons: Boolean = false,
    @SerializedName("preventSleep") var preventSleep: Boolean = true,
    @SerializedName("showFullscreen") var showFullscreen: Boolean = true,
    @SerializedName("watchdog") var watchdog: Boolean = true,
    @SerializedName("remoteAdmin") var remoteAdmin: Boolean = true,
    @SerializedName("remoteAdminPort") var remoteAdminPort: Int = 2323,
    @SerializedName("remoteAdminPassword") var remoteAdminPassword: String = "admin",
    @SerializedName("enableWifiWakelock") var enableWifiWakelock: Boolean = true,
    @SerializedName("clearCacheOnStart") var clearCacheOnStart: Boolean = false,
    @SerializedName("enableOfflineCache") var enableOfflineCache: Boolean = true,
    @SerializedName("description") var description: String = "Configuração de Produção para Tablets Veiculares - Tela Livre"
)
