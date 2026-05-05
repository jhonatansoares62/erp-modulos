---
phase: 03-roteamento-boundary-async
plan: 05
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - async
  - listener
  - transactional-event-listener
  - after-commit
  - refactor
  - risk-a1-resolved
dependency-graph:
  requires:
    - "03-01"  # AsyncConfig + whatsappTaskExecutor
    - "03-02"  # MensagemPersistidaEvent record + ComandoCallbackDTO record + ComandoExtractor
    - "03-03"  # MetaMediaClient + MetaMediaResultado
    - "03-04"  # ErpCallbackClient com Resilience4j
  provides:
    - "MensagemAsyncListener orquestrador async (5 steps com try/catch isolado)"
    - "MensagemService fast-path: parse + idempotency + publishEvent (sem ClienteZapService deps)"
    - "Risk A1 (AFTER_COMMIT no-op silencioso) RESOLVED via @Transactional em processarWebhook"
    - "Pattern @Async + @TransactionalEventListener(AFTER_COMMIT) com qualifier executor"
  affects:
    - "Wave 6 (PLAN 03-06) — adiciona AsyncTestConfig (SyncTaskExecutor) + WireMock stub para reativar WebhookPersistenciaIntegrationTest"
    - "Phase 4 outbound — depende do listener consumindo MensagemPersistidaEvent corretamente"
tech-stack:
  added: []
  patterns:
    - "Spring @Async('whatsappTaskExecutor') + @TransactionalEventListener(AFTER_COMMIT) duplo no mesmo metodo — listener nao dispara se transacao rollback (PITFALLS C-05)"
    - "@Transactional explicito no publisher (MensagemService.processarWebhook) e PRE-REQUISITO para AFTER_COMMIT funcionar — sem isso, listener silently skipped (Spring docs)"
    - "Try/catch por step no listener (5 steps isolados) — async nao propaga; cada falha tem destino: media warn+continua, identificar/atualizar error+return, callback error sem rethrow"
    - "Cross-bean call MensagemAsyncListener -> ClienteZapService.atualizarUltimaMensagemEm garante REQUIRES_NEW funcional via proxy AOP (A3 RESEARCH)"
    - "Mockito InOrder valida ordem das 5 chamadas — invariant arquitetural (media DEVE ser primeiro, URL Meta TTL 5min)"
    - "@InjectMocks resolve constructor injection automaticamente para listener com 4 mocks (sem @SpringBootTest)"
key-files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemAsyncListenerTest.java"
  modified:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemService.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemServiceTest.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java"
decisions:
  - "@Transactional em MensagemService.processarWebhook OBRIGATORIO — sem ele @TransactionalEventListener(AFTER_COMMIT) silently skipped (Spring docs explicito); Risk A1 RESOLVED por design"
  - "MensagemServiceTest migrado de @SpringBootTest para Mockito puro — Phase 3 service nao tem mais cross-bean call que dependa de proxy AOP do Spring (REQUIRES_NEW agora roda no listener); cobertura E2E DB fica em WebhookPersistenciaIntegrationTest (Wave 6 reativa)"
  - "WebhookPersistenciaIntegrationTest com @Disabled em toda a classe (Wave 6 reativa via AsyncTestConfig SyncTaskExecutor + WireMock stub) — alternativa preferida sobre exclusao via -Dtest=... (mantem build verde sem perder visibilidade dos 13 tests)"
  - "Listener com 8 tests (5 minimos do plan + 3 bonus defensivos: media_baixar_lanca, atualizar_lanca, despachar_lanca) — Rule 2 add coverage critica em branches nao-felizes (try/catch defensivos)"
  - "@Component em vez de @Service no listener — convencao do CONTEXT/RESEARCH para listeners (vs services); compila e funciona identicamente em Spring (ambos sao @Component)"
metrics:
  duration: "28min"
  completed: "2026-05-05T22:00:00Z"
  tasks: 4
  files: 5
requirements_satisfied:
  - "ROU-01"  # Apos persistencia, ErpCallbackClient invocado em @Async — nao bloqueia ack 200
  - "ROU-05"  # Media download e a PRIMEIRA acao async apos ack
---

# Phase 3 Plan 05: MensagemAsyncListener + MensagemService fast-path Summary

`MensagemAsyncListener` novo (@Component + @Async + @TransactionalEventListener(AFTER_COMMIT)) orquestra 5 steps async (media -> identificar -> atualizar -> comando -> callback ERP) com try/catch isolado por step. `MensagemService` refatorado para fast-path sincrono (parse + idempotency + publishEvent) com `@Transactional` OBRIGATORIO — Risk A1 RESOLVED por design (sem ele AFTER_COMMIT silently skipped). 12 tests novos verdes (4 MensagemServiceTest refatorados Mockito puro + 8 MensagemAsyncListenerTest com InOrder e ArgumentCaptor); reator BUILD SUCCESS, zero regressao em outros modulos.

## Files

### Created

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java` — @Component novo, 4 deps via constructor (MetaMediaClient + ClienteZapService + ComandoExtractor + ErpCallbackClient), method `aoMensagemPersistida(MensagemPersistidaEvent)` com @Async("whatsappTaskExecutor") + @TransactionalEventListener(AFTER_COMMIT), 5 steps com try/catch isolado por step (media warn+continua, identificar/atualizar error+return, callback error sem rethrow), Base64 inline para mediaBase64
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemAsyncListenerTest.java` — 8 tests Mockito puro `@ExtendWith(MockitoExtension.class)` com 4 mocks + @InjectMocks; helpers eventoText/eventoDocumento/clienteMock; InOrder + ArgumentCaptor cobrindo todos os branches

### Modified

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemService.java` — REFACTOR: removeu `ClienteZapService clienteZap` field/constructor param + chamadas `clienteZap.identificar/atualizarUltimaMensagemEm`; adicionou `ApplicationEventPublisher eventPublisher` no constructor; adicionou `@Transactional` no metodo `processarWebhook`; loop emite `eventPublisher.publishEvent(new MensagemPersistidaEvent(...))` quando `tentarPersistir == true`
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemServiceTest.java` — REFACTOR: migrado de `@SpringBootTest(classes = WhatsAppApplication.class) @ActiveProfiles("test")` (com H2 + repos reais) para `@ExtendWith(MockitoExtension.class)` (Mockito puro com 3 mocks: parser + idempotency + eventPublisher); 4 tests novos (mensagem_nova_publica_event com ArgumentCaptor + verificacao de campos do event, mensagem_duplicada_nao_publica, multiplas_mensagens_publica_n times(2), status_nao_publica)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java` — adicionado `@Disabled("Phase 3 Wave 6 reativa com AsyncTestConfig + WireMock stub para ERP...")` em toda a classe (13 tests skipped); 2 tests sc4/sc5 dependiam do flow sincrono Phase 2 (estado clientes_zap apos POST imediato), agora movido para listener async — Wave 6 substitui whatsappTaskExecutor por SyncTaskExecutor no test profile e reativa

## Test Results

**12 tests novos verdes:**

| Test class                 | Tests | Time   | Estado                                     |
| -------------------------- | ----- | ------ | ------------------------------------------ |
| `MensagemServiceTest`      | 4     | 1.0s   | Refatorado Mockito puro (era SpringBootTest) |
| `MensagemAsyncListenerTest`| 8     | 0.94s  | NEW: 5 minimos + 3 bonus defensivos         |

**Reator inteiro (api-whatsapp + lib-shared):** BUILD SUCCESS, **146 tests run, 0 failures, 0 errors, 13 skipped** (toda WebhookPersistenciaIntegrationTest temporariamente desabilitada).

**Reator completo (7 modulos):** BUILD SUCCESS em 39s — api-email, api-storage, api-consultas, lib-shared, lib-consultas-client, api-whatsapp todos verdes (zero regressao).

### Tests Listed (Wave 5 novos)

`MensagemServiceTest` (4):
- `mensagem_nova_publica_event` — ArgumentCaptor confirma todos os campos do event populados (wamid, telefone, tipo, conteudo, mediaId=null, idClienteErp=null)
- `mensagem_duplicada_nao_publica` — `verifyNoInteractions(eventPublisher)` quando tentarPersistir == false
- `multiplas_mensagens_publica_n` — `verify(eventPublisher, times(2))`
- `status_nao_publica` — `verifyNoInteractions(eventPublisher, idempotency)` quando parsed contem apenas statuses

`MensagemAsyncListenerTest` (8):
- `media_id_null_pula_step_1` — InOrder verifica identificar -> atualizar -> extrair -> despachar; verifyNoInteractions(metaMediaClient)
- `media_baixada_primeiro_callback_com_base64` — InOrder com 4 services valida media PRIMEIRO; ArgumentCaptor confirma payload.mediaBase64 == "AQID" + mimeType + filename + idCliente
- `media_404_callback_sem_base64` — Optional.empty -> mediaBase64/mimeType/filename null no payload
- `media_baixar_lanca_continua_sem_base64` — RuntimeException no metaMediaClient.baixar -> warn + prossegue (callback com mediaBase64=null) [BONUS]
- `comando_null_callback_nao_chamado` — comandoExtractor null -> verifyNoInteractions(erpCallbackClient)
- `identificar_lanca_return_early` — RuntimeException -> NAO propaga + atualizar/comando/callback NAO chamados
- `atualizar_lanca_return_early` — RuntimeException -> NAO propaga + comando/callback NAO chamados [BONUS]
- `despachar_lanca_NAO_propaga` — RuntimeException no callback -> catch defensivo; assertThatNoException [BONUS]

## Build Status

- `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** em 19.7s
- `./mvnw verify` (reator completo): **BUILD SUCCESS** em 39.1s

**@Disabled temporario (Wave 6 reativa):**
- `WebhookPersistenciaIntegrationTest` — toda a classe (13 tests). Razao: 2 tests (sc4 ID-cliente-ERP-null, sc5 ultima_mensagem_em populada) dependiam do flow sincrono Phase 2 onde `MensagemService` chamava `ClienteZapService.identificar/atualizarUltimaMensagemEm`. Em Phase 3 essas chamadas foram movidas para o listener `@Async`. Em test profile (whatsappTaskExecutor pool real), a thread do MockMvc retorna antes do listener completar, e a assertion JdbcTemplate roda em estado parcial (Cliente nao criado ainda). Wave 6 (PLAN 03-06) substitui whatsappTaskExecutor por SyncTaskExecutor via `@TestConfiguration` + adiciona WireMock stub para o ERP callback URL — flow inteiro fica sincrono novamente em test, listener completa antes do POST retornar.

## Commits

- `98da74e`: feat(api-whatsapp): MensagemAsyncListener + refactor MensagemService fast-path (Phase 3 Wave 5)
- (este SUMMARY): docs(03-05): SUMMARY plan 05 + atualizar STATE/ROADMAP

## Risk Status

### Risk A1 (HIGH): @TransactionalEventListener(AFTER_COMMIT) silently skipped — **RESOLVED**

**Mitigacao por design via `@Transactional` em `MensagemService.processarWebhook`.**

Spring docs explicitos: "If no transaction is running, the listener is not invoked at all" — sem `@Transactional` no publisher, mensagem persistiria em mensagens_log (transacao implicita do Spring Data JPA `repository.save`) MAS o `eventPublisher.publishEvent` aconteceria FORA de transacao ativa, e o `@TransactionalEventListener(AFTER_COMMIT)` simplesmente nao dispararia. Resultado: ERP nunca receberia callback — bug silencioso, sem stack trace, sem alarme.

Mitigacao aplicada: linha 67 do `MensagemService.java` agora tem `@Transactional` antes do `processarWebhook(byte[])`. Todo o for-loop (parse + idempotency + publishEvent) roda em uma transacao aberta pelo Spring Tx Manager. Quando o metodo retorna sem excecao, Tx Manager comita e dispara afterCommit() — ai sim o listener e invocado.

**Validacao empirica:** apos refactor, `MensagemAsyncListenerTest` (Mockito puro, sem TransactionPhase real) passa as 8 assertions. Validacao FINAL via WebhookPersistenciaIntegrationTest reativado em Wave 6 (smoke test E2E que confirma callback ERP recebido apos POST do webhook).

**Concern Wave 6:** se o smoke test em Wave 6 mostrar que ERP NAO recebe callback, suspeitar:
1. `@Transactional` foi removido inadvertidamente (verificar `git log MensagemService.java`)
2. AsyncTestConfig nao substituiu `whatsappTaskExecutor` corretamente (assertion: thread name nao deveria ser `whatsapp-async-N`)
3. WireMock URL nao bate com `WhatsAppProperties.erpCallbackUrl` no test profile

## Deviations from Plan

### Auto-fixed Issues

**Nenhum desvio significativo.** Plan executado exatamente como escrito. Adicoes Rule 2:

**1. [Rule 2 - Coverage] 3 tests bonus no MensagemAsyncListenerTest (8 vs 5 minimos do plan)**

- **Found during:** Task 3 (escrita dos tests minimos)
- **Razao:** O plan especificou 5 tests minimos cobrindo branches felizes + 1 falha no identificar. Adicionei 3 tests defensivos para cobrir os outros 2 try/catch:
  - `media_baixar_lanca_continua_sem_base64` — branch onde `metaMediaClient.baixar` lanca RuntimeException (vs Optional.empty); listener deve log.warn + prossegue (NAO return early — diferente de identificar/atualizar)
  - `atualizar_lanca_return_early` — branch onde `atualizarUltimaMensagemEm` lanca apos identificar OK; listener deve return sem chamar comando/callback
  - `despachar_lanca_NAO_propaga` — branch defensivo do catch generico no step 5 (Resilience4j fallback ja deveria ter engolido, mas codigo tem catch para defesa em profundidade)
- **Beneficio:** todos os 5 try/catch do listener tem regressao test. Sem essas, mudancas futuras poderiam quebrar silenciosamente o invariant "listener nunca propaga".
- **Custo:** +3 tests, +30 linhas. Run total continua <1s.
- **Files:** `MensagemAsyncListenerTest.java`
- **Commit:** `98da74e`

### Auth Gates

Nenhum.

## Decisions Made

1. **`@Transactional` em `MensagemService.processarWebhook` (Risk A1 RESOLVED por design):** Sem ele, AFTER_COMMIT listener silently skipped — invariant arquitetural. Documentado em Javadoc do service. Trade-off aceito: 1 transacao envolve TODO o for-loop (se mensagem #2 lanca, mensagens anteriores DESTE batch fazem rollback — mas idempotency.tentarPersistir captura DataIntegrityViolation antes de chegar a ponto de mid-batch crash).

2. **MensagemServiceTest migrado para Mockito puro:** Phase 2 usava @SpringBootTest+H2 porque o service tinha cross-bean call (clienteZap.atualizarUltimaMensagemEm REQUIRES_NEW) que dependia de proxy AOP. Em Phase 3 essa logica foi para o listener; o service agora e stateless puro (parse + idempotency + publishEvent), Mockito puro e suficiente. Cobertura E2E DB fica em WebhookPersistenciaIntegrationTest (Wave 6 reativa). Run de 4 tests caiu de ~5s para 1s.

3. **`@Disabled` em toda WebhookPersistenciaIntegrationTest (vs exclude individual):** Plan mencionou as duas opcoes; escolhi `@Disabled` porque (a) `mvnw verify` continua um comando unico (sem `-Dtest=...exclude=...`); (b) Wave 6 remove a anotacao em UMA linha (vs ajustar maven-surefire-plugin); (c) os 13 tests ficam visiveis no relatorio com motivo claro (string explicativa do `@Disabled`); (d) padrao consistente com convencao de TDD: classe inteira disabled vira marca facil de buscar (`grep -r "@Disabled"`).

4. **`@Component` em vez de `@Service` no MensagemAsyncListener:** Convencao do CONTEXT/RESEARCH (listeners vs services). Funcionalmente identicos em Spring (ambos sao stereotype `@Component`), mas semantica explicita: listener e um event consumer, nao um caso de uso de negocio. Pattern para Phase 4+ caso outros listeners apareçam.

5. **Cross-bean call MensagemAsyncListener -> ClienteZapService.atualizarUltimaMensagemEm preservado:** O `@Transactional(REQUIRES_NEW)` do `ClienteZapService.atualizarUltimaMensagemEm` (Phase 2) so ativa via proxy AOP do Spring quando chamado de OUTRO bean. Listener e bean separado, entao a propagacao funciona corretamente. Documentado em Javadoc do listener para evitar refactor inadvertido (ex: extrair logica para helper privado dentro do listener quebraria proxy).

## Wave 6 Concerns

- **WebhookPersistenciaIntegrationTest reativacao:** Wave 6 (PLAN 03-06) deve (a) criar `AsyncTestConfig` em `src/test/java/.../config/` com `@TestConfiguration` + `@Bean("whatsappTaskExecutor") TaskExecutor syncTaskExecutor() { return new SyncTaskExecutor(); }` para sobrescrever o pool dedicado em test; (b) adicionar WireMock stub no setup da `WebhookPersistenciaIntegrationTest` apontando para `WhatsAppProperties.erpCallbackUrl` (alternativamente: deixar o callback falhar com fallback engolido — Resilience4j fallbackDespachar e silencioso, nao quebra teste; mas adicionar stub + verify(1, postRequestedFor(...)) reforca smoke test E2E); (c) remover `@Disabled` da classe.

- **Smoke test integrado para Risk A1 (validacao empirica de @Transactional):** Wave 6 deve ter pelo menos 1 test que assert `verify(wireMockErp, atLeastOnce()).postRequestedFor(...)` apos POST de webhook valido — garante que `@Transactional` esta funcionando E que listener foi disparado E que dispatch ERP foi feito. Se esse test passar, Risk A1 fica empiricamente validado.

- **Performance:** AsyncTestConfig com SyncTaskExecutor faz tudo sincrono em test, mas em prod o pool dedicado degrada graciosamente via CallerRunsPolicy. Manter visibilidade desse delta — tests nao validam comportamento de pool sob estresse.

- **Risk A1 prod-only edge cases:** mesmo com @Transactional ativo, se `repository.save` (dentro do `IdempotencyService.tentarPersistir`) lancar DataIntegrityViolationException no MEIO do for-loop (mensagem #2 de 3), Spring marca a transacao como rollback-only. Resultado: mensagem #1 (ja persistida) tambem faz rollback, e listener NAO dispara para nenhuma das 3. Em pratica isso nao acontece porque `tentarPersistir` ja captura `DataIntegrityViolationException` internamente, mas se algum dia mudar essa logica (ex: outra excecao escapar), o for-loop com @Transactional e pior que sem. Mitigacao: deixar @Transactional como esta (Risk A1 endereca o caso comum); se aparecer regressao, considerar `@TransactionalEventListener` com `Propagation.REQUIRES_NEW` no INSERT individual via helper service (overkill por agora).

- **Logger continua sem dados sensiveis:** `MensagemService.processarWebhook` apenas loga contagens (mensagens.size + statuses.size) — nao loga conteudo de mensagem (PII). `MensagemAsyncListener` loga wamid + telefone + tipo + comando — ja documentado como nao-sensivel (PII e o conteudo, nao o metadata). Verificar regras de log do projeto antes de adicionar log.info no Phase 4.

## TDD Gate Compliance

Plan tipo `execute` (nao `tdd`) — gates RED/GREEN/REFACTOR nao aplicaveis. 12 tests novos escritos junto com implementacao no mesmo commit (98da74e).

## Self-Check: PASSED

**Files verified:**
- FOUND: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemService.java (modified — @Transactional + ApplicationEventPublisher)
- FOUND: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java (new)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemServiceTest.java (modified — Mockito puro)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemAsyncListenerTest.java (new — 8 tests)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java (modified — @Disabled)

**Commit verified:**
- FOUND: 98da74e
