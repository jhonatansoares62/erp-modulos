@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

:: ============================================================
:: ERP Kit - Script de Instalacao (Windows)
:: Verifica pre-requisitos, cria bancos, compila e configura
:: ============================================================

set "ROOT_DIR=%~dp0.."
set "MVN=%ROOT_DIR%\mvnw.cmd"
set "SCRIPTS_DIR=%~dp0"
set "CONFIG_DIR=%ROOT_DIR%\config"

:: Defaults
set "DB_HOST=localhost"
set "DB_PORT=5432"
set "DB_USER=erp_calhas"
set "DB_PASS=erp_calhas_dev"
set "INSTALL_DIR=%ProgramFiles%\ERPKit"
set "SKIP_DB=false"
set "SKIP_BUILD=false"

:: Parsear argumentos
:parse_args
if "%~1"=="" goto :start
if /i "%~1"=="--db-host" (set "DB_HOST=%~2" & shift)
if /i "%~1"=="--db-port" (set "DB_PORT=%~2" & shift)
if /i "%~1"=="--db-user" (set "DB_USER=%~2" & shift)
if /i "%~1"=="--db-pass" (set "DB_PASS=%~2" & shift)
if /i "%~1"=="--install-dir" (set "INSTALL_DIR=%~2" & shift)
if /i "%~1"=="--skip-db" set "SKIP_DB=true"
if /i "%~1"=="--skip-build" set "SKIP_BUILD=true"
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="-h" goto :usage
shift
goto :parse_args

:usage
echo.
echo   ERP Kit - Instalacao
echo   =====================
echo.
echo   Uso: install.cmd [opcoes]
echo.
echo   Opcoes:
echo     --db-host HOST       Host do PostgreSQL (padrao: localhost)
echo     --db-port PORT       Porta do PostgreSQL (padrao: 5432)
echo     --db-user USER       Usuario do banco (padrao: erp_calhas)
echo     --db-pass PASS       Senha do banco (padrao: erp_calhas_dev)
echo     --install-dir DIR    Diretorio de instalacao (padrao: Program Files\ERPKit)
echo     --skip-db            Pula criacao dos bancos de dados
echo     --skip-build         Pula compilacao (usa JARs existentes)
echo     --help, -h           Mostra esta ajuda
echo.
exit /b 0

:start
echo.
echo   ========================================
echo    ERP Kit - Instalacao
echo   ========================================
echo.

set "ERRORS=0"

:: -------------------------------------------------------
:: 1. Verificar Pre-requisitos
:: -------------------------------------------------------
echo   [1/5] Verificando pre-requisitos...
echo.

:: Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo   [ERRO] Java nao encontrado no PATH.
    echo          Instale o JDK 21: https://adoptium.net/
    set /a ERRORS+=1
) else (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "JAVA_VER=%%~v"
    )
    echo     Java: !JAVA_VER! ... OK
)

:: PostgreSQL (psql)
where psql >nul 2>&1
if %errorlevel% neq 0 (
    echo     PostgreSQL (psql): NAO ENCONTRADO
    echo     [AVISO] psql nao esta no PATH. Criacao automatica de bancos sera ignorada.
    set "SKIP_DB=true"
) else (
    for /f "tokens=3" %%v in ('psql --version 2^>^&1') do set "PG_VER=%%v"
    echo     PostgreSQL: !PG_VER! ... OK
)

echo.

if %ERRORS% gtr 0 (
    echo   [ERRO] Pre-requisitos nao atendidos. Corrija os erros acima e tente novamente.
    exit /b 1
)

:: -------------------------------------------------------
:: 2. Criar Bancos de Dados
:: -------------------------------------------------------
if "%SKIP_DB%"=="true" (
    echo   [2/5] Criacao de bancos de dados: PULADO
) else (
    echo   [2/5] Criando bancos de dados...
    echo.

    set "PGPASSWORD=%DB_PASS%"

    :: Verificar conexao
    psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d postgres -c "SELECT 1" >nul 2>&1
    if !errorlevel! neq 0 (
        echo     [AVISO] Nao foi possivel conectar ao PostgreSQL como %DB_USER%.
        echo             Tentando criar usuario e bancos com usuario 'postgres'...
        echo.

        :: Criar usuario
        psql -h %DB_HOST% -p %DB_PORT% -U postgres -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '%DB_USER%') THEN CREATE ROLE %DB_USER% WITH LOGIN PASSWORD '%DB_PASS%'; END IF; END $$;" 2>nul
        if !errorlevel! neq 0 (
            echo     [ERRO] Nao foi possivel criar usuario. Crie manualmente:
            echo            CREATE ROLE %DB_USER% WITH LOGIN PASSWORD '%DB_PASS%';
            set "SKIP_DB=true"
        ) else (
            echo     Usuario %DB_USER%: OK
        )
    )

    if not "%SKIP_DB%"=="true" (
        for %%d in (db_api_email db_api_storage) do (
            psql -h %DB_HOST% -p %DB_PORT% -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='%%d'" 2>nul | findstr "1" >nul
            if !errorlevel! neq 0 (
                psql -h %DB_HOST% -p %DB_PORT% -U postgres -c "CREATE DATABASE %%d OWNER %DB_USER%;" 2>nul
                if !errorlevel! neq 0 (
                    echo     [ERRO] Falha ao criar banco %%d
                ) else (
                    echo     Banco %%d: CRIADO
                )
            ) else (
                echo     Banco %%d: ja existe
            )
        )
    )
)
echo.

:: -------------------------------------------------------
:: 3. Build
:: -------------------------------------------------------
if "%SKIP_BUILD%"=="true" (
    echo   [3/5] Build: PULADO
) else (
    echo   [3/5] Compilando projeto...
    echo.
    cd /d "%ROOT_DIR%"
    call "%MVN%" clean package -DskipTests -q
    if !errorlevel! neq 0 (
        echo   [ERRO] Build falhou!
        exit /b 1
    )
    echo     Build: OK
)
echo.

:: -------------------------------------------------------
:: 4. Criar diretorio de instalacao e copiar artefatos
:: -------------------------------------------------------
echo   [4/5] Instalando artefatos...
echo.

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\lib" mkdir "%INSTALL_DIR%\lib"
if not exist "%INSTALL_DIR%\config" mkdir "%INSTALL_DIR%\config"
if not exist "%INSTALL_DIR%\logs" mkdir "%INSTALL_DIR%\logs"
if not exist "%INSTALL_DIR%\bin" mkdir "%INSTALL_DIR%\bin"
if not exist "%INSTALL_DIR%\uploads" mkdir "%INSTALL_DIR%\uploads"

:: Copiar JARs
for %%m in (api-email api-storage) do (
    for %%f in ("%ROOT_DIR%\%%m\target\%%m-*.jar") do (
        if not "%%~nxf"=="%%~nf.original" (
            echo %%~nxf | findstr /i ".original" >nul
            if !errorlevel! neq 0 (
                copy /y "%%f" "%INSTALL_DIR%\lib\" >nul
                echo     Copiado: %%~nxf
            )
        )
    )
)

:: Criar arquivo de configuracao de ambiente
if not exist "%INSTALL_DIR%\config\env.cmd" (
    (
        echo @echo off
        echo :: ============================================================
        echo :: ERP Kit - Configuracao de Ambiente
        echo :: Edite este arquivo para configurar as variaveis de ambiente
        echo :: ============================================================
        echo.
        echo :: Chave de API (deixe vazio para desabilitar autenticacao^)
        echo set "API_KEY="
        echo.
        echo :: PostgreSQL
        echo set "DB_HOST=%DB_HOST%"
        echo set "DB_PORT=%DB_PORT%"
        echo set "DB_USER=%DB_USER%"
        echo set "DB_PASS=%DB_PASS%"
        echo.
        echo :: Storage
        echo set "STORAGE_DIR=%INSTALL_DIR%\uploads"
        echo set "STORAGE_BASE_URL=http://localhost:8085"
        echo.
        echo :: Java (descomente se Java nao estiver no PATH^)
        echo :: set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21"
        echo :: set "PATH=%%JAVA_HOME%%\bin;%%PATH%%"
    ) > "%INSTALL_DIR%\config\env.cmd"
    echo     Criado: config\env.cmd
) else (
    echo     config\env.cmd: ja existe (preservado)
)

:: Copiar scripts de deploy
copy /y "%SCRIPTS_DIR%deploy.cmd" "%INSTALL_DIR%\bin\" >nul 2>&1
echo     Copiado: bin\deploy.cmd
echo.

:: -------------------------------------------------------
:: 5. Registrar como servicos Windows (opcional via nssm)
:: -------------------------------------------------------
echo   [5/5] Configuracao de servicos Windows...
echo.

where nssm >nul 2>&1
if %errorlevel% neq 0 (
    echo     [INFO] NSSM nao encontrado. Os modulos podem ser executados manualmente
    echo            usando o script deploy.cmd (start/stop/restart).
    echo.
    echo     Para instalar como servicos Windows, baixe o NSSM:
    echo       https://nssm.cc/download
    echo.
    echo     Depois execute:
    echo       nssm install ERPKit-Email java -jar "%INSTALL_DIR%\lib\api-email-1.0.0-SNAPSHOT.jar"
    echo       nssm install ERPKit-Storage java -jar "%INSTALL_DIR%\lib\api-storage-1.0.0-SNAPSHOT.jar"
) else (
    echo     NSSM encontrado. Deseja registrar os modulos como servicos Windows?
    echo     (Isso permite iniciar/parar pelo Gerenciador de Servicos)
    echo.
    set /p "REG_SVC=    Registrar servicos? [s/N]: "
    if /i "!REG_SVC!"=="s" (
        for %%m in (api-email api-storage) do (
            set "SVC_NAME=ERPKit-%%m"
            nssm status !SVC_NAME! >nul 2>&1
            if !errorlevel! neq 0 (
                for %%f in ("%INSTALL_DIR%\lib\%%m-*.jar") do (
                    echo %%~nxf | findstr /i ".original" >nul
                    if !errorlevel! neq 0 (
                        nssm install !SVC_NAME! java -jar "%%f"
                        nssm set !SVC_NAME! AppDirectory "%INSTALL_DIR%"
                        nssm set !SVC_NAME! AppStdout "%INSTALL_DIR%\logs\%%m-stdout.log"
                        nssm set !SVC_NAME! AppStderr "%INSTALL_DIR%\logs\%%m-stderr.log"
                        nssm set !SVC_NAME! Description "ERP Kit - %%m"
                        nssm set !SVC_NAME! Start SERVICE_AUTO_START
                        echo     Servico !SVC_NAME!: REGISTRADO
                    )
                )
            ) else (
                echo     Servico !SVC_NAME!: ja existe
            )
        )
    ) else (
        echo     Registro de servicos: PULADO
    )
)

echo.
echo   ========================================
echo    Instalacao concluida!
echo   ========================================
echo.
echo   Diretorio: %INSTALL_DIR%
echo.
echo   Proximos passos:
echo     1. Edite %INSTALL_DIR%\config\env.cmd com suas configuracoes
echo     2. Inicie os modulos: %INSTALL_DIR%\bin\deploy.cmd start
echo     3. Acesse:
echo        - API Email:   http://localhost:9091/health
echo        - API Storage: http://localhost:8085/health
echo        - Swagger:     http://localhost:9091/swagger-ui.html
echo                       http://localhost:8085/swagger-ui.html
echo.

endlocal
