package com.apex.kiosk.settings

import android.content.Context

class AppSettings(context: Context) {

    private val configManager = ConfigManager.getInstance(context)

    val config: KioskConfig
        get() = configManager.currentConfig

    fun reload() {
        configManager.loadInitialConfig()
    }

    var startUrl: String
        get() = config.startURL
        set(value) {
            config.startURL = value
            configManager.updateConfig(config)
        }

    var kioskMode: Boolean
        get() = config.kioskMode
        set(value) {
            config.kioskMode = value
            configManager.updateConfig(config)
        }

    var kioskPin: String
        get() = config.kioskPin
        set(value) {
            config.kioskPin = value
            configManager.updateConfig(config)
        }

    var autostart: Boolean
        get() = config.autostart
        set(value) {
            config.autostart = value
            configManager.updateConfig(config)
        }

    var autostartOnPower: Boolean
        get() = config.autostartOnPower
        set(value) {
            config.autostartOnPower = value
            configManager.updateConfig(config)
        }

    var turnScreenOnOnPowerConnected: Boolean
        get() = config.turnScreenOnOnPowerConnected
        set(value) {
            config.turnScreenOnOnPowerConnected = value
            configManager.updateConfig(config)
        }

    var turnScreenOffOnPowerDisconnected: Boolean
        get() = config.turnScreenOffOnPowerDisconnected
        set(value) {
            config.turnScreenOffOnPowerDisconnected = value
            configManager.updateConfig(config)
        }

    var keepScreenOn: Boolean
        get() = config.keepScreenOn
        set(value) {
            config.keepScreenOn = value
            configManager.updateConfig(config)
        }

    var showActionBar: Boolean
        get() = config.showActionBar
        set(value) {
            config.showActionBar = value
            configManager.updateConfig(config)
        }

    var showStatusBar: Boolean
        get() = config.showStatusBar
        set(value) {
            config.showStatusBar = value
            configManager.updateConfig(config)
        }

    var showNavigationBar: Boolean
        get() = config.showNavigationBar
        set(value) {
            config.showNavigationBar = value
            configManager.updateConfig(config)
        }

    var enableVolumeButtons: Boolean
        get() = config.enableVolumeButtons
        set(value) {
            config.enableVolumeButtons = value
            configManager.updateConfig(config)
        }

    var preventSleep: Boolean
        get() = config.preventSleep
        set(value) {
            config.preventSleep = value
            configManager.updateConfig(config)
        }

    var showFullscreen: Boolean
        get() = config.showFullscreen
        set(value) {
            config.showFullscreen = value
            configManager.updateConfig(config)
        }

    var watchdog: Boolean
        get() = config.watchdog
        set(value) {
            config.watchdog = value
            configManager.updateConfig(config)
        }

    var remoteAdmin: Boolean
        get() = config.remoteAdmin
        set(value) {
            config.remoteAdmin = value
            configManager.updateConfig(config)
        }

    var remoteAdminPort: Int
        get() = config.remoteAdminPort
        set(value) {
            config.remoteAdminPort = value
            configManager.updateConfig(config)
        }

    var remoteAdminPassword: String
        get() = config.remoteAdminPassword
        set(value) {
            config.remoteAdminPassword = value
            configManager.updateConfig(config)
        }

    var enableWifiWakelock: Boolean
        get() = config.enableWifiWakelock
        set(value) {
            config.enableWifiWakelock = value
            configManager.updateConfig(config)
        }

    var clearCacheOnStart: Boolean
        get() = config.clearCacheOnStart
        set(value) {
            config.clearCacheOnStart = value
            configManager.updateConfig(config)
        }

    var enableOfflineCache: Boolean
        get() = config.enableOfflineCache
        set(value) {
            config.enableOfflineCache = value
            configManager.updateConfig(config)
        }

    var description: String
        get() = config.description
        set(value) {
            config.description = value
            configManager.updateConfig(config)
        }
}
