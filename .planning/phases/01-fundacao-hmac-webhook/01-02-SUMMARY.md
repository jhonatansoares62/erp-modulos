---
phase: 01-fundacao-hmac-webhook
plan: 02
subsystem: api-whatsapp
tags: [api-whatsapp, skeleton, maven, bootstrap, spring-boot]

# Dependency graph
requires:
  - phase: 00-bootstrap
    provides: pom.xml raiz com dependencyManagement (springdoc, resilience4j) e parent Spring Boot 3.5.9
  - phase: 01-fundacao-hmac-webhook
    plan: 01
    provides: lib-shared/ApiKeyFilter 2-arg constructor (consumido em PLAN-06, nao aqui)
provides:
  - api-whatsapp/ registrado como 6o modulo Maven do reator
  - api-whatsapp/pom.xml com deps minimas (lib-shared, web, validation, data-jpa, postgresql, flyway, springdoc, h2 test, spring-boot-starter-test)
  - WhatsAppApplication.java (Spring Boot main com scanBasePackages = br.com.erpkit)
  - application.yml minimo com autoconfigure.exclude TEMPORARIO de DataSource/JPA/Flyway
  - Estrutura de diretorios placeholder (controller/service/web/config/db.migration/test) para PLAN-03..07
affects:
  - 01-fundacao-hmac-webhook (waves 3-7 podem agora rodar mvnw -pl api-whatsapp sem erro de modulo nao encontrado)
  - PLAN-03 (Properties) — adiciona @EnableConfigurationProperties em WhatsAppApplication
  - PLAN-04 (Migrations + H2) — REMOVE autoconfigure.exclude e adiciona spring.datasource/jpa/flyway no yml
  - PLAN-06 (SecurityConfig) — registra ApiKeyFilter com /webhook como path adicional usando 2-arg constructor de PLAN-01

# Tech tracking
tech-stack:
  added:
    - "api-whatsapp module (Spring Boot 3.5.9 standalone app, porta 9193)"
  patterns:
    - "Module pom espelhado em api-email/api-consultas: parent erp-modulos, deps sem version (BOM gerencia)"
    - "Bootstrap incremental: yml com autoconfigure.exclude temporario quando classpath tem JPA mas datasource ainda nao foi configurado (revertido em PLAN-04)"
    - ".gitkeep placeholder em sub-diretorios vazios para preservar estrutura de pacote no git ate PLANs subsequentes preencherem"

key-files:
  created:
    - api-whatsapp/pom.xml
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java
    - api-whatsapp/src/main/resources/application.yml
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/.gitkeep
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/.gitkeep
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/.gitkeep
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/.gitkeep
    - api-whatsapp/src/main/resources/db/migration/.gitkeep
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/.gitkeep
    - api-whatsapp/src/test/resources/.gitkeep
    - .planning/phases/01-fundacao-hmac-webhook/01-02-SUMMARY.md
  modified:
    - pom.xml  # raiz — adicionado <module>api-whatsapp</module> apos api-consultas

key-decisions:
  - "Espelhar api-email/pom.xml (e nao api-consultas/pom.xml) como base, mas SEM mail/thymeleaf — pega o conjunto Web+Validation+Data-JPA+Flyway+Postgres+H2-test que e o padrao de api modulo com persistencia (api-consultas nao tem persistencia)"
  - "autoconfigure.exclude de DataSource/JPA/Flyway em application.yml e TEMPORARIO desta wave — comentario inline no yml marca explicitamente como removido em PLAN-04. Alternativa rejeitada: adicionar H2 + datasource ja em PLAN-02. Razao: cria acoplamento prematuro com migrations que so existem em PLAN-04, e o plan explicitamente segrega isso (per Wave 4)"
  - "WhatsAppApplication SEM @EnableConfigurationProperties em PLAN-02 — WhatsAppProperties.class so existe a partir de PLAN-03. Adicao premature quebraria compile (referencia a classe inexistente)"
  - "Versao do springdoc herdada do dependencyManagement raiz (2.8.15 via property), nao re-declarada em api-whatsapp/pom.xml — alinhamento com api-email/api-consultas"

patterns-established:
  - "Esqueleto Maven incremental: PLAN-02 registra modulo + aplicacao vazia, PLANs subsequentes preenchem cada camada (Properties, migrations, validators, filters, controllers, tests). Permite ./mvnw verify verde em cada wave"
  - "autoconfigure.exclude como bridging mechanism: classpath ja tem dep, mas config completa entra em wave futura. Comentario inline com referencia ao PLAN que reverte"

requirements-completed:
  - CFG-04  # parcial — porta default 9193 estabelecida via SERVER_PORT env (PLAN-03 expande com 5 secrets WhatsApp)

# Metrics
duration: ~4min
completed: 2026-05-05
---

# Phase 01 Plan 02: api-whatsapp Bootstrap (Maven Skeleton) Summary

**Modulo `api-whatsapp` registrado como 6o no reator do monorepo com pom.xml espelhado em api-email (sem mail/thymeleaf, sem Resilience4j), WhatsAppApplication minimo com `scanBasePackages = "br.com.erpkit"`, e application.yml minimo com `autoconfigure.exclude` temporario de DataSource/JPA/Flyway. Reator inteiro (7 modulos) em BUILD SUCCESS, pronto para PLAN-03..07 preencherem Properties, migrations, HMAC validator, filter, controller e tests.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-05T06:27:07Z
- **Completed:** 2026-05-05T06:30:45Z
- **Tasks:** 5 (Task 1 root pom + Task 2 module pom + Task 3 Application/dirs + Task 4 yml + Task 5 reactor verify)
- **Files created:** 11 (pom + Application + yml + 7 .gitkeeps + SUMMARY)
- **Files modified:** 1 (pom.xml raiz)
- **Tests:** 0 novos (api-whatsapp ainda sem testes; PLAN-03 adiciona o primeiro). Demais modulos: 78 tests verdes (lib-shared 20, lib-consultas-client 3, api-email 34, api-storage 13, api-consultas 4 — soma confirmada via `mvnw verify`)

## Accomplishments

- **`pom.xml` raiz**: `<module>api-whatsapp</module>` adicionado apos `api-consultas`. `<dependencyManagement>` inalterado (api-whatsapp nao e consumido como dependencia por outros modulos em Phase 1; lib-whatsapp-client entrara em Phase 5).
- **`api-whatsapp/pom.xml`**: 9 dependencias minimas — lib-shared, spring-boot-starter-{web, validation, data-jpa, test}, postgresql (runtime), flyway-core, flyway-database-postgresql, springdoc-openapi-starter-webmvc-ui, h2 (test). Plugin spring-boot-maven-plugin para executable jar. Zero Resilience4j (verificado via `dependency:tree | grep resilience4j` retorna 0).
- **`WhatsAppApplication.java`**: classe canonica Spring Boot, `@SpringBootApplication(scanBasePackages = "br.com.erpkit")`, `main` invoca `SpringApplication.run`. SEM `@EnableConfigurationProperties` (PLAN-03 adiciona quando criar `WhatsAppProperties`).
- **`application.yml`**: server.port 9193 (parametrizavel via `SERVER_PORT`), `spring.application.name`, springdoc paths, logging level, `modulo.api-key`. `autoconfigure.exclude` temporario para evitar boot failure por classpath ter JPA sem datasource. Comentario inline no topo marca remocao em PLAN-04.
- **Estrutura de diretorios** com `.gitkeep` placeholders em controller/service/web/config/db.migration/test/java/test/resources — Maven le sources, git preserva pastas vazias, PLANs 03-07 substituem por arquivos reais.
- **Reator inteiro em BUILD SUCCESS**: 7 modulos (parent + 6 modulos), todos verdes em ~17.7s total. api-whatsapp packaging time 0.314s (so jar, sem testes ainda).

## Task Commits

1. **Task 1+2+3+4+5 (atomico):** `feat(api-whatsapp): bootstrap esqueleto Maven do modulo` — commit `78c7716`
   - Inclui: pom.xml (root) + api-whatsapp/pom.xml + WhatsAppApplication.java + application.yml + 7 .gitkeep
   - Plan especificou commit atomico unico (PLAN-02 secao `<commit>`); seguido literalmente.

**Plan metadata:** este SUMMARY commit ainda pendente (proximo passo).

## Files Created/Modified

- **`pom.xml` (raiz)** — 1 linha adicionada: `<module>api-whatsapp</module>` na linha 27, mantendo ordenacao por dependencia (libs antes de apis, apis em ordem cronologica de criacao). dependencyManagement inalterado.
- **`api-whatsapp/pom.xml`** — 81 linhas, espelhado em api-email com remocoes (sem mail/thymeleaf) per RESEARCH §7. Versoes nao declaradas (gerenciadas pelo BOM Spring Boot 3.5.9 e dependencyManagement do parent).
- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java`** — 12 linhas, padrao identico a `ConsultasApplication` mas sem `@EnableCaching`. `scanBasePackages = "br.com.erpkit"` permite PLAN-06 importar `ApiKeyFilter` de `lib-shared`.
- **`api-whatsapp/src/main/resources/application.yml`** — 41 linhas, com header de comentario explicativo + `autoconfigure.exclude` para 3 autoconfigs (DataSourceAutoConfiguration, HibernateJpaAutoConfiguration, FlywayAutoConfiguration). server.tomcat.accesslog.enabled=false como defesa em profundidade contra log de hub.verify_token (per PITFALLS C-11).
- **7 arquivos `.gitkeep`** — vazios; preservam estrutura de pacote (controller/service/web/config + test/java/test/resources + db/migration) ate PLANs futuros adicionarem arquivos reais.
- **`.planning/phases/01-fundacao-hmac-webhook/01-02-SUMMARY.md`** — este arquivo.

## Decisions Made

- **Sem `@EnableConfigurationProperties` em WhatsAppApplication agora.** PLAN-03 adicionara junto com a classe `WhatsAppProperties`. Se incluido prematuramente, classe fica unresolved e build quebra. Justificativa: bootstrap incremental respeitando waves do plan.
- **autoconfigure.exclude no `application.yml` em vez de remover `spring-boot-starter-data-jpa` do pom temporariamente.** Razao: o pom ja deve refletir a forma final per RESEARCH §7 (com data-jpa); mudar pom em wave futura para "adicionar de volta" e ruido. yml e o unico arquivo que muda de novo em PLAN-04 mesmo, entao concentrar a transicao la e mais limpo. Comentario inline marca explicitamente o que sai em PLAN-04.
- **Comentario divisor no yml descrevendo as 3 waves seguintes** (PLAN-03/04 expansao), economizando arqueologia mental para o agente futuro que abrir o arquivo.
- **`server.tomcat.accesslog.enabled: false` ja em PLAN-02** (e nao apenas em PLAN-04 quando datasource entra). Razao: defesa em profundidade contra log de query string com `hub.verify_token` (PITFALLS C-11). Custo zero adiciona-lo ja, e nao introduz dependencia em config de tomcat especifica. RESEARCH §8 ja inclui essa diretiva no yml completo.

## Deviations from Plan

None — plan executado exatamente como escrito.

Pequenas adicoes pontuais ao yml em relacao ao subset minimo do plan:
- `server.tomcat.accesslog.enabled: false` (RESEARCH §8 inclui; plan secao Task 4 menciona como subset; manti) — alinhamento defensivo, zero risco.

Nenhum desvio significativo. Nenhuma RULE 1/2/3 (auto-fix) acionada nesta wave (esqueleto e reator linear, sem oportunidade para bug).

## Issues Encountered

- **JAVA_HOME desconfigurado** (mesma situacao da Wave 1): `mvnw` falhou na primeira invocacao por apontar para `Amazon Corretto/jdk21.0.10_7` (inexistente). JDK real esta em `/c/Program Files/Java/jdk21.0.10_7/`. Resolvido exportando `JAVA_HOME` por comando. Nao requer mudanca em arquivo de projeto — e configuracao de ambiente local. Documentado em 01-01-SUMMARY tambem.

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| `<module>api-whatsapp</module>` no root pom | `grep -c "api-whatsapp" pom.xml` | 1 |
| api-whatsapp dependency tree | `./mvnw -pl api-whatsapp dependency:tree` | BUILD SUCCESS — lib-shared + spring-boot-starter-web + spring-boot-starter-validation + spring-boot-starter-data-jpa + postgresql:runtime + flyway-core + flyway-database-postgresql + springdoc + h2:test + spring-boot-starter-test:test resolvidos |
| Zero Resilience4j em api-whatsapp | `./mvnw -pl api-whatsapp dependency:tree \| grep -ic "resilience4j"` | 0 (confirmado — nada em Phase 1) |
| api-whatsapp compile | `./mvnw -pl api-whatsapp compile` | BUILD SUCCESS, 1 source file (WhatsAppApplication.java) compilado |
| Reator inteiro | `./mvnw verify` | BUILD SUCCESS — 7 modulos (parent + lib-shared + lib-consultas-client + api-email + api-storage + api-consultas + api-whatsapp), todos verdes em ~17.7s |
| Tests existentes intactos | inspect surefire output | 78 tests verdes (lib-shared 20, lib-consultas-client 3, api-email 34, api-storage 13, api-consultas 4) — zero regressao |
| Estrutura de diretorios | `find api-whatsapp/src -type d` | controller/, service/, web/, config/, db/migration/, test/java/.../whatsapp, test/resources — todos presentes |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | OK: nenhum arquivo deletado (commit 78c7716 e 100% additive) |

## Next Phase Readiness

- Pronto para PLAN 01-03 (Wave 3 da Phase 1).
- PLAN-03 vai:
  1. Criar `WhatsAppProperties.java` em `config/` (`@ConfigurationProperties("app.modulos.whatsapp")` + `@Validated` + `@NotBlank` nos 5 campos: phoneNumberId, accessToken, appSecret, verifyToken, callbackUrl, mais `callbackTimeout` opcional)
  2. Adicionar `@EnableConfigurationProperties(WhatsAppProperties.class)` em `WhatsAppApplication.java`
  3. Expandir `application.yml` com bloco `app.modulos.whatsapp.{phoneNumberId, accessToken, appSecret, verifyToken, callbackUrl}` placeholders (sem datasource ainda — PLAN-04)
  4. Adicionar primeiro teste — WhatsAppPropertiesTest unitario validando @NotBlank fail-fast
- **Cuidado para Wave 3:** quando expandir o yml, o `autoconfigure.exclude` PERMANECE (PLAN-04 que remove). Em PLAN-03 ainda nao ha datasource; o bloco continua necessario.
- **Cuidado para Wave 4:** ao adicionar datasource em PLAN-04, REMOVER as 3 linhas de exclude do yml. Comentario inline no yml ja indica isso.
- Nenhum blocker. Nenhum risco residual identificado nas tasks deste plan.

## Concerns para Wave 3 (PLAN-03)

1. **`@EnableConfigurationProperties`**: precisa adicionar em `WhatsAppApplication.java` SEM tocar em outras coisas. O scanBasePackages atual esta correto (br.com.erpkit cobre `config/` package).
2. **Fail-fast no boot**: PLAN-03 deve testar que falta de qualquer um dos 5 secrets impede boot via `@Validated`. Como `autoconfigure.exclude` ainda esta ativa em PLAN-03, o teste de fail-fast precisa nao depender de datasource — pode usar `ApplicationContextRunner` ou `@SpringBootTest(properties=...)` com properties especificas.
3. **Schema `whatsapp` no yml**: PLAN-03 nao deve adicionar `default_schema: whatsapp` ainda — isso e parte do bloco `spring.jpa` que entra em PLAN-04. Se PLAN-03 adicionar prematuramente, JPA tenta validar e nao tem datasource → boot falha.

## Self-Check: PASSED

- [x] `pom.xml` raiz contem `<module>api-whatsapp</module>` apos api-consultas (verificado via Read pos-edit, linha 27)
- [x] `api-whatsapp/pom.xml` existe e e XML valido (verificado via `mvnw dependency:tree` BUILD SUCCESS)
- [x] `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java` existe com `@SpringBootApplication(scanBasePackages = "br.com.erpkit")` (verificado via compile BUILD SUCCESS)
- [x] `api-whatsapp/src/main/resources/application.yml` existe com `spring.application.name: api-whatsapp` e `autoconfigure.exclude` (verificado via Write success + reactor verify success)
- [x] 7 arquivos `.gitkeep` criados em controller/service/web/config/db.migration/test/java/test/resources (verificado via `find api-whatsapp/src -type d`)
- [x] Commit `78c7716` existe no historico (`git log --oneline -3`)
- [x] BUILD SUCCESS no reator inteiro (7 modulos)
- [x] Zero deletions inesperadas no commit
- [x] Zero Resilience4j em api-whatsapp dependency tree (defesa contra introducao prematura de dep de Phase 4)

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
