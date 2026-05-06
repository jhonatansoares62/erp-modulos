---
phase: 04-outbound-trava-24h-whatsappcontroller
plan: 03
subsystem: api-whatsapp
tags: [whatsapp, outbound, media-cache, sha256, race-protection, ttl-30d, tdd]
requirements:
  - OUT-08
dependency-graph:
  requires:
    - "Phase 1 V3__criar_tabela_media_cache.sql (whatsapp.media_cache schema)"
    - "Phase 2 MediaCache entity + MediaCacheRepository.findByArquivoHashAndExpiraEmAfter"
    - "Phase 2 IdempotencyService pattern (save+catch DataIntegrityViolationException)"
  provides:
    - "MediaCacheService.buscarMediaId(byte[]) → Optional<String>"
    - "MediaCacheService.registrarUpload(byte[], String) → void"
    - "Constante TTL = Duration.ofDays(30)"
  affects:
    - "Plan 04-04 (WhatsAppCloudClient.enviarDocumento) podera importar MediaCacheService"
tech-stack:
  added: []
  patterns:
    - "Save+catch DataIntegrityViolationException (3a aplicacao consecutiva — IdempotencyService → ClienteZapService.identificar → MediaCacheService.registrarUpload)"
    - "sha256 hex via MessageDigest + HexFormat.of().formatHex (Java 17+ standard)"
    - "TTL estrito sem sliding (D-04): hit nao estende expira_em — turnover natural via reupload"
    - "Upsert manual (findById + delete + save) — evita @Transactional + @Modifying na repo"
key-files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java"
  modified: []
decisions:
  - "TTL estrito 30d (D-04 reafirmado): hit NAO estende expira_em — Meta documenta media_id valido por ate 30 dias; sliding TTL mascararia expiracao real do Meta levando a 4xx surpresa"
  - "Upsert manual via findById+delete+save (em vez de @Modifying repo method): mantem repository limpo (Phase 2 surface), atomico do ponto de vista da thread; race entre threads silenciada pelo catch DataIntegrityViolationException"
  - "Race scenario test usa CountDownLatch start gate + AtomicInteger erros + COUNT(*) assertion — pattern alinhado com IdempotencyServiceTest.concorrencia_2_threads_mesmo_wamid"
  - "sha256 hex via HexFormat.of().formatHex (JDK 17+ built-in) — substitui patterns legacy (BigInteger.toString(16) com leading zero bug, Apache Commons Codec dependency externa)"
metrics:
  completed: 2026-05-05
  tasks: 1
  files: 2
  tests-added: 4
  reactor-tests-after: 156
  reactor-tests-before: 152
---

# Phase 04 Plan 03: MediaCacheService Summary

**One-liner:** MediaCacheService cacheia `media_id` do Meta por sha256(bytes) com TTL estrito 30 dias e race protection via UNIQUE PK gate + catch DataIntegrityViolationException — pattern Phase 2 reaproveitado pela 3a vez consecutiva.

## What Was Built

### MediaCacheService (`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java`)

API publica:
- `Optional<String> buscarMediaId(byte[] bytes)` — calcula sha256 hex, consulta `findByArquivoHashAndExpiraEmAfter(hash, Instant.now())`, retorna `Optional<String>` com `media_id` se hit dentro do TTL, `empty` caso contrario (miss ou expirado).
- `void registrarUpload(byte[] bytes, String mediaId)` — upsert simples (`findById` + `delete` + `save`) com `expira_em = now() + 30d`. Race em concurrent reupload do mesmo arquivo silenciado via try/catch `DataIntegrityViolationException` (UNIQUE PK `arquivo_hash` e o gate atomico).

Constantes:
- `private static final Duration TTL = Duration.ofDays(30)` — D-04 (TTL estrito sem sliding).

Implementacao interna:
- `sha256Hex(byte[])` privado e estatico — `MessageDigest.getInstance("SHA-256").digest(bytes)` + `HexFormat.of().formatHex(digest)`. NoSuchAlgorithmException convertido para IllegalStateException (impossivel em prod — SHA-256 e built-in JDK desde Java 1.4).

Logging:
- `log.debug` em registrarUpload (sucesso ou race silenciado) — apenas `hash` + `mediaId` + `expira` (NUNCA `bytes={}` por T-04-03-01: bytes em log e information disclosure).

### MediaCacheServiceTest (`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java`)

`@SpringBootTest(classes = WhatsAppApplication.class)` + `@ActiveProfiles("test")` — usa H2 in-memory PG-mode com Flyway aplicando V1..V4. `@AfterEach` limpa `whatsapp.media_cache` para isolar tests.

4 cenarios verdes:
1. `hit_dentro_do_ttl_retorna_media_id` — `registrarUpload(bytes, "meta-id-abc-123")` + `buscarMediaId(bytes)` retorna `Optional.of("meta-id-abc-123")`.
2. `miss_quando_hash_nao_existe_retorna_empty` — `buscarMediaId("sem-cache".getBytes())` retorna `Optional.empty()` sem nada gravado.
3. `miss_quando_expirado_retorna_empty` — INSERT direto via `JdbcTemplate` com `expira_em = now() - 1s`. `buscarMediaId(bytes)` retorna `empty` apesar de existir row no DB. **Valida TTL estrito** (D-04) — `findByArquivoHashAndExpiraEmAfter` exclui entradas com `expira_em <= now()`.
4. `race_em_registrar_silencia_data_integrity_violation` — `ExecutorService(2)` + `CountDownLatch start` gate + `CountDownLatch done`. Ambas threads chamam `registrarUpload(bytes, "meta-id-race-<tid>")`. Apos completion: `COUNT(*) == 1` (UNIQUE PK gate atomico) E `erros.get() == 0` (DataIntegrityViolationException silenciada — nenhuma propagada). **Valida pattern Phase 2.**

## Test Results

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.401 s -- in br.com.erpkit.whatsapp.service.MediaCacheServiceTest
```

Reactor inteiro `./mvnw verify`:
```
[INFO] Tests run: 156, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Phase 1-3 mantidos verdes (152 → 156 = +4 novos).

## TDD Cycle

| Gate | Commit | Description |
|------|--------|-------------|
| RED | `4ce288e` | `test(04-03): add failing test for MediaCacheService (4 cenarios OUT-08)` — compilation failure ("cannot find symbol: MediaCacheService") confirmou ausencia |
| GREEN | `bd6a92f` | `feat(04-03): implement MediaCacheService (OUT-08 + D-04 TTL estrito 30d)` — 4 tests verdes <1s, reator BUILD SUCCESS |
| REFACTOR | (n/a) | Codigo limpo from start; JavaDoc thorough; no duplication; pattern alinhado com IdempotencyService |

## Acceptance Criteria

- [x] `MediaCacheService.buscarMediaId(byte[]) → Optional<String>` retorna media_id se expira_em > now(), empty caso contrario
- [x] `MediaCacheService.registrarUpload(byte[], String) → void` upsert com expira_em = now + 30d, race silenciado
- [x] `Duration TTL = Duration.ofDays(30)` constante (D-04 sem sliding)
- [x] sha256 calculado via `MessageDigest.getInstance("SHA-256").digest(bytes)` + `HexFormat.of().formatHex` (Java 17+ standard)
- [x] try/catch `DataIntegrityViolationException` silencia race em concurrent registrarUpload (Phase 2 pattern)
- [x] `MediaCacheServiceTest` 4 cenarios verdes (hit, miss inexistente, miss expirado, race com COUNT==1)
- [x] `./mvnw -pl api-whatsapp test -Dtest='MediaCacheServiceTest'` exits 0
- [x] Phase 1-3 tests existentes verdes (152 tests inalterados)
- [x] Reator inteiro `./mvnw verify` BUILD SUCCESS

Grep verifications:
- `grep -c 'Duration TTL = Duration.ofDays(30)'` → 1 ✓
- `grep -c 'HexFormat.of().formatHex'` → 2 (>= 1) ✓
- `grep -c 'DataIntegrityViolationException'` → 3 (>= 1; import + JavaDoc + catch) ✓
- `grep -c 'findByArquivoHashAndExpiraEmAfter'` → 1 ✓
- `grep -c '@DisplayName'` → 4 ✓ (4 tests)
- `grep -c 'CountDownLatch'` → 3 (>= 1; import + start gate + done latch) ✓

## Threat Mitigations Applied

| Threat | Mitigation |
|--------|-----------|
| T-04-03-01 (I — bytes em log) | `log.debug` apenas com `hash`, `mediaId`, `expira` — nunca bytes |
| T-04-03-02 (T — race em concurrent reupload) | UNIQUE PK `arquivo_hash` + try/catch `DataIntegrityViolationException` (pattern Phase 2) |
| T-04-03-04 (A — banco enchendo) | TTL estrito 30d sem sliding (D-04) — turnover natural |
| T-04-03-06 (C-07 — TTL boundary off-by-one) | `findByArquivoHashAndExpiraEmAfter` (>) + test `miss_quando_expirado` valida `now()-1s` |

Aceites (sem mitigacao adicional):
- T-04-03-03 (sha256 collision): trade-off computacionalmente impraticavel
- T-04-03-05 (PII em RAM): byte[] vive escopo do metodo, GC garante limpeza

## Deviations from Plan

None — plan executado exatamente como escrito. Cada constante, pattern, scenario, JavaDoc e import seguiu o action block. Nenhum bug auto-fixed (Rule 1), nenhuma funcionalidade critica adicionada (Rule 2), nenhuma issue blocking (Rule 3), nenhuma decisao arquitetural necessaria (Rule 4).

## Notes for Plan 04-04

`MediaCacheService` ja esta como `@Service` Spring component pronto para `@Autowired` em `WhatsAppCloudClient`. Caminho de uso:

```java
public String enviarDocumento(byte[] bytes, ...) {
    return mediaCacheService.buscarMediaId(bytes)
        .orElseGet(() -> {
            String mediaId = uploadMediaToMeta(bytes, ...); // chamada Cloud API
            mediaCacheService.registrarUpload(bytes, mediaId);
            return mediaId;
        });
}
```

Rationale: hit no cache evita upload + Meta rate limit 131053 (`invalid media id` em re-uploads identicos). Miss faz upload novo + cacheia para os proximos 30 dias.

## Self-Check: PASSED

Files created (verificacao):
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java` ✓ FOUND
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java` ✓ FOUND

Commits:
- `4ce288e` (RED) ✓ FOUND
- `bd6a92f` (GREEN) ✓ FOUND

TDD gate sequence: test commit → feat commit → ✓ canonical RED→GREEN order in `git log`.
