---
phase: 03-roteamento-boundary-async
plan: 01
subsystem: api
tags: [api-whatsapp, infra, resilience4j, aop, async-config, wiremock-setup, spring-boot-starter, threadpool, http-client-timeout]

requires:
  - phase: 02-persistencia-idempotencia
    provides: WhatsAppProperties (5 secrets fail-fast + callbackTimeout)
provides:
  - resilience4j-spring-boot3 2.2.0 no classpath (compile)
  - spring-boot-starter-aop no classpath (Risk A6 mitigado)
  - wiremock-standalone 3.10.0 no classpath (test, Wave 3+4+6)
  - AsyncConfig.whatsappTaskExecutor (ThreadPoolTaskExecutor: corePool=2 maxPool=10 queue=100 prefix=whatsapp-async- CallerRunsPolicy)
  - @EnableAsync centralizado em AsyncConfig (sem ser na WhatsAppApplication)
  - WhatsAppProperties.metaApiBaseUrl (default https://graph.facebook.com/v22.0, sem @NotBlank, override em test profile)
  - application.yml resilience4j.{circuitbreaker,retry}.instances.erp-callback (10/50%/60s + 3x/1s/2.0x backoff)
  - application.yml spring.http.client.{connect-timeout=5s,read-timeout=10s}
  - application-test.yml com timeouts curtos (200ms/500ms) + resilience4j wait-duration=50ms para tests rapidos
affects:
  - phase 03-roteamento-boundary-async/02 (WindowEnforcementService — sem dep direta, mas Wave 1 desbloqueia)
  - phase 03-roteamento-boundary-async/03 (MetaMediaClient — usa metaApiBaseUrl + WireMock)
  - phase 03-roteamento-boundary-async/04 (ErpCallbackClient — usa @CircuitBreaker(name="erp-callback") + @Retry(name="erp-callback"))
  - phase 03-roteamento-boundary-async/05 (MensagemAsyncListener — usa @Async("whatsappTaskExecutor"))
  - phase 03-roteamento-boundary-async/06 (Wave E2E — usa todas as 4 acima)

tech-stack:
  added:
    - "io.github.resilience4j:resilience4j-spring-boot3 2.2.0 (compile)"
    - "org.springframework.boot:spring-boot-starter-aop 3.5.9 (compile, Risk A6)"
    - "org.wiremock:wiremock-standalone 3.10.0 (test scope)"
  patterns:
    - "Spring Boot AOP starter como pre-requisito declarativo para Resilience4j @CircuitBreaker/@Retry funcionarem (sem AOP, viram no-op silencioso)"
    - "ThreadPoolTaskExecutor com queueCapacity finita + CallerRunsPolicy: degradacao graciosa em pico (executa inline na thread chamadora) em vez de OOM (SimpleAsyncTaskExecutor) ou rejeicao (AbortPolicy)"
    - "Resilience4j retry-exceptions com whitelist explicita (HttpServerErrorException + SocketTimeoutException + IOException) — 4xx categoricos NUNCA retentam (default behavior do Resilience4j: excecoes nao listadas nao retentam)"
    - "spring.http.client.{connect-timeout,read-timeout} como defesa em profundidade global para RestClient — MetaMediaClient nao precisa de Resilience4j proprio"
    - "WhatsAppProperties.metaApiBaseUrl com default valido (sem @NotBlank) + env var override (WHATSAPP_META_API_BASE_URL) + test profile placeholder (WireMock URL via @DynamicPropertySource em Wave 3)"

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/AsyncConfigSmokeTest.java
    - .planning/phases/03-roteamento-boundary-async/03-01-SUMMARY.md
  modified:
    - pom.xml
    - api-whatsapp/pom.xml
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java
    - api-whatsapp/src/main/resources/application.yml
    - api-whatsapp/src/test/resources/application-test.yml

key-decisions:
  - "spring-boot-starter-aop adicionado explicitamente como dep compile — sem ele, anotacoes Resilience4j (@CircuitBreaker, @Retry) viram no-op silencioso e Wave 4 falharia em runtime sem mensagem clara (Risk A6 do RESEARCH §Pitfall 2)"
  - "ThreadPoolTaskExecutor parametrizado com corePool=2/maxPool=10/queueCapacity=100/CallerRunsPolicy: D-02 do CONTEXT — pool dedicado degrada graciosamente em pico vs SimpleAsyncTaskExecutor (thread por task → OOM)"
  - "CallerRunsPolicy escolhido em vez de AbortPolicy — sob estresse extremo, preferir executar listener inline na thread do webhook (latencia maior) em vez de descartar mensagem (perda de side effect ja persistido)"
  - "spring.http.client integrado dentro do bloco 'spring:' existente em application.yml (nao como dotted-keys nem como --- multi-document) — coexiste com spring.datasource/jpa/flyway sem conflito de chave YAML"
  - "metaApiBaseUrl SEM @NotBlank (default valido https://graph.facebook.com/v22.0) — diferente dos 5 secrets que precisam de fail-fast pois nao tem valor default seguro"
  - "WireMock 3.10.0 standalone (Jetty 12 shadow) escolhido per RESEARCH — versao 4.x tem potencial conflito com Boot 3.5.9 (flag de risco no STATE.md); 3.10.0 valida empiricamente em Wave 3"
  - "Resilience4j retry-exceptions explicitamente lista 3 transient (HttpServerErrorException + SocketTimeoutException + IOException) — Resilience4j default NAO retenta excecoes nao listadas, portanto HttpClientErrorException (4xx) automaticamente NAO retenta sem precisar configurar ignoreExceptions (D-08, ROU-03)"

patterns-established:
  - "Pattern 'AOP gate': qualquer dep que use @aspect-based annotation (Resilience4j, @Async, @Transactional, @Cacheable) precisa de spring-boot-starter-aop OU spring-boot-starter (que ja o transitively inclui via spring-context). api-whatsapp tinha spring-boot-starter-web/jpa/validation que NAO incluem AOP — daquela razao add explicito"
  - "Pattern 'pool dedicado por subsystem': cada @Async listener deve referenciar Bean executor por nome (@Async(\"whatsappTaskExecutor\")) — evita compartilhar pool global SimpleAsyncTaskExecutor que cria thread por task"
  - "Pattern 'config-property com default valido': para configuracao opcional (override em tests, default funcional em prod), usar campo com valor default + getter/setter SEM @NotBlank — diferente dos secrets que precisam de fail-fast"
  - "Pattern 'YAML aninhado em vez de dotted-keys': quando ja existe bloco 'spring:' aninhado, ADICIONAR sub-chaves dentro dele (spring.http.client → spring: → http: → client:); evita confusao de SnakeYAML e mantem YAML idiomatico"

requirements-completed:
  - ROU-03
  - ROU-04

duration: ~12min
completed: 2026-05-05
---

# Phase 03 Plan 01: Setup Resilience4j + AOP + AsyncConfig Summary

**Wave 1 da Phase 3 — fundacao infra para todas as proximas waves: 3 deps Maven (resilience4j-spring-boot3 2.2.0 compile + spring-boot-starter-aop 3.5.9 compile + wiremock-standalone 3.10.0 test), AsyncConfig com ThreadPoolTaskExecutor dedicado (corePool=2/maxPool=10/queue=100/CallerRunsPolicy), application.yml com bloco resilience4j.{circuitbreaker,retry}.instances.erp-callback (D-03: 10/50%/60s + 3x/1s/2.0x) + spring.http.client (5s/10s), WhatsAppProperties.metaApiBaseUrl (default v22.0, override em tests). Sem mudanca funcional — fundacao.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-05T17:22:00Z
- **Completed:** 2026-05-05T17:35:00Z
- **Tasks:** 3 (Task 1 dependencias Maven + Task 2 codigo+yaml+smoke test + Task 3 build/SUMMARY/commits)
- **Files changed:** 7 (2 created + 5 modified, exclui SUMMARY)

## Accomplishments

- **Parent `pom.xml`** ganha 1 entry no `<dependencyManagement>`: `resilience4j-spring-boot3` versao `${resilience4j.version}` (2.2.0, mesmo do circuitbreaker/retry ja gerenciados pela lib-consultas-client)
- **`api-whatsapp/pom.xml`** ganha 3 dependencias: `resilience4j-spring-boot3` (compile), `spring-boot-starter-aop` (compile, **CRITICO** — Risk A6), `wiremock-standalone:3.10.0` (test, Jetty 12 shadow para Boot 3.5.9)
- **`AsyncConfig.java` novo** — `@Configuration @EnableAsync` com bean `whatsappTaskExecutor` (ThreadPoolTaskExecutor: corePool=2 / maxPool=10 / queueCapacity=100 / threadNamePrefix=`whatsapp-async-` / `CallerRunsPolicy`) per D-02 do CONTEXT
- **`WhatsAppProperties.java`** ganha campo `metaApiBaseUrl` (String, default `https://graph.facebook.com/v22.0`, **sem `@NotBlank`** — default valido) + getter/setter; `toString()` atualizado para incluir o campo (nao mascara — nao e secret)
- **`application.yml`** ganha 3 blocos:
  - `app.modulos.whatsapp.metaApiBaseUrl` com env var override (`${WHATSAPP_META_API_BASE_URL:https://graph.facebook.com/v22.0}`)
  - `spring.http.client` (`connect-timeout=5s`, `read-timeout=10s`) — defesa em profundidade global para MetaMediaClient
  - `resilience4j.circuitbreaker.instances.erp-callback` (10/50%/60s/3 half-open) + `resilience4j.retry.instances.erp-callback` (3 attempts/1s wait/exp backoff 2.0x/whitelist 3 transient exceptions)
- **`application-test.yml`** ganha overrides para tests rapidos:
  - `metaApiBaseUrl: http://localhost:0/test` (placeholder, substituido por WireMock URL via `@DynamicPropertySource` em Wave 3)
  - `spring.http.client.{connect-timeout=200ms,read-timeout=500ms}` (timeouts curtos)
  - `resilience4j.retry.instances.erp-callback.wait-duration=50ms` + `circuitbreaker.wait-duration-in-open-state=1s` (janelas curtas)
- **`AsyncConfigSmokeTest.java` novo** — `@SpringBootTest @ActiveProfiles("test")` injeta `@Qualifier("whatsappTaskExecutor")` e asserta `corePool=2`, `maxPool=10`, `queueCapacity=100`, `threadNamePrefix="whatsapp-async-"`
- **Reator `mvnw verify -pl api-whatsapp -am`:** **BUILD SUCCESS**, **113 tests verdes (112 Phase 1+2 + 1 novo smoke), 0 falhas, 0 erros, zero regressao**

## Decisions Made

- **D1 — `spring-boot-starter-aop` como dep compile explicita:** Risk A6 do RESEARCH §"Pitfall 2" — sem AOP starter, anotacoes Resilience4j sao parsadas pelo classloader mas NUNCA executadas em runtime. Nao ha mensagem de erro: `@CircuitBreaker(name="erp-callback")` simplesmente vira no-op. Wave 4 tem teste explicito de counter (3 retries em 5xx) que detectaria — mas seguranca de sair errado em prod e maior. AOP starter adiciona `aspectjweaver:1.9.25.1` ao classpath (verificado via `mvn dependency:tree`).

- **D2 — ThreadPoolTaskExecutor parametros:** `corePool=2 / maxPool=10 / queueCapacity=100 / CallerRunsPolicy` per D-02 do CONTEXT. Justificativa por parametro:
  - `corePool=2`: conservador para on-premise (servidores ERP de cliente sao tipicamente 4-8 cores; modulo WhatsApp e 1 de varios componentes do ERP)
  - `maxPool=10`: cap em pico, evita saturacao da JVM
  - `queueCapacity=100`: buffer para spikes; cada task e webhook de mensagem (~ms de processamento), 100 tasks ~= burst de ~10s
  - `CallerRunsPolicy`: degradacao graciosa — se queue cheia E maxPool atingido, listener roda na thread do webhook (latencia maior, mas nao perde mensagem). Alternativas rejeitadas: `AbortPolicy` (descarta mensagem ja persistida → side effect perdido); `DiscardOldestPolicy` (descarta task antiga, possivelmente ja parcialmente processada).

- **D3 — Resilience4j config alinhada com lib-consultas-client:** D-03 do CONTEXT — `slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=60s`, `permittedNumberOfCallsInHalfOpenState=3`. Retry: `maxAttempts=3` (3 tentativas total, nao 3 retries adicionais), `waitDuration=1s` (base do exponential backoff: 1s/2s/4s totalizando ~7s para a thread), `enableExponentialBackoff=true`, `exponentialBackoffMultiplier=2.0`. **retry-exceptions whitelist explicita:** `HttpServerErrorException` (5xx) + `SocketTimeoutException` + `IOException`. **HttpClientErrorException (4xx) NAO esta na whitelist** — Resilience4j default NAO retenta excecoes nao listadas, entao 4xx categoricos automaticamente NAO retentam sem precisar configurar `ignoreExceptions` (D-08, ROU-03). 4xx indicam bug de configuracao no ERP, nao falha temporaria — retry duplicaria side effect (PITFALLS C-05).

- **D4 — `metaApiBaseUrl` SEM @NotBlank:** Diferente dos 5 secrets do CFG-01..04 (que precisam de fail-fast pois nao tem default seguro), `metaApiBaseUrl` tem default valido `https://graph.facebook.com/v22.0` que funciona em prod. Override por env var `WHATSAPP_META_API_BASE_URL` (operador pode trocar para staging Meta) ou via `@DynamicPropertySource` em test (WireMock URL aleatoria). Adicionar `@NotBlank` aqui criaria ruido sem ganho de seguranca.

- **D5 — `spring.http.client` integrado em bloco aninhado:** Em vez de dotted-keys (`"spring.http.client.connect-timeout": 5s`) ou multi-document YAML (`---` separador), o bloco `http: client:` foi inserido dentro do `spring:` aninhado existente em `application.yml` (apos `flyway:`). Isso evita conflito de chave duplicada `spring:` no mesmo documento e mantem YAML idiomatico/legivel. Mesmo padrao em `application-test.yml`.

- **D6 — WireMock 3.10.0 (nao 4.x):** Per RESEARCH e flag de risco no STATE.md — WireMock 4.x tem potencial conflito de Jetty com Spring Boot 3.5.9. WireMock 3.10.0 usa Jetty 12 standalone shadow (jar shadowed sem expor classpath publico) que evita conflito. Validacao empirica formal acontece em Wave 3 quando primeiro test integration usar WireMockExtension.

## Risks & Mitigations

- **Risk A6 (RESEARCH §Pitfall 2): annotations Resilience4j viram no-op sem AOP starter.**
  - Mitigacao: `spring-boot-starter-aop` adicionado como dep compile EXPLICITA (nao via transitive); validacao via `mvn dependency:tree` confirma `aspectjweaver:1.9.25.1` no classpath. Wave 4 tera teste explicito (counter assertion: 3 retries em 5xx) que detectaria regressao se starter for removido acidentalmente.
- **Risk: pool corePool=2 pode subdimensionar em ambiente high-traffic.**
  - Mitigacao: `maxPool=10` permite scale-up dinamico ate 10 threads em pico; `CallerRunsPolicy` degrada graciosamente alem disso. Phase 6 (observabilidade) pode adicionar metricas via Micrometer (`task.executor.active`, `task.executor.queue.size`) para tunning empirico em prod.
- **Risk: WireMock 3.10.0 nao validada empiricamente — gap ate Wave 3.**
  - Mitigacao: dep adicionada com `<scope>test</scope>` — falha na Wave 3 nao afeta build de prod. Se Jetty conflict aparecer, fallback e usar `@AutoConfigureWireMock` do Spring Cloud Contract OU rodar WireMock como processo separado (Testcontainers).
- **Risk: `spring.http.client.read-timeout=10s` pode ser baixo demais para upload de media grande (Wave 3 MetaMediaClient).**
  - Mitigacao: RestClient permite override per-instance via builder (`requestFactory.setReadTimeout(...)`); Wave 3 pode customizar se necessario. 10s e default seguro para a maioria dos casos.

## TDD Gate Compliance

Plan type: `execute` (nao TDD por default — sem RED/GREEN/REFACTOR ciclo declarado). Smoke test criado junto com producao em Task 2 (sem RED gate explicito), mas verifica fail-fast: bean ausente ou parametros incorretos quebram o test. Aceitavel para infra plan onde nao ha behavior funcional novo.

## Self-Check: PASSED

- File `pom.xml` modificado: FOUND (entry `resilience4j-spring-boot3` em `<dependencyManagement>`)
- File `api-whatsapp/pom.xml` modificado: FOUND (3 deps adicionadas)
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java` criado: FOUND
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java` modificado: FOUND (campo `metaApiBaseUrl` + getter/setter + toString update)
- File `api-whatsapp/src/main/resources/application.yml` modificado: FOUND (blocos `spring.http.client`, `resilience4j`, `metaApiBaseUrl`)
- File `api-whatsapp/src/test/resources/application-test.yml` modificado: FOUND (overrides timeouts + resilience4j)
- File `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/AsyncConfigSmokeTest.java` criado: FOUND
- Build `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** com 113 tests verdes (zero regressao)
- Dependency tree confirma 3 artefatos: `resilience4j-spring-boot3:2.2.0:compile`, `spring-boot-starter-aop:3.5.9:compile` (com `aspectjweaver:1.9.25.1`), `wiremock-standalone:3.10.0:test`

## Concerns para Wave 2 (PLAN 03-02)

- **WindowEnforcementService dep:** Wave 2 nao depende diretamente desta wave (sem `@CircuitBreaker` nem `@Async`), mas reusa `WhatsAppProperties` (deve continuar carregando com 6 campos agora — incluindo `metaApiBaseUrl`).
- **Aspect order:** Resilience4j Spring Boot starter aplica Retry POR FORA de CircuitBreaker. Wave 4 (ErpCallbackClient) precisa entender que 1 dispatch falho = 3 calls counted no CB sliding-window (3 retries). 4 dispatches falhos consecutivos = 12 calls counted = circuit aberto. Importante para plan 03-04.
- **`@DynamicPropertySource` em Wave 3:** MetaMediaClient test deve sobrescrever `app.modulos.whatsapp.metaApiBaseUrl` com URL do WireMock instance (porta aleatoria). Plan 03-03 deve documentar pattern.
- **Test override SyncTaskExecutor:** Wave 5/6 (E2E tests) podem precisar substituir `whatsappTaskExecutor` por `SyncTaskExecutor` via `@TestConfiguration` para evitar Awaitility/CountDownLatch. Javadoc de AsyncConfig ja documenta isso.

## References

- CONTEXT.md §D-02 (pool dedicado), §D-03 (Resilience4j config alinhada com lib-consultas-client), §D-08 (4xx nao retentam)
- RESEARCH.md §"Resilience4j setup completo" (Code Example 8 — pom + yaml + AsyncConfig), §"AsyncConfig" (Code Example 1), §"Pitfall 2" (Risk A6 — AOP starter), §"WireMock" (3.10.0 vs 4.x)
- ROADMAP §Phase 3 §ROU-03 §ROU-04
- 02-04-SUMMARY.md (template de SUMMARY frontmatter usado aqui)
