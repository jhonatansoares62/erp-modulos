#!/usr/bin/env bash
# Sobe o tunnel Cloudflare (mesmo binario do ERP-Mudas) lendo o token de .secrets/odonto.env.
# Uso: bash scripts/whatsapp-dev/tunnel.sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ENVFILE="$ROOT/.secrets/odonto.env"
CF="/c/projetos/ERP-MUDAS/installer/deps/cloudflared/cloudflared.exe"

[ -f "$ENVFILE" ] || { echo "ERRO: falta $ENVFILE"; exit 1; }
[ -x "$CF" ] || { echo "ERRO: cloudflared nao encontrado em $CF"; exit 1; }
# shellcheck disable=SC1090
source "$ENVFILE"

if [ -z "${CLOUDFLARE_TUNNEL_TOKEN:-}" ] || [ "${CLOUDFLARE_TUNNEL_TOKEN:-}" = "COLE_AQUI" ]; then
  echo "ERRO: preencha CLOUDFLARE_TUNNEL_TOKEN em .secrets/odonto.env"
  exit 1
fi

echo "Subindo tunnel Cloudflare -> ${WEBHOOK_PUBLIC_HOSTNAME:-(hostname configurado no dashboard)} -> localhost:9193"
"$CF" tunnel run --token "$CLOUDFLARE_TUNNEL_TOKEN"
