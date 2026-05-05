# Phase 2: Persistencia + Idempotencia - Discussion Log

> **Audit trail only.** Decisions captured in CONTEXT.md.

**Date:** 2026-05-05
**Phase:** 2-Persistencia + Idempotencia
**Mode:** `--auto` (user delegated end-to-end execution; no interactive Q&A)
**Areas analyzed (autonomously):** Entity conventions, idempotency strategy, phone normalization location, REQUIRES_NEW propagation, parser structure, statuses persistence, controller wiring (sync vs async)

---

## Claude's Discretion (auto-decided)

User said "sim faça a fase 2 sem interromper do inicio ao fim" — delegated all gray-area decisions and the entire pipeline to Claude.

| Area | Decision | Rationale |
|------|----------|-----------|
| Entity definitions | `@Entity` + `@Table(schema=whatsapp)` + `Instant` for timestamps + no Lombok | Aligns with monorepo CONVENTIONS.md; Instant is timezone-anchored |
| Idempotency | Native `INSERT ... ON CONFLICT (wamid) DO NOTHING` + row-count gate | PITFALLS C-06 explicit; SELECT-then-INSERT has TOCTOU race |
| Phone normalization | Pure utility `TelefoneBR.normalizar(String)` storing normalized form | Simpler than bidirectional matching; UNIQUE constraint works naturally |
| `ultima_mensagem_em` update | `@Transactional(REQUIRES_NEW)` + native `UPDATE ... NOW()` | PITFALLS C-01 + DB clock authority |
| Parser | Jackson DTO hierarchy with `tipo=desconhecido` for unknown types | WEB-07 explicit |
| Statuses persistence | NOT persisted in Phase 2 | Out-of-scope for milestone (related to outbound, Phase 4) |
| Controller wiring | Synchronous in Phase 2; Phase 3 refactors to @Async | Tight phase boundary; SC-1 testable observably |
| Auto-create cliente | yes, with `id_cliente_erp = null` + race protection | PER-06; race via DataIntegrityViolationException catch |

## Deferred Ideas (auto-routed to future phases)

- @Async boundary → Phase 3
- ErpCallbackClient + MessageRouter → Phase 3
- Eager media download → Phase 3
- MediaCacheService logic → Phase 4 (entity created in Phase 2)
- WindowEnforcementService → Phase 4
- WhatsAppCloudClient → Phase 4
- Persistencia de statuses Meta → backlog/optional
- Reconciliation job para id_cliente_erp=null → fora da milestone
