#!/usr/bin/env bash
# Sobe o api-whatsapp com secrets REAIS da Meta (le .secrets/odonto.env) em H2.
# Uso: bash scripts/whatsapp-dev/run-meta.sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ENVFILE="$ROOT/.secrets/odonto.env"

[ -f "$ENVFILE" ] || { echo "ERRO: falta $ENVFILE (copie de .secrets/odonto.env.example e preencha)"; exit 1; }
# shellcheck disable=SC1090
source "$ENVFILE"

# Valida os secrets obrigatorios (senao o boot falha fail-fast com msg menos clara)
faltando=""
for v in WHATSAPP_PHONE_NUMBER_ID WHATSAPP_ACCESS_TOKEN WHATSAPP_APP_SECRET WHATSAPP_VERIFY_TOKEN WHATSAPP_ERP_CALLBACK_URL; do
  val="${!v:-}"
  if [ -z "$val" ] || [ "$val" = "COLE_AQUI" ]; then faltando="$faltando $v"; fi
done
if [ -n "$faltando" ]; then
  echo "ERRO: preencha em .secrets/odonto.env:$faltando"
  exit 1
fi

# JAVA_HOME de maquina aponta pra um caminho Corretto inexistente; forca o JDK 21 real.
# (usa o do ambiente so se ele apontar pra um java valido)
if [ ! -x "${JAVA_HOME:-}/bin/java.exe" ] && [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  export JAVA_HOME="/c/Program Files/Java/jdk21.0.10_7"
fi
cd "$ROOT"
echo "Subindo api-whatsapp (profile meta, H2) na porta 9193..."
./mvnw -pl api-whatsapp -Pdev spring-boot:run -Dspring-boot.run.profiles=meta
