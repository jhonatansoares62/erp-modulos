# ERP Modulos

## What This Is

Monorepo Spring Boot de **modulos reaproveitaveis** que o ERPKit consome em cada ERP de cliente (MUDAS, CALHAS, futuros). Cada modulo expoe uma capacidade transversal (envio de email, storage de arquivos, consultas externas, integracao WhatsApp, etc.) como um par `api-<dominio>` (servico Spring Boot) + `lib-<dominio>-client` (starter Spring Boot com auto-config + SPI), instalado on-premise junto do ERP do cliente.

A milestone ativa adiciona o **Modulo WhatsApp** (`api-whatsapp` + `lib-whatsapp-client`) seguindo o padrao ja estabelecido por `api-consultas` + `lib-consultas-client`. WhatsApp Cloud API com modelo **reativo puro de custo zero**: cliente sempre inicia, ERP responde dentro da janela 24h, nunca usa template pago.

## Core Value

**Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros.** Pro Modulo WhatsApp especificamente, isso significa **custo zero de Meta garantido por design** — nao via disciplina, via arquitetura.

## Requirements

### Validated

<!-- Capacidades ja entregues e em producao no monorepo. -->

- ✓ **api-email** — Envio SMTP transacional com templates Thymeleaf, multiplas contas configuraveis, conta padrao (`PUT /api/contas/{id}/padrao`), persistencia de logs em PostgreSQL — existing
- ✓ **api-storage** — Armazenamento de arquivos com persistencia em PostgreSQL — existing
- ✓ **api-consultas** — Consultas externas (CEP/CNPJ via BrasilAPI) com cache Caffeine — existing
- ✓ **lib-shared** — Excecoes, DTOs e filtros compartilhados entre modulos — existing
- ✓ **lib-consultas-client** — Starter Spring Boot com `@ConditionalOnProperty` auto-config, Resilience4j circuit breaker (10-call window, 50% threshold, 60s open) e retry exponencial (3 attempts, 1s/2.0x) pro api-consultas — existing
- ✓ **installer Inno Setup** — Build/deploy scripts e installer Windows pro monorepo — existing
- ✓ **Padrao arquitetural establecido** — `api-<dominio>` (servico) + `lib-<dominio>-client` (starter Spring Boot) com auto-config condicional, ObjectProvider graceful fallback no consumidor, Resilience4j para chamadas externas — existing

### Active

<!-- Milestone "Modulo WhatsApp" — escopo deste GSD project. -->

**Modulo `api-whatsapp` (servico, porta 9193, Windows Service):**

- [ ] **WHATS-01**: Recebe webhook GET (hub.challenge) e POST do Meta com validacao HMAC `X-Hub-Signature-256`
- [ ] **WHATS-02**: Idempotencia por `wamid` UNIQUE — Meta reenvia se webhook nao responde 200 em <5s
- [ ] **WHATS-03**: Persiste mensagens entrantes em `mensagens_log` com direcao `in`
- [ ] **WHATS-04**: Atualiza `ultima_mensagem_em` por telefone em `clientes_zap` (necessario pra trava de janela 24h)
- [ ] **WHATS-05**: Identifica `cliente_erp` pelo telefone via FK em `clientes_zap`
- [ ] **WHATS-06**: Roteia comando entrante via callback HTTP `POST http://localhost:8090/api/modulos/whatsapp/comando` (URL configuravel)
- [ ] **WHATS-07**: Cliente WhatsApp Cloud API com envio **texto** (mensagem de servico)
- [ ] **WHATS-08**: Cliente WhatsApp Cloud API com envio **documento** (PDF/arquivo, com upload + reuso por sha256)
- [ ] **WHATS-09**: Cliente WhatsApp Cloud API com envio **interactive button** (ate 3 botoes inline, parser do `button_reply` no webhook)
- [ ] **WHATS-10**: Cliente WhatsApp Cloud API com envio **interactive list** (ate 10 itens sectional, parser do `list_reply` no webhook)
- [ ] **WHATS-11**: Media cache `media_cache` (sha256 → media_id, TTL 30d) — evita reupload do mesmo arquivo
- [ ] **WHATS-12**: Trava custo zero #1 — **NAO existir API publica de envio de template**. WhatsAppCloudClient simplesmente nao implementa `enviarTemplate()`
- [ ] **WHATS-13**: Trava custo zero #2 — antes de cada envio, verifica `ultima_mensagem_em` < 24h. Se janela fechada: rejeita com 409 + log estruturado (sem reenvio automatico)
- [ ] **WHATS-14**: Persiste mensagens de saida em `mensagens_log` com direcao `out` + `wamid` retornado pelo Meta
- [ ] **WHATS-15**: Configuracao via `WhatsAppProperties`: `phoneNumberId`, `accessToken`, `appSecret`, `verifyToken`, `erpCallbackUrl` — todos obrigatorios, falha rapido no boot se faltar
- [ ] **WHATS-16**: Migrations Flyway no schema `whatsapp` (V1 clientes_zap, V2 mensagens_log, V3 media_cache, V4 estado_conversa minimo com so `ultima_mensagem_em`)
- [ ] **WHATS-17**: Endpoints internos pro ERP: `GET /api/whatsapp/status`, `POST /api/whatsapp/enviar-texto`, `POST /api/whatsapp/enviar-documento`, `POST /api/whatsapp/enviar-botoes`, `POST /api/whatsapp/enviar-lista`
- [ ] **WHATS-18**: SpringDoc OpenAPI em `/swagger-ui.html` e `/v3/api-docs` (alinhado com api-email/api-storage/api-consultas)

**Modulo `lib-whatsapp-client` (starter Spring Boot, espelha lib-consultas-client):**

- [ ] **LIB-01**: `@ConditionalOnProperty("app.modulos.whatsapp.enabled")` para auto-config opcional
- [ ] **LIB-02**: `WhatsAppProperties` (`app.modulos.whatsapp.{url,apiKey,timeout}`) com `spring-boot-configuration-processor`
- [ ] **LIB-03**: SPI `WhatsAppCommandHandler` — interface implementada pelo ERP (1 bean por comando: orcamento/boleto/status/nf/...)
- [ ] **LIB-04**: HTTP client interno (`WhatsAppClient`) que chama o `api-whatsapp` com Resilience4j circuit breaker + retry exponencial (mesma config de lib-consultas-client)
- [ ] **LIB-05**: Graceful fallback — se `api-whatsapp` indisponivel, ObjectProvider retorna stub que loga warn (consumidor nao quebra)

**Qualidade transversal:**

- [ ] **QA-01**: Unit tests — assinatura HMAC, idempotencia por wamid, cache de media_id por sha256, trava de janela 24h
- [ ] **QA-02**: Integration tests com **WireMock** simulando Cloud API (todos os 4 tipos de envio + webhook entrante + erro 5xx + timeout)
- [ ] **QA-03**: Build verde via `mvnw verify` em ambos os modulos novos
- [ ] **QA-04**: README.md por modulo (igual api-consultas/lib-consultas-client tem)
- [ ] **QA-05**: RUNBOOK.md com passo-a-passo da ERPKit pra provisionar 1 cliente novo (criar WABA, gerar System User token, configurar webhook URL, registrar Cloudflare Tunnel ingress, popular variaveis no instalador) — operacional, nao automatizado

### Out of Scope

<!-- Boundaries explicitas pra esta GSD project. -->

- **Engate ERP-MUDAS** (4 endpoints proxy em `ModulosController`, 4 handlers de exemplo `OrcamentoCommandHandler`/`BoletoCommandHandler`/`StatusPedidoCommandHandler`/`NotaFiscalCommandHandler`, alteracoes em `application.yml`/`pom.xml`) — vive em `C:\projetos\ERP-MUDAS`, sera GSD project separado depois
- **Alteracoes no Inno Setup installer do MUDAS** (empacotar `api-whatsapp.jar`, novo wizard com checkbox WhatsApp, criar schema, registrar servico WinSW, ingress tunnel) — vive em `C:\projetos\ERP-MUDAS\installer\`, GSD project separado
- **Engate ERP-CALHAS** — D2 explicito: piloto MUDAS apenas, CALHAS herda depois
- **Onboarding multi-cliente automatizado** (auto-provisionar WABA via Graph API, gerar System User token, criar slug, criar ingress no tunnel) — operacional manual da ERPKit por enquanto, virara automacao em milestone futuro
- **E2E com WABA real do Meta** (numero de teste gratuito ou prod) — DoD desta milestone e WireMock; E2E real e milestone seguinte (depende de verificacao Meta Business, dias/semanas fora de controle)
- **Onboarding de cliente piloto MUDAS** — milestone seguinte
- **Listener proativo de eventos do ERP** (ex: pedido aprovado → manda mensagem) — D3 proibe por design (custo zero)
- **API de envio de template** (marketing/utility/auth) — D4 proibe por design (sempre gera custo)
- **Reenvio automatico fora da janela 24h** — D5 proibe por design (gera custo)
- **Maquina de estado complexa de conversa** (`aguardando_documento`, `em_aprovacao`, etc) — fica nos handlers do ERP, api-whatsapp so persiste `ultima_mensagem_em`
- **Auto-update do api-whatsapp.jar** — `release.sh` atual so publica JAR principal; estender pipeline ou usar `recovery-update.ps1` e contexto de outro projeto (ERP MUDAS release)

## Context

**Tech stack do monorepo (do `.planning/codebase/STACK.md`):**

- Java 21, Spring Boot 3.5.9, Maven via mvnw
- SpringDoc OpenAPI 2.8.15 em todos os modulos
- Resilience4j 2.2.0 (circuit breaker + retry) usado em libs-client
- Flyway 9.22.x para migrations (api-email e api-storage ja usam)
- PostgreSQL com HikariCP em producao, H2 nos testes
- Spring Boot Test Starter (JUnit 5, Mockito, AssertJ) padrao em todos os modulos

**Padrao a seguir (api-consultas + lib-consultas-client):**

- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java` — espelhar para `WhatsAppClientAutoConfiguration`
- `api-consultas/` — espelhar estrutura completa (controller / service / dto / vo / config / db.migration)
- `C:\projetos\ERP-MUDAS\src\main\java\br\com\mudas\erp\shared\modulo\controller\ModulosController.java` linha 40 — padrao `ObjectProvider` graceful fallback (referencia, mas alteracoes la sao Out of Scope)

**Convencoes do monorepo (do `.planning/codebase/CONVENTIONS.md`):**

- Identificadores em portugues (alinhado com api-email/api-storage/api-consultas)
- Estrutura por camada: `controller`/`service`/`repository`/`dto`/`vo`/`exception`/`config`
- Validacao Bean Validation nos DTOs
- Logging SLF4J com niveis estruturados

**Documento de origem:** `C:\projetos\erp-modulos\PLANO-WHATSAPP.md` (esta milestone) referenciando o guia de pesquisa em `C:\projetos\ERP-MUDAS\TEMP\zap\integracao-whatsapp-erp.md` (467 linhas, 2026-05-01).

**Identificacao do cliente final:** telefone via campo ja existente em `Cliente` do ERP-MUDAS (verificar e adicionar indice quando o engate for implementado — fora do escopo aqui).

## Constraints

- **Custo**: ZERO de Meta. Garantido por (a) sem API de template, (b) trava hard de janela 24h, (c) reativo puro (sem listeners de eventos do ERP) — Custo recorrente com terceiros e o maior risco operacional do produto
- **Tech stack**: Spring Boot 3.5.9 + Java 21 + Maven — alinhado com o monorepo; novo modulo nao deve introduzir framework alternativo
- **Padrao arquitetural**: `api-<dominio>` + `lib-<dominio>-client` com auto-config condicional + Resilience4j — alinhamento com api-consultas/lib-consultas-client e Cleanliness do monorepo
- **Persistencia**: PostgreSQL local 5433 do cliente, schema `whatsapp` isolado, Flyway no boot — D1 (dados ficam no PC do cliente, nao em servidor ERPKit)
- **Deployment**: On-premise por cliente, Windows Service via WinSW, dependente de `postgresql-x64-15-erpmudas` — alinhamento com pacote MUDAS atual
- **Idempotencia**: `wamid` UNIQUE em `mensagens_log` — Meta reenvia entregas se webhook nao responde 200 em <5s
- **Janela 24h**: implicita arquiteturalmente (D3 reativo) e travada explicitamente (D5 hard-block) — gera custo se quebrada
- **HMAC**: validacao de `X-Hub-Signature-256` obrigatoria em todo POST do Meta — webhook publico precisa rejeitar trafico nao assinado
- **Escopo cross-repo**: Engate em ERP-MUDAS e installer ficam **fora** — esta GSD project nao toca codigo em `C:\projetos\ERP-MUDAS\`

## Key Decisions

| Decisao | Racional | Outcome |
|---------|----------|---------|
| **D1** Hospedagem on-premise por cliente (Cloudflare Tunnel proprio em `zap-<slug>.erpkit.com.br`) | Dados de conversa nunca saem do PC do cliente; alinhamento com modelo de instalacao MUDAS atual | — Pending |
| **D2** Piloto MUDAS apenas (CALHAS herda depois) | Reduz superficie do milestone; CALHAS reutiliza `lib-whatsapp-client` ja pronto | — Pending |
| **D3** Modelo reativo puro (cliente sempre inicia, ERP nunca dispara mensagem por conta propria) | Garante janela 24h sempre aberta quando ERP responde → custo zero por design | — Pending |
| **D4** Sem API de envio de template no api-whatsapp | Templates sao a unica forma de gerar custo no modelo Cloud API; ausencia da API torna impossivel enganar a trava | — Pending |
| **D5** Trava hard de janela 24h (rejeita 409 antes de chamar Cloud API) | Defesa em profundidade alem de D3; protege contra bug de handler que tente responder a uma mensagem antiga | — Pending |
| **D6** Persistencia minima de estado de conversa (so `ultima_mensagem_em`) | Estados complexos (`em_aprovacao`, `aguardando_doc`) duplicariam logica do ERP; fica nos handlers SPI | — Pending |
| **D7** DoD = WireMock simulando Cloud API; E2E real e milestone seguinte | Verificacao Meta Business pode levar dias/semanas fora de controle do dev | — Pending |
| **D8** Onboarding multi-cliente manual da ERPKit (RUNBOOK.md) | Volume baixo no inicio; automacao prematura. Estendido depois quando volume justificar | — Pending |
| **D9** 4 tipos de mensagem de saida no v1 (texto, documento, botoes, lista) | Cobre os fluxos do PLANO (orcamento PDF + APROVAR/RECUSAR + menu inicial); todos gratuitos no modelo reativo | — Pending |
| **D10** Media cache (sha256 → media_id, TTL 30d) entra no v1 | PLANO ja preve V3 migration; reduz latencia + uso da API quando o mesmo PDF e enviado varias vezes | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-05 after initialization (milestone "Modulo WhatsApp" scoped)*