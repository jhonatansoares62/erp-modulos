---
phase: 04-outbound-trava-24h-whatsappcontroller
reviewed: 2026-05-05T00:00:00Z
depth: standard
files_reviewed: 30
files_reviewed_list:
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspect.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/aspect/JanelaProtegida.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/BotaoDto.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequest.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarDocumentoRequest.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarListaRequest.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarTextoRequest.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnvioResponse.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ItemDto.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/SecaoDto.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusResponse.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/exception/JanelaConversaFechadaException.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/exception/MetaApiException.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WindowEnforcementService.java
  - api-whatsapp/src/main/resources/application.yml
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspectTest.java
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClientTest.java
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WindowEnforcementServiceTest.java
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/MultipartUploadSpikeTest.java
  - api-whatsapp/src/test/resources/application-test.yml
  - lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java
  - lib-shared/src/main/java/br/com/erpkit/shared/exception/CodigoCarrier.java
  - lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java
findings:
  critical: 3
  warning: 9
  info: 6
  total: 18
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-05-05T00:00:00Z
**Depth:** standard
**Files Reviewed:** 30
**Status:** issues_found

## Summary

A Phase 4 entrega o outbound completo (texto/documento/botoes/lista) + trava de janela 24h
via aspect AOP + WhatsAppController + cache de media (sha256, TTL 30d) + propagacao
estruturada de erros via `CodigoCarrier`. A arquitetura atende as travas de custo zero
de Meta (sem template, janela 24h hard-block, fallback para `MetaApiException` apos
Resilience4j) e o contrato HTTP esta consistente com o desenho.

Foram detectados **3 BLOCKERs** centrados em (a) corrupcao silenciosa do cache de
media sob race em entradas existentes, (b) perda de informacao da
`JanelaConversaFechadaException` no payload HTTP (campo `ultimaMensagemEm` nunca chega
ao ERP — contrato divergente do que o codigo+javadoc prometem), e (c) `mensagens_log`
para `enviarDocumento` perdendo o `filename` (substituindo-o por `caption` que pode ser
`null`), o que mata o valor de auditoria do log para o caso mais comum (PDF sem caption).

Alem disso, varios WARNINGs sobre transacionalidade ausente em `MediaCacheService.registrarUpload`,
`ClassCastException` nao mapeada em `extrairWamid`, comentario do `application-test.yml`
desalinhado com a flag H2 que esta sendo de fato setada, e estado do circuit breaker
reportado como `"UNKNOWN"` no GET /status quando o registry retorna `Optional.empty()`
(que silencia bug de bootstrap). INFOs cobrem comentarios stale, deprecacoes Java 21,
e oportunidades de hardening.

---

## Critical Issues

### CR-01: `MediaCacheService.registrarUpload` perde a entrada do cache em race com refresh

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java:75-90`

**Issue:** A "upsert simples" `findById -> delete -> save` NAO esta envolvida em
`@Transactional`. Sob load real (multiplos sends concorrentes do mesmo PDF), a sequencia:

```
T1: findById(hash) -> presente, schedule delete
T1: delete commitado
T2: findById(hash) -> empty (T1 ja deletou)
T2: save(novo)  -> commit
T1: save(novo)  -> DataIntegrityViolationException -> silenciado
```

ate aqui ok — mas se `T1.save` lanca DIVE entre `T1.delete` e o catch, o auto-commit
default do Spring Data JPA ja persistiu `T1.delete`. Resultado: row antiga foi removida,
mas T1 nao escreveu nada (catch silencia) — **e T2 pode nem ter chegado a executar**.
Janela curta onde `buscarMediaId` retorna `Optional.empty()` para um arquivo que
**estava** em cache valido — gera reupload supurfluo a Meta (cost zero ainda preservado,
mas o pattern de "TTL estrito 30d sem sliding" do D-04 e quebrado: o hit anterior some
mid-flight). Pior cenario: `T1.save` falha por outra razao (constraint, conexao perdida)
apos `T1.delete` ter commitado — cache fica permanentemente vazio para o hash,
proximo envio re-uploada (PDF de 13MB) ate a proxima `registrarUpload` ter sucesso.

O comentario "Atomico do ponto de vista do thread" no codigo afirma uma propriedade
que nao existe — operacoes JPA sem `@Transactional` em `@Service` rodam cada uma
em sua propria transacao curta (ou em nenhuma transacao via auto-commit JDBC), o
que NAO e atomico.

**Fix:** Marcar o metodo `@Transactional` para garantir delete+save no mesmo boundary
transacional + isolamento de leitura. Catch de DIVE continua valido para silenciar
race com OUTRA tx concorrente, mas o estado intermediario (deleted-but-not-yet-saved)
fica invisivel para outros threads.

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional
public void registrarUpload(byte[] bytes, String mediaId) {
    String hash = sha256Hex(bytes);
    Instant expira = Instant.now().plus(TTL);
    try {
        repository.findById(hash).ifPresent(repository::delete);
        repository.flush();  // forca delete antes do save para evitar PK collision na mesma tx
        repository.save(new MediaCache(hash, mediaId, expira));
        log.debug("MediaCache registrado: hash={} mediaId={} expira={}", hash, mediaId, expira);
    } catch (DataIntegrityViolationException e) {
        log.debug("MediaCache race em registrarUpload: hash={}", hash);
    }
}
```

Idealmente, substituir `delete + save` por um UPDATE direto seguido de fallback INSERT
(que e a forma natural de upsert em PG/H2), mas o minimo aceitavel para corrigir o
BLOCKER e o `@Transactional`.

---

### CR-02: `JanelaConversaFechadaException.ultimaMensagemEm` NUNCA chega ao ERP via HTTP

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/exception/JanelaConversaFechadaException.java:25-28`
**File:** `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java:16-32`

**Issue:** O javadoc da `JanelaConversaFechadaException` afirma:

> Carrega `telefone` normalizado e o `Instant` da ultima mensagem entrante (...) para que
> o handler global construa um `ErrorResponse` informativo e o ERP consiga decidir como
> escalar com o cliente.

Mas o `GlobalExceptionHandler.handleModuloException` somente propaga `codigo` e
`metaErrorCode` do `CodigoCarrier`. **`ultimaMensagemEm` nunca e copiada para o
`ErrorResponse`** — o ERP recebe `409 + codigo=JANELA_24H_FECHADA` e nada sobre
**quando** a janela fechou. O contrato anunciado (ERP "decide como escalar") esta
quebrado: o ERP nao sabe se faltam 30 minutos para a janela fechar de novo ou 22 horas.

O `WhatsAppControllerTest.janela_fechada_retorna_409_codigo_janela` so afirma `$.codigo`,
nao verifica que `ultimaMensagemEm` nao aparece — o gap passa silencioso.

**Fix:** Duas opcoes:

1. **Estender o contrato** (recomendado se essa info e realmente util para o ERP):
   adicionar campo `ultimaMensagemEm` ou um mapa `detalhes` em `ErrorResponse` e
   propagar em `handleModuloException` quando a excecao for `JanelaConversaFechadaException`
   (via instanceof especifico ou via outra interface tipo `DetalhesCarrier`).

2. **Remover a promessa do javadoc** se nao ha plano de propagar — atualizar
   `JanelaConversaFechadaException` para nao afirmar que o `Instant` chega no payload.
   Manter o getter so para logs internos e testes.

Como esta hoje, codigo + javadoc + testes formam um trio internamente consistente
mas o COMPORTAMENTO observavel pelo ERP esta divergente — bug de contrato.

```java
// Opcao 1 (estender ErrorResponse com mapa generico):
public class ErrorResponse {
    // ...
    private Map<String, Object> detalhes;
    // getters/setters
}

// GlobalExceptionHandler:
if (ex instanceof JanelaConversaFechadaException jce) {
    Map<String, Object> det = new HashMap<>();
    det.put("ultimaMensagemEm", jce.getUltimaMensagemEm());
    det.put("telefone", jce.getTelefone());
    error.setDetalhes(det);
}
```

---

### CR-03: `enviarDocumento` perde `filename` no log de auditoria — substitui por `caption` (nullable)

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java:161`

**Issue:** A persistencia outbound em `mensagens_log` para `enviarDocumento`:

```java
mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "document", caption, mediaId));
```

passa `caption` como `conteudo`. Para o caso comum (PDF de orcamento sem caption — o
WhatsAppControllerTest.enviarDocumento_cache_hit_pula_upload sequer envia caption,
e o teste passa `null`), o registro fica como `conteudo=NULL`. **`filename` (que e o
ID humanamente legivel do documento — "orcamento-1234.pdf") nao e logado em lugar
algum**, exceto no log INFO do logger SLF4J (linha 162) que nao e auditavel para
queries do ERP.

Comparativo: `enviarTexto` loga o `texto` em `conteudo`; `enviarBotoes`/`enviarLista`
logam o body `texto`. Apenas `document` perde o identificador unico do anexo.
Operacionalmente: cliente pergunta "voces enviaram o orcamento?" e o ERP consulta
`mensagens_log WHERE direcao='out' AND tipo='document' AND telefone=...` — recebe
linhas com `conteudo=NULL` e `media_id=<opaco do Meta>` — sem caminho para mapear
de volta ao arquivo enviado.

**Fix:** Persistir `filename` (sempre presente, validado @NotBlank) como conteudo, OU
concatenar com caption quando houver. Sugestao minima:

```java
String conteudoLog = caption != null && !caption.isBlank()
    ? filename + " | " + caption
    : filename;
mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "document", conteudoLog, mediaId));
```

Alternativa mais limpa: extender `MensagemLog` com coluna `filename` (migration adicional),
mas isso e Phase 5+. Para Phase 4, garantir que o `filename` esteja capturado e suficiente.

---

## Warnings

### WR-01: `MediaCacheService.registrarUpload` permite mediaId divergente entre threads

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java:75-90`

**Issue:** Mesmo apos correcao do CR-01, o algoritmo permite que dois threads
concorrentes (T1 + T2) cada um faca seu proprio upload a Meta, recebendo `mediaId`
diferentes para o mesmo `bytes`/`hash`. A linha "vencedora" persiste — a outra fica
orfa em Meta (Meta nao expoe DELETE para media nao usada — ela expira em ~30d).
Sem custo, mas sub-otimo: dobro de upload de PDFs grandes. O comentario claims
"Phase 2 IdempotencyService pattern" mas aquela classe usa um lock pessimista no banco
(`SELECT ... FOR UPDATE`) que NAO e replicado aqui.

**Fix:** Adicionar locking otimista via versao no `MediaCache` ou implementar a
deduplicacao de upload no nivel de chamada — antes de chamar `uploadMedia`, fazer um
lock `SELECT FOR UPDATE` na linha do hash (ou usar `ON CONFLICT DO NOTHING`-like via
Spring Data Native). Aceitavel para Phase 4 deixar como esta se documentado, mas
o codigo atual afirma "race protection" que nao existe na pratica.

---

### WR-02: `extrairWamid` faz cast inseguro `(Map<String, Object>) list.get(0)` sem fallback

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java:287`

**Issue:** Se o response do Meta for malformado (ex: `messages: [42]` ou
`messages: ["string"]` por bug futuro do Meta), o cast lanca `ClassCastException`
NAO capturada. Diferente das outras checagens (linha 280 `null`, 284 `instanceof`,
289 `instanceof`), o cast da linha 287 confia que `list.get(0)` e um `Map`.
Vai escapar como `ClassCastException` -> Resilience4j fallback -> classifier -> default
case `INDISPONIVEL_5XX` (linha 350) — o ERP recebe `502 META_INDISPONIVEL` por uma
falha que nao e indisponibilidade, e sim contrato Meta inesperado.

**Fix:** Adicionar `instanceof` check antes do cast:

```java
Object firstObj = list.get(0);
if (!(firstObj instanceof Map)) {
    throw new IllegalStateException("Response do Meta com messages[0] nao-Map: " + response);
}
@SuppressWarnings("unchecked")
Map<String, Object> first = (Map<String, Object>) firstObj;
```

---

### WR-03: `MediaCacheServiceTest.race_em_registrar_silencia` nao prova race real

**File:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java:96-138`

**Issue:** O teste de race usa duas threads + `CountDownLatch` start gate. Por causa do
H2 in-memory + transacoes default isoladas curtas, a probabilidade real de o cenario
race (`T1.save` antes de `T2.save` consigam DIVE) e muito baixa: na maioria das
execucoes, T1 termina o ciclo `findById -> delete -> save` antes de T2 nem comecar.
O assertion `count == 1` e verdadeiro pela natureza do PK, nao pela protecao do catch.
O teste pode passar mesmo se o catch fosse removido — nao prova o que afirma testar.

**Fix:** Forcar a race com sincronizacao explicita: usar um `Phaser` ou `CyclicBarrier`
entre as threads, ou usar `@SpyBean MediaCacheService` com `Mockito.doAnswer` para
introduzir um delay artificial entre `findById` e `save` no thread T1. Alternativa:
testar o race via `MediaCacheRepository` puro — chamar `repository.save` em duas
threads simultaneamente apos um `start.await()` e verificar que UM lanca DIVE, OUTRO
nao. O teste atual e falsamente confortavel.

---

### WR-04: `application-test.yml` tem comentario stale referenciando `DATABASE_TO_UPPER=false`

**File:** `api-whatsapp/src/test/resources/application-test.yml:18,41`

**Issue:** O bloco de comentarios afirma:

> DATABASE_TO_UPPER=false — preserve identifiers em lowercase

Mas a URL JDBC real usa `DATABASE_TO_LOWER=TRUE`. Sao flags H2 diferentes:
`DATABASE_TO_UPPER=false` (deprecada) e `DATABASE_TO_LOWER=TRUE` (atual). O
comentario na linha 41 ("Em H2 com DATABASE_TO_UPPER=false") tambem refere a flag
obsoleta. Operador que tente debug do schema H2 vai procurar a flag errada e ficar
confuso.

**Fix:** Atualizar comentarios para referenciar `DATABASE_TO_LOWER=TRUE`
consistentemente com a URL real.

---

### WR-05: `WhatsAppController.status` retorna `"UNKNOWN"` silenciosamente em estado de bug

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java:91-97`

**Issue:** O endpoint GET /status faz `cbRegistry.find("whatsapp-cloud").map(...).orElse("UNKNOWN")`.
Resilience4j Spring Boot starter pre-instancia CBs declarados em `application.yml`,
entao em prod normal `find` SEMPRE retorna `Optional.of(cb)`. Se algum dia
`find` retornar `empty` (nome do instance mudado em yaml, falha de auto-config silenciada
por outro plugin etc.), o endpoint retorna `"UP"` + `circuitBreakerState=UNKNOWN` —
operador interpreta como "OK, talvez circuit nao foi tocado ainda" e o bug de
configuracao passa em branco. `"UP"` + `"UNKNOWN"` deveria ser `"DEGRADED"` ou
`"DOWN"`.

**Fix:** Trocar `.orElse("UNKNOWN")` por logica de status mais agressiva — se o CB
deveria existir e nao existe, status nao e `"UP"`:

```java
@GetMapping("/status")
public ResponseEntity<StatusResponse> status() {
    Optional<CircuitBreaker> cbOpt = cbRegistry.find("whatsapp-cloud");
    if (cbOpt.isEmpty()) {
        log.error("CircuitBreaker 'whatsapp-cloud' nao registrado — bug de configuracao");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new StatusResponse("DOWN", "MISSING", properties.getPhoneNumberId()));
    }
    return ResponseEntity.ok(new StatusResponse("UP", cbOpt.get().getState().name(), properties.getPhoneNumberId()));
}
```

---

### WR-06: `JanelaEnforcementAspect` confia em `args[0] instanceof String` mas `String` pode ser vazio/branco

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspect.java:54-65`

**Issue:** O aspect aceita `args[0]` String mesmo quando vazia ou em branco:
`if (args.length == 0 || !(args[0] instanceof String telefone))` so valida o tipo.
Se algum metodo `@JanelaProtegida` for chamado internamente com `telefone=""` ou
`null` (DTO Bean Validation roda na FRONTEIRA do controller — se uma chamada interna
do servico ou de outro modulo passar pelo cliente diretamente, telefone vazio passa),
a query `findByTelefone("")` retorna `empty` -> `JanelaConversaFechadaException`
genuino mas sem mensagem util ("telefone  sem mensagem entrante registrada" — espaco
duplo onde o numero deveria estar).

Hoje os controllers validam telefone via `@Pattern(regexp = "^\\d{10,15}$")` antes
de chamar o cliente, entao o cenario e improvavel — mas a defesa no aspect e fraca
e `null` em particular nao dispara o `instanceof` mas tambem nao e detectado
explicitamente (pattern matching com null retorna false silenciosamente).

**Fix:** Validacao explicita:

```java
Object first = args.length > 0 ? args[0] : null;
if (!(first instanceof String telefone) || telefone.isBlank()) {
    throw new IllegalStateException(
        "Metodo @JanelaProtegida deve ter telefone nao-vazio como primeiro arg: "
            + pjp.getSignature());
}
```

---

### WR-07: `MultipartUploadSpikeTest` valida cliente HTTP DIFERENTE da producao

**File:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/MultipartUploadSpikeTest.java:103-107`

**Issue:** O spike usa `JdkClientHttpRequestFactory` (Java HttpClient), mas
`WhatsAppCloudClient` em prod usa `SimpleClientHttpRequestFactory` (HttpURLConnection).
Consequencias do empirico:

1. O spike NAO prova que `SimpleClientHttpRequestFactory` (linha 100 `WhatsAppCloudClient`)
   serializa multipart corretamente para Meta — apenas que `JdkClientHttpRequestFactory`
   serializa.
2. O comentario na linha 99-102 do spike admite "Java 21 defaults to HTTP/2 with ALPN,
   but WireMock plain HTTP cannot speak HTTP/2 plaintext" — mas a producao
   (`SimpleClientHttpRequestFactory` + `HttpURLConnection`) e HTTP/1.1 by default,
   entao a mudanca era desnecessaria. Mais grave: o `WhatsAppCloudClientTest`
   tambem usa `SimpleClientHttpRequestFactory` (via auto-config do `WhatsAppCloudClient`)
   mas o spike usou um path completamente diferente — duas validacoes empirias divergentes.

**Fix:** Refatorar o spike para usar `SimpleClientHttpRequestFactory` igual a producao,
ou remover o spike (ja temos cobertura empirica em `WhatsAppCloudClientTest.upload_media_envia_3_fields_obrigatorios`
linha 367-389 que usa o `WhatsAppCloudClient` real). A duplicacao com path divergente
e pior que ausencia.

---

### WR-08: `EnviarDocumentoRequest.mediaBase64` valida 18MB mas decodificacao acontece sem clamp

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarDocumentoRequest.java:27`
**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java:69-79`

**Issue:** O `@Size(max = 18_000_000)` valida o COMPRIMENTO da string base64, nao
do payload total do request HTTP. Spring deserializa o JSON inteiro em memoria antes
de Bean Validation rodar — Jackson pode aceitar request bodies arbitrariamente grandes
se o `server.tomcat.max-http-form-post-size` ou equivalente nao estiver setado.
Defesa em profundidade ausente: nao ha config no `application.yml` limitando o
tamanho do body HTTP. Atacante autenticado (com API key) pode enviar `{"mediaBase64": "<2GB>"}` —
JSON parser carrega tudo em heap, OutOfMemoryError mata o processo.

**Fix:** Adicionar limite no Spring Boot:

```yaml
spring:
  servlet:
    multipart:
      max-request-size: 25MB  # apesar de nao ser multipart, ajuda em alguns casos
server:
  tomcat:
    max-http-form-post-size: 25MB
    max-swallow-size: 25MB
```

ou explicitamente via `@RequestMapping(consumes = ...)` + `HttpInputMessage` validation.
Adicionalmente, considerar streaming em vez de string in-memory para o caso de Phase 5+
mover para multipart real.

---

### WR-09: `JanelaEnforcementAspect.@Around` resolve `instanceof String` com pattern var mas argumento nao-final pode ser shadowed

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspect.java:56`

**Issue:** Pattern matching `args[0] instanceof String telefone` — `telefone` e a
mesma variavel no escopo do throw e do `verificarJanela(telefone)`. Se um futuro refactor
adicionar passos antes de `windowService.verificarJanela(telefone)` (ex: log, sanitizacao
intermediaria), e facil acidentalmente reatribuir/sombrear `telefone`. Estilo defensivo:
extrair para variavel explicitamente nomeada ou usar `final` (Java 21 ainda nao tem
`final` em pattern var).

Tambem: o aspect nao loga o telefone que falhou validacao — em test o stack trace
mostra a `Signature`, mas operador em prod ve apenas "primeiro argumento String"
sem saber se foi `null`, `Long`, etc. Anexar o tipo real no message ajuda diagnostico.

**Fix:**

```java
Object first = args.length > 0 ? args[0] : null;
if (!(first instanceof String telefone)) {
    throw new IllegalStateException(
        "Metodo @JanelaProtegida deve ter telefone como primeiro argumento String "
            + "(recebido: " + (first == null ? "null" : first.getClass().getName()) + "): "
            + pjp.getSignature());
}
```

---

## Info

### IN-01: `MensagemLog.toString` afirma "NAO expor conteudo (PII)" mas `wamid` e `telefone` sao igualmente PII

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java:117-125`

**Issue:** O comentario declara intencao de proteger PII mas `telefone` (PII por LGPD)
e `wamid` (proxy do telefone via Meta) ainda aparecem no `toString()`. Inconsistencia
de threat model. Phase 4 nao introduz isso (entity criada em Phase 2), mas vale notar
em audit posterior.

**Fix:** Mascarar `telefone` em `toString` (ex: ultimos 4 digitos), ou remover o
comentario que sugere protecao parcial.

---

### IN-02: `MediaCacheServiceTest` usa `Thread.currentThread().getId()` deprecated em Java 19+

**File:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java:109`

**Issue:** `Thread.getId()` foi marcado deprecated em Java 19; substituido por
`Thread.threadId()`. Compila com warning em Java 21.

**Fix:**
```java
service.registrarUpload(bytes, "meta-id-race-" + Thread.currentThread().threadId());
```

---

### IN-03: `WhatsAppCloudClient` usa raw `Map` types em varios pontos

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java:124,159,186,223,247,253`

**Issue:** Linhas como `Map response = postMessages(body)` e o tipo de retorno
`private Map postMessages(...)` usam `Map` raw — gera warnings de unchecked operations.
Mantem comportamento funcional mas perde checagem do compilador.

**Fix:** Tipar como `Map<String, Object>` consistentemente:

```java
private Map<String, Object> postMessages(Map<String, Object> body) {
    return restClient.post()
        // ...
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
}
```

---

### IN-04: Comentario do `MediaCacheService` afirma "Pattern Phase 2 (IdempotencyService)" mas pattern divergente

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MediaCacheService.java:28-33,85-89`

**Issue:** Os comentarios afirmam "Pattern Phase 2 (IdempotencyService)" mas o
IdempotencyService da Phase 2 usa lock pessimista (`SELECT FOR UPDATE`) ou um INSERT
com `ON CONFLICT DO NOTHING` (RESEARCH §2.4). `MediaCacheService` usa
`findById -> delete -> save + try/catch DIVE` que e um pattern diferente. Comentario
engana o proximo leitor para acreditar que ja temos garantias que nao temos.

**Fix:** Reescrever comentario para descrever o pattern real (best-effort upsert com
catch DIVE, race tolerada, sem lock).

---

### IN-05: `EnviarListaRequest.@AssertTrue` valida soma de itens mas nao retorna o numero atual no message

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarListaRequest.java:41`

**Issue:** Mensagem do `@AssertTrue` e estatica: "Total de itens em todas as secoes
excede 10 (Cloud API limit)". O ERP recebe 400 sem saber QUANTOS itens excederam.
Nao e bug, mas reduce DX. Phase 5+ pode considerar mover para um custom validator
que injete o total real na mensagem.

**Fix:** (opcional) Custom validator dinamico ou adicionar `total: <N>` ao `campos`.

---

### IN-06: `WhatsAppCloudClient` log de upload pode vazar `filename` com caracteres especiais

**File:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java:259-260`

**Issue:** `log.info("WhatsApp Cloud uploadMedia ok: filename={} mime={} sizeBytes={} mediaId={}", filename, ...)`
loga `filename` sem escape. Se o ERP enviar `filename="orcamento\n[FATAL]\nlinha-falsa"`,
o log fica corrompido / ataca log parsers. Filename validacao no DTO `@Size(max=255)`
nao impede newlines/control chars.

**Fix:** Adicionar `@Pattern(regexp = "^[^\\r\\n\\t]+$")` em `EnviarDocumentoRequest.filename`
ou sanitizar antes de logar.

---

_Reviewed: 2026-05-05T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
