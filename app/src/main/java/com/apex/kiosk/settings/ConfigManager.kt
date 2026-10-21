package com.apex.kiosk.settings

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.InputStreamReader

class ConfigManager private constructor(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val listeners = mutableListOf<OnConfigChangeListener>()

    @Volatile
    var currentConfig: KioskConfig = KioskConfig()
        private set

    init {
        loadInitialConfig()
    }

    interface OnConfigChangeListener {
        fun onConfigChanged(newConfig: KioskConfig)
    }

    fun addListener(listener: OnConfigChangeListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: OnConfigChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyConfigChanged(newConfig: KioskConfig) {
        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener.onConfigChanged(newConfig)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying config listener", e)
                }
            }
        }
    }

    /**
     * ETAPA 2: Parser e Provisionamento Zero-Touch (fully-auto-settings.json)
     * Ordem de busca:
     * 1. Armazenamento externo / Download (/sdcard/, /storage/emulated/0/Download/)
     * 2. getExternalFilesDir(null)
     * 3. Fallback nos assets locais (assets/fully-auto-settings.json)
     */
    fun loadInitialConfig(): KioskConfig {
        // 1. Tenta carregar do armazenamento externo ou Downloads
        val externalSearchPaths = listOf(
            File("/sdcard/fully-auto-settings.json"),
            File("/storage/emulated/0/Download/fully-auto-settings.json"),
            File(Environment.getExternalStorageDirectory(), "fully-auto-settings.json"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fully-auto-settings.json"),
            File("/sdcard/telalivre.json"),
            File("/storage/emulated/0/Download/telalivre.json")
        )

        for (file in externalSearchPaths) {
            if (file.exists() && file.canRead()) {
                val config = parseJsonFile(file)
                if (config != null) {
                    Log.i(TAG, "Provisionamento carregado de: ${file.absolutePath}")
                    currentConfig = config
                    saveToInternalStorage(config)
                    return config
                }
            }
        }

        // 2. Tenta carregar de context.getExternalFilesDir(null)
        val appExternalDir = context.getExternalFilesDir(null)
        if (appExternalDir != null) {
            val appFile = File(appExternalDir, "fully-auto-settings.json")
            if (appFile.exists() && appFile.canRead()) {
                val config = parseJsonFile(appFile)
                if (config != null) {
                    Log.i(TAG, "Provisionamento carregado de: ${appFile.absolutePath}")
                    currentConfig = config
                    saveToInternalStorage(config)
                    return config
                }
            }
        }

        // 3. Verifica se já há uma configuração interna persistida previamente
        val internalFile = File(context.filesDir, "active_kiosk_config.json")
        if (internalFile.exists() && internalFile.canRead()) {
            val config = parseJsonFile(internalFile)
            if (config != null) {
                Log.i(TAG, "Configuração ativa carregada da persistência interna.")
                currentConfig = config
                return config
            }
        }

        // 4. Fallback embutido no APK: assets/fully-auto-settings.json ou assets/default_config.json
        val assetNames = listOf("fully-auto-settings.json", "default_config.json")
        for (asset in assetNames) {
            try {
                context.assets.open(asset).use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        val config = gson.fromJson(reader, KioskConfig::class.java)
                        if (config != null) {
                            Log.i(TAG, "Configuração de fábrica carregada dos assets: $asset")
                            currentConfig = config
                            saveToInternalStorage(config)
                            return config
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Retorna valores padrão caso nada seja encontrado
        currentConfig = KioskConfig()
        saveToInternalStorage(currentConfig)
        return currentConfig
    }

    private fun parseJsonFile(file: File): KioskConfig? {
        return try {
            file.reader().use { reader ->
                gson.fromJson(reader, KioskConfig::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao processar arquivo JSON: ${file.absolutePath}", e)
            null
        }
    }

    fun updateConfig(newConfig: KioskConfig) {
        currentConfig = newConfig
        saveToInternalStorage(newConfig)
        notifyConfigChanged(newConfig)
    }

    fun importFromJsonString(jsonString: String): Boolean {
        return try {
            val config = gson.fromJson(jsonString, KioskConfig::class.java)
            if (config != null) {
                updateConfig(config)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar JSON String", e)
            false
        }
    }

    fun importFromFile(file: File): Boolean {
        val config = parseJsonFile(file) ?: return false
        updateConfig(config)
        return true
    }

    fun exportToJsonString(): String {
        return gson.toJson(currentConfig)
    }

    private fun saveToInternalStorage(config: KioskConfig) {
        try {
            val internalFile = File(context.filesDir, "active_kiosk_config.json")
            internalFile.writeText(gson.toJson(config))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao persistir configuração interna", e)
        }
    }

    companion object {
        private const val TAG = "ConfigManager"

        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
