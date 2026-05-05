# Phase 1 Plan Check Report

**Phase:** 1 — Fundacao HMAC + Webhook
**Plans verified:** 7 (PLAN-01..07)
**Verificado:** 2026-05-05
**Modo:** Goal-backward verification (decisao por criterio, nao por esforco)

---

## Verdict

**PASS WITH NOTES**

Os 7 plans cobrem integralmente os 5 success criteria do ROADMAP Phase 1 e os 9 requirements (WEB-01..04, PER-01, CFG-01..04). A sequenciacao de waves e correta, dependencias sao acyclic, e o conjunto de tasks (auto-tasks + tdd) e executavel sem replan. Existe 1 risco re-classificado HIGH (verificar antes de executar PLAN-04) e 6 WARNINGs operacionais que merecem watch durante execucao mas nao impedem o gate de execucao.

A decisao chave: nenhum plan reduz escopo de uma decisao locked do CONTEXT.md (D-01..D-06). Cada D-XX esta concretamente implementado em pelo menos um plan.

---

## Coverage Matrix

### Success Criteria -> Plans

| # | ROADMAP Phase 1 Success Criterion | Plans entregando | Cobertura |
|---|------------------------------------|------------------|-----------|
| 1 | GET hub.challenge plain text + 403 em token errado | PLAN-06 (controller) + PLAN-07 (3 tests) | COVERED |
| 2 | POST com X-Hub-Signature-256 valido 200; 1-byte modificado 401; MessageDigest.isEqual nao .equals() | PLAN-05 (validator) + PLAN-06 (filter wiring) + PLAN-07 (3 tests) | COVERED |
| 3 | HMAC sobre bytes brutos via CachedBodyHttpServletRequest (eager); UTF-8 portugues valida | PLAN-05 (wrapper + validator + 11 unit tests) + PLAN-07 (test end-to-end portugues) | COVERED |
| 4 | Boot fail-fast sem propriedades; secrets nunca em logs | PLAN-03 (Properties + 7 tests + toString masking) | COVERED |
| 5 | Flyway V1-V4 no schema whatsapp; mvnw verify -pl api-whatsapp verde com H2 | PLAN-04 (4 SQL files + datasource + 3 tests) + PLAN-07 Task 3 (reator inteiro) | COVERED |

### Requirements -> Plans

| Req | Description (resumo) | Plan(s) | Cobertura |
|-----|---------------------|---------|-----------|
| WEB-01 | GET /webhook/whatsapp ecoa hub.challenge plain text + 403 | PLAN-06 + PLAN-07 | COVERED |
| WEB-02 | POST /webhook/whatsapp HMAC-SHA256 timing-safe | PLAN-05 (validator) + PLAN-06 (filter) + PLAN-07 (test) | COVERED |
| WEB-03 | Custom HttpServletRequestWrapper eager (nao ContentCachingRequestWrapper) | PLAN-05 (wrapper) + PLAN-06 (uso no filter) + PLAN-07 (test) | COVERED |
| WEB-04 | Webhook responde 200 em <1s, apenas HMAC + idempotency fast-path | PLAN-06 (POST stub minimo D-04) + PLAN-07 (200 verified, latencia P95 deferida pra Phase 6) | COVERED |
| PER-01 | Schema PostgreSQL whatsapp via flyway.schemas | PLAN-04 (application.yml + migration V1) | COVERED |
| CFG-01 | WhatsAppProperties 5 campos NotBlank + fail-fast | PLAN-03 (Properties + 5 NotBlank + test) | COVERED |
| CFG-02 | application.yml com placeholders WHATSAPP env vars | PLAN-03 (yml expandido) | COVERED |
| CFG-03 | Logs nunca imprimem accessToken/appSecret | PLAN-03 (toString masking + keys-to-sanitize + tests) | COVERED |
| CFG-04 | Porta default 9193 configuravel | PLAN-02 (yml minimo com SERVER_PORT default 9193) + PLAN-03 (yml expandido preserva) | COVERED |

**Coverage:** 5/5 success criteria + 9/9 requirements. Zero gaps.

---

## Concerns

### BLOCKER (must fix before execution)

Nenhum BLOCKER absoluto. O item inicialmente classificado como blocker foi rebaixado a HIGH WARNING porque ha gate empirico em-cadeia (PLAN-04 Task 8 detecta a falha empiricamente).

### WARNING (proceed with caution)

#### W-01 — A1 (BIGINT GENERATED ALWAYS AS IDENTITY) e empiricamente nao validado [HIGH]

- **Dimension:** scope_sanity / dependency_correctness
- **Plans:** PLAN-04 (todas as 4 migrations + Task 7 + Task 8)
- **Description:** RESEARCH.md Risco/Assumption A1 documenta: BIGINT GENERATED ALWAYS AS IDENTITY pode nao funcionar em alguma versao de H2. O ROADMAP success criterion 5 exige mvnw verify -pl api-whatsapp verde com H2. Se H2 do BOM Spring Boot 3.5.9 nao suportar a sintaxe, build quebra.
- **Plan response:** PLAN-04 Risks documenta fallback (profile-specific yml com BIGSERIAL para PG e AUTO_INCREMENT para H2; custo: 4 migrations duplicadas).
- **Recommended action:** Antes de iniciar PLAN-04 Task 8, executar um spike rapido: criar migration de teste com BIGINT GENERATED ALWAYS AS IDENTITY em H2 modo PG e confirmar que aplica. Se falhar, escolher fallback antes de comprometer todas as 4 migrations.

#### W-02 — Confirmacao do autoconfigure.exclude temporario sera removido em 2 lugares [MEDIUM]

- **Dimension:** task_completeness / context_compliance
- **Plans:** PLAN-02 (introducao em prod yml + test) -> PLAN-03 (preservacao) -> PLAN-04 (remocao)
- **Description:** PLAN-02 introduz spring.autoconfigure.exclude para 3 classes (DataSource, JPA, Flyway) em ambos yml. PLAN-03 explicitamente PRESERVA. PLAN-04 Task 5 + Task 6 EXIGEM remocao em ambos. Risco: PLAN-04 Task 5 esquece de remover do application.yml ou Task 6 esquece de remover do application-test.yml, deixando JPA/Flyway desabilitados em runtime — sintoma silencioso porque tests podem ainda passar.
- **Recommended action:** PLAN-04 Phase Checks 4 e 5 ja verificam grep -c retornar 0 em ambos. Manter rigor — falhar build se grep retornar > 0 em qualquer dos dois.

#### W-03 — RequestParam(hub.mode) com ponto pode quebrar em runtime (A4 do RESEARCH) [MEDIUM]

- **Dimension:** verification_derivation / requirement_coverage
- **Plans:** PLAN-06 (controller) + PLAN-07 (test gate)
- **Description:** Spring MVC suporta @RequestParam(hub.mode) literal com ponto, mas comportamento exato em Spring 3.5.x nao foi unitariamente validado em outro modulo do monorepo. Se quebrar, GET retorna 400 em vez de 200/403 — falha em PLAN-07 Task 1 test get_handshake_*.
- **Plan response:** PLAN-06 Risks documenta fallback (HttpServletRequest.getParameter(hub.mode)). PLAN-07 Task 2 documenta diagnostico inverso (GET retorna 400 -> A4 fallback).
- **Recommended action:** PLAN-07 e o gate. Se 3 tests get_handshake_* falharem com 400, aplicar fallback documentado em PLAN-06 Risks.

#### W-04 — body_vazio_retorna_false test do PLAN-05 tem semantica ambigua [LOW]

- **Dimension:** task_completeness / verification_derivation
- **Plans:** PLAN-05 (Task 3 — HmacValidatorTest)
- **Description:** PLAN-05 Task 3 implementa body_vazio_retorna_false testando empty body com signature de OUTRO body. Isso e tecnicamente correto. Porem: empty body com signature CORRETA de empty body retorna true no validator, e PLAN-05 Risks documenta que isso e operacionalmente questionavel. Phase 1 nao adiciona checagem semantica body.length minimo.
- **Recommended action:** Aceitar como-e em Phase 1. Documentar em PLAN-05 SUMMARY que body vazio com HMAC valido de empty body retorna 200, e que Phase 2 (parser entry-point) deve adicionar pre-check semantico.

#### W-05 — HealthController scope creep em PLAN-06 [LOW]

- **Dimension:** scope_sanity
- **Plans:** PLAN-06 (Task 4)
- **Description:** PLAN-06 inclui criacao de HealthController.java (Task 4) que nao esta explicitamente nos 5 success criteria do ROADMAP. Justificativa do plan: /health esta em DEFAULT_PUBLIC_PATHS do ApiKeyFilter, e sem o controller GET /health retorna 404 — qualquer monitor falha. Plan-fix: criar stub minimo (5 linhas, sem Actuator).
- **Recommended action:** Aceitar como-e. Se durante execucao se descobrir que outros pontos do PLAN-06 estao apertados, considerar mover HealthController pra um PLAN-08 separado, mas nao e necessario.

#### W-06 — MockMvc + filter wiring em PLAN-07 [MEDIUM]

- **Dimension:** verification_derivation
- **Plans:** PLAN-07 (Task 1)
- **Description:** PLAN-07 escolhe MockMvcBuilders.webAppContextSetup(context).build() em vez de @AutoConfigureMockMvc. PLAN-07 Risks documenta que webAppContextSetup deve auto-incluir filters do FilterRegistrationBean — porem isso nao foi validado em outro modulo do monorepo.
- **Risk:** Se filters NAO rodarem em teste (sintoma: post_sem_header_signature_retorna_401 retorna 200), PLAN-07 fica em loop de revisao.
- **Plan response:** PLAN-07 Risks lista 3 fallbacks: addFilters explicito, MockMvcConfigurers, ou AutoConfigureMockMvc annotation alternativa.
- **Recommended action:** Se PLAN-07 Task 1 falhar com sintoma de filter pulado, aplicar fallback addFilters(filterRegistrationBean.getFilter()) antes de qualquer outra mudanca.

### INFO

#### I-01 — flyway-database-postgresql na pom.xml mas H2 e usado em test

- **Plan:** PLAN-02 pom + PLAN-04 application-test.yml
- **Description:** RESEARCH 7 confirma que H2 usa adapter do flyway-core (transitive), nao precisa de flyway-database-h2. flyway-database-postgresql no classpath em test apenas significa que Flyway tenta carregar adapter PG mas usa H2 driver — comportamento documentado da Flyway 10+.
- **Recommended action:** Sem acao. Documentar em PLAN-04 SUMMARY se algum WARN log de Flyway aparecer.

#### I-02 — MessageDigest.isEqual requer arrays de mesmo tamanho

- **Plan:** PLAN-05 (HmacValidator)
- **Description:** PLAN-05 Risks documenta que MessageDigest.isEqual retorna false sem throw quando arrays tem tamanhos diferentes — defesa em profundidade ja embutida (EXPECTED_HEX_LENGTH = 64 valida tamanho do hex antes de decodar).

---

## Risk Assessment — 7 Risks Levantados pelo Planner

| # | Risco do planner | Verdict | Severity | Comentario |
|---|------------------|---------|----------|------------|
| 1 | PLAN-02 temporary autoconfigure.exclude pareado com PLAN-04 removal | AGREE | MEDIUM | Cobertura adequada via Phase Checks de PLAN-04 (grep retorna 0). Ver W-02. |
| 2 | PLAN-04 empirical assumptions (BIGINT IDENTITY portability) | AGREE | HIGH | Esta e a maior incerteza nao-validada da Phase 1. Recomendo spike antes de PLAN-04. Ver W-01. |
| 3 | PLAN-05 empty body semantics | AGREE | LOW | Tecnicamente correto matematicamente. Documentar em SUMMARY pra Phase 2 enderecar. Ver W-04. |
| 4 | PLAN-06 RequestParam(hub.mode) com dot | AGREE | MEDIUM | Gate empirico claro em PLAN-07. Fallback documentado. Ver W-03. |
| 5 | PLAN-07 MockMvc filter wiring | AGREE | MEDIUM | Gate empirico imediato; 3 fallbacks documentados. Ver W-06. |
| 6 | HealthController scope creep em PLAN-06 | AGREE | LOW | Justificativa solida (path em DEFAULT_PUBLIC_PATHS); tamanho minimo. Aceitar. Ver W-05. |
| 7 | Conformance com global commit conventions | AGREE | LOW | Cada plan tem bloco commit com mensagem PT-BR Conventional Commits + comando gsd-tools.cjs commit. Conformacao OK. Adicional: verificar que apos cada commit, PLAN-XX nao faz push (CLAUDE.md global proibe). Visivelmente nenhum plan invoca push — OK. |

---

## PITFALLS Check

| PITFALLS | Severity | Enderecado por | Verificacao |
|----------|----------|----------------|-------------|
| C-02 (HMAC body consumed) | P0 | PLAN-05 (CachedBodyHttpServletRequest com eager read) + PLAN-06 (Filter HIGHEST_PRECEDENCE) | grep gate Task 2 PLAN-05 (StreamUtils.copyToByteArray) + grep gate ContentCachingRequestWrapper retorna 0 |
| C-03 (HMAC timing attack) | P1 | PLAN-05 (HmacValidator usa MessageDigest.isEqual) | grep gate Task 1 PLAN-05 (MessageDigest.isEqual 1x; String.equals/Arrays.equals 0x) |
| C-04 (UTF-8 charset) | P1 | PLAN-05 (byte array direto, nunca via String) + PLAN-07 (test portugues end-to-end) | Test payload_em_portugues_utf8_retorna_true em PLAN-05 + post_com_payload_portugues_e_hmac_valido_retorna_200 em PLAN-07 |
| C-09 (Bearer token in logs) | P1 | PLAN-03 (logging.level.org.springframework.web=INFO + keys-to-sanitize) | Phase Check 5 do PLAN-03 (grep yml). DEFER do interceptor de Authorization para Phase 4 — explicito em CONTEXT.md Deferred Ideas. |
| C-10 (hub.challenge plain text) | P1 | PLAN-06 (produces=TEXT_PLAIN_VALUE no GET) + PLAN-07 (test content().string sem aspas) | Phase Check 4 do PLAN-06 + Test get_handshake_com_token_correto_retorna_challenge_plain_text |
| C-11 (verifyToken in query logs) | P1 | PLAN-03 (server.tomcat.accesslog.enabled=false explicito) | Phase Check yml do PLAN-03; CONTEXT.md D-05 explicito |

**6/6 PITFALLS criticos para Phase 1 cobertos.**

---

## Atomicity / Commit Hygiene

Cada plano declara explicitamente 1 commit atomico via gsd-tools.cjs commit com lista de arquivos especifica. Padroes verificados:

- **PLAN-01:** 1 commit, 2 arquivos (ApiKeyFilter.java + ApiKeyFilterTest.java). Build verde apos: mvnw verify -pl lib-shared,api-email,api-storage,api-consultas (Task 3 e gate de regressao).
- **PLAN-02:** 1 commit, 11 arquivos (pom + Application + yml + 7 .gitkeep). Build verde apos: reator inteiro mvnw verify. Nota: 11 arquivos e alto, mas 7 sao .gitkeep zero-byte — context budget OK.
- **PLAN-03:** 1 commit, 5 arquivos (Properties + Application modificado + 2 yml + test). Build verde apos: mvnw verify -pl api-whatsapp com 7 tests novos.
- **PLAN-04:** 1 commit, 7 arquivos (4 SQL + 2 yml + 1 test). Build verde apos: mvnw verify -pl api-whatsapp com Tests run >= 10. Possivel falha em Task 8 (W-01).
- **PLAN-05:** 1 commit, 3 arquivos (HmacValidator + Wrapper + test). Build verde apos: mvnw verify -pl api-whatsapp Tests >= 21.
- **PLAN-06:** 1 commit, 4 arquivos (Filter + SecurityConfig + 2 controllers). Build verde apos: mvnw verify -pl api-whatsapp Tests >= 21 (sem novos tests).
- **PLAN-07:** 1 commit, 1 arquivo (IntegrationTest). Build verde apos: mvnw verify (root) com 6 modulos + Tests >= 28.

**Atomicidade:** Cada plan e 1 commit. Cada plan deixa o build verde — verificavel via Phase Checks. PLAN-02 e PLAN-04 sao os unicos que deixam o build amarelo temporario (excludes de auto-config), e PLAN-04 ate o final remove tudo. OK.

**PT-BR conformance:** Todas as mensagens de commit em PT-BR Conventional Commits. Mensagens de erro Bean Validation, logs, e identificadores de codigo em PT-BR conforme CONVENTIONS.md.

---

## Recommendation

**PASS WITH NOTES — Plans estao prontos para execucao via /gsd-execute-phase 1.**

**Watch-list operacional durante execucao:**

1. **PLAN-04 Task 8 (W-01):** Antes de comprometer 4 migrations, fazer spike rapido confirmando que BIGINT GENERATED ALWAYS AS IDENTITY aplica em H2 modo PG. Se falhar, aplicar fallback profile-specific yml documentado em PLAN-04 Risks antes de prosseguir.

2. **PLAN-04 Task 5 + Task 6 (W-02):** Verificar que grep -c retorna 0 em ambos application.yml e application-test.yml. Build verde nao e suficiente — pode passar com excludes ainda no test profile.

3. **PLAN-07 Task 1 (W-03 + W-06):** Dois gates empiricos pareados:
   - GET retornando 400 em vez de 200/403 -> A4 quebrou, aplicar fallback HttpServletRequest.getParameter em WebhookController (PLAN-06).
   - POST sem header retornando 200 em vez de 401 -> filter nao registrado em test, aplicar addFilters explicito.

4. **Pre-commit hooks:** Lembrar que cada plan executa gsd-tools.cjs commit. Se algum hook falhar, criar NOVO commit com fix — NUNCA --amend em pipeline GSD (per CLAUDE.md global).

**Nao e necessario replan.** Os 7 plans capturam corretamente a totalidade do escopo da Phase 1, respeitam todas as 6 decisoes locked do CONTEXT.md, evitam todas as 8 deferred ideas, e enderecam os 6 PITFALLS criticos. Os warnings sao operacionais (gates empiricos) com fallbacks documentados.

---

*Plan check report — Phase 1: Fundacao HMAC + Webhook*
*Verified: 2026-05-05*
