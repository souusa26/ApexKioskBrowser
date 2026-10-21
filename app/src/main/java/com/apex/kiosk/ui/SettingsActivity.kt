package com.apex.kiosk.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apex.kiosk.R
import com.apex.kiosk.settings.AppSettings
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = AppSettings(this)

        val editStartUrl = findViewById<EditText>(R.id.editStartUrl)
        val editKioskPin = findViewById<EditText>(R.id.editKioskPin)
        val switchKioskMode = findViewById<SwitchMaterial>(R.id.switchKioskMode)
        val switchKeepScreenOn = findViewById<SwitchMaterial>(R.id.switchKeepScreenOn)
        val switchRemoteAdmin = findViewById<SwitchMaterial>(R.id.switchRemoteAdmin)
        val switchJsInterface = findViewById<SwitchMaterial>(R.id.switchJsInterface)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnExitKiosk = findViewById<Button>(R.id.btnExitKiosk)

        // Preenche com os valores atuais do perfil
        editStartUrl.setText(settings.startUrl)
        editKioskPin.setText(settings.kioskPin)
        switchKioskMode.isChecked = settings.kioskMode
        switchKeepScreenOn.isChecked = settings.keepScreenOn
        switchRemoteAdmin.isChecked = settings.remoteAdmin
        switchJsInterface.isChecked = true

        btnSave.setOnClickListener {
            val url = editStartUrl.text.toString().trim()
            val pin = editKioskPin.text.toString().trim()

            if (url.isNotEmpty()) settings.startUrl = url
            if (pin.isNotEmpty()) settings.kioskPin = pin

            settings.kioskMode = switchKioskMode.isChecked
            settings.keepScreenOn = switchKeepScreenOn.isChecked
            settings.remoteAdmin = switchRemoteAdmin.isChecked

            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnExitKiosk.setOnClickListener {
            settings.kioskMode = false
            Toast.makeText(this, "Saindo do modo quiosque...", Toast.LENGTH_SHORT).show()
            finishAffinity()
        }
    }
}
