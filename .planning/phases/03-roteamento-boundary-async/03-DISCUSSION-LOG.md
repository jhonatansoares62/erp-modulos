# Phase 3: Roteamento + Boundary Async - Discussion Log

> **Audit trail only.** Decisoes em CONTEXT.md.

**Date:** 2026-05-05
**Phase:** 3-Roteamento + Boundary Async
**Mode:** `--auto` (user delegated end-to-end execution)

## Claude's Discretion (auto-decided)

| Area | Decision | Rationale |
|------|----------|-----------|
| Async pattern | `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` | PITFALLS C-05; event dispara apos commit garante consistency |
| Thread pool | ThreadPoolTaskExecutor dedicado (`whatsappTaskExecutor`, core=2, max=10, queue=100, CallerRunsPolicy) | OOM risk em SimpleAsyncTaskExecutor; degrada graciosamente em pico |
| Resilience4j | `@CircuitBreaker(erp-callback)` + `@Retry(erp-callback)` annotations | Espelha lib-consultas-client; AOP-driven |
| Retry policy | maxAttempts=3, exp backoff 1s/2s/4s, retry-exceptions = 5xx/timeout/IOException | Per ROADMAP SC-2 + ROU-03 (4xx nunca retry) |
| Callback fallback | Apenas log error, NAO retry adicional | ROU-03 — ERP pode ter executado parcialmente; duplicate avoidance |
| Media download | Primeira acao do listener; 404 logado WARN; sem retry | PITFALLS C-08 (5min expiry); RestClient simples sem CB |
| Media transport | byte[] base64 no JSON do callback | Self-contained; evita acoplamento filesystem com ERP |
| Comando extraction | text → primeira palavra; interactive → id; document/image → tipo literal | Sem NLP (fora do scope per PROJECT.md) |
| Bearer auth | Header `Authorization: Bearer ...`, NUNCA query param | PITFALLS C-09, C-14 |
| Test executor | Override pool com SyncTaskExecutor em test profile | Elimina flake de timing em integration tests |
| MensagemService.processarWebhook | Refatorar para fast-path: parse + idempotency + persist + dispatch event; cliente identification + atualizar timestamp movem para listener | Fast path < 100ms garante SC-1 do ROADMAP |
| Phase 2 tests | Refatorar `MensagemServiceTest` para mockar `ApplicationEventPublisher`; integration tests usam SyncTaskExecutor para assertions DB sem flake | Trade-off vs Awaitility |

## Deferred Ideas
- WhatsAppCloudClient + Window24h → Phase 4
- MediaCacheService outbound → Phase 4
- lib-whatsapp-client → Phase 5
- DLQ para callback failures → backlog
- Metrics Micrometer → Phase 6
