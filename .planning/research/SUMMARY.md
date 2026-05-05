# Project Research Summary

**Project:** Modulo WhatsApp — api-whatsapp + lib-whatsapp-client
**Domain:** WhatsApp Cloud API integration (brownfield Spring Boot 3.5.9 / Java 21 monorepo)
**Researched:** 2026-05-05
**Confidence:** HIGH

## Executive Summary

This milestone adds two new modules to the erp-modulos monorepo following the established `api-<dominio>` + `lib-<dominio>-client` pattern: `api-whatsapp` (Spring Boot service, port 9193, Windows Service) and `lib-whatsapp-client` (Spring Boot starter with auto-config and SPI). The integration targets Meta WhatsApp Cloud API v22.0 exclusively — the on-premise API was sunset in October 2025. The foundational architectural principle is **zero Meta cost guaranteed by design**: no template-send API exists in the code (D4), and a hard 24h window block (D5) rejects outbound calls before they reach the Cloud API when the customer service window is closed. The system is reactive-only — the customer always initiates, the ERP only responds.

The recommended implementation approach mirrors `api-consultas` + `lib-consultas-client` in structure, using Spring `RestClient` (no external SDK; the only credible Java community SDK is JitPack-only and stale), JDK-native `javax.crypto.Mac` for HMAC-SHA256 webhook validation, and a DB `UNIQUE(wamid)` constraint for idempotency instead of Redis. The single new test dependency is `wiremock-spring-boot:3.8.1` (not 4.2.1, which has a Jetty classpath conflict with Spring Boot 3.5.9). No net-new production dependency is required — `RestClient` is already in `spring-web`, Jackson 2.x is transitive, and all cryptography uses the JDK.

The two most critical implementation risks are: (1) a TOCTOU race on the 24h window check when `ultima_mensagem_em` is read before the inbound webhook transaction commits — mitigated by using `INSERT ... ON CONFLICT DO NOTHING` with affected-row check before dispatching the ERP callback; and (2) the `ContentCachingRequestWrapper` body-reading trap — the wrapper is lazy and returns an empty byte array in filters, breaking HMAC validation and creating a potential security bypass. Both must be addressed in Phase 1, before any other feature is built.

---

## Key Findings

### Recommended Stack

The monorepo's existing Spring Boot 3.5.9 / Java 21 / Maven / Flyway / PostgreSQL / H2 / Resilience4j / JUnit 5 / WireMock stack is unchanged. The only net-new Maven declaration is a single `test`-scoped dependency: `org.wiremock.integrations:wiremock-spring-boot:3.8.1`. Everything else is already on the classpath.

**Core technologies (net-new):**
- Spring `RestClient` (spring-web, already present): outbound calls to Meta Graph API v22.0 — modern fluent HTTP client replacing deprecated `RestTemplate`; no extra dependency
- Meta Graph API v22.0 (`graph.facebook.com/v22.0/{phoneNumberId}/messages`): single unified endpoint for all 4 message types + media upload; on-premise API dead since Oct 2025
- `javax.crypto.Mac` + `java.security.MessageDigest` (JDK 21): HMAC-SHA256 webhook validation and SHA-256 media cache key — zero additional dependency
- Jackson 2.19.x (Spring Boot BOM, transitive): webhook payload parsing with `@JsonIgnoreProperties(ignoreUnknown = true)` for Meta's evolving schema
- `wiremock-spring-boot:3.8.1` (test scope only): integration tests simulating Cloud API; **do not upgrade to 4.2.1** — Jetty classpath conflict with Boot 3.5.9

**What NOT to use:**
- `Bindambc/whatsapp-business-java-api`: JitPack-only, last release Aug 2024, unknown Boot 3.5 compat
- `RestTemplate`: deprecated in Spring 7.1, removal in Spring 8; do not use for new code
- Any template-send mechanism: architecturally prohibited (cost guarantee)

### Expected Features

The feature set is divided by the zero-cost constraint as primary filter. See `FEATURES.md` for the full list; key groupings below.

**Must have — v1 (table stakes):**
- Webhook GET hub.challenge verification + POST with HMAC `X-Hub-Signature-256` validation
- Idempotency by `wamid` UNIQUE constraint (`ON CONFLICT DO NOTHING` + row-count check)
- Parse inbound: `text`, `interactive/button_reply`, `interactive/list_reply`
- `clientes_zap` table: telefone → cliente_erp FK resolution
- `ultima_mensagem_em` update on every inbound event
- 24h window hard-block before any outbound (409 + structured log)
- Outbound: text, document (PDF + media cache by sha256 TTL 30d), interactive button (max 3), interactive list (max 10 items)
- `mensagens_log` (in + out, with `wamid`)
- Delivery status webhook parsing (update log on sent/delivered/read/failed)
- ERP routing via configurable callback HTTP `POST {erpCallbackUrl}`
- `WhatsAppProperties` fail-fast on boot (all 5 fields mandatory)
- Flyway migrations V1–V4 (`clientes_zap`, `mensagens_log`, `media_cache`, `estado_conversa`)
- `GET /api/whatsapp/status` health endpoint
- SpringDoc OpenAPI (`/swagger-ui.html`, `/v3/api-docs`)

**Should have — v1.x (after first client pilot):**
- Mark-as-read on inbound receipt (blue double-check; same `/messages` endpoint)
- Typing indicator before slow responses
- Structured Meta error code categorization (131047/131049/130429)
- Phone number normalization at ingest (Brazilian 9th-digit DDD rule)
- Message volume metrics endpoint `/api/whatsapp/metricas`

**Defer — v2+:**
- Graceful window-closed notification (emit event to ERP instead of silent 409)
- RUNBOOK automation (auto-provision WABA via Graph API)
- Inbound media passthrough (log media_id without downloading binary)

**NEVER implement (anti-features):**
- `enviarTemplate()` method — any template = Meta billing; method must not exist in `WhatsAppCloudClient`
- Proactive/scheduled outbound
- Mass broadcast / bulk send
- Retry queue outside 24h window
- Multi-tenant routing within single instance
- Conversation state machine in api-whatsapp (state belongs in ERP handlers)

### Architecture Approach

The architecture is a layered call graph (not a pipeline), with a single async boundary: after HMAC validation and idempotency check, the controller returns HTTP 200 to Meta immediately, then dispatches the rest of the processing asynchronously. This is the only design that guarantees the 5-second Meta timeout is never exceeded. All outbound send calls are guarded by `WindowEnforcementService` (reads `ultima_mensagem_em`; throws `JanelaFechadaException` → 409 if window closed), which is the architectural enforcement of zero cost. The `WhatsAppCommandRegistry` SPI in `lib-whatsapp-client` decouples api-whatsapp from ERP business logic — api-whatsapp never contains ERP domain knowledge.

**Major components:**
1. `WebhookController` — GET hub.challenge + POST inbound; acks 200 after HMAC + idempotency; async fan-out via `@Async`
2. `WhatsAppController` — internal ERP endpoints (`enviar-texto/documento/botoes/lista/status`); requires API key; synchronous with 409 propagation
3. `MensagemService` — orchestrator: coordinates HMAC validation, idempotency, persistence, window check, ERP callback, outbound send
4. `HmacValidator` — `CachedBodyHttpServletRequest` (eager byte read) + `MessageDigest.isEqual()` (constant-time); `@Order(HIGHEST_PRECEDENCE)`
5. `IdempotencyService` — `INSERT ... ON CONFLICT (wamid) DO NOTHING`; only dispatch ERP callback if `affectedRows == 1`
6. `WindowEnforcementService` — reads `ultima_mensagem_em`; hard rejects with `JanelaFechadaException` (409) if > 24h
7. `WhatsAppCloudClient` — `RestClient` calls to `graph.facebook.com/v22.0`; no `enviarTemplate()` method
8. `MediaCacheService` — sha256 → media_id with TTL 30d; invalidate and re-upload on expired media_id (error 131053)
9. `ErpCallbackClient` — `POST {erpCallbackUrl}`; timeout 10s; NO retry (ERP may have partially executed)
10. `MessageRouter` — identifies `cliente_erp` by phone; dispatches to `ErpCallbackClient`; routes `ComandoResposta` to correct outbound method
11. `WhatsAppCommandHandler` SPI + `WhatsAppCommandRegistry` (in lib-whatsapp-client) — exact keyword match, then prefix fallback; one bean per command in ERP context

**lib-whatsapp-client mirrors lib-consultas-client exactly** except for the SPI addition (`WhatsAppCommandHandler` + `WhatsAppCommandRegistry`). Same Resilience4j config (10-call window, 50%, 60s open; 3 retries, 1s/2.0x), same `@ConditionalOnProperty`, same `ObjectProvider` graceful fallback pattern.

### Critical Pitfalls

1. **TOCTOU race on 24h window check (C-01 — P0):** `ultima_mensagem_em` may be stale if the check runs before the inbound webhook transaction commits. Mitigation: use `INSERT ... ON CONFLICT DO NOTHING` + affected-row check as the atomic gate; `WindowEnforcementService` reads must be a fresh committed read outside the webhook transaction. Test at ±1s of the 24h boundary with real DB reads, not mocked timestamps.

2. **`ContentCachingRequestWrapper` body-reading trap (C-02 — P0):** Spring's `ContentCachingRequestWrapper` is lazy — calling `getContentAsByteArray()` in a filter before `doFilter()` returns empty bytes. HMAC of empty bytes never matches, causing all webhooks to fail OR the developer adds an empty-byte shortcut that accepts any unauthenticated POST. Mitigation: use a custom `CachedBodyHttpServletRequest extends HttpServletRequestWrapper` that reads all bytes eagerly at construction time. Filter must have `@Order(Ordered.HIGHEST_PRECEDENCE)`.

3. **HMAC timing attack via `String.equals()` (C-03 — P1):** Short-circuit string comparison leaks timing information. Mitigation: always use `MessageDigest.isEqual(byte[], byte[])` — constant-time XOR comparison; never `computedHex.equals(receivedHex)` or `Arrays.equals()`.

4. **Webhook Unicode charset mismatch in HMAC (C-04 — P1):** If body bytes are converted to `String` (JVM default or ISO-8859-1) before HMAC computation, any Portuguese character (`ã`, `ç`, `é`) causes HMAC mismatch for real messages while ASCII-only tests pass. Mitigation: always work with `byte[]` from `CachedBodyHttpServletRequest.getCachedBody()` — never an intermediate `String`.

5. **Synchronous ERP callback causes Meta retry storm (C-05 — P1):** Calling `erpCallbackClient` inside the synchronous webhook handler blocks for up to 10s, exceeding Meta's 5s timeout and triggering retries that flood `mensagens_log` with UNIQUE violations. Mitigation: ack 200 immediately after HMAC + idempotency; publish `MensagemRecebidaEvent` for async processing via `@Async` or `ApplicationEventPublisher`.

**Additional high-priority pitfalls to address per phase:**
- C-06: wamid TOCTOU concurrent delivery — use `ON CONFLICT DO NOTHING` + row-count (Phase 2)
- C-09/C-11: Bearer token + verifyToken in logs — mask `Authorization` header, exclude from actuator (Phase 1+4)
- C-10: hub.challenge returned as JSON instead of plain text — `produces = TEXT_PLAIN_VALUE` (Phase 1)
- C-12: Missing WABA → App subscription ("shadow delivery") — verify `GET /{WABA_ID}/subscribed_apps` in RUNBOOK (Phase 6)
- C-13: Brazilian 9th-digit phone normalization (DDD 11-19/21/22/24/27-28 rule) — normalize at `clientes_zap` INSERT (Phase 2)

---

## Implications for Roadmap

The build order is driven by three hard constraints: (a) HMAC + idempotency must exist before any other feature is testable, (b) persistence must exist before routing, and (c) the Cloud API client must be stable before the lib-client contract can be finalized. Six phases emerge naturally from these dependencies.

### Phase 1: Infraestrutura base + HMAC + webhook skeleton
**Rationale:** Nothing else can be built or safely tested without HMAC validation and the webhook endpoint. This is the security and transport foundation. C-02, C-03, C-04, C-10, C-11 all live here — they must be fixed at construction, not retrofitted.
**Delivers:** Module scaffolding (pom.xml, package structure, Flyway V1–V4 migrations), `WhatsAppProperties` with fail-fast boot validation, `CachedBodyHttpServletRequest` + `HmacValidator`, `WebhookController` GET hub.challenge (plain text, TEXT_PLAIN_VALUE), WebhookController POST stub returning 200, `SecurityConfig` exposing `/webhook` without API key.
**Addresses:** WHATS-01, WHATS-15, WHATS-16 (migrations must exist for Phase 2 entities)
**Avoids:** C-02 (eager body read), C-03 (constant-time comparison), C-04 (raw bytes HMAC), C-10 (plain text challenge), C-11 (verifyToken masking)
**Research flag:** Standard patterns — no additional research needed.

### Phase 2: Persistencia + idempotencia + identificacao de cliente
**Rationale:** Persistence entities and idempotency must be in place before async dispatch is meaningful. The `ON CONFLICT DO NOTHING` + row-count pattern is the atomic gate against duplicate ERP callbacks (C-06). Brazilian phone normalization (C-13) must be applied at INSERT time into `clientes_zap`, not at send time.
**Delivers:** `ClienteZap`, `MensagemLog`, `MediaCache` entities; all three JPA repositories; `IdempotencyService` with `ON CONFLICT DO NOTHING`; `ClienteZapService` (identificarPorTelefone, registrarOuAtualizar, atualizarUltimaMensagem) with Brazilian phone normalization at ingest.
**Addresses:** WHATS-02, WHATS-03, WHATS-04, WHATS-05, WHATS-16 (entities need migrations from Phase 1)
**Avoids:** C-06 (atomic idempotency gate), C-13 (phone normalization at INSERT)
**Research flag:** Standard patterns — no additional research needed.

### Phase 3: Roteamento + callback ERP + async boundary
**Rationale:** The async boundary is the most architecturally sensitive decision in this codebase. Once routing is in place, the single async split point (ack 200 → async fan-out) can be implemented and tested under load with a WireMock ERP stub introducing delays > 5s. This phase locks in the delivery guarantee.
**Delivers:** `ErpCallbackClient` (HTTP POST to `erpCallbackUrl`, 10s timeout, no retry); `MessageRouter` (phone lookup + callback dispatch + `ComandoResposta` routing); `MensagemService.processarAsync()` integrating Phases 1+2+3; async dispatch via `@Async` / `ApplicationEventPublisher`.
**Addresses:** WHATS-06 (ERP callback routing)
**Avoids:** C-05 (async boundary — 200 before ERP callback), C-06 (row-count gate prevents double callback dispatch)
**Research flag:** Standard patterns. The `@Async` thread pool configuration may need tuning if the ERP callback volume is higher than expected, but default Spring Boot executor is sufficient for on-premise single-client load.

### Phase 4: Outbound + media cache + trava 24h + WhatsAppController
**Rationale:** Outbound capabilities depend on the persistence and routing from Phases 2–3. `WindowEnforcementService` must be built alongside `WhatsAppCloudClient` — they are co-dependent (window check gates all Cloud API calls). Media cache (sha256 → media_id TTL 30d) must be built with document send, not after, because sending the same PDF without cache wastes bandwidth and hits rate limits immediately.
**Delivers:** `WhatsAppCloudClient` (enviarTexto, enviarDocumento, enviarBotoes, enviarLista — no `enviarTemplate` method); `MediaCacheService` (sha256 lookup + upload + TTL; expired media_id invalidation + 1× retry); `WindowEnforcementService` (hard 409 + structured log); `WhatsAppController` (internal ERP endpoints with API key); delivery status webhook parsing (update `mensagens_log.status`).
**Addresses:** WHATS-07, WHATS-08, WHATS-09, WHATS-10, WHATS-11, WHATS-12, WHATS-13, WHATS-14, WHATS-17
**Avoids:** C-01 (window check as committed read outside webhook transaction), C-07 (strict `isAfter()` TTL check + expired invalidation), C-09 (Bearer token masked in RestClient logs), C-14 (header auth only, no `?access_token=` query param)
**Research flag:** Standard patterns. `RestClient` configuration for multipart media upload (`/media` endpoint) should be validated against Meta's current `multipart/form-data` field names (`messaging_product=whatsapp`, `type`, `file`) — documented in STACK.md but worth a quick verification test.

### Phase 5: lib-whatsapp-client
**Rationale:** The lib contract (`ComandoRequest`/`ComandoResposta` DTOs, `WhatsAppCommandHandler` SPI, `WhatsAppCommandRegistry`) can only be finalized once the api-whatsapp outbound contracts are stable. Building lib after Phase 4 avoids DTO churn. The Resilience4j configuration and auto-config pattern are identical to `lib-consultas-client` — copy-adapt, do not reinvent.
**Delivers:** `WhatsAppClientAutoConfiguration` (`@ConditionalOnProperty("app.modulos.whatsapp.enabled")`); `WhatsAppProperties` (ERP side: url, apiKey, timeout); `WhatsAppClient` interface + `WhatsAppClientImpl` (Resilience4j CB 10-call/50%/60s + retry 3/1s/2.0x); `WhatsAppCommandHandler` SPI + `WhatsAppCommandRegistry` (exact keyword + prefix fallback); `ComandoRequest`/`ComandoResposta` DTOs; `WhatsAppException`/`WhatsAppIndisponivelException`; `ObjectProvider` graceful fallback; `AutoConfiguration.imports`.
**Addresses:** LIB-01, LIB-02, LIB-03, LIB-04, LIB-05
**Avoids:** Anti-pattern of exposing `WhatsAppCloudClient` directly to ERP (all ERP sends go through api-whatsapp endpoints, not directly to Meta)
**Research flag:** Standard patterns — mirror lib-consultas-client exactly. No new research needed.

### Phase 6: Qualidade — testes + OpenAPI + RUNBOOK
**Rationale:** Tests are best written after the system is stable to avoid test-code coupling to implementation details. WireMock integration tests must cover the full Cloud API surface: all 4 send types, webhook inbound, 5xx, timeout, and error 131047. RUNBOOK.md is operational documentation that prevents the shadow-delivery bug (C-12) and phone normalization issues from becoming production incidents.
**Delivers:** Unit tests for HmacValidator (correct body, modified body, empty body, Portuguese text body), IdempotencyService (duplicate wamid, concurrent delivery), MediaCacheService (cache hit, miss, expired TTL), WindowEnforcementService (at ±1s of 24h boundary with real DB reads); WireMock integration tests for all 4 outbound types + webhook + 5xx + timeout + error 131047; `WhatsAppClientAutoConfigurationTest`; SpringDoc OpenAPI; README.md per module; RUNBOOK.md (WABA setup, System User permanent token, webhook URL config, Cloudflare Tunnel, verify `GET /{WABA_ID}/subscribed_apps`).
**Addresses:** QA-01, QA-02, QA-03, QA-04, QA-05, WHATS-18
**Avoids:** C-12 (RUNBOOK step: verify subscribed_apps before going live)
**Research flag:** `wiremock-spring-boot:3.8.1` vs `4.2.1` Jetty conflict should be verified by attempting `4.2.1` in a test branch first; if no collision, upgrade. Document outcome in STACK.md.

### Phase Ordering Rationale

- **Security first:** HMAC validation (C-02, C-03, C-04) cannot be retrofitted without risking a security hole during the gap. It is the first code written.
- **Persistence before routing:** `MensagemLog.wamid UNIQUE` is the idempotency source of truth (C-06). The ERP callback must not fire until this constraint is the atomic gate.
- **Async boundary at Phase 3 not Phase 1:** Implementing `@Async` dispatch before persistence exists creates a race between the async fan-out and the missing DB rows. Building persistence first makes the async split safe.
- **Window enforcement co-located with outbound:** C-01 (TOCTOU on 24h check) is only testable once both `ultima_mensagem_em` updates (Phase 2) and outbound calls (Phase 4) exist. Building them in the same phase ensures they are tested together.
- **Lib after api stable:** `ComandoResposta.TipoResposta` enum must match the 4 outbound types exactly. Defining the lib contract before Phase 4 confirms the types causes rework.
- **Tests last (within reason):** Unit tests for individual components can be written inline. The full WireMock integration suite requires the complete outbound client to exist.

### Research Flags

Phases needing deeper research during planning:
- **Phase 4 (outbound + media):** Confirm current Meta `multipart/form-data` field names for `/media` upload endpoint — `messaging_product`, `type`, `file` as documented, but Meta updates API without notice. Quick empirical test with WireMock echo before writing production code.

Phases with standard patterns (skip research-phase):
- **Phase 1:** HMAC-SHA256 + `CachedBodyHttpServletRequest` pattern is well-documented in PITFALLS.md. No research needed — just implement the pattern exactly.
- **Phase 2:** Spring Data JPA + Flyway + PostgreSQL UNIQUE constraints are the monorepo baseline.
- **Phase 3:** `@Async` + `ApplicationEventPublisher` are Spring Boot standard. `ErpCallbackClient` mirrors `BrasilApiProvider` pattern from api-consultas.
- **Phase 5:** Direct mirror of `lib-consultas-client`. Zero new patterns.
- **Phase 6:** WireMock + JUnit 5 patterns already used in existing modules.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core RestClient/JDK-crypto/Jackson confirmed from Spring official docs + Boot 3.5 BOM. WireMock version is MEDIUM — 3.8.1 confirmed safe, 4.2.1 Jetty conflict not empirically tested against Boot 3.5.9. |
| Features | HIGH | Based on Meta official docs + PLANO-WHATSAPP.md (verified against Cloud API v22.0). Anti-feature boundaries (no template send) are architectural, not policy. |
| Architecture | HIGH | Based on existing monorepo codebase (lib-consultas-client patterns confirmed by reading actual source files) + Meta webhook documentation + PLANO-WHATSAPP.md decisions D1-D10. |
| Pitfalls | HIGH | C-02/C-03/C-04 (HMAC traps) cross-referenced with Spring Framework official Javadoc + GitHub issue #28391. C-01 (TOCTOU) is a first-principles analysis. C-12 (shadow delivery) sourced from post-mortem community article cross-referenced with Meta subscription API. C-13 (Brazilian 9th digit) sourced from multiple Brazilian WhatsApp API providers. |

**Overall confidence:** HIGH

### Gaps to Address

- **WireMock 4.2.1 Jetty conflict:** Unknown whether Jetty classpath collision actually manifests with Spring Boot 3.5.9 (which uses embedded Tomcat by default in `spring-boot-starter-web`). If the embedded container is Tomcat (not Jetty), the conflict may not occur. Verify by attempting `wiremock-spring-boot:4.2.1` in a test branch; stay on 3.8.1 if any classpath error appears.
- **Meta `/media` upload field names for v22.0:** STACK.md documents `messaging_product=whatsapp` + `type` + `file` fields, but Meta's API changelog should be checked at implementation time. The outbound media upload is the most likely endpoint to have changed between research date and implementation.
- **Temporary dev token lifetime:** PITFALLS.md flags that developers commonly start with the Meta test page's 24h temporary token and accidentally copy it to production. The RUNBOOK.md must include an explicit step to generate a permanent System User token ("Never" expiry) before the QA phase begins.
- **`ultima_mensagem_em` clock source:** PITFALLS.md recommends using DB-side `NOW()` in the UPDATE query to avoid JVM/DB clock drift at the 24h boundary. This must be verified during Phase 2 implementation — a `@Query("UPDATE clientes_zap SET ultima_mensagem_em = NOW() WHERE telefone = :telefone")` native query is safer than passing `Instant.now()` from the application layer.

---

## Sources

### Primary (HIGH confidence)
- Meta for Developers — WhatsApp Cloud API Message API (v22.0): https://developers.facebook.com/documentation/business-messaging/whatsapp/reference/whatsapp-business-phone-number/message-api
- Meta for Developers — Send Messages guide: https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/send-messages
- Meta for Developers — Webhooks Getting Started (X-Hub-Signature-256): https://developers.facebook.com/docs/graph-api/webhooks/getting-started
- Meta for Developers — On-Premises API Sunset (Oct 2025): https://developers.facebook.com/docs/whatsapp/on-premises/sunset
- Meta for Developers — WhatsApp Pricing: https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing
- Meta for Developers — Error Codes: https://developers.facebook.com/documentation/business-messaging/whatsapp/support/error-codes
- Spring.io blog — The state of HTTP clients in Spring (RestTemplate deprecation): https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring/
- Spring Framework — ContentCachingRequestWrapper Javadoc + GitHub issue #28391: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html
- Existing monorepo codebase: `lib-consultas-client/` and `api-consultas/` (Resilience4j + auto-config patterns confirmed by source)
- `C:\projetos\erp-modulos\PLANO-WHATSAPP.md` — decisions D1–D10, model, end-to-end flow
- `.planning/PROJECT.md` — requirements WHATS-01..18 and LIB-01..05
- `.planning/codebase/CONVENTIONS.md` — naming conventions, no explicit `@Transactional`

### Secondary (MEDIUM confidence)
- GitHub — `wiremock/wiremock-spring-boot` releases (3.8.1 confirmed; 4.2.1 Jetty compat unverified): https://github.com/wiremock/wiremock-spring-boot/releases
- Hookdeck — Guide to WhatsApp Webhooks (webhook payload structure, button_reply/list_reply): https://hookdeck.com/webhooks/platforms/guide-to-whatsapp-webhooks-features-and-best-practices
- Medium / Siri Prasad — "Shadow Delivery" mystery (missing WABA subscription): https://medium.com/@siri.prasad/the-shadow-delivery-mystery-why-your-whatsapp-cloud-api-webhooks-silently-fail-and-how-to-fix-2c7383fec59f
- `C:\projetos\ERP-MUDAS\TEMP\zap\integracao-whatsapp-erp.md` (2026-05-01, 467 lines) — domain-specific, written against Cloud API v22.0

### Tertiary (MEDIUM-LOW confidence)
- Zoko + Gupshup — Brazilian 9th-digit number normalization rules: https://www.zoko.io/learning-article/whatsapp-id-brazil-mexico, https://support.gupshup.io/hc/en-us/articles/4407840924953
- Fyno — WhatsApp Rate Limits for Developers: https://www.fyno.io/blog/whatsapp-rate-limits-for-developers-a-guide-to-smooth-sailing-clycvmek2006zuj1oof8uiktv
- YCloud / Chat2Desk — WhatsApp API Pricing changes July 2025 (service window billing model confirmed free): https://www.ycloud.com/blog/whatsapp-api-pricing-update

---

## Must-Remember Items for the Planner

These are cross-cutting decisions that apply to every phase and must be kept in mind throughout planning:

1. **`enviarTemplate()` must not exist.** Not deprecated, not hidden, not private — the method simply does not exist in `WhatsAppCloudClient`. This is the first line of zero-cost defense (D4).

2. **The async boundary is exactly one.** HMAC validation + idempotency check → return 200 → async everything else. Moving more work into the sync path (even for simplicity) risks Meta retry storms.

3. **`CachedBodyHttpServletRequest` is mandatory.** `ContentCachingRequestWrapper` will silently break HMAC or create a security bypass. There is no exception to using the custom eager wrapper.

4. **`MessageDigest.isEqual()` not `.equals()`.** Every HMAC comparison must use constant-time comparison. Code review checklist item.

5. **`ON CONFLICT DO NOTHING` + row-count check is the idempotency gate.** A SELECT-before-INSERT pattern has a TOCTOU window. The DB constraint is the authority; the row-count tells you whether to dispatch the ERP callback.

6. **`WindowEnforcementService` check is a fresh committed read.** Not a cached value, not inside the inbound webhook transaction. A stale read at the 24h boundary can cause a billable send (C-01 is P0).

7. **No retry on ERP callback.** The ERP handler may have partially executed. Retrying re-runs business logic twice (marks an order "viewed" twice, generates two PDFs, etc.).

8. **Bearer token + verifyToken + appSecret are never in logs.** Mask in `RestClient` interceptor; exclude from `/actuator/env`; never log `WhatsAppProperties` fields.

9. **WABA subscription must be explicitly verified.** Meta dashboard showing "Active" is insufficient. `GET /{WABA_ID}/subscribed_apps` must show the app. Document in RUNBOOK.md.

10. **Brazilian phone normalization is at INSERT into `clientes_zap`.** Not at send time. Bidirectional match (13-digit and 14-digit) for lookup.

---

*Research completed: 2026-05-05*
*Ready for roadmap: yes*
