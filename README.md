# Apex Kiosk Browser 📱🚗 (Clean-Room Fully Kiosk Parity)

Implementação nativa limpa (*clean-room*) do **Fully Kiosk Browser** para Android, desenvolvida em **Kotlin** e arquitetada para operação contínua em **tablets veiculares, embarcados, COSU (Corporate-Owned, Single-Use) e Digital Signage**.

O aplicativo consome nativamente o perfil de provisionamento zero-touch **`fully-auto-settings.json`** e implementa 100% dos controles nativos de bloqueio, energia e administração remota.

---

## 🏛️ Arquitetura do Sistema

A arquitetura do `ApexKioskBrowser` é dividida em módulos desacoplados orientados a eventos de hardware e conectividade:

```
                +---------------------------------------+
                |        fully-auto-settings.json       |
                | (SDCard / Downloads / Files / Assets) |
                +-------------------+-------------------+
                                    |
                            [ConfigManager]
                                    |
          +-------------------------+-------------------------+
          |                         |                         |
[KioskManager]             [MainActivity]           [KioskForegroundService]
- LockTask (Device Owner)  - HTML5 & Fullscreen Vid  - Persistência START_STICKY
- Sticky Immersive Mode    - Offline Cache Resilience- Servidor NanoHTTPD :2323
- DispatchKeyEvent Block   - Touch PIN Keypad (2609) - High-Perf WifiLock
- Screen Wake / LockNow    - Crash Watchdog Restart  - Watchdog Top-App Loop
          ^                         ^                         ^
          |                         |                         |
          +-------------------------+-------------------------+
                                    |
                        [BootAndPowerReceiver]
             - BOOT_COMPLETED & QUICKBOOT_POWERON
             - ACTION_POWER_CONNECTED (Acorda tela)
             - ACTION_POWER_DISCONNECTED (lockNow)
```

---

## 📋 Mapeamento Exato do Perfil JSON (`fully-auto-settings.json`)

```json
{
  "startURL": "https://vividness-reggae-sizably.ngrok-free.dev/player/",
  "kioskMode": true,
  "kioskPin": "2609",
  "autostart": true,
  "autostartOnPower": true,
  "turnScreenOnOnPowerConnected": true,
  "turnScreenOffOnPowerDisconnected": true,
  "keepScreenOn": true,
  "showActionBar": false,
  "showStatusBar": false,
  "showNavigationBar": false,
  "enableVolumeButtons": false,
  "preventSleep": true,
  "showFullscreen": true,
  "watchdog": true,
  "remoteAdmin": true,
  "remoteAdminPort": 2323,
  "remoteAdminPassword": "admin",
  "enableWifiWakelock": true,
  "clearCacheOnStart": false,
  "enableOfflineCache": true,
  "description": "Configuração de Produção para Tablets Veiculares - Tela Livre"
}
```

### Ordem de Descoberta Zero-Touch do JSON
Ao iniciar, o `ConfigManager` procura o arquivo na seguinte ordem:
1. **Armazenamento Externo / Raiz:** `/sdcard/fully-auto-settings.json`, `/storage/emulated/0/Download/fully-auto-settings.json`
2. **Diretório do App:** `context.getExternalFilesDir(null)/fully-auto-settings.json`
3. **Persistência Interna Ativa:** `context.filesDir/active_kiosk_config.json`
4. **Fallback de Fábrica Embutido no APK:** `assets/fully-auto-settings.json`

---

## ⚡ Comportamento Nativo de Energia Veicular

| Evento | Flag JSON | Ação Executada |
| :--- | :--- | :--- |
| **Inicialização do Aparelho** | `autostart: true` | Inicia o app automaticamente no boot (`BOOT_COMPLETED` e `QUICKBOOT_POWERON`). |
| **Ignição / Carregador Conectado** | `autostartOnPower: true`<br>`turnScreenOnOnPowerConnected: true` | Dispara `PowerManager.WakeLock` (`ACQUIRE_CAUSES_WAKEUP | SCREEN_BRIGHT_WAKE_LOCK`), ativa `FLAG_KEEP_SCREEN_ON` e traz a `MainActivity` para a frente. |
| **Ignição Desligada / USB Desconectada** | `turnScreenOffOnPowerDisconnected: true` | Bloqueia a tela instantaneamente via `DevicePolicyManager.lockNow()` (se Device Owner) ou reduz o brilho da janela ao mínimo com liberação de wake locks. |
| **Prevenção de Suspensão** | `keepScreenOn: true`<br>`preventSleep: true` | Mantém a janela com `FLAG_KEEP_SCREEN_ON` e adquire um `PARTIAL_WAKE_LOCK` em segundo plano. |

---

## 🔒 Lockdown e Desbloqueio com PIN

1. **Launcher Principal do Sistema:** O app declara `<category android:name="android.intent.category.HOME" />`, podendo ser selecionado como o Launcher padrão do Android.
2. **Modo Imersivo Sticky:** Status bar, Navigation bar e Action bar são 100% ocultadas.
3. **Bloqueio de Teclas Físicas:** Quando `enableVolumeButtons == false` e `kioskMode == true`, as teclas de Volume + e Volume - e a tecla Voltar são completamente interceptadas e descartadas no `dispatchKeyEvent`.
4. **Desbloqueio Seguro (Gesto ou Botão Flutuante):**
   - **Gesto Oculto:** Dê **7 toques rápidos no canto superior esquerdo ou inferior direito**.
   - **Botão Flutuante:** Toque no botão circular âmbar de cadeado no canto superior direito.
   - Digite o PIN configurado (`2609`) no teclado numérico touch screen.
   - Ao autenticar com sucesso, o app executa `stopLockTask()` e abre o painel administrativo (Recarregar, Configurações, Limpar Cache, Sair do Modo Quiosque).

---

## 🛡️ Ativação do Modo Device Owner (Bloqueio 100% Inviolável)

Para que o tablet não permita que nenhum usuário saia do app de forma alguma (desativando o botão Home, barra de notificações e menus de sistema):

1. Habilite a **Depuração USB** nas Opções do Desenvolvedor do Android.
2. No computador, execute via terminal/prompt de comando:
```bash
adb shell dpm set-device-owner com.apex.kiosk/.kiosk.ApexDeviceAdminReceiver
```
> **Nota:** No Android puro, o comando deve ser executado antes de adicionar contas pessoais do Google no aparelho (ou após um factory reset). Se não for possível ativar como Device Owner, o app operará via Screen Pinning nativo + Interceptação de foco e Serviço de Acessibilidade.

---

## 🌐 Administração Remota e Endpoints REST (Porta 2323)

O servidor embutido **NanoHTTPD** roda no `KioskForegroundService` na porta 2323:

- **Painel Web HTML:** `http://[IP_DO_TABLET]:2323/` (Exibe status de bateria, carregador, URL e botões de ação).
- **Voltar para a StartURL:**
  `GET /?cmd=loadStartUrl&password=admin`
- **Recarregar Página:**
  `GET /?cmd=reload&password=admin`
- **Carregar Nova URL em Tempo Real:**
  `GET /?cmd=loadUrl&url=https://outrolink.com&password=admin`
- **Limpar Cache da WebView:**
  `GET /?cmd=clearCache&password=admin`
- **Ligar a Tela:**
  `GET /?cmd=screenOn&password=admin`
- **Desligar a Tela:**
  `GET /?cmd=screenOff&password=admin`
- **Exportar Configuração Atual (JSON):**
  `GET /?cmd=getConfig&password=admin`
- **Atualizar Configuração Dinamicamente (Zero-Touch via REST):**
  `POST /?cmd=setConfig&password=admin` (com o novo JSON no corpo da requisição).
- **Status do Dispositivo (JSON):**
  `GET /?cmd=getDeviceInfo&password=admin`

---

## 🛠️ Como Compilar o APK

Para compilar via linha de comando local ou em CI:
```bash
./gradlew assembleDebug
```
O arquivo APK gerado estará em:
`app/build/outputs/apk/debug/app-debug.apk`
