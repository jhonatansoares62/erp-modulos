# Phase 5: lib-whatsapp-client - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning
**Mode:** Auto-decidido pelo Claude em `/gsd-autonomous --auto` apos auditoria do analog `lib-consultas-client` + requirements LIB-01..LIB-08

<domain>
## Phase Boundary

ERPs (ERP-MUDAS, ERP-CALHAS, futuros) consomem `api-whatsapp` via **starter Spring Boot reusavel** `lib-whatsapp-client`, espelhando o padrao ja estabelecido por `lib-consultas-client`:

1. **Auto-config condicional** — Adicionar a lib ao `pom.xml` de um ERP sem `app.modulos.whatsapp.enabled=true` nao cria nenhum bean. Modulo desligado por default; ERP habilita via env var/property.
2. **SPI de comandos** — ERP implementa interface `WhatsAppCommandHandler` (1 bean por keyword: `orcamento`, `boleto`, `aprovar`, etc) declarando como o comando entrante e processado. `WhatsAppCommandRegistry` coleta os handlers do contexto Spring e roteia exact-match primeiro, fallback para prefix via `handler.matches(comando)`.
3. **HTTP client com Resilience4j** — `WhatsAppClient` chama os 5 endpoints do `api-whatsapp` (`POST /api/whatsapp/enviar-texto|documento|botoes|lista` + `GET /api/whatsapp/status`) usando Spring `RestClient` (NAO `RestTemplate` — alinhamento com Phase 4 e LIB-06) + circuit breaker (10-call window, 50% threshold, 60s open) + retry exponencial (3 tentativas, 1s/2.0x). Config identica a `lib-consultas-client`.
4. **ObjectProvider graceful fallback** — Se modulo desabilitado OU `api-whatsapp` indisponivel, ERP injeta stub que loga WARN e retorna nulo sem lancar excecao. Padrao identico ao `ConsultasClient` em `ModulosController.java:40` (referencia em ERP-MUDAS, fora de escopo aqui).
5. **META-INF auto-config registration** — `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lista `br.com.erpkit.whatsapp.client.WhatsAppClientAutoConfiguration` (convencao Spring Boot 3.x; lib-consultas-client ja segue esse padrao).

**Em escopo (LIB-01..LIB-08, 8 reqs):**

- `lib-whatsapp-client/pom.xml` — espelha `lib-consultas-client/pom.xml` exatamente: `spring-boot-starter` + `spring-boot-configuration-processor` + `spring-web` (RestClient) + `resilience4j-circuitbreaker` + `resilience4j-retry`. Adicionar `<module>lib-whatsapp-client</module>` no `pom.xml` raiz.
- `WhatsAppClientAutoConfiguration` — `@AutoConfiguration` + `@ConditionalOnProperty(prefix="app.modulos.whatsapp", name="enabled", havingValue="true")` + `@EnableConfigurationProperties(WhatsAppProperties.class)` + `@Bean @ConditionalOnMissingBean WhatsAppClient whatsAppClient(WhatsAppProperties props)` + `@Bean @ConditionalOnMissingBean WhatsAppCommandRegistry commandRegistry(ObjectProvider<WhatsAppCommandHandler> handlers)`.
- `WhatsAppProperties` — `@ConfigurationProperties(prefix="app.modulos.whatsapp")` com campos:
  - `boolean enabled` (default `false`)
  - `String url` (default `"http://localhost:9193"`)
  - `String apiKey` (opcional, futuro — pode ser null)
  - `Duration timeout` (default `Duration.ofSeconds(5)`)
- `WhatsAppClient` interface + `WhatsAppClientImpl` — metodos publicos:
  - `EnvioResponse enviarTexto(String telefone, String texto)`
  - `EnvioResponse enviarDocumento(String telefone, byte[] bytes, String filename, String mimeType, String caption)` — internamente faz base64 antes do POST JSON (D-01 da Phase 4)
  - `EnvioResponse enviarBotoes(String telefone, String texto, List<BotaoDto> botoes)` — max 3
  - `EnvioResponse enviarLista(String telefone, String texto, List<SecaoDto> secoes)` — max 10 itens total
  - `StatusResponse status()` — proxy do `GET /api/whatsapp/status`
  - `boolean isOnline()` — health check leve (`GET /actuator/health` ou similar do api-whatsapp)
  - `boolean isHabilitado()` — espelha `props.enabled`
  - `String getCircuitBreakerState()` — diagnostico
- `WhatsAppCommandHandler` SPI — interface com 3 metodos:
  - `String getComando()` — keyword principal (ex: `"orcamento"`)
  - `default boolean matches(String comando)` — default `getComando().equalsIgnoreCase(comando)`; handler pode override para prefix (ex: `"aprovar 1234"` casa com `"aprovar"`)
  - `WhatsAppRespostaDto processar(WhatsAppComandoDto comando)` — recebe payload do api-whatsapp e retorna resposta a enviar
- `WhatsAppCommandRegistry` — `@Component` que recebe `ObjectProvider<WhatsAppCommandHandler>` no construtor, materializa List no boot. Metodo publico `Optional<WhatsAppCommandHandler> resolver(String comando)`:
  1. **Tier 1 (exact O(1))**: lookup em `Map<String, WhatsAppCommandHandler>` por `getComando().toLowerCase()` — handler primeiro registrado vence em colisao.
  2. **Tier 2 (fallback iter)**: se Tier 1 nao retorna, itera handlers chamando `matches()` em ordem de registracao (Spring DI order). Primeiro `true` vence. `O(n)` mas n e pequeno (~5-10 handlers tipicos).
  3. Empty se nenhum casar.
- DTOs (`br.com.erpkit.whatsapp.client.dto.*`):
  - `WhatsAppComandoDto` — recebido do api-whatsapp via callback: campos `{String telefone, String comando, String payload, Long idCliente, String wamid, String tipo, String mediaBase64 (optional)}`. Espelha `ComandoCallbackDTO` da Phase 3 (consistencia cross-modulo).
  - `WhatsAppRespostaDto` — retornado pelo handler: discriminator `tipo` (texto/documento/botoes/lista) + variant fields. Java record com factory methods `texto(String)`, `documento(byte[], String, String, String)`, `botoes(String, List<BotaoDto>)`, `lista(String, List<SecaoDto>)`. Null = handler nao quer responder (apenas registra).
  - `BotaoDto` (record `{String id, String title}`) — espelha o do api-whatsapp.
  - `SecaoDto` (record `{String titulo, List<ItemDto> itens}`) — espelha o do api-whatsapp.
  - `ItemDto` (record `{String id, String title, String description}`).
  - `EnvioResponse` (record `{String wamid}`) — espelha o do api-whatsapp Controller.
  - `StatusResponse` (record `{String status, String circuitBreakerState, String phoneNumberId}`) — espelha o do api-whatsapp.
- Excecoes (`br.com.erpkit.whatsapp.client.exception.*`):
  - `WhatsAppException` — wrapper de erros 4xx/5xx categorizados do api-whatsapp (com `statusCode` + `body`).
  - `WhatsAppIndisponivelException` — circuit breaker aberto OU connection refused OU timeout exausto.
- META-INF: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listando `br.com.erpkit.whatsapp.client.WhatsAppClientAutoConfiguration`.
- Tests:
  - `WhatsAppClientAutoConfigurationTest` — `ApplicationContextRunner` validando: (a) sem `enabled=true` → nenhum bean; (b) com `enabled=true` → 1 `WhatsAppClient` + 1 `WhatsAppCommandRegistry` no contexto.
  - `WhatsAppCommandRegistryTest` — coleta handlers via lista direta (sem Spring): exact-match wins; prefix fallback funciona; case-insensitive em getComando(); empty quando nada casa.
  - **NAO** ha WireMock aqui — integration tests vivem em Phase 6 (QA-02).
- Root `pom.xml` — adicionar `<module>lib-whatsapp-client</module>` no `<modules>` (similar ao `lib-consultas-client` ja listado).

**Fora de escopo:**

- **Engate em ERP-MUDAS** (ModulosController proxy + handlers `OrcamentoCommandHandler` etc.) — outro repo, outro GSD project — D2 in PROJECT.md proibe tocar.
- **Engate em ERP-CALHAS** — D2 explicito, piloto MUDAS apenas, CALHAS herda depois.
- **README.md** detalhado por modulo — Phase 6 (QA-04).
- **RUNBOOK.md** operacional — Phase 6 (QA-06).
- **SpringDoc OpenAPI** — lib starter nao expoe HTTP endpoints; OpenAPI vive no api-whatsapp (Phase 6 QA-05).
- **WireMock integration tests** — Phase 6 (QA-02).
- **`api-key` (X-WHATSAPP-API-KEY)** — campo `apiKey` em Properties existe (LIB-03) mas v1 nao exige no api-whatsapp; futuro. Header so e enviado se nao-blank (espelha lib-consultas-client).
- **Persistencia local na lib** — handler decide o que persistir; lib nao tem JPA/Flyway.
- **Auto-update da lib** — vive no `release.sh` do api-whatsapp (Phase 6 closeout pode incluir tag).

</domain>

<decisions>
## Implementation Decisions

### D-01: Mirror `lib-consultas-client` em estrutura + naming + Resilience4j config

`lib-whatsapp-client` adopta literalmente o layout de pastas e o estilo de Java codigo de `lib-consultas-client`:

- 4 packages: root (`br.com.erpkit.whatsapp.client`), `.dto`, `.exception` (sem `.config` — auto-config fica no root)
- 1 classe principal `WhatsAppClientImpl` com mesmo padrao `execute(Supplier)` + circuit breaker `decorateSupplier` + retry `decorateSupplier`
- Resilience4j config IDENTICA ao consultas: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=60s`, `permittedNumberOfCallsInHalfOpenState=3`, `maxAttempts=3`, `intervalFunction=ofExponentialBackoff(1000, 2.0)`, `retryExceptions=RestClientException`, `ignoreExceptions=HttpClientErrorException` (4xx categoricos nao retentam — alinhado com Phase 4 D-08).

**Por que mirror estrito:** consumidor (ERP) ja conhece o mental model de `lib-consultas-client`. Mesmas constantes, mesma forma de injecao, mesma config = zero curva de aprendizado. Divergir gera surpresa.

**Diferenca consciente vs consultas:**
- Usa **Spring `RestClient`** (Phase 4 / LIB-06), NAO `RestTemplate` como em ConsultasClientImpl — divergencia justificada pela diretiva explicita do LIB-06 + alinhamento com WhatsAppCloudClient da Phase 4 (RestClient fluent + Resilience4j).
- Tem o SPI `WhatsAppCommandHandler` + `WhatsAppCommandRegistry` — `lib-consultas-client` NAO tem SPI (e client REST puro). Nova primitiva, encapsulada em 2 arquivos.

### D-02: SPI `WhatsAppCommandHandler` interface default-method matching

```java
public interface WhatsAppCommandHandler {

    String getComando();

    default boolean matches(String comando) {
        return comando != null && comando.equalsIgnoreCase(getComando());
    }

    WhatsAppRespostaDto processar(WhatsAppComandoDto comando);
}
```

**Por que `default matches()` em vez de classe abstrata:**
- Handler simples (1-keyword): implementa apenas `getComando()` + `processar()`. Default `matches()` da match-equality OK.
- Handler com prefix (ex: `"aprovar 1234"`): override `matches(comando)` para `comando.toLowerCase().startsWith("aprovar")`. Granular sem boilerplate.
- Interface (vs classe abstrata) permite handler ser record imutavel ou herdar de outra classe ERP — flexibilidade.

**Trade-off aceito:** handler que esquece de override `matches()` em caso de prefix nao casa. Mitigacao: documentar com Javadoc + exemplos em README.md (Phase 6).

### D-03: Routing 2-tier (exact O(1) + fallback iter) com primeiro-registrado-vence

```java
public Optional<WhatsAppCommandHandler> resolver(String comando) {
    if (comando == null || comando.isBlank()) return Optional.empty();
    String normalizado = comando.trim();

    // Tier 1: exact-match O(1)
    WhatsAppCommandHandler exato = exactMap.get(normalizado.toLowerCase());
    if (exato != null) return Optional.of(exato);

    // Tier 2: fallback iter — first match wins
    return handlers.stream()
        .filter(h -> h.matches(normalizado))
        .findFirst();
}
```

**Por que 2-tier:**
- Tier 1 cobre 95% dos casos (handler nomeia exact keyword). O(1) lookup.
- Tier 2 cobre prefix-matching (`"aprovar 1234"` → handler `"aprovar"`). O(n) mas n pequeno.

**Por que primeiro-registrado-vence em colisao Tier 1:**
- Spring DI order e deterministico (`@Order` annotation respeitada).
- Em colisao de exact-match, ERP escolhe a precedencia explicitamente via `@Order(1)`. Sem `@Order`, ordem do classpath — documentado em README (Phase 6).

**Alternativa rejeitada:** lancar excecao em colisao. Rejeitado porque (a) Spring DI nao garante ordem cross-restart sem `@Order`, gerando bug intermitente; (b) ERP pode querer override em tempo de teste (substituir handler de prod por mock). Primeiro-vence + `@Order` da controle sem fragilidade.

### D-04: `WhatsAppRespostaDto` discriminator-record com factory methods

```java
public record WhatsAppRespostaDto(
    Tipo tipo,
    String texto,
    DocumentoPayload documento,
    List<BotaoDto> botoes,
    List<SecaoDto> secoes
) {
    public enum Tipo { TEXTO, DOCUMENTO, BOTOES, LISTA }

    public record DocumentoPayload(byte[] bytes, String filename, String mimeType, String caption) {}

    public static WhatsAppRespostaDto texto(String texto) {
        return new WhatsAppRespostaDto(Tipo.TEXTO, texto, null, null, null);
    }
    public static WhatsAppRespostaDto documento(byte[] bytes, String filename, String mimeType, String caption) {
        return new WhatsAppRespostaDto(Tipo.DOCUMENTO, null, new DocumentoPayload(bytes, filename, mimeType, caption), null, null);
    }
    public static WhatsAppRespostaDto botoes(String texto, List<BotaoDto> botoes) {
        return new WhatsAppRespostaDto(Tipo.BOTOES, texto, null, List.copyOf(botoes), null);
    }
    public static WhatsAppRespostaDto lista(String texto, List<SecaoDto> secoes) {
        return new WhatsAppRespostaDto(Tipo.LISTA, texto, null, null, List.copyOf(secoes));
    }
}
```

**Por que record com factory methods + discriminator enum:**
- Imutavel (record), seguro para passar entre threads.
- Factory methods fail-fast (assinatura obvia, IDE-friendly) + List.copyOf defensivo.
- Discriminator enum (`Tipo`) permite `WhatsAppClient` despachar para o metodo correto via `switch` exaustivo (Java 21 sealed pattern).

**Alternativa rejeitada:** sealed hierarchy (`sealed interface` + 4 records). Rejeitado por verbosidade (4 arquivos vs 1), e Jackson serialization mais simples com discriminator-string (futuro: lib poderia serializar resposta de volta ao api-whatsapp via REST se SPI for remoto — sealed exige `@JsonTypeInfo` config).

### D-05: `WhatsAppClient` despacha por `Tipo` quando recebe `WhatsAppRespostaDto` opcional helper

API publica oferece **dois layers**:

**Layer 1 (fine-grained):** 4 metodos tipados — `enviarTexto`, `enviarDocumento`, `enviarBotoes`, `enviarLista`. ERP que sabe exatamente o que quer chama direto. Tipo-safe.

**Layer 2 (convenience):** `EnvioResponse despachar(String telefone, WhatsAppRespostaDto resposta)` — switch sobre `resposta.tipo()`, chama Layer 1 internamente. Usado pelo Registry: quando handler retorna `WhatsAppRespostaDto`, registry chama `client.despachar(telefone, resposta)` sem se preocupar com tipo.

```java
public EnvioResponse despachar(String telefone, WhatsAppRespostaDto resposta) {
    if (resposta == null) return null;
    return switch (resposta.tipo()) {
        case TEXTO -> enviarTexto(telefone, resposta.texto());
        case DOCUMENTO -> enviarDocumento(telefone, resposta.documento().bytes(),
            resposta.documento().filename(), resposta.documento().mimeType(),
            resposta.documento().caption());
        case BOTOES -> enviarBotoes(telefone, resposta.texto(), resposta.botoes());
        case LISTA -> enviarLista(telefone, resposta.texto(), resposta.secoes());
    };
}
```

**Trade-off aceito:** API superficie ligeiramente maior (5 vs 4 metodos publicos). Vale o ergonomia.

### D-06: Tests so cobrem auto-config + registry (sem WireMock)

Cobertura Phase 5:

| Test | Scopo | Pattern |
|------|-------|---------|
| `WhatsAppClientAutoConfigurationTest` | enabled=false → 0 beans; enabled=true → WhatsAppClient + WhatsAppCommandRegistry | `ApplicationContextRunner` (mirror lib-consultas-client) |
| `WhatsAppCommandRegistryTest` | exact-match wins; prefix fallback; case-insensitive; null/blank empty; collision = first-registered | JUnit puro, sem Spring (registry pode ser instanciado direto) |

**Por que NAO WireMock aqui:**
- `WhatsAppClientImpl` chamando o api-whatsapp real e o cenario que Phase 6 (QA-02) cobre. Duplicar aqui aumenta tempo de build sem ganho.
- `lib-consultas-client` segue o mesmo padrao: apenas `ConsultasClientAutoConfigurationTest`, sem WireMock. Consistencia.

**O que NAO testa em Phase 5:** retry counters, circuit breaker state transitions, payload do JSON real — Phase 6.

### D-07: `WhatsAppProperties` campos minimos + 1 default seguro

```java
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
public class WhatsAppProperties {
    private boolean enabled = false;
    private String url = "http://localhost:9193";
    private String apiKey;  // optional, future
    private Duration timeout = Duration.ofSeconds(5);
    // getters/setters
}
```

**Por que `url=http://localhost:9193` e nao `http://api-whatsapp:9193` (DNS-aware):**
- ERP roda on-premise no mesmo host do api-whatsapp (D1 PROJECT.md). Localhost loopback e o caso default.
- DNS-aware exigiria service discovery (overkill v1).
- ERP que rode em host diferente override via env `APP_MODULOS_WHATSAPP_URL=http://...`.

**Por que `enabled=false` default:**
- ERP que adiciona a lib ao pom mas nao habilita explicitamente NAO ganha bean. Conservative — alinhado com lib-consultas-client.
- Habilita via `app.modulos.whatsapp.enabled=true` em `application.yml` ou env `APP_MODULOS_WHATSAPP_ENABLED=true`.

**Por que `apiKey` opcional:**
- `api-whatsapp` v1 nao exige API key (mesmo host, mesmo trust). LIB-03 ja lista `apiKey:String (opcional, futuro)`.
- Quando setado nao-blank, `WhatsAppClient` envia header `X-API-Key: <value>` em cada request — espelha consultas pattern.

### D-08: GroupId + artifactId + version do modulo

- `<groupId>br.com.erpkit</groupId>` — alinhado com lib-consultas-client + api-whatsapp + monorepo.
- `<artifactId>lib-whatsapp-client</artifactId>` — naming convention.
- `<version>` herda do parent `<relativePath>../pom.xml</relativePath>` — mesmo que lib-consultas-client (release uniforme).
- Java target 21 (heranca do parent).

</decisions>

<code_context>
## Existing Code Insights

**Analog direto (espelhar literalmente):**

- `lib-consultas-client/pom.xml` — template do pom.xml; copiar e renomear `consultas` → `whatsapp`. Dependencias identicas exceto: trocar `spring-boot-starter` por `spring-boot-starter-web` se RestClient nao vier transitivamente (testar primeiro).
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java` — 19 linhas, template do WhatsAppClientAutoConfiguration. Substituir: classe nome, prefix `consultas` → `whatsapp`, beans criados.
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java` — 148 linhas, template do WhatsAppClientImpl. Adaptacoes obrigatorias:
  - Trocar `RestTemplate` por `RestClient` (Spring 6.x fluent API — Phase 4 ja usa)
  - Substituir 2 metodos `consultarCep`/`consultarCnpj` por 5 metodos `enviarTexto`/`enviarDocumento`/`enviarBotoes`/`enviarLista` + `status` + helper `despachar`
  - Substituir `NOME_MODULO = "consultas"` por `"whatsapp"`
  - Bearer NAO se aplica aqui (api-whatsapp e local, sem token Meta — token vive no api-whatsapp)
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasProperties.java` — template do WhatsAppProperties.
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/exception/Consultas{Exception,IndisponivelException}.java` — template das excecoes.
- `lib-consultas-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — template do meta-inf (1 linha, listando o auto-config FQN).
- `lib-consultas-client/src/test/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfigurationTest.java` — template do test.

**Referencia Phase 4 (api-whatsapp WhatsAppController):**

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java` — 5 endpoints definindo contratos REST que `WhatsAppClient` precisa chamar.
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/dto/*.java` — 5 DTOs request + EnvioResponse + StatusResponse + BotaoDto + SecaoDto + ItemDto. Phase 5 vai espelhar os request DTOs como wire format do POST (JSON via RestClient).

**Referencia Phase 3 (ComandoCallbackDTO):**

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/listener/ComandoCallbackDTO.java` (record Phase 3 D-06) — define wire format do POST `/api/modulos/whatsapp/comando` ao ERP. `WhatsAppComandoDto` (em lib-whatsapp-client) espelha esse record para que ERP receba o mesmo shape.

**Padrao a referenciar (ERP-MUDAS, fora de escopo):**

- `C:\projetos\ERP-MUDAS\src\main\java\br\com\mudas\erp\shared\modulo\controller\ModulosController.java:40` — ObjectProvider graceful fallback do ConsultasClient. Quando ERP-MUDAS for engatar lib-whatsapp-client (milestone seguinte), espelhar esse pattern aqui tambem.

**Root pom.xml:**

- `C:\projetos\erp-modulos\pom.xml` — secao `<modules>` precisa de novo entry `<module>lib-whatsapp-client</module>`. Verificar tambem `<dependencyManagement>` para version bom (lib-consultas-client esta listado).

</code_context>

<specifics>
## Specific Ideas

- **Manter naming portugues** — `enviarTexto`/`enviarDocumento` (NAO `sendText`), `getComando`/`processar` (NAO `getCommand`/`process`). Alinhado com convencoes do monorepo (api-email/api-storage/api-consultas).
- **Records sempre que possivel** — DTOs sao records (Java 21), Properties e classe POJO (Spring Boot config processor exige getters/setters classicos).
- **Logging estruturado** — `log.warn("Modulo whatsapp offline: {}", e.getMessage())` (mirror consultas) — sempre incluir nome do modulo no log message para grep em multi-module logs.
- **Sem custom RestClient builder** — usar `RestClient.create()` simples + `requestFactory(SimpleClientHttpRequestFactory)` para timeout (mirror consultas).
- **Bean Validation @Valid em `WhatsAppComandoDto` NAO** — DTO recebido como parametro do handler (POJO), validation vive no api-whatsapp Controller que ja faz @Valid antes de enviar.
- **`WhatsAppCommandRegistry` thread-safe por construcao** — `exactMap` populado no constructor (imutavel apos init); `handlers` e `List.copyOf(...)` imutavel. `resolver()` sem synchronized.

</specifics>

<deferred>
## Deferred Ideas

- **WhatsApp client API key auth** (envio de header `X-API-Key`) — campo ja existe em `WhatsAppProperties.apiKey`; quando setado nao-blank, header enviado. v1 nao exige no api-whatsapp; Phase 6 RUNBOOK pode documentar pra futuras releases que enderecam multi-tenant.
- **DNS-aware service discovery** — `url=http://localhost:9193` default e suficiente. Future: env override para clusters.
- **Annotation `@WhatsAppHandler(comando="orcamento")` em vez de SPI interface** — Spring meta-annotations + classpath scanning. Rejeitado v1 (interface e mais simples e Spring DI ordena handlers por `@Order` ja).
- **Async dispatch (handler retorna `CompletableFuture<WhatsAppRespostaDto>`)** — registry assume sync por v1. Futuro: variant interface `WhatsAppAsyncCommandHandler` ou metodo default async.
- **Persistencia local na lib** (cache de comandos processados, fila de retry, etc.) — handler decide. Lib starter nao tem JPA dependency.

</deferred>

<canonical_refs>
## Canonical References (MANDATORY)

Downstream agents (researcher, planner, executor) DEVEM ler estes paths exatos:

- `.planning/REQUIREMENTS.md` (linhas 53-61) — LIB-01..LIB-08 sao a spec normativa
- `.planning/ROADMAP.md` (linhas 96-107) — Phase 5 goal + 5/5 success criteria
- `.planning/PROJECT.md` (linhas 52-60) — Resumo + key decisions D1-D10
- `.planning/phases/04-outbound-trava-24h-whatsappcontroller/04-CONTEXT.md` — Phase 4 decisions com referencias arquiteturais relevantes (ComandoCallbackDTO da Phase 3 D-06, contratos REST do Controller Phase 4)
- `.planning/phases/04-outbound-trava-24h-whatsappcontroller/04-06-SUMMARY.md` — Phase 4 closeout, DTOs estaveis
- `lib-consultas-client/` (RAIZ inteira) — analog principal, espelhar literalmente arquitetura
- `lib-consultas-client/pom.xml` — template direto
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java`
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java`
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasProperties.java`
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/exception/ConsultasIndisponivelException.java`
- `lib-consultas-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `lib-consultas-client/src/test/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfigurationTest.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java` — contrato REST a chamar
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/listener/ComandoCallbackDTO.java` — wire format de comando entrante (Phase 3 D-06)
- `pom.xml` (root) — adicionar entry em `<modules>`

Sem refs externos (proprietary spec). Sem ADRs separados — decisoes vivem em PROJECT.md.

</canonical_refs>
