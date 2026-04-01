@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

:: ============================================================
:: ERP Kit - Build Script (Windows)
:: Compila todos os modulos Maven do projeto
:: ============================================================

set "ROOT_DIR=%~dp0.."
set "MVN=%ROOT_DIR%\mvnw.cmd"
set "SKIP_TESTS=false"

:: Parsear argumentos
:parse_args
if "%~1"=="" goto :start
if /i "%~1"=="--skip-tests" set "SKIP_TESTS=true"
if /i "%~1"=="-st" set "SKIP_TESTS=true"
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="-h" goto :usage
shift
goto :parse_args

:usage
echo.
echo   ERP Kit - Build
echo   ================
echo.
echo   Uso: build.cmd [opcoes]
echo.
echo   Opcoes:
echo     --skip-tests, -st   Pula execucao dos testes
echo     --help, -h           Mostra esta ajuda
echo.
exit /b 0

:start
echo.
echo   ========================================
echo    ERP Kit - Build
echo   ========================================
echo.

:: Verificar Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo   [ERRO] Java nao encontrado no PATH.
    echo          Instale o JDK 21 e tente novamente.
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%~v"
)
echo   Java: !JAVA_VER!

:: Verificar Maven Wrapper
if not exist "%MVN%" (
    echo   [ERRO] Maven Wrapper nao encontrado em: %MVN%
    exit /b 1
)
echo   Maven Wrapper: OK
echo.

:: Build
cd /d "%ROOT_DIR%"

if "%SKIP_TESTS%"=="true" goto :build_no_tests

echo   [BUILD] Compilando todos os modulos (com testes)...
echo.
call "%MVN%" clean package
goto :check_build

:build_no_tests
echo   [BUILD] Compilando todos os modulos (sem testes)...
echo.
call "%MVN%" clean package -DskipTests

:check_build
if %errorlevel% neq 0 (
    echo.
    echo   [ERRO] Build falhou! Verifique os erros acima.
    exit /b 1
)

echo.
echo   ========================================
echo    Build concluido com sucesso!
echo   ========================================
echo.

:: Listar JARs gerados
echo   JARs gerados:
echo   ---
call :list_jar api-email
call :list_jar api-storage
echo.

endlocal
goto :eof

:list_jar
set "MOD=%~1"
for %%f in ("%ROOT_DIR%\%MOD%\target\%MOD%-*.jar") do (
    echo %%~nxf | findstr /i ".original" >nul
    if !errorlevel! neq 0 (
        set "FSIZE=%%~zf"
        set /a "FSIZE_MB=!FSIZE! / 1048576"
        echo     %MOD%: %%~nxf [!FSIZE_MB! MB]
    )
)
goto :eof
