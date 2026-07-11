# api-whatsapp

Módulo plugável de integração com o **WhatsApp Cloud API** — modelo **reativo puro, custo
zero de Meta garantido por design**: o cliente sempre inicia a conversa, o ERP responde
dentro da janela de 24h, e não existe envio de *template* pago.

- **Porta:** `9193` (env `SERVER_PORT`)
- **Banco:** PostgreSQL local, schema `whatsapp` (migrations Flyway V1–V4 no boot)
- **Deploy:** on-premise por cliente (Windows Service via WinSW)

Para consumir a partir de um ERP, use a lib [`lib-whatsapp-client`](../lib-whatsapp-client/README.md).

## Endpoints

### Webhook do Meta (público, validado por HMAC-SHA256)
| Método | Path | Descrição |
|--------|------|-----------|
| `GET`  | `/webhook/whatsapp` | Handshake do Meta (`hub.challenge` echo em plain text) |
| `POST` | `/webhook/whatsapp` | Mensagens entrantes + status de entrega — exige `X-Hub-Signature-256` válido |

### Endpoints internos do ERP (protegidos por `X-API-Key`)
| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/api/whatsapp/enviar-texto` | Texto (max 4096 chars) |
| `POST` | `/api/whatsapp/enviar-documento` | Documento (bytes em base64 no JSON) |
| `POST` | `/api/whatsapp/enviar-botoes` | Interactive buttons (max 3) |
| `POST` | `/api/whatsapp/enviar-lista` | Interactive list (max 10 itens) |
| `GET`  | `/api/whatsapp/status` | Status + estado do circuit breaker |
| `GET`  | `/health` | Liveness (público) |

Documentação interativa: **Swagger UI** em `/swagger-ui.html` · OpenAPI JSON em `/v3/api-docs`.

## Configuração (env vars)

| Variável | Obrigatória | Default | Descrição |
|----------|:-----------:|---------|-----------|
| `WHATSAPP_PHONE_NUMBER_ID` | ✅ | — | Phone Number ID da WABA |
| `WHATSAPP_ACCESS_TOKEN` | ✅ | — | Token do System User (permanente) |
| `WHATSAPP_APP_SECRET` | ✅ | — | App Secret (valida HMAC do webhook) |
| `WHATSAPP_VERIFY_TOKEN` | ✅ | — | Token do handshake `hub.verify_token` |
| `WHATSAPP_ERP_CALLBACK_URL` | ✅ | — | Base URL do ERP para callback de comandos |
| `WHATSAPP_DB_URL` | ✅ | — | JDBC do PostgreSQL local — deve incluir `?currentSchema=whatsapp` (ex.: `jdbc:postgresql://localhost:5433/db_api_whatsapp?currentSchema=whatsapp`) |
| `WHATSAPP_DB_USERNAME` / `WHATSAPP_DB_PASSWORD` | ✅ | — | Credenciais do banco (fornecidas por deployment; sem default embutido) |
| `WHATSAPP_CALLBACK_TIMEOUT` | — | `5s` | Timeout do callback ao ERP |
| `WHATSAPP_META_API_BASE_URL` | — | `https://graph.facebook.com/v22.0` | Base do Graph API (override em testes) |
| `API_KEY` | — | *(vazio)* | Chave dos endpoints internos (`X-API-Key`) |
| `SERVER_PORT` | — | `9193` | Porta HTTP |

> O boot **falha imediatamente** se qualquer um dos 5 secrets obrigatórios estiver ausente
> (fail-fast). Tokens/segredos **nunca** aparecem em logs.

## Garantias de custo zero

1. **Sem API de template** — `WhatsAppCloudClient` expõe apenas `enviarTexto/Documento/Botoes/Lista`; teste de regressão falha se surgir qualquer método com "template".
2. **Trava hard de janela 24h** — envio com `ultima_mensagem_em > 24h` retorna `409 JANELA_24H_FECHADA` **antes** de chamar a Cloud API (aspect `@JanelaProtegida`, `HIGHEST_PRECEDENCE`).
3. **Reativo puro** — o ERP só responde a mensagens que o cliente iniciou.

## Rodar local (dev)

```bash
export JAVA_HOME="/c/Program Files/Java/jdk21.0.10_7"   # JDK 21 (Corretto)
./mvnw -pl api-whatsapp -am spring-boot:run
```

Testes: `./mvnw -pl api-whatsapp test` (unit + WireMock integration).

## Deploy on-premise

Ver **[RUNBOOK.md](RUNBOOK.md)** — passo a passo de criação do app Meta + WABA, geração do
token permanente, verificação de `subscribed_apps` (evita o bug de *shadow delivery*),
configuração do webhook via Cloudflare Tunnel e testes de fumaça.
