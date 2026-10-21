@echo off
chcp 65001 > nul
title Apex Kiosk Browser - Compilação de APK
cls
echo ========================================================
echo   APEX KIOSK BROWSER - COMPILAÇÃO DO APK ANDROID
echo ========================================================
echo.
echo Perfil Ativo: Configuração Veicular (Tela Livre)
echo URL Inicial:  https://vividness-reggae-sizably.ngrok-free.dev/player/
echo PIN Padrão:   2609
echo Porta Remota: 2323 (admin)
echo.
echo Opções de Compilação:
echo   [1] Compilar APK via Gradle Wrapper (Linha de Comando)
echo   [2] Instalar direto no Tablet conectado via ADB
echo   [3] Abrir a pasta do projeto no Explorador do Windows
echo   [4] Sair
echo.

set /p opcao="Escolha uma opção [1-4]: "

if "%opcao%"=="1" goto COMPILAR
if "%opcao%"=="2" goto INSTALAR_ADB
if "%opcao%"=="3" goto ABRIR_PASTA
if "%opcao%"=="4" goto SAIR

:COMPILAR
echo.
echo [*] Verificando ambiente de compilação...
where gradlew >nul 2>&1
if %errorlevel% neq 0 (
    echo [i] Gradle Wrapper local será utilizado se o Android SDK estiver configurado.
    echo [i] Caso prefira compilação visual em 1 clique:
    echo     Basta abrir a pasta 'ApexKioskBrowser' no Android Studio e clicar em 'Build > Build APK'.
)
pause
goto FIM

:INSTALAR_ADB
echo.
echo [*] Verificando dispositivos conectados via ADB...
adb devices
echo.
echo Se o seu tablet veicular estiver listado acima com depuração USB ativa:
echo Execute: adb install -r app\build\outputs\apk\debug\app-debug.apk
pause
goto FIM

:ABRIR_PASTA
explorer .
goto FIM

:SAIR
exit

:FIM
