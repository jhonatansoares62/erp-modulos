# Phase 1: Fundacao HMAC + Webhook - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 1-Fundacao HMAC + Webhook
**Mode:** `--auto` (user delegated all 4 gray areas to Claude after seeing the menu)
**Areas discussed:** Onde valida HMAC, Bypass de API-key no /webhook, Fail-fast nas Properties, Escopo do POST stub

---

## Onde valida HMAC

| Option | Description | Selected |
|--------|-------------|----------|
| Servlet Filter @Order(HIGHEST_PRECEDENCE) | Filter delegando a HmacValidator service. Body nunca chega no Spring MVC se invalido. PITFALLS C-02 documenta esta como obrigatorio. | ✓ |
| HmacValidator service injetado no WebhookController | Service-no-controller. ARCHITECTURE.md sugere este caminho. Tecnicamente seguro com CachedBodyHttpServletRequest, mas o body ja foi roteado pelo MVC. | |
| HandlerInterceptor | Intermediario entre Filter e Controller. Mais cedo que controller, mais tarde que Filter. Nao oferece vantagem clara sobre Filter. | |

**Selected option:** Filter at HIGHEST_PRECEDENCE delegando a HmacValidator service (D-01 em CONTEXT.md)
**Notes:** Conflito explicito com ARCHITECTURE.md reconhecido — CONTEXT.md sobrescreve para Phase 1. Service `HmacValidator` continua existindo (testavel via unit test sem servlet), so muda quem o invoca. Trade-off: ligeiramente menos testavel que service-no-controller; mitigado pelo split em duas camadas (Filter para wiring, Validator para logica pura).

---

## Bypass de API-key no /webhook

| Option | Description | Selected |
|--------|-------------|----------|
| Modificar lib-shared/ApiKeyFilter para aceitar additionalPublicPaths | Mudanca cirurgica e backward-compat. Webhook fica como dado de configuracao do api-whatsapp. Outros modulos futuros (payment webhooks, OAuth callbacks) reusam. | ✓ |
| Override local: criar 2o filter no api-whatsapp que pula /webhook antes do ApiKeyFilter | Nao toca lib-shared. Mais filters em cascata, mais ordem para gerenciar. Hardcoda policy em cada modulo. | |
| Filter ordering customizado | Vago. Acabaria caindo numa das duas variantes acima. | |

**Selected option:** Modificar lib-shared/ApiKeyFilter (D-02)
**Notes:** Construtor de 1 arg preservado (`new ApiKeyFilter(apiKey)`) — zero impacto em api-email/api-storage/api-consultas. Construtor de 2 args adicionado: `new ApiKeyFilter(apiKey, Set.of("/webhook"))`. Risco de tocar codigo compartilhado mitigado por teste novo em lib-shared cobrindo ambos construtores.

---

## Fail-fast nas Properties

| Option | Description | Selected |
|--------|-------------|----------|
| @Validated + Jakarta Bean Validation (@NotBlank) com mensagens em PT-BR | Padrao Spring Boot. Dependencia ja transitiva no monorepo. BindValidationException no boot lista campo + mensagem. | ✓ |
| @PostConstruct manual com IllegalStateException por campo | Mais codigo, stack trace mais ruidoso. Sem ganho funcional. | |
| ApplicationContextInitializer pre-context | Checa env vars antes do contexto subir. Overkill para 5 propriedades. | |

**Selected option:** @Validated + Bean Validation com @NotBlank em PT-BR (D-03)
**Notes:** Mensagens nomeiam a env var (`WHATSAPP_ACCESS_TOKEN nao definida`) para o operador da ERPKit identificar imediatamente qual valor faltou no `service-config-whatsapp.xml` (WinSW). `toString()` override mascara `accessToken/appSecret/verifyToken` para defesa em profundidade contra log acidental.

---

## Escopo do POST /webhook stub

| Option | Description | Selected |
|--------|-------------|----------|
| HMAC + 200 vazio | Phase boundary apertada. Phase 2 substitui pelo flow real (parser → idempotency → @Async). | ✓ |
| HMAC + parse minimo (extrai wamid+telefone) | Testa parser cedo, paga divida tecnica de Phase 2. Mas e implementacao meio-pronta. | |
| HMAC + log do corpo + 200 | Debugging-friendly. Anti-pattern documentado: vaza phone numbers, message content, PII em logs. | |

**Selected option:** Stub minimo HMAC + 200 vazio (D-04)
**Notes:** Sucesso da Phase 1 ja exige integration test com payload portugues `"Ola, gostaria de um orcamento"` — testa HMAC sobre body real do Meta sem precisar parsear o JSON. Adicionar parsing prematuro violaria CLAUDE.md global ("Don't add features beyond what the task requires"). Log do body explicitamente rejeitado por PITFALLS Security Mistakes table.

---

## Claude's Discretion

User delegou todas as 4 areas ao Claude apos ver o menu (resposta: "deixa o claude decidir o melhor"). Decisoes acima sao defaults recomendados baseados em:
- PITFALLS.md (C-02 obriga Filter; C-09/C-11 informam logging strategy; D-04 evita anti-pattern de body log)
- CONVENTIONS.md (PT-BR em mensagens; ausencia de @Transactional explicito)
- Padroes existentes do monorepo (api-consultas espelhado para estrutura, ApiKeyFilter estendido nao reescrito)
- Phase boundary tight (CLAUDE.md: "Don't add features beyond what the task requires")

Decisoes adicionais nao apresentadas como gray areas mas registradas em CONTEXT.md D-05/D-06:
- D-05: Logging cross-cutting alinhado com PITFALLS C-09/C-11 (CommonsRequestLoggingFilter off, accesslog off, actuator env keys-to-sanitize)
- D-06: Migrations em SQL portavel (`BIGINT GENERATED ALWAYS AS IDENTITY` em vez de `BIGSERIAL`) para H2 PostgreSQL-mode rodar limpo em test

## Deferred Ideas

Capturadas em CONTEXT.md `<deferred>`:
- Parser de WebhookPayloadDTO → Phase 2 (WEB-07)
- Idempotency por wamid → Phase 2 (WEB-05/WEB-06)
- Async dispatch → Phase 3 (ROU-01..05)
- Health check com WABA subscription via Graph API → Phase 4 ou 6 (WHATS-17, PITFALLS C-12)
- Mascarar Authorization: Bearer em logs do RestClient → Phase 4 (PITFALLS C-09)
- P95 load test do POST → Phase 6 com WireMock
- Testcontainers para PostgreSQL real em CI → revisitar Phase 6 se H2 PostgreSQL-mode mostrar gap

Nenhuma ideia descartada — tudo foi roteado para a fase apropriada.
