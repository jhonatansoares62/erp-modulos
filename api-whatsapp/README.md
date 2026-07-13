# api-whatsapp

Módulo plugável de integração com o **WhatsApp Cloud API** — modelo **reativo puro, custo
zero de Meta garantido por design**: o cliente sempre inicia a conversa, o ERP responde
dentro da janela de 24h, e não existe envio de *template* pago.

- **Porta:** `9193` (env `SERVER_PORT`)
- **Banco:** PostgreSQL local, schema `whatsapp` (migrations Flyway no boot — atualmente até **V14**)
- **Deploy:** on-premise por cliente (Windows Service via WinSW)

> Além da integração-base (webhook + envio), o módulo hoje inclui: **app do atendente**
> (login/JWT, inbox de conversas com handoff, métricas), **persona configurável** do assistente,
> e a camada de **segurança/LGPD** (cripto em repouso, auditoria de acesso, retenção e DSAR —
> ver a seção [Segurança / LGPD](#segurança--lgpd-dados-em-repouso)).

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

### App do atendente + LGPD (Bearer JWT do atendente **ou** `X-API-Key`)
| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/api/whatsapp/auth/login` | Login do atendente (emite JWT); `/auth/me` = quem sou eu |
| `GET`/`PUT` | `/api/whatsapp/config` | Credenciais Meta — **cifradas em repouso**; o GET só informa se cada campo está preenchido (nunca o segredo) |
| `GET`/`PUT` | `/api/whatsapp/assistente` | Persona do assistente (nome, tom, emoji, horário, mensagens genéricas) |
| `GET`  | `/api/whatsapp/conversas` … | Inbox: lista/chat/detalhe + `POST .../assumir` e `.../encerrar` (handoff) |
| `GET`  | `/api/whatsapp/relatorios/*` | Métricas de uso e custo |
| `GET`  | `/api/whatsapp/auditoria` | **Trilha de auditoria** de acesso ao dado do paciente (LGPD) |
| `POST` | `/api/whatsapp/retencao/executar` | Executa o motor de **retenção** sob demanda (LGPD; roda diário sozinho) |
| `GET`  | `/api/whatsapp/titular/{tel}/exportar` | **DSAR** — exporta os dados do titular (conteúdo **decifrado**) |
| `POST` | `/api/whatsapp/titular/{tel}/esquecer` | **DSAR** — anonimiza as mensagens + remove os vínculos do titular |

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
| `WHATSAPP_ENC_KEY_FILE` | — | `whatsapp-enc.key` | Caminho da chave de cripto em repouso (auto-gera se ausente). No instalado: `C:\erpkit\config\<erp>\modulos\whatsapp.key` |
| `WHATSAPP_ENC_KEY` | — | — | Chave de cripto em base64 (override direto do arquivo) |
| `WHATSAPP_RETENCAO_MENSAGENS_MESES` | — | `24` | Meses até anonimizar o conteúdo das conversas |
| `WHATSAPP_RETENCAO_HABILITADO` | — | `true` | Liga/desliga o job diário de retenção |

> O boot **falha imediatamente** se qualquer um dos 5 secrets obrigatórios estiver ausente
> (fail-fast). Tokens/segredos **nunca** aparecem em logs.

## Garantias de custo zero

1. **Sem API de template** — `WhatsAppCloudClient` expõe apenas `enviarTexto/Documento/Botoes/Lista`; teste de regressão falha se surgir qualquer método com "template".
2. **Trava hard de janela 24h** — envio com `ultima_mensagem_em > 24h` retorna `409 JANELA_24H_FECHADA` **antes** de chamar a Cloud API (aspect `@JanelaProtegida`, `HIGHEST_PRECEDENCE`).
3. **Reativo puro** — o ERP só responde a mensagens que o cliente iniciou.

## Segurança / LGPD (dados em repouso)

Trilha de conformidade implementada (validada E2E no instalado):

- **Cripto em repouso (AES-256-GCM).** As credenciais Meta (`config_meta`) e o **conteúdo das
  conversas** (`mensagens_log.conteudo`) são cifrados no banco via `CampoCifradoConverter`
  (JPA `@Convert`). O formato é `v1:base64(iv‖ct+tag)`; a leitura decifra transparente e
  **tolera texto plano legado** (prefixo `v1:` distingue → migração sem downtime), com um
  backfill que cifra o histórico no boot. A chave mora **fora do banco** (`whatsapp.key` na
  config-central; ver `WHATSAPP_ENC_KEY_FILE`) → um `pg_dump`/backup **não expõe** token nem
  conversa. A leitura **nunca derruba o boot** (falha → cai no seed de env var).
  > ⚠️ **Inclua a chave no backup de arquivos.** Sem ela, o conteúdo cifrado é irrecuperável.
- **Trilha de auditoria.** Acesso ao dado do paciente no inbox (abrir chat, assumir/encerrar) e
  as ações DSAR (exportar/esquecer) ficam em `auditoria_acesso` (quem × quem × quando). O
  abrir-chat tem dedup por janela (o inbox faz *polling*); a lista de conversas não é auditada.
- **Retenção operacional.** Job diário (`@Scheduled`, + endpoint) anonimiza `mensagens_log`
  mais velhas que `WHATSAPP_RETENCAO_MENSAGENS_MESES` (default 24) — zera conteúdo/telefone
  mantendo os metadados (as métricas não usam esses campos) — e purga o `media_cache` expirado.
  O módulo é **operacional-only**; a guarda de prontuário (≥20 anos) é do ERP, não daqui.
- **DSAR (direitos do titular).** `GET /titular/{tel}/exportar` (acesso/portabilidade, conteúdo
  decifrado) e `POST /titular/{tel}/esquecer` (eliminação — anonimiza + remove `clientes_zap`/
  `estado_conversa`; a trilha de auditoria é mantida como registro de accountability).

> **Escopo:** cobre a camada de **software**. Os itens de **processo/jurídico** (política de
> privacidade, DPA operador↔controlador, RIPD, Cláusulas-Padrão da ANPD para a transferência
> Meta/EUA) ficam a cargo do controlador/DPO.

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
