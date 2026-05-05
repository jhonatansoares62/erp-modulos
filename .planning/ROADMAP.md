# Roadmap: ERP Modulos — Milestone "Modulo WhatsApp"

## Overview

Dois novos modulos adicionados ao monorepo seguindo o padrao `api-<dominio>` + `lib-<dominio>-client` ja estabelecido por `api-consultas` + `lib-consultas-client`. A jornada comeca pela fundacao de seguranca (HMAC + webhook), avanca pela persistencia e idempotencia, adiciona o boundary async de roteamento, constroi o cliente Cloud API com as duas travas de custo zero (sem `enviarTemplate` + hard-block de janela 24h), empacota o starter Spring Boot para os ERPs consumirem, e finaliza com cobertura de testes WireMock, OpenAPI e RUNBOOK operacional. Cada fase e entregavel independente que pode ser verificado antes de avancar.

## Phases

- [ ] **Phase 1: Fundacao HMAC + Webhook** - Estrutura do modulo, configuracao fail-fast, HMAC-SHA256 com eager body read, endpoint GET hub.challenge (plain text), POST webhook stub retornando 200, migrations Flyway V1-V4
- [ ] **Phase 2: Persistencia + Idempotencia** - Entidades JPA, repositorios, IdempotencyService com ON CONFLICT DO NOTHING, ClienteZapService com normalizacao de telefone BR, atualizacao REQUIRES_NEW de ultima_mensagem_em
- [ ] **Phase 3: Roteamento + Boundary Async** - ErpCallbackClient, MessageRouter, MensagemService.processarAsync() integrando Phases 1+2, ack 200 antes do async fan-out, download eager de media entrante
- [ ] **Phase 4: Outbound + Trava 24h + WhatsAppController** - WhatsAppCloudClient (texto/doc/botoes/lista, sem enviarTemplate), MediaCacheService, WindowEnforcementService (hard 409), endpoints internos ERP, log de saida
- [ ] **Phase 5: lib-whatsapp-client** - Starter Spring Boot espelhando lib-consultas-client: auto-config condicional, SPI WhatsAppCommandHandler + WhatsAppCommandRegistry, WhatsAppClient com Resilience4j, ObjectProvider graceful fallback, META-INF auto-config
- [ ] **Phase 6: Qualidade — Testes + OpenAPI + RUNBOOK** - Unit tests (HMAC/idempotencia/media-cache/janela-24h), integration tests WireMock (4 tipos de envio + webhook + 5xx + timeout), SpringDoc OpenAPI, README.md por modulo, RUNBOOK.md operacional

## Phase Details

### Phase 1: Fundacao HMAC + Webhook
**Goal**: O modulo `api-whatsapp` arranca, valida segredos no boot, aceita o handshake do Meta (hub.challenge) e rejeita qualquer POST sem assinatura HMAC valida — fundacao de seguranca antes de qualquer persistencia ou logica de negocio
**Depends on**: Nothing (first phase)
**Requirements**: WEB-01, WEB-02, WEB-03, WEB-04, CFG-01, CFG-02, CFG-03, CFG-04, PER-01
**Success Criteria** (what must be TRUE):
  1. `GET /webhook/whatsapp?hub.mode=subscribe&hub.verify_token=X&hub.challenge=Y` retorna exatamente `Y` como plain text (Content-Type text/plain, sem JSON, sem aspas) e status 200; verify_token errado retorna 403
  2. `POST /webhook/whatsapp` com `X-Hub-Signature-256` valida retorna 200 em menos de 1 segundo; body modificado em qualquer byte retorna 401 — comparacao usa `MessageDigest.isEqual()` (constant-time), nunca `.equals()`
  3. HMAC e computado sobre os bytes brutos do body via `CachedBodyHttpServletRequest` (eager read na construcao) — payload com texto portugues (`"Ola, gostaria de um orcamento"`) valida corretamente, nunca via `ContentCachingRequestWrapper`
  4. Boot falha imediatamente com mensagem clara se qualquer propriedade obrigatoria (`phoneNumberId`, `accessToken`, `appSecret`, `verifyToken`, `erpCallbackUrl`) estiver ausente — `accessToken`/`appSecret`/`verifyToken` nunca aparecem em logs
  5. Flyway aplica migrations V1 (clientes_zap), V2 (mensagens_log), V3 (media_cache), V4 (estado_conversa) no schema `whatsapp` no boot; `mvnw verify -pl api-whatsapp` verde com H2
**Plans**: 7 plans
  - [x] 01-PLAN-01-lib-shared-additional-public-paths.md — Estender ApiKeyFilter com construtor de 2 args (Set additionalPublicPaths) preservando backward-compat
  - [x] 01-PLAN-02-api-whatsapp-skeleton.md — Bootstrap esqueleto Maven do modulo api-whatsapp (pom + WhatsAppApplication + application.yml minimo)
  - [ ] 01-PLAN-03-properties-fail-fast.md — WhatsAppProperties com 5 @NotBlank + Bean Validation fail-fast + toString mascarado + application-test.yml
  - [x] 01-PLAN-04-flyway-migrations-v1-v4.md — Migrations Flyway V1-V4 (clientes_zap, mensagens_log, media_cache, estado_conversa) + datasource Postgres/H2
  - [ ] 01-PLAN-05-hmac-validator-cached-body.md — HmacValidator pure function + CachedBodyHttpServletRequest com eager body read
  - [ ] 01-PLAN-06-hmac-filter-security-config-webhook-controller.md — HmacSignatureFilter + SecurityConfig + WebhookController (GET handshake plain text, POST stub) + HealthController
  - [ ] 01-PLAN-07-integration-tests.md — Integration tests MockMvc end-to-end fechando os 5 ROADMAP success criteria
**UI hint**: no

### Phase 2: Persistencia + Idempotencia
**Goal**: Mensagens entrantes sao persistidas de forma idempotente, clientes sao identificados (ou criados) pelo telefone com normalizacao BR, e `ultima_mensagem_em` e atualizado atomicamente preparando a trava 24h
**Depends on**: Phase 1
**Requirements**: WEB-05, WEB-06, WEB-07, PER-02, PER-03, PER-04, PER-05, PER-06, PER-07
**Success Criteria** (what must be TRUE):
  1. Dois POSTs com o mesmo `wamid` resultam em exatamente 1 linha em `mensagens_log` — o segundo retorna 200 silenciosamente (`DataIntegrityViolationException` capturada, nao propagada); `ON CONFLICT (wamid) DO NOTHING` + row-count e o gate atomico, nao SELECT-before-INSERT
  2. Payload Meta com `message.text`, `message.interactive.button_reply`, `message.interactive.list_reply`, `message.document` e `statuses.status` sao todos parseados corretamente; tipo desconhecido e persistido com `tipo=desconhecido` sem erro
  3. Telefone `+5547984178525` (DDD 47, Santa Catarina) e normalizado para `5547841 78525` no INSERT em `clientes_zap`; DDDs SP (11-19), RJ (21/22/24), ES (27/28) mantem o 9o digito
  4. `ClienteZapService.identificar(telefone)` cria registro com `id_cliente_erp=null` para telefones desconhecidos e segue o fluxo sem erro; numero mapeado retorna `idClienteErp` correto
  5. `ultima_mensagem_em` e atualizado com `NOW()` do banco em transacao `REQUIRES_NEW` separada — nao `Instant.now()` da JVM; garantia contra TOCTOU race com a trava 24h
**Plans**: TBD
**UI hint**: no

### Phase 3: Roteamento + Boundary Async
**Goal**: Mensagens entrantes validadas e persistidas sao roteadas ao ERP via callback HTTP async — o ack 200 ao Meta e retornado antes de qualquer I/O externo, eliminando o risco de retry storm
**Depends on**: Phase 2
**Requirements**: ROU-01, ROU-02, ROU-03, ROU-04, ROU-05
**Success Criteria** (what must be TRUE):
  1. `POST /webhook/whatsapp` retorna 200 em menos de 1 segundo mesmo que o ERP callback demore 10 segundos (WireMock com delay) — o ack precede toda I/O externa via `@Async`
  2. `ErpCallbackClient` faz `POST {erpCallbackUrl}/api/modulos/whatsapp/comando` com payload `{telefone, comando, payload, idCliente}` e Resilience4j circuit breaker (10-call window, 50% threshold, 60s open) + retry 3x (1s/2s/4s)
  3. Timeout ou 5xx do ERP callback produz `log.error` estruturado e encerra o fluxo sem retentar nem enviar resposta ao cliente — ERP pode ter executado parcialmente
  4. Media entrante (cliente enviou documento/imagem) tem URL Meta baixada e bytes guardados como **primeira** acao async apos o ack 200 — URL Meta expira em 5 minutos; miss de 404 e logado como WARN, mensagem e persistida sem bytes
  5. Dois webhooks identicos (mesmo wamid) disparados simultaneamente resultam em exatamente 1 callback ao ERP — row-count do `ON CONFLICT DO NOTHING` e o gate de dispatch
**Plans**: TBD
**UI hint**: no

### Phase 4: Outbound + Trava 24h + WhatsAppController
**Goal**: O ERP consegue enviar os 4 tipos de mensagem de saida (texto, documento, botoes, lista) com custo zero garantido por arquitetura — `enviarTemplate()` nao existe no codigo, e a trava hard de janela 24h rejeita qualquer envio fora da janela antes de chamar a Cloud API
**Depends on**: Phase 3
**Requirements**: OUT-01, OUT-02, OUT-03, OUT-04, OUT-05, OUT-06, OUT-07, OUT-08, OUT-09, OUT-10, OUT-11
**Success Criteria** (what must be TRUE):
  1. `WhatsAppCloudClient` expoe `enviarTexto`, `enviarDocumento`, `enviarBotoes`, `enviarLista` — nenhum metodo `enviarTemplate` existe no codigo; busca por "template" no codigo-fonte do cliente retorna zero resultados
  2. `POST /api/whatsapp/enviar-*` com `ultima_mensagem_em` > 24h retorna 409 com codigo `JANELA_24H_FECHADA` e log estruturado antes de qualquer chamada a Cloud API — trava e via `WindowEnforcementService` lendo `ultima_mensagem_em` como committed read fora da transacao do webhook
  3. `enviarDocumento` com o mesmo PDF enviado duas vezes realiza upload apenas na primeira vez — `MediaCacheService` retorna `media_id` cacheado por sha256 na segunda chamada; entrada expirada (`expira_em < now()`) dispara reupload e atualiza `expira_em`
  4. Erro 4xx da Cloud API (400/401/403) nao e retentado, e logado com `meta_error_code`; erro 5xx e timeout acionam Resilience4j retry exponencial (3 tentativas, 1s/2s/4s); `Authorization: Bearer` nunca aparece nos logs
  5. Mensagem de saida bem-sucedida e persistida em `mensagens_log` com `direcao=out` e `wamid` retornado pelo Meta; envio com janela aberta via `/api/whatsapp/enviar-*` retorna 200 com o wamid
**Plans**: TBD
**UI hint**: no

### Phase 5: lib-whatsapp-client
**Goal**: ERPs podem consumir o `api-whatsapp` via starter Spring Boot com auto-config condicional, Resilience4j embutido e SPI de handlers de comando — espelha `lib-consultas-client` em estrutura e comportamento, sem expor a Cloud API diretamente
**Depends on**: Phase 4
**Requirements**: LIB-01, LIB-02, LIB-03, LIB-04, LIB-05, LIB-06, LIB-07, LIB-08
**Success Criteria** (what must be TRUE):
  1. Adicionar `lib-whatsapp-client` ao `pom.xml` de um ERP sem configurar `app.modulos.whatsapp.enabled=true` nao cria nenhum bean — auto-config e condicional por `@ConditionalOnProperty`
  2. Com `enabled=true` e `api-whatsapp` indisponivel, o ERP injeta um stub que loga WARN e retorna nulo sem lancar excecao — `ObjectProvider` graceful fallback identico ao padrao `ConsultasClient` em `ModulosController.java:40`
  3. `WhatsAppCommandRegistry` coleta todos os beans `WhatsAppCommandHandler` do contexto Spring e roteia: match exato em `getComando()` primeiro, fallback para prefixo (ex: `"aprovar 1234"` casa com handler `"aprovar"`)
  4. `WhatsAppClientImpl` aplica Resilience4j circuit breaker (10-call window, 50% threshold, 60s open) + retry exponencial (3 tentativas, 1s/2.0x) identico a `lib-consultas-client` — config via `WhatsAppProperties`
  5. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lista `WhatsAppClientAutoConfiguration`; `mvnw verify -pl lib-whatsapp-client` verde
**Plans**: TBD
**UI hint**: no

### Phase 6: Qualidade — Testes + OpenAPI + RUNBOOK
**Goal**: O modulo e verificavel, documentado e operacionalizavel — testes cobrem todos os caminhos criticos (HMAC, idempotencia, janela 24h, media cache), WireMock simula a Cloud API, e o RUNBOOK previne o bug de "shadow delivery" (missing WABA subscription) em producao
**Depends on**: Phase 5
**Requirements**: QA-01, QA-02, QA-03, QA-04, QA-05, QA-06, QA-07
**Success Criteria** (what must be TRUE):
  1. Unit tests cobrem: HMAC (payload valido / body modificado / texto portugues / body vazio), idempotencia (wamid duplicado / concurrent delivery / row-count gate), media cache (hit / miss / expirado), normalizacao telefone (DDD 47 vs DDD 11), trava 24h (`ultima_mensagem_em = now()-23h59m` permite; `now()-24h01m` rejeita com 409 lendo banco real)
  2. Integration tests WireMock 3.8.1 cobrem: envio texto, envio documento (upload + reuso cache), envio botoes (max 3), envio lista (max 10 itens), webhook entrante com payload real do Meta (text + button_reply + list_reply + document), erro 5xx (retry), timeout (retry), 4xx (sem retry), `WhatsAppClientAutoConfigurationTest` com modulo habilitado e desabilitado
  3. `mvnw verify -pl api-whatsapp` e `mvnw verify -pl lib-whatsapp-client` e build agregado do reactor retornam BUILD SUCCESS
  4. README.md em `api-whatsapp/` e `lib-whatsapp-client/` com quickstart, lista de properties e exemplo de `WhatsAppCommandHandler`; SpringDoc OpenAPI acessivel em `/swagger-ui.html` e `/v3/api-docs`
  5. RUNBOOK.md documenta passo-a-passo: criar app Meta + WABA + Phone Number ID, gerar System User token permanente ("Never" expiry), verificar `GET /{WABA_ID}/subscribed_apps` (passo obrigatorio — shadow delivery bug), configurar webhook URL via Cloudflare Tunnel, testar hub.challenge via curl
**Plans**: TBD
**UI hint**: no

## Progress

**Execution Order:** 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Fundacao HMAC + Webhook | 4/7 | In Progress|  |
| 2. Persistencia + Idempotencia | 0/TBD | Not started | - |
| 3. Roteamento + Boundary Async | 0/TBD | Not started | - |
| 4. Outbound + Trava 24h + WhatsAppController | 0/TBD | Not started | - |
| 5. lib-whatsapp-client | 0/TBD | Not started | - |
| 6. Qualidade — Testes + OpenAPI + RUNBOOK | 0/TBD | Not started | - |
