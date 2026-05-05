---
phase: 01-fundacao-hmac-webhook
plan: 03
type: execute
wave: 3
depends_on:
  - "01-02"
files_modified:
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java  # MODIFY: add @EnableConfigurationProperties
  - api-whatsapp/src/main/resources/application.yml  # EXPAND with placeholders
  - api-whatsapp/src/test/resources/application-test.yml  # NEW (test profile com dummy values)
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java  # NEW
autonomous: true
requirements:
  - CFG-01  # WhatsAppProperties com 5 campos @NotBlank + callbackTimeout, fail-fast no boot
  - CFG-02  # application.yml com placeholders ${WHATSAPP_*}
  - CFG-03  # Logs nunca imprimem accessToken/appSecret/verifyToken (toString mascarado)
  - CFG-04  # Porta default 9193 configuravel via SERVER_PORT env var
tags:
  - api-whatsapp
  - configuration
  - bean-validation
  - fail-fast
  - secrets

must_haves:
  truths:
    - "WhatsAppProperties existe com 5 campos @NotBlank (phoneNumberId, accessToken, appSecret, verifyToken, erpCallbackUrl) + callbackTimeout"
    - "Mensagens de @NotBlank em PT-BR nomeando a env var faltante (ex: 'WHATSAPP_PHONE_NUMBER_ID nao definida')"
    - "Boot falha com BindValidationException se qualquer um dos 5 campos estiver blank"
    - "toString() retorna [REDACTED] para accessToken, appSecret, verifyToken (nao imprime os valores reais)"
    - "application.yml tem placeholders ${WHATSAPP_PHONE_NUMBER_ID}, ${WHATSAPP_ACCESS_TOKEN}, ${WHATSAPP_APP_SECRET}, ${WHATSAPP_VERIFY_TOKEN}, ${WHATSAPP_ERP_CALLBACK_URL} — sem default (boot falha se faltar)"
    - "application-test.yml fornece valores dummy para os 5 campos (test passa sem precisar de env vars reais)"
    - "WhatsAppApplication tem @EnableConfigurationProperties(WhatsAppProperties.class)"
    - "mvnw verify -pl api-whatsapp BUILD SUCCESS com novo test class verde"
  artifacts:
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java"
      provides: "Configuracao validada de 5 secrets + callbackTimeout"
      contains: "@ConfigurationProperties(prefix = \"app.modulos.whatsapp\")"
    - path: "api-whatsapp/src/main/resources/application.yml"
      provides: "Placeholders de env vars"
      contains: "${WHATSAPP_PHONE_NUMBER_ID:}"
    - path: "api-whatsapp/src/test/resources/application-test.yml"
      provides: "Dummy values pra Bean Validation passar em test"
      contains: "phoneNumberId: test-phone-id"
    - path: "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java"
      provides: "7 cenarios cobrindo presenca/ausencia de cada campo + toString masking"
      contains: "boot_sem_phoneNumberId_falha"
  key_links:
    - from: "WhatsAppApplication"
      to: "WhatsAppProperties"
      via: "@EnableConfigurationProperties"
      pattern: "@EnableConfigurationProperties\\(WhatsAppProperties\\.class\\)"
    - from: "application.yml"
      to: "WhatsAppProperties"
      via: "prefix app.modulos.whatsapp.*"
      pattern: "app\\.modulos\\.whatsapp"
    - from: "WhatsAppProperties.toString()"
      to: "[REDACTED] for sensitive fields"
      via: "manual override"
      pattern: "\\[REDACTED\\]"
---

<objective>
Criar `WhatsAppProperties` (`@ConfigurationProperties("app.modulos.whatsapp")` + `@Validated` + `@NotBlank` em 5 campos) com `toString()` mascarando secrets, expandir `application.yml` com placeholders `${WHATSAPP_*}` (sem default — boot falha se faltar), criar `application-test.yml` com dummy values, registrar `@EnableConfigurationProperties` em `WhatsAppApplication`, e adicionar test cobrindo: (a) boot com 5 properties passa; (b) ausencia de cada campo causa BindValidationException em PT-BR nomeando a env var; (c) toString() oculta secrets.

Purpose: Decisao D-03 do CONTEXT.md + requirements CFG-01..04. Operador da ERPKit que esquecer uma env var no `service-config-whatsapp.xml` (WinSW) ve no log de boot exatamente qual env var faltou em mensagem PT-BR — sem precisar adivinhar. CFG-03 (logs nao imprimem secrets) e enforced via `toString()` mascarado.

Output:
- `WhatsAppProperties.java` com 5 `@NotBlank` (mensagens PT-BR) + `Duration callbackTimeout` (default PT5S) + getters/setters + `toString()` mascarado
- `application.yml` expandido (datasource + jpa + flyway + app.modulos.whatsapp + placeholders)
- `application-test.yml` com dummy values para boot test passar
- `WhatsAppApplication` com `@EnableConfigurationProperties(WhatsAppProperties.class)`
- `WhatsAppPropertiesValidationTest` com 7 tests verdes
- `mvnw verify -pl api-whatsapp` BUILD SUCCESS
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
@.planning/phases/01-fundacao-hmac-webhook/01-02-SUMMARY.md
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java
@api-whatsapp/src/main/resources/application.yml
@api-email/src/main/resources/application.yml
@api-email/src/test/resources/application-test.yml

<interfaces>
<!-- Codigo-fonte exato a copiar de RESEARCH.md §5 -->

WhatsAppProperties (RESEARCH §5 linhas 513-577):
```java
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
@Validated
public class WhatsAppProperties {
    @NotBlank(message = "WHATSAPP_PHONE_NUMBER_ID nao definida")
    private String phoneNumberId;
    @NotBlank(message = "WHATSAPP_ACCESS_TOKEN nao definida")
    private String accessToken;
    @NotBlank(message = "WHATSAPP_APP_SECRET nao definida")
    private String appSecret;
    @NotBlank(message = "WHATSAPP_VERIFY_TOKEN nao definida")
    private String verifyToken;
    @NotBlank(message = "WHATSAPP_ERP_CALLBACK_URL nao definida")
    private String erpCallbackUrl;
    private Duration callbackTimeout = Duration.ofSeconds(5);
    // getters / setters
    @Override public String toString() {
        return "WhatsAppProperties{phoneNumberId=" + phoneNumberId
            + ", accessToken=[REDACTED], appSecret=[REDACTED], verifyToken=[REDACTED]"
            + ", erpCallbackUrl=" + erpCallbackUrl
            + ", callbackTimeout=" + callbackTimeout + "}";
    }
}
```

WhatsAppApplication (RESEARCH §5 linhas 583-598):
```java
@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppApplication {
    public static void main(String[] args) { SpringApplication.run(WhatsAppApplication.class, args); }
}
```

application.yml expandido (RESEARCH §8 linhas 796-878). Per Task 4 abaixo, MANTER a `autoconfigure.exclude` de DataSource/JPA/Flyway de PLAN-02 — sera removida em PLAN-04 quando migrations entrarem.

application-test.yml (RESEARCH §9 linhas 890-929) — datasource + jpa + flyway tambem ficam comentados/excluidos em PLAN-03 (entram em PLAN-04). Em PLAN-03, application-test.yml so precisa fornecer dummy values pra Bean Validation passar.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Criar WhatsAppProperties.java com 5 @NotBlank + toString mascarado</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java</files>
  <behavior>
    - 5 campos privados String com `@NotBlank(message = "WHATSAPP_<NAME> nao definida")` (mensagens em PT-BR conforme D-03 / CONVENTIONS.md)
    - 1 campo `Duration callbackTimeout = Duration.ofSeconds(5)` (default, sem `@NotBlank`)
    - Getters e setters explicitos para todos os 6 campos (sem Lombok, alinhado com api-email pattern)
    - `toString()` retorna string contendo `[REDACTED]` para accessToken/appSecret/verifyToken — nunca os valores reais (CFG-03)
    - Anotacao `@ConfigurationProperties(prefix = "app.modulos.whatsapp")` + `@Validated` (Spring annotation, nao javax)
  </behavior>
  <action>
    Copiar integralmente o codigo Java da secao 5 do `01-RESEARCH.md` (linhas 513-577) para o novo arquivo `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java`.

    Verificar:
    - Imports: `jakarta.validation.constraints.NotBlank` (NAO `javax.validation` — Spring Boot 3.x usa Jakarta)
    - Anotacao `@Validated` de `org.springframework.validation.annotation.Validated` (Spring), NAO `org.springframework.validation.annotation.Validated` placeholder. Verificar import.
    - `@ConfigurationProperties(prefix = "app.modulos.whatsapp")` de `org.springframework.boot.context.properties`
    - Campos privados; getters/setters publicos para todos os 6 campos
    - `toString()` override mascara accessToken, appSecret, verifyToken — phoneNumberId e erpCallbackUrl podem aparecer (nao sao secrets, sao identificadores)
    - Mensagens `@NotBlank` em PT-BR sem acentos (RESEARCH usa `nao definida` sem til; manter assim para evitar problema de encoding em log de boot)
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q</automated>
  </verify>
  <done>
    - Arquivo compila sem erros
    - `grep "@NotBlank" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java` retorna 5 matches (uma por campo secret)
    - `grep "REDACTED" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java` retorna 3 matches
    - `grep "Duration callbackTimeout" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java` retorna 1 match
  </done>
</task>

<task type="auto">
  <name>Task 2: Modificar WhatsAppApplication para registrar @EnableConfigurationProperties</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java</files>
  <action>
    Adicionar a anotacao `@EnableConfigurationProperties(WhatsAppProperties.class)` em `WhatsAppApplication` (em cima ou abaixo de `@SpringBootApplication`). Adicionar import:

    ```java
    import org.springframework.boot.context.properties.EnableConfigurationProperties;
    import br.com.erpkit.whatsapp.config.WhatsAppProperties;
    ```

    Per RESEARCH §5 "Habilitacao" (linhas 583-598) + "Open Question 4" (linhas 1193-1202): `@EnableConfigurationProperties` e a abordagem mais explicita e alinhada com docs Spring Boot 3.5.x. NAO usar `@ConfigurationPropertiesScan` ou `@Component` na Properties.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "@EnableConfigurationProperties" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - grep retorna 1 (a anotacao e o import; checa pelo menos a anotacao)
    - WhatsAppApplication mantem `scanBasePackages = "br.com.erpkit"` inalterado
  </done>
</task>

<task type="auto">
  <name>Task 3: Expandir application.yml com placeholders ${WHATSAPP_*} (sem default)</name>
  <files>api-whatsapp/src/main/resources/application.yml</files>
  <action>
    Substituir o conteudo minimo de PLAN-02 pelo yml completo da secao 8 do `01-RESEARCH.md` (linhas 796-878), com 1 ressalva:

    **Manter** o bloco `spring.autoconfigure.exclude` que desliga DataSource/JPA/Flyway autoconfig (nao foi removido em PLAN-02). Sera removido em PLAN-04 quando o datasource/migrations entrarem. Em PLAN-03 nao queremos boot do contexto JPA — so queremos validar que as Properties carregam corretamente.

    Conteudo final esperado (RESEARCH §8 linhas 796-878 com adapter):

    ```yaml
    server:
      port: ${SERVER_PORT:9193}
      tomcat:
        accesslog:
          enabled: false  # PITFALLS C-11

    spring:
      application:
        name: api-whatsapp

      # PLAN-03 only — PLAN-04 remove estas linhas quando DataSource entra
      autoconfigure:
        exclude:
          - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
          - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
          - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration

    app:
      modulos:
        whatsapp:
          phoneNumberId: ${WHATSAPP_PHONE_NUMBER_ID:}
          accessToken: ${WHATSAPP_ACCESS_TOKEN:}
          appSecret: ${WHATSAPP_APP_SECRET:}
          verifyToken: ${WHATSAPP_VERIFY_TOKEN:}
          erpCallbackUrl: ${WHATSAPP_ERP_CALLBACK_URL:}
          callbackTimeout: ${WHATSAPP_CALLBACK_TIMEOUT:5s}

    modulo:
      versao: 1.0.0
      api-key: ${API_KEY:}

    springdoc:
      api-docs:
        path: /v3/api-docs
      swagger-ui:
        path: /swagger-ui.html

    logging:
      level:
        br.com.erpkit.whatsapp: INFO
        org.springframework.web: INFO

    management:
      endpoint:
        env:
          keys-to-sanitize:
            - password
            - secret
            - key
            - token
            - accessToken
            - appSecret
            - verifyToken
    ```

    **Sintaxe critica do `${WHATSAPP_X:}` com colon vazio:** o yml interpreta como "use env var WHATSAPP_X, se ausente use string vazia" — string vazia falha `@NotBlank` no boot, que e exatamente o desejado pra fail-fast (per CFG-01/CFG-02). Sem o `:` final, Spring Boot nao tem placeholder e falha com erro diferente, menos amigavel.

    NAO incluir `spring.datasource`, `spring.jpa`, `spring.flyway` neste plano — entram em PLAN-04 (junto com a remocao do `autoconfigure.exclude`).
  </action>
  <verify>
    <automated>grep -c "WHATSAPP_" api-whatsapp/src/main/resources/application.yml</automated>
  </verify>
  <done>
    - grep retorna >= 5 (5 placeholders + possivelmente WHATSAPP_CALLBACK_TIMEOUT)
    - YAML e valido (Spring Boot consegue parsear — verificavel rodando o test em Task 5)
    - Bloco `autoconfigure.exclude` ainda presente com comentario indicando que sera removido em PLAN-04
  </done>
</task>

<task type="auto">
  <name>Task 4: Criar application-test.yml com dummy values</name>
  <files>api-whatsapp/src/test/resources/application-test.yml</files>
  <action>
    Criar `api-whatsapp/src/test/resources/application-test.yml` MINIMO em PLAN-03 (datasource H2 entra em PLAN-04). Em PLAN-03 so precisamos fornecer os 5 dummy values + manter excludes de auto-config:

    ```yaml
    spring:
      autoconfigure:
        exclude:
          - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
          - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
          - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration

    # Dummy values pra Bean Validation passar em SpringBootTest — testes que precisam
    # de valores reais (HmacValidatorTest com appSecret) podem sobrescrever via
    # @SpringBootTest(properties = {...}) ou @TestPropertySource.
    app:
      modulos:
        whatsapp:
          phoneNumberId: test-phone-id
          accessToken: test-access-token
          appSecret: test-app-secret
          verifyToken: test-verify-token
          erpCallbackUrl: http://localhost:0/test
          callbackTimeout: 5s

    modulo:
      versao: 1.0.0-test
      api-key: test-key
    ```

    A versao expandida (com H2 datasource) entra em PLAN-04. Documentar inline no yml: `# datasource + jpa + flyway adicionados em PLAN-04`
  </action>
  <verify>
    <automated>test -f api-whatsapp/src/test/resources/application-test.yml && grep "test-app-secret" api-whatsapp/src/test/resources/application-test.yml</automated>
  </verify>
  <done>
    - Arquivo existe com os 5 dummy values
    - YAML valido
    - Comentario inline indicando que datasource entra em PLAN-04
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 5: Criar WhatsAppPropertiesValidationTest com 7 cenarios</name>
  <files>api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java</files>
  <behavior>
    7 testes per RESEARCH §12.4 linhas 1106-1114:

    1. `boot_com_todas_as_5_propriedades_passa` — context loads quando `application-test.yml` fornece todas as 5 props (test profile ativo)
    2. `boot_sem_phoneNumberId_falha` — `@SpringBootTest(properties = "app.modulos.whatsapp.phoneNumberId=")` deve falhar com excecao contendo "WHATSAPP_PHONE_NUMBER_ID nao definida"
    3. `boot_sem_accessToken_falha` — similar para accessToken
    4. `boot_sem_appSecret_falha` — similar para appSecret
    5. `boot_sem_verifyToken_falha` — similar para verifyToken
    6. `boot_sem_erpCallbackUrl_falha` — similar para erpCallbackUrl
    7. `toString_mascara_secrets` — instancia WhatsAppProperties manual, popula campos com valores reais ("real-token-xyz"), chama toString() — verifica que o output NAO contem "real-token-xyz" e CONTEM "[REDACTED]" 3x
  </behavior>
  <action>
    Criar `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java`.

    Estrutura sugerida (combinando padrao do api-email + spec do RESEARCH §12.4):

    ```java
    package br.com.erpkit.whatsapp.config;

    import org.junit.jupiter.api.Test;
    import org.springframework.boot.SpringApplication;
    import org.springframework.boot.context.properties.bind.validation.BindValidationException;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.springframework.test.context.ActiveProfiles;
    import org.springframework.beans.factory.BeanCreationException;
    import org.springframework.beans.factory.annotation.Autowired;
    import br.com.erpkit.whatsapp.WhatsAppApplication;

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.assertj.core.api.Assertions.assertThatThrownBy;

    @SpringBootTest(classes = WhatsAppApplication.class)
    @ActiveProfiles("test")
    class WhatsAppPropertiesValidationTest {

        @Autowired
        WhatsAppProperties properties;

        @Test
        void boot_com_todas_as_5_propriedades_passa() {
            assertThat(properties.getPhoneNumberId()).isEqualTo("test-phone-id");
            assertThat(properties.getAccessToken()).isEqualTo("test-access-token");
            // ... outros 3
        }

        @Test
        void toString_mascara_secrets() {
            WhatsAppProperties p = new WhatsAppProperties();
            p.setPhoneNumberId("phone-real");
            p.setAccessToken("access-real-xyz");
            p.setAppSecret("secret-real-xyz");
            p.setVerifyToken("verify-real-xyz");
            p.setErpCallbackUrl("http://erp/x");
            String s = p.toString();
            assertThat(s).doesNotContain("access-real-xyz", "secret-real-xyz", "verify-real-xyz");
            assertThat(s).contains("[REDACTED]");
        }

        // Para os 5 testes de "falta_X_falha": rodar SpringApplication com properties customizadas
        // e capturar excecao. Padrao recomendado:
        //
        //   @Test
        //   void boot_sem_phoneNumberId_falha() {
        //     assertThatThrownBy(() -> {
        //         SpringApplication app = new SpringApplication(WhatsAppApplication.class);
        //         app.setDefaultProperties(java.util.Map.of(
        //             "spring.profiles.active", "test",
        //             "app.modulos.whatsapp.phoneNumberId", ""
        //         ));
        //         app.run();
        //     }).hasMessageContaining("WHATSAPP_PHONE_NUMBER_ID nao definida");
        //   }
        //
        // Alternativa: usar @SpringBootTest com nested static class + @ContextConfiguration
        // sobrescrevendo so 1 prop. Escolher o padrao ja usado em api-email se existir;
        // caso contrario, usar a abordagem SpringApplication.run() acima.
    }
    ```

    **Nota importante sobre encoding:** os tests devem assertar `"WHATSAPP_PHONE_NUMBER_ID nao definida"` (sem til em "nao") matching exato a mensagem no `@NotBlank`. Se a fonte usar "não" (com til), o test deve usar identico — mas RESEARCH §5 usa "nao" (sem til). Manter consistencia com WhatsAppProperties.java de Task 1.

    **Nested test approach (alternativa):** Se SpringApplication.run() em test gerar muito ruido, considerar rodar o `boot_sem_X_falha` como um shell em Task de verificacao (`./mvnw -pl api-whatsapp spring-boot:run -Dspring-boot.run.arguments="--app.modulos.whatsapp.phoneNumberId="` deve falhar). Mas a forma JUnit pura do exemplo acima e preferida e auto-verificavel.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp test -Dtest=WhatsAppPropertiesValidationTest -q</automated>
  </verify>
  <done>
    - Surefire reporta "Tests run: 7, Failures: 0"
    - Todos os 7 cenarios cobrem o que RESEARCH §12.4 lista
    - Mensagens PT-BR ("nao definida") matched corretamente
    - `toString_mascara_secrets` confirma que os 3 secrets nao vazam
  </done>
</task>

<task type="auto">
  <name>Task 6: Verificar build do reator</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -pl api-whatsapp -q` para confirmar que o modulo build esta verde com Properties + test class. Em paralelo, rodar `./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas -q` (modulos consumidores de lib-shared) para confirmar que nada regrediu (esperado: zero impacto, pois api-whatsapp e isolado).

    Se SpringBootTest falhar com "DataSource not configured" significa que o `autoconfigure.exclude` nao foi aplicado em test profile — verificar que `application-test.yml` (Task 4) inclui as mesmas 3 exclusoes que `application.yml`.
  </action>
  <verify>
    <automated>./mvnw verify -pl api-whatsapp -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS
    - Tests run: 7 (todos do WhatsAppPropertiesValidationTest)
    - Outros modulos nao afetados
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Env var → Spring Boot context | Secret entra como `${WHATSAPP_*}` placeholder; Bean Validation rejeita se ausente/blank |
| Properties → Logs | `toString()` controla o que aparece em log de erro do Spring |
| Properties → Actuator (futuro) | `keys-to-sanitize` impede exposicao em /actuator/env quando actuator entrar (Phase 4) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-03-01 | Information Disclosure | accessToken/appSecret/verifyToken em log de boot | mitigate | `toString()` mascara com `[REDACTED]`; test `toString_mascara_secrets` enforced (Task 5) |
| T-03-02 | Spoofing | accessToken hardcoded em yml | mitigate | application.yml usa `${WHATSAPP_*:}` placeholder com default vazio — boot falha se env var nao definida (CFG-02). Per RESEARCH PITFALLS C-09 / "Security Mistakes" tabela. |
| T-03-03 | DoS | Bean Validation nao roda → app sobe com config quebrada | mitigate | `@Validated` em `@ConfigurationProperties` aciona Hibernate Validator; Spring Boot starter-validation ja no pom (PLAN-02 §7); test `boot_sem_*_falha` valida (Task 5). |
| T-03-04 | Information Disclosure | verifyToken em query string log | mitigate | `server.tomcat.accesslog.enabled: false` (PITFALLS C-11). Aplicado em application.yml Task 3. |
| T-03-05 | Information Disclosure | /actuator/env expoe secrets | accept (defer) | Actuator nao esta no classpath em Phase 1; `keys-to-sanitize` configurada como defesa em profundidade (RESEARCH §8 + Open Q1). Tratado em Phase 4 quando WHATS-17 trouxer actuator. |
</threat_model>

<verification>
## Phase Checks

1. `./mvnw -pl api-whatsapp test -Dtest=WhatsAppPropertiesValidationTest` — Tests run: 7, Failures: 0
2. `grep "@NotBlank" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java | wc -l` >= 5
3. `grep "REDACTED" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java | wc -l` == 3
4. `grep "WHATSAPP_" api-whatsapp/src/main/resources/application.yml` retorna >= 5 placeholders
5. `grep "test-app-secret" api-whatsapp/src/test/resources/application-test.yml` retorna 1
6. `grep "@EnableConfigurationProperties" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java` retorna 1
7. `./mvnw verify -pl api-whatsapp` BUILD SUCCESS
</verification>

<success_criteria>
- WhatsAppProperties existe com 5 `@NotBlank` (mensagens PT-BR) + Duration callbackTimeout default 5s + toString mascarado
- Boot falha com BindValidationException se qualquer um dos 5 secrets faltar (testado em 5 metodos do test class)
- application.yml tem placeholders sem default → fail-fast quando env var ausente
- application-test.yml fornece dummy values pra test passar sem env vars reais
- WhatsAppApplication registra @EnableConfigurationProperties
- 7 tests verdes em WhatsAppPropertiesValidationTest
- mvnw verify -pl api-whatsapp BUILD SUCCESS
- 1 commit atomico
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(api-whatsapp): adicionar WhatsAppProperties fail-fast com 5 secrets

5 campos obrigatorios (@NotBlank) com mensagens PT-BR nomeando env var:
phoneNumberId, accessToken, appSecret, verifyToken, erpCallbackUrl. Mais
Duration callbackTimeout (default PT5S). Boot falha imediatamente via
BindValidationException se qualquer secret estiver ausente. toString()
retorna [REDACTED] para os 3 secrets — operador da ERPKit ve em log de boot
exatamente qual env var corrigir no service-config-whatsapp.xml (WinSW).

application.yml expandido com placeholders ${WHATSAPP_*:} (sem default).
application-test.yml fornece dummy values pra suite passar sem env vars reais.

Refs: D-03 (CONTEXT.md), CFG-01..04 (REQUIREMENTS.md), 01-RESEARCH.md §5 §8 §9 §12.4
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(api-whatsapp): adicionar WhatsAppProperties fail-fast com 5 secrets" --files \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java \
  api-whatsapp/src/main/resources/application.yml \
  api-whatsapp/src/test/resources/application-test.yml \
  api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java
```
</commit>

<risks>
- **Spring Boot 3.5.x reflection no `@ConfigurationProperties` sem `@Component`** — RESEARCH Open Q4 confirmou via training data que `@EnableConfigurationProperties` na app principal e suficiente. Mitigacao: `assertThat(properties).isNotNull()` no test `boot_com_todas_as_5_propriedades_passa` confirma o bind funcionou.
- **Mensagem `@NotBlank` mismatch encoding** — Tests assertam mensagem literal "nao definida" sem til. Se algum dev/agente colocar til por correcao automatica, test quebra. Mitigacao: comentario no Properties.java explicando "sem til, alinhado com test".
- **`assertThatThrownBy(SpringApplication::run)` em test pode deixar contexto Spring sujo entre tests** — Spring Boot Test reseta contexto, mas SpringApplication.run() manual pode escapar. Se sintoma aparecer, isolar o teste em uma classe separada com `@DirtiesContext`.
- **A2 (RESEARCH §13): `management.endpoint.env.keys-to-sanitize` em yml sem actuator no classpath** — Risco de Spring Boot reclamar de property unknown. Confirmar empiricamente no `mvnw verify`. Se reclamar, comentar a chave inteira ate Phase 4.
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-03-SUMMARY.md` com:
- WhatsAppProperties criado com 5 @NotBlank + Duration callbackTimeout
- application.yml expandido com placeholders (autoconfigure.exclude ainda presente, marcado pra remocao em PLAN-04)
- application-test.yml criado com dummy values
- WhatsAppApplication com @EnableConfigurationProperties
- 7 tests verdes em WhatsAppPropertiesValidationTest
- Confirmacao de toString masking enforced via test
- Commit hash
- Reminder pra PLAN-04: remover `autoconfigure.exclude` em ambos os yml E adicionar datasource/jpa/flyway
</output>
