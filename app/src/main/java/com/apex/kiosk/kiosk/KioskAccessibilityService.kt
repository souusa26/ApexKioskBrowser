package com.apex.kiosk.kiosk

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.apex.kiosk.MainActivity
import com.apex.kiosk.settings.AppSettings

class KioskAccessibilityService : AccessibilityService() {

    private lateinit var settings: AppSettings

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Bloqueia tentativas de abrir outras janelas se o modo quiosque estiver ativo
        if (settings.kioskMode) {
            val packageName = event?.packageName?.toString() ?: return
            if (packageName != applicationContext.packageName &&
                !packageName.contains("systemui") &&
                !packageName.contains("inputmethod")
            ) {
                // Traz o app do Kiosk imediatamente de volta para o primeiro plano
                val bringBack = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(bringBack)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // Bloqueia botões de volume se configurado no perfil veicular
        if (!settings.enableVolumeButtons) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
            ) {
                return true // Consome o evento, impedindo a alteração de volume
            }
        }

        // Bloqueia teclas de navegação no modo quiosque
        if (settings.kioskMode) {
            if (keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_BACK
            ) {
                return true
            }
        }

        return super.onKeyEvent(event)
    }
}
