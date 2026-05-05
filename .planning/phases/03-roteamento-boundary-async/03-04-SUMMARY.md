---
phase: 03-roteamento-boundary-async
plan: 04
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - resilience4j
  - circuit-breaker
  - retry
  - rest-client
  - wiremock
  - aop-validation
  - risk-a6-resolved
dependency-graph:
  requires:
    - "03-01"  # spring-boot-starter-aop + resilience4j-spring-boot3 + WireMock 3.10.0
    - "03-02"  # ComandoCallbackDTO record (Wave 2)
    - "03-03"  # padrao WireMock + @DynamicPropertySource (validado empiricamente)
  provides:
    - "ErpCallbackClient @Service com @CircuitBreaker + @Retry"
    - "Risk A6 (AOP no-op) RESOLVED empiricamente"
    - "Risk A3 (CB shared state) mitigado via @BeforeEach reset"
    - "Pattern Resilience4j fallbackMethod-on-outer-aspect documentado"
  affects:
    - "Wave 5 (MensagemAsyncListener) — usa ErpCallbackClient.despachar como ultima acao"
tech-stack:
  added: []
  patterns:
    - "Resilience4j Spring AOP: fallbackMethod no aspect OUTER (Retry), nao no INNER (CircuitBreaker)"
    - "WireMock scenario state para retry counter assertions (gate empirico de AOP)"
    - "@BeforeEach CircuitBreaker::reset para isolacao cross-test em Singleton bean"
    - "RestClient + SimpleClientHttpRequestFactory(timeout) per-instance (vs spring.http.client global)"
    - "ResourceAccessException explicito em retry-exceptions (wrapper Spring RestClient sobre SocketTimeoutException)"
key-files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ErpCallbackClientTest.java"
  modified:
    - "api-whatsapp/src/test/resources/application-test.yml"
    - "api-whatsapp/src/main/resources/application.yml"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesHappyPathTest.java"
decisions:
  - "fallbackMethod localizado em @Retry (outer aspect), NAO em @CircuitBreaker (inner) — caso contrario fallback inner converte excecao em sucesso e Retry outer NAO retenta (descoberto empiricamente: counter==1 em vez de 3)"
  - "ResourceAccessException adicionada em retry-exceptions (prod + test) — RestClient.toBodilessEntity() empacota SocketTimeoutException nesse wrapper; sem entry, timeouts NAO retentariam"
  - "callbackTimeout 5s -> 500ms apenas em application-test.yml (necessario para timeout test); WhatsAppPropertiesHappyPathTest assertion ajustada para refletir test profile"
metrics:
  duration: "17min"
  completed: "2026-05-05T21:37:00Z"
  tasks: 3
  files: 5
requirements_satisfied:
  - "ROU-02"  # POST {erpCallbackUrl}/api/modulos/whatsapp/comando com payload ComandoCallbackDTO
  - "ROU-03"  # Resilience4j circuit breaker (10/50%/60s) + retry exponencial (3 tentativas)
  - "ROU-04"  # Timeout 5s + log error sem trava do webhook (ja respondeu 200)
---

# Phase 3 Plan 04: ErpCallbackClient com Resilience4j Summary

ErpCallbackClient @Service novo despacha comando ao ERP via RestClient.post com `@CircuitBreaker(name="erp-callback")` + `@Retry(name="erp-callback", fallbackMethod="fallbackDespachar")` resolvidos via Spring AOP — Risk A6 RESOLVED empiricamente: test `cinquecentos_recupera_counter_3` confirma counter == 3 ao WireMock (sem AOP seria 1).

## Files

### Created

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java` — service @Service com 2 annotations Resilience4j + fallbackDespachar privado (log.error sem rethrow)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ErpCallbackClientTest.java` — 6 tests `@SpringBootTest(classes = WhatsAppApplication.class) @ActiveProfiles("test")` + WireMock 3.10.0 standalone + dynamicPort + @DynamicPropertySource + @BeforeEach reset

### Modified

- `api-whatsapp/src/test/resources/application-test.yml` — callbackTimeout 5s -> 500ms + ResourceAccessException em retry-exceptions
- `api-whatsapp/src/main/resources/application.yml` — ResourceAccessException em retry-exceptions (paridade com test, comportamento prod-correto)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesHappyPathTest.java` — assertion 5s -> 500ms (test profile alinhado com nova config)

## Test Results

**6 tests novos verdes (1.5s test class run):**

| Test                                       | Counter Esperado | Counter Observado | Estado                                 |
| ------------------------------------------ | ---------------- | ----------------- | -------------------------------------- |
| `happy_path_counter_1`                     | 1                | 1                 | sem retry, sem fallback                |
| **`cinquecentos_recupera_counter_3`**      | **3**            | **3**             | **AOP funcionando (Risk A6 RESOLVED)** |
| `cinquecentos_persistente_fallback_log`    | 3                | 3                 | fallback engole, sem rethrow           |
| `quatrocentos_no_retry_counter_1`          | 1                | 1                 | 4xx NAO retenta (whitelist)            |
| `timeout_retry_e_fallback`                 | > 1              | 3                 | ResourceAccessException retenta        |
| `circuit_open_apos_falhas_repetidas`       | 12 + 0          | 12 + 0           | OPEN apos 4 dispatches; 5o = fallback IMEDIATO sem nova request |

**Reator inteiro (api-whatsapp + lib-shared):** BUILD SUCCESS, **138 tests verdes** (132 anteriores + 6 novos), zero regressao em Phase 1+2 ou Wave 1-3.

**Reator completo (7 modulos):** BUILD SUCCESS — api-email, api-storage, api-consultas, lib-shared, lib-consultas-client, api-whatsapp todos verdes.

## Build Status

- `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** em 19s
- `./mvnw verify` (reator completo): **BUILD SUCCESS** em 34s

## Commits

- `f09b3e0`: feat(api-whatsapp): ErpCallbackClient com @CircuitBreaker + @Retry Resilience4j (Phase 3 Wave 4)

## Risk Status

### Risk A6 (CRITICAL): AOP no-op silencioso — **RESOLVED**

**Mitigacao validada empiricamente.** Test `cinquecentos_recupera_counter_3` configura WireMock scenario state (500 -> 500 -> 200) e assert `verify(3, postRequestedFor(...))`. Counter == 3 PROVA que `@Retry` esta sendo interceptado por `RetryAspect` do Spring AOP — sem `spring-boot-starter-aop` no classpath (ja garantido em Wave 1), counter seria 1.

Empiricamente:
- `aspectjweaver:1.9.25.1` no classpath (`mvnw dependency:tree`)
- `resilience4j-spring-boot3:2.2.0` no classpath
- spring-boot-starter-aop:3.5.9 dep compile EXPLICITA em api-whatsapp/pom.xml
- 6/6 counter assertions deram match exato com expectativa

### Risk A3: CircuitBreaker shared state cross-test — Mitigado

`@BeforeEach`: `cbRegistry.find("erp-callback").ifPresent(CircuitBreaker::reset)` — antes de cada test, CB volta ao estado CLOSED + sliding-window zerado. Verificado empiricamente: `circuit_open_apos_falhas_repetidas` deixa CB em OPEN; teste subsequente comeca em CLOSED.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] fallbackMethod localizado em @Retry (outer) em vez de @CircuitBreaker (inner)**

- **Found during:** Task 2 (primeira execucao dos tests)
- **Issue:** Plan ditou `@CircuitBreaker(name="erp-callback", fallbackMethod="fallbackDespachar") @Retry(name="erp-callback")`. Resultado empirico: counter == 1 em TODOS os tests com 5xx (esperado: 3 em retry-recupera/persistente). Fallback estava sendo invocado apos 1a tentativa sem retry. Causa-raiz: aspect order do Resilience4j Spring Boot starter — Retry order = LOWEST_PRECEDENCE-3 (outer), CircuitBreaker order = LOWEST_PRECEDENCE-2 (inner). Quando `fallbackMethod` esta no INNER (CircuitBreaker), o fallback inner CONVERTE a excecao em retorno void de sucesso ANTES da outer (Retry) ver o erro. Outer Retry recebe "sucesso" e nao retenta.
- **Fix:** Mover `fallbackMethod` para `@Retry` (annotation OUTER):
  ```java
  @CircuitBreaker(name = "erp-callback")
  @Retry(name = "erp-callback", fallbackMethod = "fallbackDespachar")
  public void despachar(ComandoCallbackDTO payload) { ... }
  ```
  Assim: Retry executa todas as tentativas; SO ENTAO chama fallback se todas falharem. CircuitBreaker inner continua contabilizando cada attempt no sliding-window (sem swallow).
- **Files modified:** `ErpCallbackClient.java` (annotations + Javadoc explicando o gotcha)
- **Validacao:** apos fix, `cinquecentos_recupera_counter_3` passou (counter == 3); 5/6 testes verdes na 2a execucao.
- **Commit:** f09b3e0

**2. [Rule 1 - Bug] ResourceAccessException nao listada em retry-exceptions — timeouts nao retentavam**

- **Found during:** Task 2 (segunda execucao dos tests apos fix #1)
- **Issue:** Test `timeout_retry_e_fallback` falhou com counter == 1 mesmo com `@Retry` agora ativo. Plan listou `java.net.SocketTimeoutException` em retry-exceptions, mas Spring `RestClient.retrieve().toBodilessEntity()` empacota `SocketTimeoutException` em `org.springframework.web.client.ResourceAccessException("Could not retrieve response status code: ...")`. Resilience4j compara via `instanceof` na excecao throwable — ResourceAccessException NAO e SocketTimeoutException nem IOException, entao nao matcheava a whitelist.
- **Fix:** Adicionar `org.springframework.web.client.ResourceAccessException` em `retry-exceptions` em ambos `application.yml` (prod) e `application-test.yml` (test). Justificativa: este e o real wrapper que RestClient produz em qualquer I/O error transient (timeout, connection refused, network down) — em prod tambem precisa retentar.
- **Files modified:** `application.yml`, `application-test.yml`
- **Validacao:** apos fix, `timeout_retry_e_fallback` passou (counter == 3 — 3 retries todos timing out); 6/6 testes verdes.
- **Commit:** f09b3e0

**3. [Rule 1 - Bug] Regressao em WhatsAppPropertiesHappyPathTest apos callbackTimeout 5s -> 500ms**

- **Found during:** Task 3 (reator full build apos fix #2)
- **Issue:** Plan disse "Phase 1+2 tests nao chamam callback ERP — sem regressao esperada", mas `WhatsAppPropertiesHappyPathTest.boot_com_todas_as_5_propriedades_passa` (Phase 1) hardcoded `assertThat(properties.getCallbackTimeout()).isEqualTo(Duration.ofSeconds(5))` para validar bind YAML happy-path. Como `application-test.yml` agora tem 500ms, esse assertion quebrou (`expected: 5S but was: 0.5S`).
- **Fix:** Atualizar assertion para `Duration.ofMillis(500)` + Javadoc explicando que test profile diverge do default prod (5s) por causa do timeout test de Wave 4. Default em PRODUCAO continua 5s no `WhatsAppProperties.callbackTimeout`.
- **Files modified:** `WhatsAppPropertiesHappyPathTest.java`
- **Validacao:** test passa; 138/138 reator inteiro verde.
- **Commit:** f09b3e0

### Auth Gates

Nenhum.

## Decisions Made

1. **fallbackMethod no @Retry (outer aspect), NAO no @CircuitBreaker (inner aspect)**: regra geral em Resilience4j Spring AOP quando ambas annotations coexistem na mesma method. Sem este detalhe, AOP funciona mas comportamento parece "no-op" para Retry — armadilha sutil. Documentado em Javadoc do ErpCallbackClient com explicacao do aspect order para futuros leitores.

2. **ResourceAccessException explicita em retry-exceptions (prod + test)**: descoberto empiricamente que RestClient.toBodilessEntity() converte SocketTimeoutException nesse wrapper. Manter SocketTimeoutException + IOException + HttpServerErrorException tambem para defesa em profundidade (caso configuracao do client mude no futuro).

3. **callbackTimeout 500ms no test profile, 5s default prod**: justificavel para suportar `timeout_retry_e_fallback` em <5s total wall-time. `WhatsAppPropertiesHappyPathTest` agora valida o test-profile value (sem perda de coverage — `WhatsAppProperties.callbackTimeout = Duration.ofSeconds(5)` no field initializer continua sendo o default-prod imutavel).

4. **Aspect order documentado em Javadoc**: futuros ajustes na class podem inadvertidamente mover fallbackMethod entre annotations sem perceber. Comentario explica o gotcha + a evidencia empirica (counter == 1 vs 3) para que ninguem desfaca o fix.

## Wave 5 Concerns

- **MensagemAsyncListener (PLAN 03-05) deve injetar `ErpCallbackClient` via constructor** e chamar `erpCallbackClient.despachar(payload)` como ultima acao do listener (apos extrair comando + persistir mensagem + opcionalmente baixar media). NAO precisa try/catch — fallback do despachar engole excecao via log.error.
- **Listener tambem precisa @Async + thread pool da Wave 1**: ack-first ja foi feito pelo controller; listener roda em background thread. Combinacao @Async + dispatch via despachar = webhook nao bloqueia mesmo com retry de 1s/2s/4s + fallback.
- **Idempotency do listener**: se Meta reenviar webhook, idempotency.tentarPersistir ja vai dedupar via wamid UNIQUE — listener nao roda 2x para mesmo wamid (Wave 5 deve confirmar empiricamente).
- **Verificar logs de fallback em prod**: como fallback e pure log.error, falha de callback ERP fica silenciosa para o controle de fluxo (correto — D-08), mas precisa monitoramento (Phase 6 pode adicionar metric counter `whatsapp_callback_failures_total`).

## TDD Gate Compliance

Plan tipo `execute` (nao `tdd`) — gates RED/GREEN/REFACTOR nao aplicaveis. 6 tests escritos junto com implementacao no mesmo commit (f09b3e0).

## Self-Check: PASSED

**Files verified:**
- FOUND: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ErpCallbackClientTest.java
- FOUND: api-whatsapp/src/test/resources/application-test.yml (modified)
- FOUND: api-whatsapp/src/main/resources/application.yml (modified)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesHappyPathTest.java (modified)

**Commit verified:**
- FOUND: f09b3e0
