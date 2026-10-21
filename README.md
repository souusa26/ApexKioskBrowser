# Apex Kiosk Browser 📱🚗 (Clone Nativo do Fully Kiosk)

Implementação nativa limpa (*clean-room*) do **Fully Kiosk Browser** para Android, desenvolvida em **Kotlin** e especificamente calibrada para tablets veiculares e quiosques comerciais.

O aplicativo já vem pré-configurado com o perfil de produção:
`"Configuração de Produção para Tablets Veiculares - Tela Livre"`

---

## 📋 Mapeamento Exato do Perfil JSON Implementado

| Parâmetro no JSON | Valor | Como Funciona no App |
| :--- | :--- | :--- |
| `startURL` | `https://vividness-reggae-sizably.ngrok-free.dev/player/` | Carregado automaticamente no `WebView` no startup |
| `kioskMode` | `true` | Ativa o bloqueio quiosque (`startLockTask()` / `KioskManager`), impedindo a saída do app |
| `kioskPin` | `"2609"` | PIN obrigatório para abrir as configurações ou desativar o quiosque |
| `autostart` | `true` | Inicia automaticamente assim que o tablet liga (`BootReceiver`) |
| `autostartOnPower` | `true` | Abre o app instantaneamente ao plugar o carregador veicular / ligar a chave do carro |
| `turnScreenOnOnPowerConnected` | `true` | Acorda a tela (`ACQUIRE_CAUSES_WAKEUP`) ao ligar o carro |
| `turnScreenOffOnPowerDisconnected` | `true` | Escurece / desliga a tela ao desligar o carro, economizando a bateria do veículo |
| `keepScreenOn` | `true` | `FLAG_KEEP_SCREEN_ON` para a tela nunca apagar durante o trajeto |
| `showActionBar` | `false` | Barra superior de título completamente removida (`NoActionBar`) |
| `showStatusBar` | `false` | Barra de status (relógio, bateria, notificações) 100% oculta via `WindowInsetsControllerCompat` |
| `showNavigationBar` | `false` | Barra de navegação inferior (Voltar, Home, Recentes) 100% oculta |
| `enableVolumeButtons` | `false` | Bloqueia fisicamente os botões de volume (`KeyEvent.KEYCODE_VOLUME_*`) para evitar alterações por passageiros |
| `preventSleep` | `true` | Adquire `PowerManager.PARTIAL_WAKE_LOCK` contínuo em background |
| `showFullscreen` | `true` | Modo imersivo pegajoso (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) ocupando 100% dos pixels do display |
| `watchdog` | `true` | Monitor de falhas (`UncaughtExceptionHandler`): se o app travar ou fechar inesperadamente, ele reinicia sozinho em milissegundos |
| `remoteAdmin` | `true` | Roda um servidor HTTP embutido em segundo plano via `RemoteAdminService` |
| `remoteAdminPort` | `2323` | Porta padrão compatível com Fully Kiosk e Home Assistant (`http://[IP_DO_TABLET]:2323`) |
| `remoteAdminPassword` | `"admin"` | Autenticação para comandos remotos |
| `enableWifiWakelock` | `true` | `WifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF)` para o Wi-Fi nunca entrar em economia de energia |
| `clearCacheOnStart` | `false` | Preserva os dados do player em cache para inicialização ultrarrápida |
| `enableOfflineCache` | `true` | Ativa `WebSettings.LOAD_DEFAULT`, `domStorageEnabled` e `databaseEnabled` para reprodução offline |

---

## 🛠️ Como Acessar o Menu Secreto no Tablet

1. Dê **4 toques rápidos no canto superior direito da tela** (onde ficaria o relógio).
2. Uma caixa solicitará o **PIN do Administrador**.
3. Digite `2609` e clique em **Acessar**.
4. Você terá acesso à tela de configurações para alterar a URL, o PIN ou sair do aplicativo.

---

## 🌐 Comandos Remotos Compatíveis (Porta 2323)

Você pode controlar o tablet pelo navegador do seu computador ou integrar com o Home Assistant:

- **Painel Web:** Acesse `http://[IP_DO_TABLET]:2323/`
- **Ligar Tela:** `http://[IP_DO_TABLET]:2323/?cmd=screenOn&password=admin`
- **Desligar Tela:** `http://[IP_DO_TABLET]:2323/?cmd=screenOff&password=admin`
- **Status da Bateria e Tela (JSON):** `http://[IP_DO_TABLET]:2323/?cmd=getDeviceInfo&password=admin`
- **Trocar URL:** `http://[IP_DO_TABLET]:2323/?cmd=loadURL&url=https://outraurl.com&password=admin`
- **Reiniciar App:** `http://[IP_DO_TABLET]:2323/?cmd=restartApp&password=admin`

---

## 📱 Injeção JavaScript (`window.fully`)

Qualquer página HTML/JS executada dentro do WebView pode controlar o tablet usando os mesmos comandos do Fully Kiosk:

```javascript
// Exemplos de chamadas JavaScript direto na sua página do player:
console.log("Bateria: " + window.fully.getBatteryLevel() + "%");
console.log("Conectado ao carregador: " + window.fully.isPlugged());
console.log("IP do Tablet: " + window.fully.getIp4Address());

// Acordar a tela ou ajustar o brilho
window.fully.turnScreenOn();
window.fully.setScreenBrightness(255); // Brilho máximo
```

---

## 🚀 Como Gerar o APK (.apk)

1. Abra a pasta `ApexKioskBrowser` no **Android Studio** (ou use a linha de comando com `gradlew assembleRelease` / `gradlew assembleDebug`).
2. O APK final será gerado em:
   `app/build/outputs/apk/debug/app-debug.apk` ou `app-release.apk`
3. Instale no seu tablet com o comando:
   ```bash
   adb install -r app-debug.apk
   ```
4. *(Opcional)* Para ativar o modo quiosque à prova de evasão (Device Owner via ADB):
   ```bash
   adb shell dpm set-device-owner com.apex.kiosk/.kiosk.ApexDeviceAdminReceiver
   ```
