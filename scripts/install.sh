#!/usr/bin/env bash
# ============================================================
# ERP Kit - Script de Instalação (Linux/macOS)
# Verifica pré-requisitos, cria bancos, compila e configura
# ============================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MVN="$ROOT_DIR/mvnw"
SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
DB_HOST="localhost"
DB_PORT="5432"
DB_USER="erp_calhas"
DB_PASS="erp_calhas_dev"
INSTALL_DIR="/opt/erpkit"
SKIP_DB=false
SKIP_BUILD=false

usage() {
    echo ""
    echo "  ERP Kit - Instalação"
    echo "  ====================="
    echo ""
    echo "  Uso: install.sh [opções]"
    echo ""
    echo "  Opções:"
    echo "    --db-host HOST       Host do PostgreSQL (padrão: localhost)"
    echo "    --db-port PORT       Porta do PostgreSQL (padrão: 5432)"
    echo "    --db-user USER       Usuário do banco (padrão: erp_calhas)"
    echo "    --db-pass PASS       Senha do banco (padrão: erp_calhas_dev)"
    echo "    --install-dir DIR    Diretório de instalação (padrão: /opt/erpkit)"
    echo "    --skip-db            Pula criação dos bancos de dados"
    echo "    --skip-build         Pula compilação (usa JARs existentes)"
    echo "    --help, -h           Mostra esta ajuda"
    echo ""
    exit 0
}

# Parsear argumentos
while [[ $# -gt 0 ]]; do
    case "$1" in
        --db-host)   DB_HOST="$2"; shift ;;
        --db-port)   DB_PORT="$2"; shift ;;
        --db-user)   DB_USER="$2"; shift ;;
        --db-pass)   DB_PASS="$2"; shift ;;
        --install-dir) INSTALL_DIR="$2"; shift ;;
        --skip-db)   SKIP_DB=true ;;
        --skip-build) SKIP_BUILD=true ;;
        --help|-h)   usage ;;
        *)           echo "Argumento desconhecido: $1"; usage ;;
    esac
    shift
done

echo ""
echo "  ========================================"
echo "   ERP Kit - Instalação"
echo "  ========================================"
echo ""

ERRORS=0

# -------------------------------------------------------
# 1. Verificar Pré-requisitos
# -------------------------------------------------------
echo "  [1/5] Verificando pré-requisitos..."
echo ""

# Java
if ! command -v java &>/dev/null; then
    echo -e "  ${RED}[ERRO]${NC} Java não encontrado no PATH."
    echo "         Instale o JDK 21: https://adoptium.net/"
    ERRORS=$((ERRORS + 1))
else
    JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
    echo "    Java: $JAVA_VER ... OK"
fi

# PostgreSQL
if ! command -v psql &>/dev/null; then
    echo "    PostgreSQL (psql): NÃO ENCONTRADO"
    echo "    [AVISO] psql não está no PATH. Criação automática será ignorada."
    SKIP_DB=true
else
    PG_VER=$(psql --version 2>&1 | awk '{print $3}')
    echo "    PostgreSQL: $PG_VER ... OK"
fi

echo ""

if [[ $ERRORS -gt 0 ]]; then
    echo -e "  ${RED}[ERRO]${NC} Pré-requisitos não atendidos."
    exit 1
fi

# -------------------------------------------------------
# 2. Criar Bancos de Dados
# -------------------------------------------------------
if [[ "$SKIP_DB" == "true" ]]; then
    echo "  [2/5] Criação de bancos de dados: PULADO"
else
    echo "  [2/5] Criando bancos de dados..."
    echo ""

    export PGPASSWORD="$DB_PASS"

    # Verificar conexão
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "SELECT 1" &>/dev/null; then
        echo "    [AVISO] Não foi possível conectar como $DB_USER."
        echo "            Tentando com usuário 'postgres'..."

        # Criar usuário
        if sudo -u postgres psql -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASS'; END IF; END \$\$;" 2>/dev/null; then
            echo "    Usuário $DB_USER: OK"
            PG_ADMIN="sudo -u postgres psql"
        else
            echo -e "    ${YELLOW}[AVISO]${NC} Não foi possível criar usuário. Crie manualmente."
            SKIP_DB=true
        fi
    else
        PG_ADMIN="psql -h $DB_HOST -p $DB_PORT -U $DB_USER"
    fi

    if [[ "$SKIP_DB" != "true" ]]; then
        for db in db_api_email db_api_storage; do
            exists=$($PG_ADMIN -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='$db'" 2>/dev/null | tr -d ' ')
            if [[ "$exists" != "1" ]]; then
                if $PG_ADMIN -d postgres -c "CREATE DATABASE $db OWNER $DB_USER;" 2>/dev/null; then
                    echo "    Banco $db: CRIADO"
                else
                    echo -e "    ${RED}[ERRO]${NC} Falha ao criar banco $db"
                fi
            else
                echo "    Banco $db: já existe"
            fi
        done
    fi

    unset PGPASSWORD
fi
echo ""

# -------------------------------------------------------
# 3. Build
# -------------------------------------------------------
if [[ "$SKIP_BUILD" == "true" ]]; then
    echo "  [3/5] Build: PULADO"
else
    echo "  [3/5] Compilando projeto..."
    echo ""
    cd "$ROOT_DIR"
    chmod +x "$MVN" 2>/dev/null || true
    "$MVN" clean package -DskipTests -q
    echo "    Build: OK"
fi
echo ""

# -------------------------------------------------------
# 4. Criar diretório de instalação e copiar artefatos
# -------------------------------------------------------
echo "  [4/5] Instalando artefatos..."
echo ""

sudo mkdir -p "$INSTALL_DIR"/{lib,config,logs,bin,uploads}
sudo chown -R "$(whoami)" "$INSTALL_DIR"

# Copiar JARs
for module in api-email api-storage; do
    jar_file=$(find "$ROOT_DIR/$module/target" -maxdepth 1 -name "${module}-*.jar" ! -name "*.original" 2>/dev/null | head -1)
    if [[ -n "$jar_file" ]]; then
        cp "$jar_file" "$INSTALL_DIR/lib/"
        echo "    Copiado: $(basename "$jar_file")"
    fi
done

# Criar arquivo de configuração de ambiente
if [[ ! -f "$INSTALL_DIR/config/env.sh" ]]; then
    cat > "$INSTALL_DIR/config/env.sh" << 'ENVEOF'
#!/usr/bin/env bash
# ============================================================
# ERP Kit - Configuração de Ambiente
# Edite este arquivo para configurar as variáveis de ambiente
# ============================================================

# Chave de API (deixe vazio para desabilitar autenticação)
export API_KEY=""

# PostgreSQL
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_USER="erp_calhas"
export DB_PASS="erp_calhas_dev"

# Storage
export STORAGE_DIR="/opt/erpkit/uploads"
export STORAGE_BASE_URL="http://localhost:8085"

# Java (descomente se Java não estiver no PATH)
# export JAVA_HOME="/usr/lib/jvm/java-21"
# export PATH="$JAVA_HOME/bin:$PATH"
ENVEOF
    chmod +x "$INSTALL_DIR/config/env.sh"
    echo "    Criado: config/env.sh"
else
    echo "    config/env.sh: já existe (preservado)"
fi

# Copiar script de deploy
cp "$SCRIPTS_DIR/deploy.sh" "$INSTALL_DIR/bin/"
chmod +x "$INSTALL_DIR/bin/deploy.sh"
echo "    Copiado: bin/deploy.sh"
echo ""

# -------------------------------------------------------
# 5. Configurar systemd (Linux)
# -------------------------------------------------------
echo "  [5/5] Configuração de serviços..."
echo ""

if command -v systemctl &>/dev/null; then
    echo "    Systemd detectado. Criando units..."

    for module in api-email api-storage; do
        jar_file=$(find "$INSTALL_DIR/lib" -maxdepth 1 -name "${module}-*.jar" 2>/dev/null | head -1)
        port="9091"
        [[ "$module" == "api-storage" ]] && port="8085"

        sudo tee "/etc/systemd/system/erpkit-${module}.service" > /dev/null << SVCEOF
[Unit]
Description=ERP Kit - ${module}
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=$(whoami)
EnvironmentFile=$INSTALL_DIR/config/env.sh
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -jar $jar_file --server.port=$port
Restart=on-failure
RestartSec=10
StandardOutput=append:$INSTALL_DIR/logs/${module}.log
StandardError=append:$INSTALL_DIR/logs/${module}-error.log

[Install]
WantedBy=multi-user.target
SVCEOF
        echo "    Serviço erpkit-${module}: CRIADO"
    done

    sudo systemctl daemon-reload
    echo ""
    echo "    Para habilitar na inicialização:"
    echo "      sudo systemctl enable erpkit-api-email erpkit-api-storage"
    echo ""
    echo "    Para iniciar:"
    echo "      sudo systemctl start erpkit-api-email erpkit-api-storage"
else
    echo "    [INFO] Systemd não disponível. Use o script deploy.sh para gerenciar."
fi

echo ""
echo -e "  ${GREEN}========================================"
echo "   Instalação concluída!"
echo -e "  ========================================${NC}"
echo ""
echo "  Diretório: $INSTALL_DIR"
echo ""
echo "  Próximos passos:"
echo "    1. Edite $INSTALL_DIR/config/env.sh com suas configurações"
echo "    2. Inicie os módulos: $INSTALL_DIR/bin/deploy.sh start"
echo "    3. Acesse:"
echo "       - API Email:   http://localhost:9091/health"
echo "       - API Storage: http://localhost:8085/health"
echo "       - Swagger:     http://localhost:9091/swagger-ui.html"
echo "                      http://localhost:8085/swagger-ui.html"
echo ""
