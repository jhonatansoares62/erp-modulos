---
phase: 01-fundacao-hmac-webhook
plan: 02
type: execute
wave: 2
depends_on:
  - "01-01"  # Embora api-whatsapp consuma lib-shared, em PLAN-02 a unica dependencia e estar no reator. ApiKeyFilter so e usado em PLAN-06.
files_modified:
  - pom.xml  # raiz — registrar <module>api-whatsapp</module>
  - api-whatsapp/pom.xml  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java  # NEW
  - api-whatsapp/src/main/resources/application.yml  # NEW (minimo)
autonomous: true
requirements:
  - CFG-04  # parcial — porta default 9193 vai aparecer aqui (sera completado em PLAN-03 com env var configuravel)
tags:
  - api-whatsapp
  - skeleton
  - maven
  - bootstrap

must_haves:
  truths:
    - "Reator inteiro (`mvnw verify` from root) builda com sucesso incluindo o novo modulo api-whatsapp"
    - "Modulo api-whatsapp tem pom.xml com parent erp-modulos 1.1.0-SNAPSHOT, deps minimas (lib-shared, web, validation, data-jpa, postgresql, flyway-core, flyway-database-postgresql, springdoc, h2 test)"
    - "WhatsAppApplication.java existe com @SpringBootApplication(scanBasePackages = \"br.com.erpkit\")"
    - "Diretorio tree minimo (controller/service/web/config/db.migration) existe (pode estar vazio em sub-diretorios — Maven nao exige)"
    - "Modulo NAO inclui Resilience4j (Phase 4 territory) e NAO inclui qualquer @Entity (Phase 2 territory)"
  artifacts:
    - path: "pom.xml"
      provides: "Registro de api-whatsapp no reator"
      contains: "<module>api-whatsapp</module>"
    - path: "api-whatsapp/pom.xml"
      provides: "Module pom com parent + dependencias minimas"
      contains: "<artifactId>api-whatsapp</artifactId>"
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java"
      provides: "Spring Boot main class"
      contains: "scanBasePackages"
    - path: "api-whatsapp/src/main/resources/application.yml"
      provides: "Config minima para boot (server.port, spring.application.name)"
      contains: "spring.application.name: api-whatsapp"
  key_links:
    - from: "pom.xml (raiz)"
      to: "api-whatsapp/pom.xml"
      via: "<modules><module>api-whatsapp</module>"
      pattern: "<module>api-whatsapp</module>"
    - from: "api-whatsapp/pom.xml"
      to: "br.com.erpkit:erp-modulos parent"
      via: "<parent>"
      pattern: "<artifactId>erp-modulos</artifactId>"
---

<objective>
Criar o esqueleto Maven do modulo `api-whatsapp` espelhando `api-email/pom.xml`, registra-lo no reator (`pom.xml` raiz), e criar a classe principal `WhatsAppApplication` mais um `application.yml` minimo (apenas o suficiente para boot vazio compilar). PLANs subsequentes (03-07) preencherao Properties, migrations, HMAC validator, filter, controller e tests.

Purpose: Bootstrap incremental — uma vez que este plano fecha verde, todos os planos seguintes ja podem rodar `mvnw verify -pl api-whatsapp` sem erro de modulo nao encontrado. Sem esse esqueleto, qualquer arquivo `.java` adicionado depois nao compila.

Output:
- `<module>api-whatsapp</module>` no `pom.xml` raiz (apos `api-consultas`)
- `api-whatsapp/pom.xml` herdando do parent + deps espelhadas em `api-email/pom.xml` (sem Resilience4j)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java`
- `api-whatsapp/src/main/resources/application.yml` minimo
- Diretorios placeholders para `controller/`, `service/`, `web/`, `config/`, `db/migration/`, `src/test/java/...`, `src/test/resources/`
- `mvnw verify` (root) BUILD SUCCESS
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
@.planning/phases/01-fundacao-hmac-webhook/01-01-SUMMARY.md
@pom.xml
@api-email/pom.xml
@api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java
@api-consultas/src/main/resources/application.yml

<interfaces>
<!-- Padrao a seguir do api-consultas -->

ConsultasApplication.java:
```java
@SpringBootApplication(scanBasePackages = "br.com.erpkit")
public class ConsultasApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsultasApplication.class, args);
    }
}
```

api-email/pom.xml estrutura:
- Parent: br.com.erpkit:erp-modulos:1.1.0-SNAPSHOT
- Dependencies: lib-shared, spring-boot-starter-web, spring-boot-starter-validation, spring-boot-starter-data-jpa, postgresql (runtime), flyway-core, springdoc-openapi-starter-webmvc-ui, spring-boot-starter-test, h2 (test)
- Build: spring-boot-maven-plugin

ApiKeyFilter (lib-shared, ja com 2-arg constructor de PLAN-01):
- `new ApiKeyFilter(apiKey)` — 1 arg, backward-compat
- `new ApiKeyFilter(apiKey, additionalPublicPaths)` — 2 args, paths publicos extras
- (consumido em PLAN-06, nao aqui)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Adicionar &lt;module&gt;api-whatsapp&lt;/module&gt; ao pom.xml raiz</name>
  <files>pom.xml</files>
  <action>
    Editar `pom.xml` (raiz) adicionando `<module>api-whatsapp</module>` apos `<module>api-consultas</module>` no bloco `<modules>` (linha ~26).

    Resultado esperado (per `01-RESEARCH.md` §11 linhas 1032-1041):
    ```xml
    <modules>
        <module>lib-shared</module>
        <module>lib-consultas-client</module>
        <module>api-email</module>
        <module>api-storage</module>
        <module>api-consultas</module>
        <module>api-whatsapp</module>
    </modules>
    ```

    NAO adicionar entrada em `<dependencyManagement>` (api-whatsapp nao e consumido por outros modulos como dependencia em Phase 1; lib-whatsapp-client entra em Phase 5). Per D-02 e 01-RESEARCH.md §11, a unica mudanca no root pom e a entrada em `<modules>`.
  </action>
  <verify>
    <automated>grep -c "api-whatsapp" pom.xml</automated>
  </verify>
  <done>
    - Bloco `<modules>` agora tem 6 modulos (5 antigos + api-whatsapp)
    - `<dependencyManagement>` permanece inalterado
    - Comando grep retorna >= 1
  </done>
</task>

<task type="auto">
  <name>Task 2: Criar api-whatsapp/pom.xml espelhado em api-email</name>
  <files>api-whatsapp/pom.xml</files>
  <action>
    Criar `api-whatsapp/pom.xml` copiando integralmente o XML da secao 7 do `01-RESEARCH.md` (linhas 702-783).

    Pontos chave (verificar contra a fonte):
    - `<parent>` aponta para `br.com.erpkit:erp-modulos:1.1.0-SNAPSHOT` (sem relativePath; herdado do diretorio acima)
    - `<artifactId>api-whatsapp</artifactId>`
    - `<name>ERP Kit - API WhatsApp</name>`
    - `<description>Modulo plugavel de integracao com WhatsApp Cloud API (reativo, custo zero)</description>`
    - Dependencies (sem version, herdadas do parent BOM):
      * lib-shared (br.com.erpkit)
      * spring-boot-starter-web
      * spring-boot-starter-validation
      * spring-boot-starter-data-jpa
      * postgresql (runtime scope)
      * flyway-core
      * flyway-database-postgresql
      * springdoc-openapi-starter-webmvc-ui
      * spring-boot-starter-test (test scope)
      * com.h2database:h2 (test scope)
    - `<build>` com `spring-boot-maven-plugin`
    - **NAO incluir Resilience4j** (Phase 4 territory) — explicito em RESEARCH.md §7 Notes
    - **NAO incluir nenhuma dep customizada nao listada** — manter strict alinhamento com api-email
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp dependency:tree -q -DoutputFile=/tmp/api-whatsapp-deps.txt && grep -c "spring-boot-starter-web" /tmp/api-whatsapp-deps.txt</automated>
  </verify>
  <done>
    - Arquivo existe e e XML valido
    - Maven consegue resolver dependencias (`dependency:tree` retorna sucesso)
    - spring-boot-starter-web aparece na arvore
    - Resilience4j NAO aparece na arvore (defesa contra introducao prematura)
    - lib-shared aparece na arvore (cross-modulo OK)
  </done>
</task>

<task type="auto">
  <name>Task 3: Criar WhatsAppApplication.java + estrutura de diretorios</name>
  <files>
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java,
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/.gitkeep,
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/.gitkeep,
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/.gitkeep,
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/.gitkeep,
    api-whatsapp/src/main/resources/db/migration/.gitkeep,
    api-whatsapp/src/test/java/br/com/erpkit/whatsapp/.gitkeep,
    api-whatsapp/src/test/resources/.gitkeep
  </files>
  <action>
    1. Criar `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java` com o conteudo da secao 5 do `01-RESEARCH.md` (linhas 583-598). Em PLAN-02 a anotacao `@EnableConfigurationProperties(WhatsAppProperties.class)` AINDA NAO deve ser incluida (a classe Properties so existe a partir de PLAN-03). Usar APENAS:

       ```java
       package br.com.erpkit.whatsapp;

       import org.springframework.boot.SpringApplication;
       import org.springframework.boot.autoconfigure.SpringBootApplication;

       @SpringBootApplication(scanBasePackages = "br.com.erpkit")
       public class WhatsAppApplication {
           public static void main(String[] args) {
               SpringApplication.run(WhatsAppApplication.class, args);
           }
       }
       ```

       PLAN-03 vai adicionar `@EnableConfigurationProperties(WhatsAppProperties.class)` quando criar a classe Properties.

    2. Criar diretorios placeholders (Maven exige que o source root exista, mas sub-diretorios podem ser vazios). Para garantir versionamento via git, adicionar `.gitkeep` em:
       - `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/`
       - `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/`
       - `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/`
       - `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/`
       - `api-whatsapp/src/main/resources/db/migration/`
       - `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/`
       - `api-whatsapp/src/test/resources/`

       Cada `.gitkeep` e arquivo vazio. PLANs 03-07 substituem alguns por arquivos reais (Properties, V*.sql, controller, etc).
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q</automated>
  </verify>
  <done>
    - WhatsAppApplication.java compila sem erros
    - Estrutura de diretorios existe (verificavel via `ls api-whatsapp/src/main/java/br/com/erpkit/whatsapp/`)
    - `mvnw -pl api-whatsapp compile` retorna BUILD SUCCESS
  </done>
</task>

<task type="auto">
  <name>Task 4: Criar application.yml minimo para boot</name>
  <files>api-whatsapp/src/main/resources/application.yml</files>
  <action>
    Criar `api-whatsapp/src/main/resources/application.yml` MINIMO — apenas o suficiente pra `WhatsAppApplication` bootar em test sem properties (PLAN-03 vai expandir com placeholders dos 5 secrets).

    Conteudo do PLAN-02 (subset do que aparece em RESEARCH.md §8 — completar em PLAN-03):

    ```yaml
    server:
      port: ${SERVER_PORT:9193}

    spring:
      application:
        name: api-whatsapp

      # Em PLAN-02 ainda nao temos datasource — desabilitar autoconfig de JPA/Flyway pra evitar erro de boot
      autoconfigure:
        exclude:
          - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
          - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
          - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration

    springdoc:
      api-docs:
        path: /v3/api-docs
      swagger-ui:
        path: /swagger-ui.html

    logging:
      level:
        br.com.erpkit.whatsapp: INFO
        org.springframework.web: INFO

    modulo:
      versao: 1.0.0
      api-key: ${API_KEY:}
    ```

    **Nota importante** sobre `autoconfigure.exclude`:
    - Em PLAN-02 ainda nao ha datasource configurado nem migrations. O modulo tem `spring-boot-starter-data-jpa` no classpath (per pom.xml secao 7 do RESEARCH), o que faz Spring Boot tentar auto-configurar Hibernate e Flyway no boot e falhar por falta de datasource. A exclusao temporaria desbloqueia o `mvnw verify` deste plano.
    - PLAN-03 (Properties) ainda mantem essa exclusao porque `application-test.yml` ainda nao existe.
    - PLAN-04 (Migrations + application-test.yml com H2) **REMOVE** essas 3 linhas de exclusao — datasource + JPA + Flyway entram em producao.
    - Documentar inline no yml: `# PLAN-02 only — removed in PLAN-04 when application-test.yml + H2 entram`
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp test -Dtest=NonexistentTest -q -DfailIfNoTests=false</automated>
  </verify>
  <done>
    - Arquivo `application.yml` existe
    - YAML e valido (Spring Boot consegue parsear)
    - `mvnw -pl api-whatsapp test` (sem testes ainda) retorna BUILD SUCCESS
    - Comentario inline indica que `autoconfigure.exclude` e temporario
  </done>
</task>

<task type="auto">
  <name>Task 5: Verificar build do reator inteiro</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -q` na raiz para verificar que todo o reator (incluindo o novo `api-whatsapp` vazio) constroi com sucesso. Esperado: 6 modulos compilam e testes existentes (lib-shared, api-email, api-storage, api-consultas) passam; api-whatsapp builda sem testes (ainda nao tem).

    Se algum erro:
    - "Cannot resolve api-whatsapp" → root pom Task 1 nao salvou
    - "Hibernate cannot configure" → autoconfigure.exclude nao ativou; verificar yml
    - "Resilience4j missing" → algo no pom esta errado; nao deve haver Resilience4j em api-whatsapp neste plano
  </action>
  <verify>
    <automated>./mvnw verify -q</automated>
  </verify>
  <done>
    - Output: BUILD SUCCESS
    - 6 modulos no Reactor Summary (lib-shared, lib-consultas-client, api-email, api-storage, api-consultas, api-whatsapp)
    - api-whatsapp aparece com tests = 0 (ainda nao temos)
    - Outros modulos com tests > 0 todos verdes
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Maven build → reator | Mudanca em pom.xml raiz pode quebrar todos os modulos se sintaxe XML invalida |
| api-whatsapp pom → BOM heredado | Versoes de deps vem do parent Spring Boot 3.5.9 (gerenciadas centralmente) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02-01 | Tampering | pom.xml raiz | mitigate | Mudanca minima (1 linha em `<modules>`); Task 5 verifica reator inteiro builda — qualquer XML mal-formado quebra na hora |
| T-02-02 | DoS | Application boot com JPA sem datasource | mitigate | `autoconfigure.exclude` temporaria de DataSource/JPA/Flyway evita boot failure; PLAN-04 reverte quando datasource real entra |
| T-02-03 | Configuration error | application.yml drift entre modulos | accept | Estrutura espelhada em api-email/api-consultas; revisao em PLAN-03 quando expandir |
</threat_model>

<verification>
## Phase Checks

1. `grep "<module>api-whatsapp</module>" pom.xml` retorna 1 match
2. `./mvnw -pl api-whatsapp compile` BUILD SUCCESS
3. `./mvnw -pl api-whatsapp dependency:tree | grep "resilience4j"` retorna VAZIO (Resilience4j nao deve estar)
4. `./mvnw verify` (root) BUILD SUCCESS — Reactor mostra 6 modulos
5. `find api-whatsapp/src -type d` mostra a estrutura esperada (controller, service, web, config, db/migration, test/java, test/resources)
</verification>

<success_criteria>
- Modulo api-whatsapp registrado no reator e builda com sucesso
- WhatsAppApplication.java compila com `scanBasePackages = "br.com.erpkit"` (per RESEARCH.md §5)
- application.yml minimo presente, com `autoconfigure.exclude` temporario marcado para remocao em PLAN-04
- Reator inteiro (`mvnw verify` from root) BUILD SUCCESS
- Plano fechado com 1 commit atomico
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(api-whatsapp): bootstrap esqueleto Maven do modulo

Adiciona api-whatsapp ao reator com pom.xml espelhado em api-email
(sem Resilience4j — Phase 4) e WhatsAppApplication minimo. application.yml
exclui temporariamente DataSource/JPA/Flyway autoconfig (sera revertido em
PLAN-04 quando migrations entrarem). Estrutura de pacotes preparada para
controller/service/web/config (a preencher em PLANs 03-07).

Refs: 01-RESEARCH.md §7 §11, 01-CONTEXT.md
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(api-whatsapp): bootstrap esqueleto Maven do modulo" --files \
  pom.xml \
  api-whatsapp/pom.xml \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java \
  api-whatsapp/src/main/resources/application.yml \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/.gitkeep \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/.gitkeep \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/.gitkeep \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/.gitkeep \
  api-whatsapp/src/main/resources/db/migration/.gitkeep \
  api-whatsapp/src/test/java/br/com/erpkit/whatsapp/.gitkeep \
  api-whatsapp/src/test/resources/.gitkeep
```
</commit>

<risks>
- **A6 (RESEARCH §13): `spring-boot-starter-data-jpa` em classpath sem `@Entity` causa erro de boot.** Mitigacao adotada: `autoconfigure.exclude` temporaria neste plan; reverter em PLAN-04 quando datasource H2 + migrations Flyway entrarem.
- **Versoes da BOM nao alinhadas**: deps sem `<version>` herdam do parent Spring Boot 3.5.9. Se alguma dep nao estiver no BOM, build falha. RESEARCH.md §14 confirmou que todas as deps da secao 7 estao gerenciadas.
- **Inno Setup / Windows path issues**: `find` no Git Bash em Windows pode comportar-se inesperadamente. Verificacoes preferem comandos Maven (cross-platform) sobre `find/grep` em filesystem.
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-02-SUMMARY.md` documentando:
- Estrutura criada (pom + Application + yml + diretorios)
- Reactor Summary do `mvnw verify` (6 modulos, todos verdes)
- Confirmacao de zero Resilience4j em api-whatsapp dependency tree
- Marca explicita: "autoconfigure.exclude e TEMPORARIO — reverter em PLAN-04"
- Commit hash criado
</output>
