# Phase 5 — Discussion Log

**Mode:** `--auto` (Claude auto-decided todas as gray areas com base no analog `lib-consultas-client` + REQUIREMENTS.md LIB-01..LIB-08)
**Date:** 2026-05-19
**Outcome:** CONTEXT.md em `05-CONTEXT.md` — pronto para `/gsd-plan-phase 5`

## Areas resolvidas (auto-selecionadas)

### Area 1: Estrutura do modulo + naming
- **Q:** Espelhar literalmente `lib-consultas-client` ou variar?
- **Auto-selected:** Mirror literal (D-01) — mesmo layout de pastas, mesmo padrao Java, Resilience4j config identica. Justificativa: alinhamento com analog reduz curva de aprendizado para consumidor.

### Area 2: SPI `WhatsAppCommandHandler` shape
- **Q:** Interface com default method `matches()` vs abstract class vs annotation?
- **Auto-selected:** Interface + default `matches()` (D-02). Justificativa: handler simples implementa 2 metodos; handler com prefix override `matches()`. Sem boilerplate, flexivel (record/POJO).

### Area 3: Routing algorithm em `WhatsAppCommandRegistry`
- **Q:** Single-pass iter vs 2-tier (exact O(1) + fallback) vs priority queue?
- **Auto-selected:** 2-tier (exact O(1) Map + fallback iter) com primeiro-registrado-vence em colisao (D-03). Justificativa: 95% dos casos sao exact-match; fallback cobre prefix matching com O(n) onde n e pequeno.

### Area 4: `WhatsAppRespostaDto` representation
- **Q:** Sealed hierarchy (4 records) vs discriminator-record vs Map<String, Object>?
- **Auto-selected:** Discriminator-record com enum `Tipo` + factory methods (D-04). Justificativa: 1 arquivo vs 4, factory methods sao IDE-friendly, Jackson-safe sem `@JsonTypeInfo`.

### Area 5: `WhatsAppClient` public API surface
- **Q:** Apenas 4 envio metodos vs +1 helper `despachar(WhatsAppRespostaDto)` vs DSL fluent?
- **Auto-selected:** 4 tipados + 1 `despachar` (D-05). Justificativa: ERP que quer controle fino usa Layer 1; Registry usa Layer 2 (`despachar`) sem se preocupar com tipo. Trade-off de 1 metodo extra publico vale.

### Area 6: Test depth em Phase 5
- **Q:** Apenas auto-config smoke vs auto-config + registry vs +WireMock?
- **Auto-selected:** auto-config + registry routing (D-06). Justificativa: WireMock vive em Phase 6 (QA-02) — duplicar aumenta build time sem ganho. Mirror lib-consultas-client.

### Area 7: WhatsAppProperties campos + defaults
- **Q:** Quais campos? Quais defaults?
- **Auto-selected:** `enabled=false`, `url=http://localhost:9193`, `apiKey=null (optional)`, `timeout=PT5S` (D-07). Justificativa: conservative defaults (disabled), localhost loopback (D1 PROJECT on-premise), apiKey opcional v1.

### Area 8: pom.xml groupId/artifactId/version
- **Q:** Standalone version ou herdar do parent?
- **Auto-selected:** `<groupId>br.com.erpkit</groupId>` + `<artifactId>lib-whatsapp-client</artifactId>` + herda version do parent (D-08). Justificativa: monorepo libera release uniforme, mirror consultas.

## Deferred (out of scope)

- WhatsApp client API key auth (v1 nao exige)
- DNS-aware service discovery
- Annotation `@WhatsAppHandler` em vez de SPI
- Async dispatch
- Persistencia local na lib

## Canonical refs identified

11 paths exatos listados em `<canonical_refs>` do CONTEXT.md — researcher e planner precisam ler todos antes de pular para implementacao.

## Claude's discretion (no questions asked)

Todas as 8 areas foram auto-decididas em modo `--auto`. ERP-MUDAS/CALHAS engate fora de escopo (D2 PROJECT.md); Phase 6 cobre WireMock + README + RUNBOOK + SpringDoc.
