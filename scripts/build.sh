#!/usr/bin/env bash
# ============================================================
# ERP Kit - Build Script (Linux/macOS)
# Compila todos os módulos Maven do projeto
# ============================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MVN="$ROOT_DIR/mvnw"
SKIP_TESTS=false

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

usage() {
    echo ""
    echo "  ERP Kit - Build"
    echo "  ================"
    echo ""
    echo "  Uso: build.sh [opções]"
    echo ""
    echo "  Opções:"
    echo "    --skip-tests, -st   Pula execução dos testes"
    echo "    --help, -h           Mostra esta ajuda"
    echo ""
    exit 0
}

# Parsear argumentos
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-tests|-st) SKIP_TESTS=true ;;
        --help|-h) usage ;;
        *) echo "Argumento desconhecido: $1"; usage ;;
    esac
    shift
done

echo ""
echo "  ========================================"
echo "   ERP Kit - Build"
echo "  ========================================"
echo ""

# Verificar Java
if ! command -v java &>/dev/null; then
    echo -e "  ${RED}[ERRO]${NC} Java não encontrado no PATH."
    echo "         Instale o JDK 21 e tente novamente."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
echo "  Java: $JAVA_VER"

# Verificar Maven Wrapper
if [[ ! -x "$MVN" ]]; then
    chmod +x "$MVN" 2>/dev/null || true
fi
if [[ ! -f "$MVN" ]]; then
    echo -e "  ${RED}[ERRO]${NC} Maven Wrapper não encontrado em: $MVN"
    exit 1
fi
echo "  Maven Wrapper: OK"
echo ""

# Build
cd "$ROOT_DIR"

if [[ "$SKIP_TESTS" == "true" ]]; then
    echo "  [BUILD] Compilando todos os módulos (sem testes)..."
    echo ""
    "$MVN" clean package -DskipTests
else
    echo "  [BUILD] Compilando todos os módulos (com testes)..."
    echo ""
    "$MVN" clean package
fi

echo ""
echo -e "  ${GREEN}========================================"
echo "   Build concluído com sucesso!"
echo -e "  ========================================${NC}"
echo ""

# Listar JARs gerados
echo "  JARs gerados:"
echo "  ---"
for module in api-email api-storage; do
    jar_file=$(find "$ROOT_DIR/$module/target" -maxdepth 1 -name "${module}-*.jar" ! -name "*.original" 2>/dev/null | head -1)
    if [[ -n "$jar_file" ]]; then
        size=$(du -h "$jar_file" | cut -f1)
        echo "    $module: $(basename "$jar_file") ($size)"
    fi
done
echo ""
