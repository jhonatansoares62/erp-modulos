# Requirements: ERP Modulos — Milestone "Modulo WhatsApp"

**Defined:** 2026-05-05
**Core Value:** Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros — pro Modulo WhatsApp: **custo zero de Meta garantido por design**, nao por disciplina.

## v1 Requirements

Requirements pra primeira release dos 2 modulos novos (`api-whatsapp` + `lib-whatsapp-client`). Cada um mapeia pra uma fase do roadmap.

### Webhook (recebimento de mensagens entrantes do Meta)

- [ ] **WEB-01**: Endpoint `GET /webhook/whatsapp` ecoa `hub.challenge` recebido como **plain text** (Content-Type `text/plain`, sem JSON, status 200) quando `hub.verify_token` bate com `WhatsAppProperties.verifyToken` — caso contrario 403
- [ ] **WEB-02**: Endpoint `POST /webhook/whatsapp` valida assinatura HMAC-SHA256 do header `X-Hub-Signature-256` contra os bytes brutos do body usando `WhatsAppProperties.appSecret` — comparacao **timing-safe** (`MessageDigest.isEqual`) — caso contrario 401 sem persistir
- [ ] **WEB-03**: HMAC validation usa **custom `HttpServletRequestWrapper`** que le bytes do body **eagerly na construcao** (NAO `ContentCachingRequestWrapper` — esse nao cacheia eager e leva a bug de "skip se vazio" que abre forge)
- [ ] **WEB-04**: Webhook responde **200 OK pro Meta em <1s** (limite real Meta: 5s, mas margem de seguranca) executando apenas: HMAC validation + idempotency check fast-path. Persistencia/roteamento/outbound rodam em `@Async` apos o ack
- [ ] **WEB-05**: Idempotencia fast-path por `wamid` em `IdempotencyService` — se ja visto recentemente, responde 200 sem reprocessar
- [ ] **WEB-06**: Idempotencia hard-guard por `UNIQUE wamid` em `mensagens_log` — `DataIntegrityViolationException` em duplicate e silenciada (catch + log debug + return 200), nao propagada
- [ ] **WEB-07**: Parser do payload Meta entende ao menos: `message.text`, `message.interactive.button_reply` (com `id` e `title`), `message.interactive.list_reply` (com `id` e `title`), `message.document` (com `id`/`mime_type`/`filename`), e callback de status (`statuses.status` = sent/delivered/read/failed) — para entradas desconhecidas, persiste em `mensagens_log` com `tipo=desconhecido` sem erro

### Persistencia (schema, migrations, cliente)

- [ ] **PER-01**: Schema PostgreSQL `whatsapp` criado pelo instalador (fora do escopo) e usado pelo modulo via `spring.datasource.url=...?currentSchema=whatsapp` ou `flyway.schemas=whatsapp`
- [ ] **PER-02**: Migration `V1__clientes_zap.sql` cria tabela `clientes_zap` com colunas: `id BIGSERIAL PK`, `id_cliente_erp BIGINT` (FK logica, sem constraint cross-schema), `telefone VARCHAR(20) UNIQUE NOT NULL`, `ultima_mensagem_em TIMESTAMP`, `criado_em TIMESTAMP DEFAULT NOW()`
- [ ] **PER-03**: Migration `V2__mensagens_log.sql` cria `mensagens_log` com: `id BIGSERIAL PK`, `wamid VARCHAR(255) UNIQUE NOT NULL`, `telefone VARCHAR(20) NOT NULL`, `direcao VARCHAR(3) CHECK (direcao IN ('in','out'))`, `tipo VARCHAR(50)`, `conteudo TEXT`, `media_id VARCHAR(255)`, `criado_em TIMESTAMP DEFAULT NOW()`, indices em `telefone` e `criado_em`
- [ ] **PER-04**: Migration `V3__media_cache.sql` cria `media_cache` com: `arquivo_hash CHAR(64) PK` (sha256 hex), `media_id VARCHAR(255) NOT NULL`, `criado_em TIMESTAMP DEFAULT NOW()`, `expira_em TIMESTAMP NOT NULL`
- [ ] **PER-05**: **Normalizacao de telefone brasileiro** no INSERT em `clientes_zap` — DDDs fora SP (11)/RJ (21,22,24)/ES (27,28) sao registrados no WhatsApp **sem** o 9o digito (regra ANATEL 2010, mas WhatsApp manteve o formato antigo nesses DDDs). Funcao `normalizarTelefoneBrasil(String)` aplicada antes de gravar/buscar. Bug silencioso (error 131026) sem isso
- [ ] **PER-06**: Resolucao do `id_cliente_erp` por telefone — `ClienteZapService.identificar(telefone)` busca em `clientes_zap`; se nao existe, cria registro com `id_cliente_erp=null` (cliente nao mapeado ainda) e segue o fluxo
- [ ] **PER-07**: Atualizacao de `ultima_mensagem_em` por telefone em **transacao separada** (`Propagation.REQUIRES_NEW`) para evitar TOCTOU race com a trava 24h — relogio do banco (`NOW()`) nao `Instant.now()` da JVM

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

- [ ] **CFG-01**: `WhatsAppProperties` (no `api-whatsapp`) com `phoneNumberId`, `accessToken`, `appSecret`, `verifyToken`, `erpCallbackUrl`, `callbackTimeout` — todos `@NotBlank`/required, falha rapido no boot via Bean Validation se faltar
- [ ] **CFG-02**: `application.yml` documentado com placeholders `${WHATSAPP_PHONE_NUMBER_ID}`, `${WHATSAPP_ACCESS_TOKEN}` etc — instalador injeta via env vars, nada hardcoded
- [ ] **CFG-03**: Logs **nunca** imprimem `accessToken` ou `appSecret` — mascarar via `Logger` filter ou ofuscar nos `toString()` das classes Properties
- [ ] **CFG-04**: Porta default `9193` (matching plano) configuravel via `server.port`

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

Mapeamento requirement → fase. Preenchido pelo gsd-roadmapper na proxima etapa.

| Requirement | Phase | Status |
|-------------|-------|--------|
| WEB-01 | TBD | Pending |
| WEB-02 | TBD | Pending |
| WEB-03 | TBD | Pending |
| WEB-04 | TBD | Pending |
| WEB-05 | TBD | Pending |
| WEB-06 | TBD | Pending |
| WEB-07 | TBD | Pending |
| PER-01 | TBD | Pending |
| PER-02 | TBD | Pending |
| PER-03 | TBD | Pending |
| PER-04 | TBD | Pending |
| PER-05 | TBD | Pending |
| PER-06 | TBD | Pending |
| PER-07 | TBD | Pending |
| OUT-01 | TBD | Pending |
| OUT-02 | TBD | Pending |
| OUT-03 | TBD | Pending |
| OUT-04 | TBD | Pending |
| OUT-05 | TBD | Pending |
| OUT-06 | TBD | Pending |
| OUT-07 | TBD | Pending |
| OUT-08 | TBD | Pending |
| OUT-09 | TBD | Pending |
| OUT-10 | TBD | Pending |
| OUT-11 | TBD | Pending |
| ROU-01 | TBD | Pending |
| ROU-02 | TBD | Pending |
| ROU-03 | TBD | Pending |
| ROU-04 | TBD | Pending |
| ROU-05 | TBD | Pending |
| LIB-01 | TBD | Pending |
| LIB-02 | TBD | Pending |
| LIB-03 | TBD | Pending |
| LIB-04 | TBD | Pending |
| LIB-05 | TBD | Pending |
| LIB-06 | TBD | Pending |
| LIB-07 | TBD | Pending |
| LIB-08 | TBD | Pending |
| CFG-01 | TBD | Pending |
| CFG-02 | TBD | Pending |
| CFG-03 | TBD | Pending |
| CFG-04 | TBD | Pending |
| QA-01 | TBD | Pending |
| QA-02 | TBD | Pending |
| QA-03 | TBD | Pending |
| QA-04 | TBD | Pending |
| QA-05 | TBD | Pending |
| QA-06 | TBD | Pending |
| QA-07 | TBD | Pending |

**Coverage:**
- v1 requirements: 49 total
- Mapped to phases: 0 (will be filled by gsd-roadmapper)
- Unmapped: 49 ⚠️ (expected at this stage)

---
*Requirements defined: 2026-05-05*
*Last updated: 2026-05-05 after research synthesis (5 docs / 1938 lines integrated)*
