@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

:: ============================================================
:: ERP Kit - Deploy / Gerenciamento (Windows)
:: Comandos: start, stop, restart, status, logs
:: ============================================================

:: Detectar diretorio de instalacao
set "SCRIPT_DIR=%~dp0"

:: Se executado de dentro de bin/, subir um nivel
if exist "%SCRIPT_DIR%..\lib" (
    set "INSTALL_DIR=%SCRIPT_DIR%.."
) else if exist "%SCRIPT_DIR%..\api-email\target" (
    :: Executado do diretorio scripts/ no projeto fonte
    set "INSTALL_DIR=%SCRIPT_DIR%.."
    set "DEV_MODE=true"
) else (
    set "INSTALL_DIR=%SCRIPT_DIR%.."
)

:: Carregar configuracao de ambiente se existir
if exist "%INSTALL_DIR%\config\env.cmd" (
    call "%INSTALL_DIR%\config\env.cmd"
)

set "LOGS_DIR=%INSTALL_DIR%\logs"
if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

:: Modulos disponiveis
set "MODULES=api-email api-storage"
set "PORT_api-email=9091"
set "PORT_api-storage=8085"

:: Parsear argumentos
set "ACTION=%~1"
set "TARGET=%~2"

if "%ACTION%"=="" goto :usage
if /i "%ACTION%"=="--help" goto :usage
if /i "%ACTION%"=="-h" goto :usage

:: Validar acao
set "VALID_ACTION=false"
for %%a in (start stop restart status logs) do (
    if /i "%ACTION%"=="%%a" set "VALID_ACTION=true"
)
if "%VALID_ACTION%"=="false" (
    echo   [ERRO] Acao desconhecida: %ACTION%
    goto :usage
)

:: Se target vazio, aplicar a todos os modulos
if "%TARGET%"=="" (
    for %%m in (%MODULES%) do (
        call :do_action %ACTION% %%m
    )
) else (
    :: Validar modulo
    set "VALID_MODULE=false"
    for %%m in (%MODULES%) do (
        if /i "%TARGET%"=="%%m" set "VALID_MODULE=true"
    )
    if "!VALID_MODULE!"=="false" (
        echo   [ERRO] Modulo desconhecido: %TARGET%
        echo   Modulos disponiveis: %MODULES%
        exit /b 1
    )
    call :do_action %ACTION% %TARGET%
)

goto :eof

:: -------------------------------------------------------
:: Funcoes
:: -------------------------------------------------------

:usage
echo.
echo   ERP Kit - Deploy / Gerenciamento
echo   ==================================
echo.
echo   Uso: deploy.cmd ^<acao^> [modulo]
echo.
echo   Acoes:
echo     start     Inicia o(s) modulo(s)
echo     stop      Para o(s) modulo(s)
echo     restart   Reinicia o(s) modulo(s)
echo     status    Mostra status do(s) modulo(s)
echo     logs      Mostra ultimas linhas do log
echo.
echo   Modulos:
echo     api-email     API de Email (porta 9091)
echo     api-storage   API de Storage (porta 8085)
echo.
echo   Se nenhum modulo for especificado, a acao se aplica a todos.
echo.
echo   Exemplos:
echo     deploy.cmd start               Inicia todos
echo     deploy.cmd stop api-email      Para apenas o api-email
echo     deploy.cmd restart api-storage Reinicia o api-storage
echo     deploy.cmd status              Mostra status de todos
echo     deploy.cmd logs api-email      Mostra log do api-email
echo.
exit /b 0

:do_action
set "ACT=%~1"
set "MOD=%~2"

if /i "%ACT%"=="start" call :do_start %MOD%
if /i "%ACT%"=="stop" call :do_stop %MOD%
if /i "%ACT%"=="restart" (
    call :do_stop %MOD%
    ping -n 3 127.0.0.1 >nul
    call :do_start %MOD%
)
if /i "%ACT%"=="status" call :do_status %MOD%
if /i "%ACT%"=="logs" call :do_logs %MOD%
goto :eof

:do_start
set "MOD=%~1"
set "PORT=!PORT_%MOD%!"

:: Verificar se ja esta rodando
call :check_port %PORT% IS_RUNNING
if "!IS_RUNNING!"=="true" (
    echo   [%MOD%] Ja esta rodando na porta %PORT%
    goto :eof
)

:: Encontrar JAR
set "JAR_FILE="
if defined DEV_MODE (
    for %%f in ("%INSTALL_DIR%\%MOD%\target\%MOD%-*.jar") do (
        echo %%~nxf | findstr /i ".original" >nul
        if !errorlevel! neq 0 set "JAR_FILE=%%f"
    )
) else (
    for %%f in ("%INSTALL_DIR%\lib\%MOD%-*.jar") do (
        echo %%~nxf | findstr /i ".original" >nul
        if !errorlevel! neq 0 set "JAR_FILE=%%f"
    )
)

if not defined JAR_FILE (
    echo   [%MOD%] [ERRO] JAR nao encontrado. Execute o build primeiro.
    goto :eof
)

echo   [%MOD%] Iniciando na porta %PORT%...

:: Iniciar em background
start "%MOD%" /b javaw -jar "%JAR_FILE%" > "%LOGS_DIR%\%MOD%.log" 2>&1

:: Aguardar startup (max 30s)
set "WAIT=0"
:wait_start
if %WAIT% geq 30 (
    echo   [%MOD%] [AVISO] Timeout aguardando startup. Verifique o log.
    goto :eof
)
ping -n 3 127.0.0.1 >nul
set /a WAIT+=2

call :check_port %PORT% IS_UP
if "!IS_UP!"=="true" (
    echo   [%MOD%] Rodando na porta %PORT% - OK
    goto :eof
)
goto :wait_start

:do_stop
set "MOD=%~1"
set "PORT=!PORT_%MOD%!"

call :check_port %PORT% IS_RUNNING
if "!IS_RUNNING!"=="false" (
    echo   [%MOD%] Nao esta rodando
    goto :eof
)

echo   [%MOD%] Parando...

:: Encontrar PID pela porta e matar
for /f %%p in ('powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue).OwningProcess | Select-Object -First 1"') do (
    set "KILL_PID=%%p"
    taskkill /PID %%p /F >nul 2>&1
    if !errorlevel! neq 0 (
        :: Fallback: tentar via PowerShell
        powershell -NoProfile -Command "Stop-Process -Id %%p -Force -ErrorAction SilentlyContinue" >nul 2>&1
    )
)

ping -n 3 127.0.0.1 >nul

call :check_port %PORT% STILL_RUNNING
if "!STILL_RUNNING!"=="true" (
    echo   [%MOD%] [AVISO] Nao foi possivel parar. Execute como Administrador.
) else (
    echo   [%MOD%] Parado - OK
)
goto :eof

:do_status
set "MOD=%~1"
set "PORT=!PORT_%MOD%!"

call :check_port %PORT% IS_RUNNING
if "!IS_RUNNING!"=="true" (
    :: Tentar health check
    curl -s -o nul -w "%%{http_code}" http://localhost:%PORT%/health >"%TEMP%\erpkit_hc.txt" 2>nul
    set /p HC=<"%TEMP%\erpkit_hc.txt"
    del "%TEMP%\erpkit_hc.txt" 2>nul

    if "!HC!"=="200" (
        echo   [%MOD%] RODANDO  porta=%PORT%  health=OK
    ) else (
        echo   [%MOD%] RODANDO  porta=%PORT%  health=?
    )
) else (
    echo   [%MOD%] PARADO   porta=%PORT%
)
goto :eof

:do_logs
set "MOD=%~1"
set "LOG_FILE=%LOGS_DIR%\%MOD%.log"

if not exist "%LOG_FILE%" (
    echo   [%MOD%] Arquivo de log nao encontrado: %LOG_FILE%
    goto :eof
)

echo   [%MOD%] Ultimas 50 linhas de %LOG_FILE%:
echo   ---
powershell -Command "Get-Content '%LOG_FILE%' -Tail 50"
echo.
goto :eof

:check_port
:: Verifica se uma porta esta em uso
:: %1 = porta, %2 = nome da variavel de retorno
set "%~2=false"
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort %~1 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 set "%~2=true"
goto :eof

endlocal
