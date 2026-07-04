#!/usr/bin/env bash
#
# Smoke test do api-whatsapp rodando em profile 'dev' (porta 9193), com os mocks
# (mock-servers.py) no ar. Exercita outbound (envio via Meta-mock), validacoes de
# seguranca e inbound (webhook assinado com HMAC -> callback no ERP-echo).
#
# Uso:  bash scripts/whatsapp-dev/smoke.sh
# Vars opcionais: BASE, API_KEY, APP_SECRET (defaults do application-dev.yml).
set -u

BASE="${BASE:-http://localhost:9193}"
API_KEY="${API_KEY:-dev-key}"
APP_SECRET="${APP_SECRET:-dev-app-secret}"
HERE="$(cd "$(dirname "$0")" && pwd)"
RUN="$$$RANDOM"   # token unico por execucao — evita colisao de wamid (idempotencia)

hr() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

hr "health (deve ser 200)"
curl -s -o /dev/null -w "GET /health -> %{http_code}\n" "$BASE/health"

hr "swagger UI (302 -> redirect pro index, normal)"
curl -s -o /dev/null -w "GET /swagger-ui.html -> %{http_code} (segue redirect: %{redirect_url})\n" "$BASE/swagger-ui.html"

# A trava de 24h so libera outbound para quem JA mandou uma mensagem entrante.
# Por isso: primeiro simulamos o cliente escrevendo (abre a janela), depois enviamos.
post_webhook() {
  local file="$1" desc="$2" sig tmp
  tmp="$(mktemp)"
  # wamid unico por execucao (senao a idempotencia trata como replay e pula o dispatch)
  sed "s/dev001/dev-$RUN/" "$file" > "$tmp"
  sig=$(openssl dgst -sha256 -hmac "$APP_SECRET" -r < "$tmp" | cut -d' ' -f1)
  hr "INBOUND: $desc (HMAC valido -> 200 + callback no ERP-echo)"
  curl -s -o /dev/null -w "POST /webhook/whatsapp -> %{http_code}\n" \
    -X POST "$BASE/webhook/whatsapp" \
    -H "X-Hub-Signature-256: sha256=$sig" -H "Content-Type: application/json" \
    --data-binary @"$tmp"
  rm -f "$tmp"
}

post_webhook "$HERE/payloads/text-portugues.json" "cliente 5547984178525 escreve (abre janela 24h)"
echo "  (aguardando o processamento async abrir a janela...)"; sleep 3

hr "OUTBOUND: enviar-texto para quem escreveu (deve retornar {\"wamid\":...} do Meta-mock)"
curl -s -X POST "$BASE/api/whatsapp/enviar-texto" \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"telefone":"5547984178525","texto":"Ola do smoke test"}'
echo

hr "OUTBOUND: enviar-botoes para o mesmo (deve retornar wamid)"
curl -s -X POST "$BASE/api/whatsapp/enviar-botoes" \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"telefone":"5547984178525","texto":"Confirma a consulta?","botoes":[{"id":"confirmar_agendamento_42","title":"Confirmar"},{"id":"remarcar_agendamento_42","title":"Remarcar"}]}'
echo

hr "TRAVA 24h: enviar para numero que NUNCA escreveu (deve ser 409 JANELA_24H_FECHADA)"
curl -s -o /dev/null -w "POST enviar-texto (janela fechada) -> %{http_code}\n" \
  -X POST "$BASE/api/whatsapp/enviar-texto" \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"telefone":"5511900000000","texto":"nao deveria passar"}'

hr "VALIDACAO: telefone invalido (deve ser 400)"
curl -s -o /dev/null -w "POST enviar-texto (telefone ruim) -> %{http_code}\n" \
  -X POST "$BASE/api/whatsapp/enviar-texto" \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"telefone":"abc","texto":"x"}'

hr "SEGURANCA: sem X-API-Key (deve ser 401)"
curl -s -o /dev/null -w "POST enviar-texto (sem key) -> %{http_code}\n" \
  -X POST "$BASE/api/whatsapp/enviar-texto" -H "Content-Type: application/json" \
  -d '{"telefone":"5547984178525","texto":"x"}'

post_webhook "$HERE/payloads/button-reply.json" "cliente responde um botao (confirmar_agendamento_42)"

hr "INBOUND: assinatura invalida (deve ser 401)"
curl -s -o /dev/null -w "POST /webhook/whatsapp (sig ruim) -> %{http_code}\n" \
  -X POST "$BASE/webhook/whatsapp" \
  -H "X-Hub-Signature-256: sha256=deadbeef" -H "Content-Type: application/json" \
  --data-binary @"$HERE/payloads/text-portugues.json"

hr "STATUS"
curl -s "$BASE/api/whatsapp/status" -H "X-API-Key: $API_KEY"
echo

printf '\n\033[1mPronto.\033[0m Veja o terminal do mock-servers.py: os envios devem aparecer no META-MOCK\n'
printf 'e os webhooks entrantes devem gerar um callback no ERP-ECHO (~1-2s depois, async).\n'
