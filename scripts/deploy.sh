#!/usr/bin/env bash
# ============================================================
# ERP Kit - Deploy / Gerenciamento (Linux/macOS)
# Comandos: start, stop, restart, status, logs
# ============================================================

set -uo pipefail

# Detectar diretório de instalação
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ -d "$SCRIPT_DIR/../lib" ]]; then
    INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
elif [[ -d "$SCRIPT_DIR/../api-email/target" ]]; then
    INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
    DEV_MODE=true
else
    INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
fi

# Carregar configuração de ambiente se existir
if [[ -f "$INSTALL_DIR/config/env.sh" ]]; then
    source "$INSTALL_DIR/config/env.sh"
fi

LOGS_DIR="$INSTALL_DIR/logs"
PID_DIR="$INSTALL_DIR/logs"
mkdir -p "$LOGS_DIR"

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Módulos
declare -A PORTS
PORTS[api-email]=9091
PORTS[api-storage]=8085
MODULES="api-email api-storage"

usage() {
    echo ""
    echo "  ERP Kit - Deploy / Gerenciamento"
    echo "  =================================="
    echo ""
    echo "  Uso: deploy.sh <ação> [módulo]"
    echo ""
    echo "  Ações:"
    echo "    start     Inicia o(s) módulo(s)"
    echo "    stop      Para o(s) módulo(s)"
    echo "    restart   Reinicia o(s) módulo(s)"
    echo "    status    Mostra status do(s) módulo(s)"
    echo "    logs      Mostra últimas linhas do log (follow)"
    echo ""
    echo "  Módulos:"
    echo "    api-email     API de Email (porta 9091)"
    echo "    api-storage   API de Storage (porta 8085)"
    echo ""
    echo "  Se nenhum módulo for especificado, a ação se aplica a todos."
    echo ""
    echo "  Exemplos:"
    echo "    deploy.sh start               Inicia todos"
    echo "    deploy.sh stop api-email      Para apenas o api-email"
    echo "    deploy.sh restart api-storage Reinicia o api-storage"
    echo "    deploy.sh status              Mostra status de todos"
    echo "    deploy.sh logs api-email      Mostra log do api-email"
    echo ""
    exit 0
}

find_jar() {
    local module="$1"
    local jar_file=""

    if [[ "${DEV_MODE:-}" == "true" ]]; then
        jar_file=$(find "$INSTALL_DIR/$module/target" -maxdepth 1 -name "${module}-*.jar" ! -name "*.original" 2>/dev/null | head -1)
    else
        jar_file=$(find "$INSTALL_DIR/lib" -maxdepth 1 -name "${module}-*.jar" ! -name "*.original" 2>/dev/null | head -1)
    fi

    echo "$jar_file"
}

get_pid() {
    local module="$1"
    local pid_file="$PID_DIR/${module}.pid"

    if [[ -f "$pid_file" ]]; then
        local pid
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return
        fi
        rm -f "$pid_file"
    fi

    # Fallback: buscar pela porta
    local port="${PORTS[$module]}"
    local pid
    pid=$(lsof -ti ":$port" 2>/dev/null | head -1)
    echo "$pid"
}

do_start() {
    local module="$1"
    local port="${PORTS[$module]}"

    local existing_pid
    existing_pid=$(get_pid "$module")
    if [[ -n "$existing_pid" ]]; then
        echo "  [$module] Já está rodando (PID: $existing_pid) na porta $port"
        return
    fi

    local jar_file
    jar_file=$(find_jar "$module")
    if [[ -z "$jar_file" ]]; then
        echo -e "  [$module] ${RED}[ERRO]${NC} JAR não encontrado. Execute o build primeiro."
        return
    fi

    echo "  [$module] Iniciando na porta $port..."

    nohup java -jar "$jar_file" > "$LOGS_DIR/${module}.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/${module}.pid"

    # Aguardar startup (max 30s)
    local wait=0
    while [[ $wait -lt 30 ]]; do
        sleep 2
        wait=$((wait + 2))
        if curl -s "http://localhost:$port/health" &>/dev/null; then
            echo -e "  [$module] ${GREEN}Rodando${NC} na porta $port (PID: $pid) - OK"
            return
        fi
        # Verificar se processo ainda existe
        if ! kill -0 "$pid" 2>/dev/null; then
            echo -e "  [$module] ${RED}[ERRO]${NC} Processo morreu. Verifique o log:"
            echo "           $LOGS_DIR/${module}.log"
            rm -f "$PID_DIR/${module}.pid"
            return
        fi
    done
    echo -e "  [$module] ${YELLOW}[AVISO]${NC} Timeout aguardando startup. Verifique o log."
}

do_stop() {
    local module="$1"
    local port="${PORTS[$module]}"

    local pid
    pid=$(get_pid "$module")
    if [[ -z "$pid" ]]; then
        echo "  [$module] Não está rodando"
        return
    fi

    echo "  [$module] Parando (PID: $pid)..."

    kill "$pid" 2>/dev/null

    # Aguardar parada graceful (max 10s)
    local wait=0
    while [[ $wait -lt 10 ]]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            break
        fi
        sleep 1
        wait=$((wait + 1))
    done

    # Force kill se necessário
    if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null
        sleep 1
    fi

    rm -f "$PID_DIR/${module}.pid"

    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "  [$module] ${GREEN}Parado${NC} - OK"
    else
        echo -e "  [$module] ${RED}[AVISO]${NC} Não foi possível parar."
    fi
}

do_status() {
    local module="$1"
    local port="${PORTS[$module]}"

    local pid
    pid=$(get_pid "$module")
    if [[ -n "$pid" ]]; then
        local health="?"
        local hc
        hc=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/health" 2>/dev/null)
        [[ "$hc" == "200" ]] && health="OK"
        echo -e "  [$module] ${GREEN}RODANDO${NC}  porta=$port  pid=$pid  health=$health"
    else
        echo -e "  [$module] ${RED}PARADO${NC}   porta=$port"
    fi
}

do_logs() {
    local module="$1"
    local log_file="$LOGS_DIR/${module}.log"

    if [[ ! -f "$log_file" ]]; then
        echo "  [$module] Arquivo de log não encontrado: $log_file"
        return
    fi

    echo "  [$module] Últimas 50 linhas de $log_file:"
    echo "  ---"
    tail -n 50 "$log_file"
    echo ""
}

# -------------------------------------------------------
# Main
# -------------------------------------------------------

ACTION="${1:-}"
TARGET="${2:-}"

if [[ -z "$ACTION" || "$ACTION" == "--help" || "$ACTION" == "-h" ]]; then
    usage
fi

# Validar ação
case "$ACTION" in
    start|stop|restart|status|logs) ;;
    *) echo "  [ERRO] Ação desconhecida: $ACTION"; usage ;;
esac

# Determinar módulos alvo
if [[ -z "$TARGET" ]]; then
    TARGETS=($MODULES)
else
    # Validar módulo
    valid=false
    for m in $MODULES; do
        [[ "$TARGET" == "$m" ]] && valid=true
    done
    if [[ "$valid" == "false" ]]; then
        echo "  [ERRO] Módulo desconhecido: $TARGET"
        echo "  Módulos disponíveis: $MODULES"
        exit 1
    fi
    TARGETS=("$TARGET")
fi

echo ""

for mod in "${TARGETS[@]}"; do
    case "$ACTION" in
        start)   do_start "$mod" ;;
        stop)    do_stop "$mod" ;;
        restart) do_stop "$mod"; sleep 2; do_start "$mod" ;;
        status)  do_status "$mod" ;;
        logs)    do_logs "$mod" ;;
    esac
done

echo ""
