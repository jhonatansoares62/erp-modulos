# Requirements: ERP Modulos — Milestone "Modulo WhatsApp"

**Defined:** 2026-05-05
**Core Value:** Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros — pro Modulo WhatsApp: **custo zero de Meta garantido por design**, nao por disciplina.

## v1 Requirements

Requirements pra primeira release dos 2 modulos novos (`api-whatsapp` + `lib-whatsapp-client`). Cada um mapeia pra uma fase do roadmap.

### Webhook (recebimento de mensagens entrantes do Meta)

- [x] **WEB-01**: Endpoint `GET /webhook/whatsapp` ecoa `hub.challenge` recebido como **plain text** (Content-Type `text/plain`, sem JSON, status 200) quando `hub.verify_token` bate com `WhatsAppProperties.verifyToken` — caso contrario 403
- [x] **WEB-02**: Endpoint `POST /webhook/whatsapp` valida assinatura HMAC-SHA256 do header `X-Hub-Signature-256` contra os bytes brutos do body usando `WhatsAppProperties.appSecret` — comparacao **timing-safe** (`MessageDigest.isEqual`) — caso contrario 401 sem persistir
- [x] **WEB-03**: HMAC validation usa **custom `HttpServletRequestWrapper`** que le bytes do body **eagerly na construcao** (NAO `ContentCachingRequestWrapper` — esse nao cacheia eager e leva a bug de "skip se vazio" que abre forge)
- [x] **WEB-04**: Webhook responde **200 OK pro Meta em <1s** (limite real Meta: 5s, mas margem de seguranca) executando apenas: HMAC validation + idempotency check fast-path. Persistencia/roteamento/outbound rodam em `@Async` apos o ack
- [x] **WEB-05**: Idempotencia fast-path por `wamid` em `IdempotencyService` — se ja visto recentemente, responde 200 sem reprocessar
- [x] **WEB-06**: Idempotencia hard-guard por `UNIQUE wamid` em `mensagens_log` — `DataIntegrityViolationException` em duplicate e silenciada (catch + log debug + return 200), nao propagada
- [x] **WEB-07**: Parser do payload Meta entende ao menos: `message.text`, `message.interactive.button_reply` (com `id` e `title`), `message.interactive.list_reply` (com `id` e `title`), `message.document` (com `id`/`mime_type`/`filename`), e callback de status (`statuses.status` = sent/delivered/read/failed) — para entradas desconhecidas, persiste em `mensagens_log` com `tipo=desconhecido` sem erro

### Persistencia (schema, migrations, cliente)

- [x] **PER-01**: Schema PostgreSQL `whatsapp` criado pelo instalador (fora do escopo) e usado pelo modulo via `spring.datasource.url=...?currentSchema=whatsapp` ou `flyway.schemas=whatsapp`
- [x] **PER-02**: Migration `V1__clientes_zap.sql` cria tabela `clientes_zap` com colunas: `id BIGSERIAL PK`, `id_cliente_erp BIGINT` (FK logica, sem constraint cross-schema), `telefone VARCHAR(20) UNIQUE NOT NULL`, `ultima_mensagem_em TIMESTAMP`, `criado_em TIMESTAMP DEFAULT NOW()` — migration deployada em Phase 1 PLAN-04; **@Entity ClienteZap mapeada em Phase 2 Plan 01 com Hibernate validate aceitando schema (commit 1d2b4c6)**
- [x] **PER-03**: Migration `V2__mensagens_log.sql` cria `mensagens_log` com: `id BIGSERIAL PK`, `wamid VARCHAR(255) UNIQUE NOT NULL`, `telefone VARCHAR(20) NOT NULL`, `direcao VARCHAR(3) CHECK (direcao IN ('in','out'))`, `tipo VARCHAR(50)`, `conteudo TEXT`, `media_id VARCHAR(255)`, `criado_em TIMESTAMP DEFAULT NOW()`, indices em `telefone` e `criado_em` — migration deployada em Phase 1 PLAN-04; **@Entity MensagemLog mapeada em Phase 2 Plan 01 com @Enumerated(STRING) Direcao + columnDefinition=TEXT em conteudo (commit 1d2b4c6)**
- [x] **PER-04**: Migration `V3__media_cache.sql` cria `media_cache` com: `arquivo_hash CHAR(64) PK` (sha256 hex), `media_id VARCHAR(255) NOT NULL`, `criado_em TIMESTAMP DEFAULT NOW()`, `expira_em TIMESTAMP NOT NULL` — migration deployada em Phase 1 PLAN-04; **@Entity MediaCache mapeada em Phase 2 Plan 01 com columnDefinition=CHAR(64) em arquivoHash (commit 1d2b4c6)**
- [x] **PER-05**: **Normalizacao de telefone brasileiro** no INSERT em `clientes_zap` — DDDs fora SP (11)/RJ (21,22,24)/ES (27,28) sao registrados no WhatsApp **sem** o 9o digito (regra ANATEL 2010, mas WhatsApp manteve o formato antigo nesses DDDs). Funcao `normalizarTelefoneBrasil(String)` aplicada antes de gravar/buscar. Bug silencioso (error 131026) sem isso — **utility puro `TelefoneBR.normalizar(String)` implementado em Phase 2 Plan 02 (commit b0bba6f); aplicado no INSERT/lookup pelo ClienteZapService em Plan 04 (commit f347de4)**
- [x] **PER-06**: Resolucao do `id_cliente_erp` por telefone — `ClienteZapService.identificar(telefone)` busca em `clientes_zap`; se nao existe, cria registro com `id_cliente_erp=null` (cliente nao mapeado ainda) e segue o fluxo — **implementado em Phase 2 Plan 04 (commit f347de4) com race protection via try/catch DataIntegrityViolationException + re-fetch (UNIQUE telefone como gate atomico)**
- [x] **PER-07**: Atualizacao de `ultima_mensagem_em` por telefone em **transacao separada** (`Propagation.REQUIRES_NEW`) para evitar TOCTOU race com a trava 24h — relogio do banco (`NOW()`) nao `Instant.now()` da JVM — **implementado em Phase 2 Plan 04 (commit f347de4) via native @Query `UPDATE ... SET ultima_mensagem_em = NOW()` em `ClienteZapService.atualizarUltimaMensagemEm` REQUIRES_NEW; test 6 valida commit imediato visivel via 2a conexao JdbcTemplate**

### Outbound (Cloud API + travas custo zero)

- [ ] **OUT-01**: `WhatsAppCloudClient.enviarTexto(telefone, texto)` chama `POST graph.facebook.com/v22.0/{phoneNumberId}/messages` com payload `messaging_product=whatsapp, type=text` via Spring `RestClient` (NAO `RestTemplate`)
- [ ] **OUT-02**: `WhatsAppCloudClient.enviarDocumento(telefone, bytes, filename, mimeType, caption)` faz upload via `POST /{phoneNumberId}/media` (multipart) entao envia `type=document` referenciando `media_id` retornado
- [ ] **OUT-03**: `WhatsAppCloudClient.enviarBotoes(telefone, texto, botoes)` envia `type=interactive` com `interactive.type=button` (ate **3 botoes** reply, cada um `{type:'reply', reply:{id, title}}`); falha early se >3 botoes
- [ ] **OUT-04**: `WhatsAppCloudClient.enviarLista(telefone, texto, secoes)` envia `type=interactive` com `interactive.type=list` (ate **10 itens totais** distribuidos em 1+ secoes, cada item `{id, title, description?}`); falha early se >10 itens
- [ ] **OUT-05**: **NAO existir** `WhatsAppCloudClient.enviarTemplate(...)` — metodo simplesmente nao faz parte da API publica. Trava custo zero #1 garantida por ausencia de codigo, nao por flag
- [ ] **OUT-06**: `WindowEnforcementService.verificarJanela(telefone)` consulta `ultima_mensagem_em` via query direta (pula cache JPA) **fora** da transacao do webhook — se diff > 24h, lanca `JanelaConversaFechadaException` que vira HTTP 409 com codigo `JANELA_24H_FECHADA`. Trava custo zero #2
- [ ] **OUT-07**: Antes de cada chamada Cloud API em `WhatsAppCloudClient`, hook `@Aspect` ou `@Before` invoca `WindowEnforcementService.verificarJanela(telefone)` — bloqueio inviolavel via interceptor, nao dependendo de cada metodo lembrar
- [ ] **OUT-08**: Media cache `MediaCacheService` — antes de upload, calcula `sha256(bytes)`, busca em `media_cache.arquivo_hash`. Hit (e nao expirado) → reusa `media_id`. Miss → upload + grava com `expira_em = now() + 30 dias`
- [ ] **OUT-09**: Persiste mensagem de saida em `mensagens_log` com `direcao=out` + `wamid` retornado pelo Meta apos sucesso da chamada
- [ ] **OUT-10**: Tratamento de erros Cloud API: 4xx categoricos (400/401/403) **nao retentar**, logar erro estruturado com `meta_error_code`. 5xx + timeout: Resilience4j retry exponencial (3 tentativas, backoff 1s/2s/4s)
- [ ] **OUT-11**: Endpoints internos chamaveis pelo ERP: `POST /api/whatsapp/enviar-texto`, `POST /api/whatsapp/enviar-documento`, `POST /api/whatsapp/enviar-botoes`, `POST /api/whatsapp/enviar-lista`, `GET /api/whatsapp/status` — todos delegam pro `WhatsAppCloudClient` (com trava 24h ja aplicada)

### Roteamento (callback ERP, async boundary)

- [ ] **ROU-01**: Apos persistencia da mensagem entrante, `MessageRouter` invoca `ErpCallbackClient.entregar(comando)` em `@Async` — nao bloqueia o ack 200
- [ ] **ROU-02**: `ErpCallbackClient` faz `POST {erpCallbackUrl}/api/modulos/whatsapp/comando` com payload `{telefone, comando, payload, idCliente}` — `erpCallbackUrl` configuravel (default `http://localhost:8090`)
- [ ] **ROU-03**: ERP callback usa Resilience4j circuit breaker (mesma config de lib-consultas-client: 10-call window, 50% threshold, 60s open) + retry exponencial (3 tentativas, 1s/2s/4s)
- [ ] **ROU-04**: ERP callback timeout default 5s (configuravel via `app.modulos.whatsapp.callback-timeout`); timeout/erro nao trava o webhook (ja respondeu 200), so loga e nao envia resposta de saida
- [ ] **ROU-05**: **Download de media entrante** (cliente mandou imagem/PDF) e a **PRIMEIRA** acao async apos ack — URL Meta expira em 5min e a fila async pode atrasar. Bytes baixados sao guardados em memoria/temp pra entregar pro ERP no callback

### Lib WhatsApp Client (starter Spring Boot)

- [ ] **LIB-01**: Modulo `lib-whatsapp-client/` com `pom.xml` declarando `spring-boot-starter` + `spring-boot-configuration-processor` + Resilience4j — espelha `lib-consultas-client/pom.xml`
- [ ] **LIB-02**: `WhatsAppClientAutoConfiguration` com `@ConditionalOnProperty(prefix="app.modulos.whatsapp", name="enabled", havingValue="true")` — modulo desligado por default, ERPs habilitam explicitamente
- [ ] **LIB-03**: `WhatsAppProperties` com `@ConfigurationProperties(prefix="app.modulos.whatsapp")` — campos `enabled:boolean`, `url:String` (default `http://localhost:9193`), `apiKey:String` (opcional, futuro), `timeout:Duration` (default `PT5S`)
- [ ] **LIB-04**: SPI `WhatsAppCommandHandler` interface — metodos: `String getComando()` (keyword, ex: `"orcamento"`), `boolean matches(String comando)` (default true se igual ao getComando, override pra prefix match), `WhatsAppRespostaDto processar(WhatsAppComandoDto)`
- [ ] **LIB-05**: `WhatsAppCommandRegistry` componente Spring que coleta todos os `WhatsAppCommandHandler` beans via DI e roteia comando entrante: exact-match em `getComando()` primeiro, fallback pra `matches()` (suporta `aprovar 1234` casar com handler `aprovar`)
- [ ] **LIB-06**: `WhatsAppClient` HTTP client interno usando Spring `RestClient` chama o `api-whatsapp` (URL via Properties) com Resilience4j circuit breaker + retry — mesmos defaults de lib-consultas-client
- [ ] **LIB-07**: ObjectProvider graceful fallback no consumidor — se `api-whatsapp` indisponivel ou modulo desabilitado, ERP injeta stub que loga warn e nao quebra. Padrao de `ConsultasClient` no `ModulosController.java:40`
- [ ] **LIB-08**: META-INF auto-config registration: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lista `WhatsAppClientAutoConfiguration` (Spring Boot 3.x convention)

### Configuracao (properties + secrets)

- [x] **CFG-01**: `WhatsAppProperties` (no `api-whatsapp`) com `phoneNumberId`, `accessToken`, `appSecret`, `verifyToken`, `erpCallbackUrl`, `callbackTimeout` — todos `@NotBlank`/required, falha rapido no boot via Bean Validation se faltar
- [x] **CFG-02**: `application.yml` documentado com placeholders `${WHATSAPP_PHONE_NUMBER_ID}`, `${WHATSAPP_ACCESS_TOKEN}` etc — instalador injeta via env vars, nada hardcoded
- [x] **CFG-03**: Logs **nunca** imprimem `accessToken` ou `appSecret` — mascarar via `Logger` filter ou ofuscar nos `toString()` das classes Properties
- [x] **CFG-04**: Porta default `9193` (matching plano) configuravel via `server.port`

### Qualidade (testes, docs, runbook)

- [ ] **QA-01**: Unit tests cobrem: HMAC validation (positive/negative/timing-safe), idempotency wamid (UNIQUE catch), media cache sha256 (hit/miss/expirado), normalizacao telefone BR (todos os DDDs SP/RJ/ES vs outros), trava janela 24h (TOCTOU race com `REQUIRES_NEW`)
- [ ] **QA-02**: Integration tests com **WireMock 3.8.1** simulando Cloud API: todos os 4 envios (texto/doc/botoes/lista), webhook entrante com payload real do Meta (texto + button_reply + list_reply + document), erro 5xx + timeout, 4xx sem retry
- [ ] **QA-03**: Build verde via `mvnw verify -pl api-whatsapp` e `mvnw verify -pl lib-whatsapp-client` (e build agregado do reactor)
- [ ] **QA-04**: README.md em cada modulo (igual `api-consultas/README.md` e `lib-consultas-client/README.md` ja tem) — quickstart, properties, exemplo de handler SPI
- [ ] **QA-05**: SpringDoc OpenAPI em `/swagger-ui.html` e `/v3/api-docs` (igual outros modulos)
- [ ] **QA-06**: **RUNBOOK.md** operacional pra ERPKit — passo-a-passo provisionar 1 cliente: criar app Meta + WABA + Phone Number ID + System User token + verificar webhook URL via Cloud Tunnel + **subscribe explicito da app no WABA via `POST /{WABA_ID}/subscribed_apps`** (Meta UI 2025 quebrou subscribe automatico — sem isso, "shadow delivery": webhook verifica mas nenhuma mensagem chega)
- [ ] **QA-07**: Test fixtures de payloads Meta reais (capturar dos exemplos no guia de pesquisa, linha 175-203 de `integracao-whatsapp-erp.md`)

## v2 Requirements

Reconhecidos mas adiados pro proximo milestone (`Modulo WhatsApp v2`).

### Differentiators

- **DIFF-01**: Mark-as-read (`messages.read`) — endpoint Meta gratuito, low complexity
- **DIFF-02**: Typing indicator (`typing_indicator` 25s) — UX nice-to-have
- **DIFF-03**: Categorizacao estruturada de erros Meta por `error.code` em metricas Prometheus/Micrometer
- **DIFF-04**: Volume metrics (mensagens in/out por dia, por tipo, por erro) via Micrometer
- **DIFF-05**: Retencao automatica de `mensagens_log` (purge >90 dias) configuravel — LGPD compliance

### Operacao

- **OPS-V2-01**: Onboarding multi-cliente automatizado — endpoint/CLI no api-whatsapp pra auto-provisionar WABA via Graph API
- **OPS-V2-02**: Auto-update do `api-whatsapp.jar` integrado ao `release.sh` (hoje so publica JAR principal)
- **OPS-V2-03**: Engate ERP-CALHAS (handlers + ModulosController + installer)
- **OPS-V2-04**: E2E real com WABA do Meta + 1 cliente piloto MUDAS

## Out of Scope

Exclusoes explicitas — anti-features do FEATURES.md research e boundaries cross-repo.

| Feature | Reason |
|---------|--------|
| **API de envio de template** (marketing/utility/auth) | Templates sao a UNICA forma de gerar custo no Cloud API — D4 proibe por design |
| **Listener proativo de eventos do ERP** (ex: pedido aprovado → mensagem) | D3 reativo puro: ERP nunca dispara mensagem por conta propria — geraria custo |
| **Reenvio automatico fora janela 24h** | Geraria custo (so possivel via template) — violacao da Core Value |
| **Mass broadcast / scheduled outbound** | Mesmo problema: precisa template ou viola janela 24h |
| **Maquina de estado complexa de conversa** (`em_aprovacao`, `aguardando_doc`) | Duplica logica do ERP. api-whatsapp persiste so `ultima_mensagem_em`; estado fica nos handlers SPI |
| **Multi-tenant em um processo** | D1 on-premise: cada cliente tem 1 instancia api-whatsapp local. Single-tenant por design simplifica config + isola dados |
| **Inbound media auto-storage permanente** | Bytes baixados sao entregues ao ERP via callback e nao guardados pelo modulo (ERP decide retencao) |
| **AI/LLM/NLP pra interpretar comandos** | Comandos sao keywords simples (`orcamento`, `boleto`, `aprovar X`); NLP e overkill e adiciona dependencia/custo |
| **Engate em ERP-MUDAS** (4 endpoints proxy + handlers + alteracoes em pom/yml) | Outro repo (`C:\projetos\ERP-MUDAS`) — outro GSD project depois |
| **Inno Setup installer changes** (empacotar jar, wizard, schema, WinSW, ingress) | Outro repo (`C:\projetos\ERP-MUDAS\installer\`) — outro GSD project depois |
| **Onboarding multi-cliente automatizado** (provisionar WABA, gerar token, slug, ingress) | Operacional manual da ERPKit por enquanto (RUNBOOK.md cobre); volume baixo nao justifica automacao |
| **E2E com WABA real do Meta** | DoD desta milestone e WireMock; verificacao Meta Business pode levar 1-2 semanas fora de controle |
| **Cliente piloto real (MUDAS)** | Milestone seguinte; depende de E2E acima |
| **Auto-update do api-whatsapp.jar** | `release.sh` atual so publica JAR principal — fora do escopo deste milestone |

## Traceability

Mapeamento requirement → fase. Preenchido pelo gsd-roadmapper.

| Requirement | Phase | Status |
|-------------|-------|--------|
| WEB-01 | Phase 1 | Complete |
| WEB-02 | Phase 1 | Complete |
| WEB-03 | Phase 1 | Complete |
| WEB-04 | Phase 1 | Complete |
| WEB-05 | Phase 2 | Complete |
| WEB-06 | Phase 2 | Complete |
| WEB-07 | Phase 2 | Complete |
| PER-01 | Phase 1 | Complete |
| PER-02 | Phase 2 | Pending |
| PER-03 | Phase 2 | Pending |
| PER-04 | Phase 2 | Pending |
| PER-05 | Phase 2 | Complete |
| PER-06 | Phase 2 | Complete |
| PER-07 | Phase 2 | Complete |
| OUT-01 | Phase 4 | Pending |
| OUT-02 | Phase 4 | Pending |
| OUT-03 | Phase 4 | Pending |
| OUT-04 | Phase 4 | Pending |
| OUT-05 | Phase 4 | Pending |
| OUT-06 | Phase 4 | Pending |
| OUT-07 | Phase 4 | Pending |
| OUT-08 | Phase 4 | Pending |
| OUT-09 | Phase 4 | Pending |
| OUT-10 | Phase 4 | Pending |
| OUT-11 | Phase 4 | Pending |
| ROU-01 | Phase 3 | Pending |
| ROU-02 | Phase 3 | Pending |
| ROU-03 | Phase 3 | Pending |
| ROU-04 | Phase 3 | Pending |
| ROU-05 | Phase 3 | Pending |
| LIB-01 | Phase 5 | Pending |
| LIB-02 | Phase 5 | Pending |
| LIB-03 | Phase 5 | Pending |
| LIB-04 | Phase 5 | Pending |
| LIB-05 | Phase 5 | Pending |
| LIB-06 | Phase 5 | Pending |
| LIB-07 | Phase 5 | Pending |
| LIB-08 | Phase 5 | Pending |
| CFG-01 | Phase 1 | Complete |
| CFG-02 | Phase 1 | Complete |
| CFG-03 | Phase 1 | Complete |
| CFG-04 | Phase 1 | Complete |
| QA-01 | Phase 6 | Pending |
| QA-02 | Phase 6 | Pending |
| QA-03 | Phase 6 | Pending |
| QA-04 | Phase 6 | Pending |
| QA-05 | Phase 6 | Pending |
| QA-06 | Phase 6 | Pending |
| QA-07 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 49 total
- Mapped to phases: 49/49
- Unmapped: 0

| Phase | Requirements | Count |
|-------|-------------|-------|
| Phase 1 | WEB-01, WEB-02, WEB-03, WEB-04, PER-01, CFG-01, CFG-02, CFG-03, CFG-04 | 9 |
| Phase 2 | WEB-05, WEB-06, WEB-07, PER-02, PER-03, PER-04, PER-05, PER-06, PER-07 | 9 |
| Phase 3 | ROU-01, ROU-02, ROU-03, ROU-04, ROU-05 | 5 |
| Phase 4 | OUT-01, OUT-02, OUT-03, OUT-04, OUT-05, OUT-06, OUT-07, OUT-08, OUT-09, OUT-10, OUT-11 | 11 |
| Phase 5 | LIB-01, LIB-02, LIB-03, LIB-04, LIB-05, LIB-06, LIB-07, LIB-08 | 8 |
| Phase 6 | QA-01, QA-02, QA-03, QA-04, QA-05, QA-06, QA-07 | 7 |
| **Total** | | **49** |

---
*Requirements defined: 2026-05-05*
*Last updated: 2026-05-05 — traceability preenchida pelo gsd-roadmapper (49/49 mapeados, 6 fases)*
