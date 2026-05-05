---
phase: 01-fundacao-hmac-webhook
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java
  - lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java
autonomous: true
requirements:
  - PER-01  # parcial — desbloqueio de paths publicos /webhook que mais tarde habilita schema/webhook stack
tags:
  - lib-shared
  - security
  - filter
  - backward-compat

must_haves:
  truths:
    - "ApiKeyFilter aceita 2 construtores: (apiKey) e (apiKey, additionalPublicPaths)"
    - "Construtor de 1 arg comporta-se identicamente ao codigo atual (zero regressao em api-email/api-storage/api-consultas)"
    - "Construtor de 2 args inclui DEFAULT_PUBLIC_PATHS + additionalPublicPaths (uniao, nao substituicao)"
    - "additionalPublicPaths null e tratado como Set vazio (nao quebra)"
    - "mvnw verify -pl lib-shared retorna BUILD SUCCESS com novos tests + 9 tests existentes"
  artifacts:
    - path: "lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java"
      provides: "Filter com construtor 2-arg permitindo paths publicos extras por modulo"
      contains: "public ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths)"
    - path: "lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java"
      provides: "9 tests existentes (regression) + 5 tests novos cobrindo construtor de 2 args"
      contains: "construtor_2_args_permite_path_adicional"
  key_links:
    - from: "ApiKeyFilter(String)"
      to: "ApiKeyFilter(String, Set)"
      via: "delegation: this(apiKey, Set.of())"
      pattern: "this\\(apiKey, Set\\.of\\(\\)\\)"
    - from: "isPublicPath()"
      to: "this.publicPaths field (instance)"
      via: "direct reference, no longer using static constant"
      pattern: "this\\.publicPaths"
---

<objective>
Estender `lib-shared/ApiKeyFilter.java` para aceitar `Set<String> additionalPublicPaths` no construtor (default vazio), mantendo o construtor de 1 argumento backward-compatible. Adicionar tests cobrindo ambos os construtores. Este plan e a fundacao que permite ao `api-whatsapp/SecurityConfig` (PLAN-06) registrar `/webhook/*` como path publico (validado via HMAC, nao API key) sem hardcodar policy em modulo individual.

Purpose: Decisao D-02 do CONTEXT.md — manter ApiKeyFilter como ponto canonico de policy de paths publicos no monorepo, permitindo extensao por modulo via construtor adicional sem reescrita.

Output:
- `lib-shared/ApiKeyFilter.java` modificado (2 construtores, instance field `publicPaths`)
- `lib-shared/ApiKeyFilterTest.java` com 5 tests novos adicionados (9 existentes preservados)
- `mvnw verify -pl lib-shared` BUILD SUCCESS
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md
@.planning/phases/01-fundacao-hmac-webhook/01-RESEARCH.md
@lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java
@lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java

<interfaces>
<!-- Estado atual de ApiKeyFilter (a modificar) -->

```java
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/api/info", "/swagger-ui", "/v3/api-docs");

    private final String apiKey;

    public ApiKeyFilter(String apiKey) { this.apiKey = apiKey; }

    @Override
    protected void doFilterInternal(...) {
        String path = request.getRequestURI();
        if (isPublicPath(path)) { filterChain.doFilter(...); return; }
        String key = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(key)) {
            // 401 com ErrorResponse JSON
        }
        filterChain.doFilter(...);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
```

ErrorResponse (lib-shared/dto/ErrorResponse.java):
- Construtor: `new ErrorResponse(int status, String erro, String mensagem)`
- Usado pelo filter para escrever 401 JSON quando API key invalida.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Estender ApiKeyFilter com construtor de 2 args (Set additionalPublicPaths)</name>
  <files>lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java</files>
  <behavior>
    - Construtor `ApiKeyFilter(String apiKey)` continua funcionando igual ao atual (delega para o novo com Set vazio)
    - Novo construtor `ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths)` cria campo de instancia `publicPaths` = uniao de DEFAULT_PUBLIC_PATHS + additionalPublicPaths
    - additionalPublicPaths null e tratado como Set vazio (nao NPE)
    - `isPublicPath` agora usa `this.publicPaths` (instance) em vez do static
    - `PUBLIC_PATHS` renomeado para `DEFAULT_PUBLIC_PATHS` (privado static final preservado, so renomeado)
  </behavior>
  <action>
    Substituir o conteudo do arquivo `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` pela implementacao da secao 6 (Diff conceitual) do RESEARCH.md (`01-RESEARCH.md` §6 "lib-shared ApiKeyFilter — Modificacao", linhas 611-680).

    Pontos chave:
    - Adicionar imports: `java.util.HashSet`, `java.util.Set` (Set ja importado)
    - Renomear `PUBLIC_PATHS` para `DEFAULT_PUBLIC_PATHS`
    - Adicionar campo de instancia `private final Set<String> publicPaths`
    - Construtor de 1 arg: `public ApiKeyFilter(String apiKey) { this(apiKey, Set.of()); }`
    - Construtor de 2 args: aceita Set<String> additionalPublicPaths, faz uniao com DEFAULT_PUBLIC_PATHS via HashSet, atribui `Set.copyOf(merged)` para imutabilidade. Trata null como Set vazio.
    - `isPublicPath` usa `this.publicPaths` em vez de `DEFAULT_PUBLIC_PATHS`
    - Mensagens de erro 401 mantidas em PT-BR ("Não autorizado", "API Key inválida ou ausente") — preservar acentuacao existente (per CONVENTIONS.md PT-BR)
    - Per D-02: backward-compat com api-email/api-storage/api-consultas e MANDATORIO
  </action>
  <verify>
    <automated>./mvnw compile -pl lib-shared -q</automated>
  </verify>
  <done>
    - Arquivo compila sem erros
    - Construtor de 1 arg ainda existe
    - Novo construtor de 2 args existe e aceita Set<String>
    - `grep "DEFAULT_PUBLIC_PATHS" lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` retorna match
    - `grep "this.publicPaths" lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` retorna match
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Adicionar 5 tests novos em ApiKeyFilterTest cobrindo o construtor de 2 args</name>
  <files>lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java</files>
  <behavior>
    Os 9 tests existentes (cobrindo construtor de 1 arg, public paths default, API key valida/invalida) devem continuar passando inalterados. Adicionar 5 novos tests:

    1. `construtor_1_arg_continua_funcionando` — `new ApiKeyFilter("k")` num path nao-default sem header `X-API-Key` retorna 401 (regression)
    2. `construtor_2_args_permite_path_adicional_como_publico` — `new ApiKeyFilter("k", Set.of("/webhook"))`, request a `/webhook/whatsapp` sem header retorna 200 (filter nao bloqueia, chain continua)
    3. `construtor_2_args_com_set_vazio_comporta_se_como_1_arg` — `new ApiKeyFilter("k", Set.of())` em path nao-default retorna 401
    4. `construtor_2_args_com_null_nao_quebra` — `new ApiKeyFilter("k", null)` num path default (`/health`) retorna 200 (default paths preservados)
    5. `additional_paths_somam_se_aos_defaults` — `new ApiKeyFilter("k", Set.of("/webhook"))` em `/health` retorna 200 E em `/webhook/x` retorna 200
  </behavior>
  <action>
    Adicionar 5 metodos `@Test` em `ApiKeyFilterTest.java` seguindo:
    - O padrao dos 9 tests existentes (MockMvc ou MockHttpServletRequest/MockHttpServletResponse — espelhar o que ja existe)
    - Specificacao na secao 12.3 do `01-RESEARCH.md` (linhas 1090-1100) e na secao 6.4 (linhas 692-698)
    - Usar `@DisplayName` em PT-BR
    - Reusar fixtures/setup ja presentes no arquivo (nao duplicar)
    - Quando precisar simular `chain.doFilter(...)` foi chamado: usar `Mockito.verify(chain).doFilter(any(), any())` ou inspecionar o status do response (200 == filter chained / passou; 401 == filter bloqueou)

    Nao tocar nos 9 tests existentes — sao regression e devem continuar verdes.
  </action>
  <verify>
    <automated>./mvnw -pl lib-shared test -Dtest=ApiKeyFilterTest -q</automated>
  </verify>
  <done>
    - Todos os tests (9 antigos + 5 novos = 14) passam
    - Output do Surefire mostra "Tests run: 14, Failures: 0"
    - Nenhum test antigo modificado (verificar via git diff que so adicoes ocorreram)
  </done>
</task>

<task type="auto">
  <name>Task 3: Verificar reator inteiro nao regrediu (api-email, api-storage, api-consultas)</name>
  <files>(nenhum modificado — verificacao apenas)</files>
  <action>
    Rodar `./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas -q` (uma execucao do reator restrito aos modulos consumidores de lib-shared). Se algum teste de api-email, api-storage ou api-consultas falhar por causa da mudanca em ApiKeyFilter, parar e investigar — os 3 modulos consomem `new ApiKeyFilter(apiKey)` (construtor de 1 arg) e essa chamada deve continuar funcionando identicamente.

    Risco mitigado: o construtor de 1 arg foi PRESERVADO via delegation `this(apiKey, Set.of())` — nenhum codigo dos consumidores muda. A verificacao e defesa em profundidade contra regressao acidental.
  </action>
  <verify>
    <automated>./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas -q</automated>
  </verify>
  <done>
    - Output: BUILD SUCCESS
    - Tests de lib-shared, api-email, api-storage, api-consultas todos verdes
    - Nenhum WARN/ERROR de compilacao ou test relacionado a ApiKeyFilter
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Cliente HTTP → API Key Filter | Trafico nao autenticado entra; filter decide se path e publico ou requer X-API-Key |
| API Key Filter → Controllers | Apos passar pelo filter, request e considerada autenticada (ou explicitamente publica) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-01 | Spoofing | ApiKeyFilter (path matching) | mitigate | `isPublicPath` usa `String::startsWith` — request a `/webhook/whatsapp` casa com `/webhook` (correto). Tests cobrem path matching exato. |
| T-01-02 | Elevation of Privilege | ApiKeyFilter modificacao | mitigate | Construtor de 1 arg PRESERVADO via delegation — api-email/api-storage/api-consultas continuam com mesma policy. Test "construtor_1_arg_continua_funcionando" e regression gate. |
| T-01-03 | Tampering | additionalPublicPaths field | mitigate | Field e `Set.copyOf(merged)` — imutavel. Caller nao pode adicionar paths apos construcao. |
| T-01-04 | Information Disclosure | Public paths como prefix match | accept | `/webhook` casa com `/webhook/whatsapp/admin` se alguem criar esse endpoint (subpath publico). Aceito porque nesta phase webhook e o unico path adicional; futuras adicoes devem revisar policy. |
</threat_model>

<verification>
## Phase Checks

1. `./mvnw compile -pl lib-shared` retorna BUILD SUCCESS
2. `./mvnw -pl lib-shared test -Dtest=ApiKeyFilterTest` mostra "Tests run: 14, Failures: 0"
3. `./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas` retorna BUILD SUCCESS — zero regressao em consumidores
4. `git diff lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` mostra apenas adicoes (construtor novo, campo de instancia, rename de constante) — nao remocoes da logica de doFilterInternal
5. `git diff lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java` mostra 5 metodos `@Test` adicionados (sem remocoes)
</verification>

<success_criteria>
- ApiKeyFilter aceita 2 construtores funcionais (1 arg backward-compat, 2 args com additionalPublicPaths)
- 14 tests verdes em ApiKeyFilterTest (9 originais + 5 novos)
- `mvnw verify -pl lib-shared` BUILD SUCCESS
- `mvnw verify -pl lib-shared,api-email,api-storage,api-consultas` BUILD SUCCESS — nenhum modulo consumidor quebrou
- Plano fechado com 1 commit atomico via `gsd-tools.cjs commit`
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(lib-shared): adicionar construtor 2-arg em ApiKeyFilter com additionalPublicPaths

Permite que modulos passem paths publicos extras (ex: /webhook do api-whatsapp,
validado via HMAC e nao API key). Construtor de 1 arg preservado via delegation
para zero regressao em api-email/api-storage/api-consultas.

Refs: D-02 (CONTEXT.md), 01-RESEARCH.md §6
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(lib-shared): adicionar construtor 2-arg em ApiKeyFilter com additionalPublicPaths" --files \
  lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java \
  lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java
```
</commit>

<risks>
- **Quebra dos 9 tests existentes em ApiKeyFilterTest** — provavel se o renomeio `PUBLIC_PATHS` → `DEFAULT_PUBLIC_PATHS` quebrar test que faz reflection ou assert em nome literal. Mitigacao: nao mexer nos tests antigos; apenas adicionar. Se algum quebrar, investigar caso a caso.
- **api-email/api-storage/api-consultas falham no reator** — improvavel (construtor de 1 arg preservado), mas Task 3 e o gate. Se falhar, rollback via `git restore` no arquivo modificado e investigar o que quebrou (provavelmente import ou assinatura mal preservada).
- **Spring Boot reflexivamente nao encontra ApiKeyFilter por causa da mudanca** — improvavel (Spring usa o construtor publico injetado pela `FilterRegistrationBean`, ambos publicos). Se ocorrer, sintoma sera NoSuchMethodException no boot dos modulos consumidores.
</risks>

<output>
Apos completar todas as tasks, criar `.planning/phases/01-fundacao-hmac-webhook/01-01-SUMMARY.md` documentando:
- Quais arquivos foram modificados (ApiKeyFilter.java + ApiKeyFilterTest.java)
- Resultado do `mvnw verify` no reator restrito
- Confirmacao de zero regressao em api-email/api-storage/api-consultas
- Commit hash criado
</output>
