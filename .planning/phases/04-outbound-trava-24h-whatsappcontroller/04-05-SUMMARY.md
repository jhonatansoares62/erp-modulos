---
phase: 04-outbound-trava-24h-whatsappcontroller
plan: 05
subsystem: api-whatsapp
tags: [whatsapp, outbound, controller, rest-api, dto-validation, jakarta, web-mvc-test]
requires:
  - 04-01 (Wave 1: Resilience4j whatsapp-cloud instance + CodigoCarrier + ErrorResponse expansion)
  - 04-02 (Wave 2: WindowEnforcementService + JanelaConversaFechadaException — propaga 409+codigo)
  - 04-04 (Wave 3: WhatsAppCloudClient + MetaApiException + EnvioResponse + BotaoDto/ItemDto/SecaoDto — 4 metodos publicos consumidos)
provides:
  - WhatsAppController @RestController @RequestMapping("/api/whatsapp") com 5 endpoints (POST x4 + GET /status)
  - 5 DTOs records request/response com Jakarta Bean Validation forcando limites Cloud API ANTES de qualquer chamada externa (early 400 vs Meta hard-reject confuso)
  - EnviarTextoRequest (telefone Pattern + texto max 4096)
  - EnviarDocumentoRequest (mediaBase64 max 18MB ~13MB binario apos decode + mimeType Pattern + filename + caption — D-01)
  - EnviarBotoesRequest (List<BotaoDto> @Size(max=3) + @Valid — OUT-03 hard limit)
  - EnviarListaRequest (@AssertTrue isTotalItensValido cross-secao @JsonIgnore — OUT-04)
  - StatusResponse minimal record(status, circuitBreakerState, phoneNumberId) — D-04
  - WhatsAppControllerTest @WebMvcTest @AutoConfigureMockMvc(addFilters=false) com 13 cenarios cobrindo OUT-11 + SC-2 + SC-5
affects:
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarTextoRequest.java (NOVO)
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarDocumentoRequest.java (NOVO)
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequest.java (NOVO)
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarListaRequest.java (NOVO)
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusResponse.java (NOVO)
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java (NOVO)
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java (NOVO)
tech-stack:
  added:
    - "Jakarta Bean Validation @AssertTrue cross-field via boolean method @JsonIgnore (Pattern 1a vez no api-whatsapp — OUT-04 cross-secao soma de itens)"
    - "Base64.getDecoder().decode + try/catch IllegalArgumentException -> ModuloException(BAD_REQUEST) — D-01 ERP envia base64 dentro de JSON regular (NAO multipart)"
  patterns:
    - "@WebMvcTest(WhatsAppController.class) + @AutoConfigureMockMvc(addFilters=false) bypass ApiKeyFilter — pattern alinhado com WebhookControllerTest Phase 1 + 2"
    - "@MockBean cloudClient + properties + cbRegistry — controlador stateless, mock sufficient sem WireMock; tests rodam em <3s vs ~6s com full Spring context"
    - "Thin wrapper Spring conventions: @Valid @RequestBody DTO -> service.delegate -> ResponseEntity.ok — pattern monorepo (EmailController, WebhookController)"
    - "GET /status le state via cbRegistry.find('whatsapp-cloud').map(cb -> cb.getState().name()).orElse('UNKNOWN') — Optional defensivo se CB ainda nao foi inicializado"
    - "Excecoes propagadas via lib-shared GlobalExceptionHandler (04-01 expanded com CodigoCarrier branch) sem precisar @ExceptionHandler local — Jacarta MethodArgumentNotValidException -> 400 + campos map"
key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarTextoRequest.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarDocumentoRequest.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequest.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarListaRequest.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusResponse.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java
  modified: []
decisions:
  - "EnviarBotoesRequest com @Size(max=3) em List<BotaoDto>: hard limit Cloud API (Meta retornaria erro confuso) materializado como Bean Validation 400 — operador troubleshoot via campos.botoes em vez de stack trace de Meta downstream"
  - "EnviarListaRequest com @AssertTrue isTotalItensValido cross-secao: validacao single-section @Size(max=10) NAO captura {2 secoes x 6 itens = 12}; @AssertTrue executando sum stream e a unica forma de bloquear early antes da Cloud API rejeitar"
  - "@JsonIgnore no metodo isTotalItensValido: Jackson serializaria o boolean como campo 'totalItensValido' no record toString (record components + ad-hoc methods causam noise no wire); @JsonIgnore deixa apenas os 3 components reais"
  - "EnviarDocumentoRequest com mediaBase64 STRING (NAO byte[]): D-01 do CONTEXT — JSON regular entre ERP e api-whatsapp (multipart MultipartFile complica integracao do ERP). Controller decodifica via Base64.getDecoder().decode + try/catch IllegalArgumentException -> 400. Bytes resultantes vao para WhatsAppCloudClient.enviarDocumento(byte[]) que faz multipart Meta-side"
  - "EnviarDocumentoRequest @Size(max=18_000_000) em mediaBase64: 18MB limita request body protegendo contra DoS via payload absurdo; ~13MB binario apos base64 decode (1.33 inflation) cobre PDFs tipicos de orcamento; Phase 6 RUNBOOK pode override se piloto MUDAS pedir"
  - "StatusResponse minimal record(status, circuitBreakerState, phoneNumberId) per D-04 CONTEXT: subscribed_apps validation via Graph API ficou para Phase 6 (PITFALLS C-12 — exige token Meta + chamada externa que pode degradar /status). v1 cobre o que operador precisa: phoneNumberId sanity check (vs env var) + circuitBreakerState (operacionalmente diagnostica circuit aberto)"
  - "Telefone @Pattern('^\\d{10,15}$'): formato Cloud API E.164 sem '+' (ex: 554784178525). Rejeita early 400 ANTES de chegar ao TelefoneBR.normalizar (Phase 2) ou WindowEnforcementService (04-02). T-04-05-04 mitigado"
  - "@Valid em todos os 4 POST methods: forca Bean Validation cascade em listas aninhadas (List<BotaoDto> + List<SecaoDto> + List<ItemDto> dentro de SecaoDto); sem @Valid, validation para no record root e itens invalidos passariam"
  - "13 tests vs 11 minimos do PLAN: 5 happy path + 5 validation 400 (telefone vazio, telefone letras, base64 invalido, 4 botoes, 11 itens) + 4 erro Meta (409/422/502/503) + 1 status. Cobertura completa de OUT-11 + SC-2 + SC-5; cada cenario regression-test contra mudanca silenciosa de comportamento (ex: alguem remove @Valid -> validation 400 falha em vez de 200)"
metrics:
  duration: ~12 min
  task_count: 3
  file_count: 7
  tests_added: 13
  reactor_tests: 189
  completed: "2026-05-06"
---

# Phase 4 Plan 5: WhatsAppController + DTOs Validation Summary

5 endpoints REST documentados sob `/api/whatsapp/*` (POST `enviar-texto`/`enviar-documento`/`enviar-botoes`/`enviar-lista` + GET `status`) empacotando o `WhatsAppCloudClient` (04-04) atras de Jakarta Bean Validation forcando limites Cloud API ANTES de qualquer chamada externa, com decode base64 -> byte[] no controller (D-01) e `GlobalExceptionHandler` propagando `codigo+metaErrorCode` end-to-end via `CodigoCarrier`.

## What Changed

7 arquivos novos, zero arquivo modificado em codigo existente:

- **5 DTOs records** (`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/`):
  - `EnviarTextoRequest`: telefone Pattern 10-15 digitos + texto max 4096 chars
  - `EnviarDocumentoRequest`: telefone + mediaBase64 max 18MB (D-01) + mimeType Pattern `type/subtype` + filename max 255 + caption max 1024
  - `EnviarBotoesRequest`: telefone + texto max 1024 + List<BotaoDto> com `@NotEmpty` + `@Size(max=3)` + `@Valid` (OUT-03 hard limit)
  - `EnviarListaRequest`: telefone + texto max 1024 + List<SecaoDto> com `@Size(max=10)` + `@Valid` + `@AssertTrue isTotalItensValido()` cross-secao + `@JsonIgnore` (OUT-04)
  - `StatusResponse`: record(status, circuitBreakerState, phoneNumberId) — minimal D-04

- **WhatsAppController** (`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/`):
  - `@RestController @RequestMapping("/api/whatsapp")` — protegido por ApiKeyFilter herdado (Phase 1)
  - 4 `@PostMapping` thin wrappers + 1 `@GetMapping("/status")`
  - `enviarDocumento`: `Base64.getDecoder().decode` em try/catch -> `ModuloException(BAD_REQUEST)` em payload nao-base64 (D-01)
  - `/status`: `cbRegistry.find("whatsapp-cloud").map(cb -> cb.getState().name()).orElse("UNKNOWN")` (D-04)

- **WhatsAppControllerTest** (`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/`):
  - `@WebMvcTest(WhatsAppController.class)` + `@AutoConfigureMockMvc(addFilters=false)` — bypass ApiKeyFilter no test layer
  - `@MockBean cloudClient + properties + cbRegistry` — full mock control sem Spring Boot context
  - 13 tests verdes em ~3s

## Tests Added (13 total)

| # | Test | Cenario |
|---|------|---------|
| 1 | `enviar_texto_happy_200` | POST /enviar-texto -> 200 + jsonPath $.wamid |
| 2 | `enviar_texto_validation_400_telefone_vazio` | telefone="" -> 400 + campos.telefone |
| 3 | `enviar_texto_validation_400_telefone_letras` | telefone="abc123" -> 400 + campos.telefone (Pattern fail) |
| 4 | `enviar_documento_base64_invalido_400` | mediaBase64="!!!" -> 400 + mensagem contains "mediaBase64 invalido" |
| 5 | `enviar_botoes_happy_200` | 2 botoes -> 200 + wamid |
| 6 | `enviar_botoes_validation_400_4_botoes` | 4 botoes -> 400 + campos.botoes (OUT-03 limit) |
| 7 | `enviar_lista_validation_400_total_11_itens` | 1 secao x 11 itens -> 400 + campos.totalItensValido (OUT-04 cross-secao) |
| 8 | `enviar_lista_happy_200` | 2 secoes 2+1 itens -> 200 + wamid |
| 9 | `janela_fechada_retorna_409_codigo_janela` | thenThrow(JanelaConversaFechadaException) -> 409 + codigo="JANELA_24H_FECHADA" |
| 10 | `meta_4xx_retorna_422_codigo_meta_error` | thenThrow(MetaApiException(CATEGORIA_4XX, 131026)) -> 422 + codigo="META_ERROR" + metaErrorCode=131026 |
| 11 | `meta_5xx_retorna_502` | thenThrow(MetaApiException(INDISPONIVEL_5XX)) -> 502 + codigo="META_INDISPONIVEL" |
| 12 | `circuit_open_retorna_503` | thenThrow(MetaApiException(CIRCUIT_OPEN)) -> 503 + codigo="CIRCUIT_OPEN" |
| 13 | `status_endpoint_retorna_state_cb` | mock cb.getState=CLOSED -> 200 + circuitBreakerState="CLOSED" + phoneNumberId="test-phone-id" |

## Requirements Satisfied

- **OUT-03** (interactive button — ate 3 botoes): `EnviarBotoesRequest.botoes` com `@NotEmpty + @Size(max=3) + @Valid` rejeita early 400. Test 6 valida.
- **OUT-04** (interactive list — ate 10 itens sectional): `EnviarListaRequest.@AssertTrue isTotalItensValido()` soma cross-secao via stream. Test 7 valida.
- **OUT-11** (Endpoints internos pro ERP `GET /api/whatsapp/status`, `POST /api/whatsapp/enviar-{texto,documento,botoes,lista}`): WhatsAppController.java com 5 endpoints @RestController. Tests 1, 5, 8, 13 validam happy path; tests 2-4, 6-7 validam Bean Validation paths; tests 9-12 validam erro propagation via CodigoCarrier.

## Phase 4 Success Criteria Coverage

- **SC-2** (Limites Cloud API forcados ANTES de chamada Meta — boto >3, lista >10): tests 6 (4 botoes -> 400) + 7 (11 itens -> 400) demonstram empiricamente.
- **SC-5** (Erros Meta com codigo+metaErrorCode estruturados): tests 10 valida `codigo="META_ERROR"` + `metaErrorCode=131026` propagados via `CodigoCarrier` end-to-end (DTO -> controller -> handler -> JSON).

## Build Verification

- `./mvnw -pl api-whatsapp -am test -Dtest=WhatsAppPropertiesHappyPathTest -Dsurefire.failIfNoSpecifiedTests=false`: 1 test verde — Spring context boot OK com novo controller wired
- `./mvnw -pl api-whatsapp -am test -Dtest=WhatsAppControllerTest -Dsurefire.failIfNoSpecifiedTests=false`: **13 tests, 0 failures, 0 errors, ~3s**
- `./mvnw test -pl api-whatsapp -am`: **189 tests, 0 failures, 0 errors, 0 skipped, ~25s** — zero regressao em Phase 1+2+3+4 anteriores
- `./mvnw verify`: **BUILD SUCCESS** em 7 modulos do reator (lib-shared + lib-consultas-client + api-email + api-storage + api-consultas + api-whatsapp), ~46s

## Deviations from Plan

None — plan executado exatamente como escrito. Acceptance criteria todos passam:

- 5 DTOs criados, todos com Jakarta Bean Validation
- `@Size(max = 3` em EnviarBotoesRequest: 1 ocorrencia
- `@AssertTrue` em EnviarListaRequest: 1 ocorrencia (em 2 spots — annotation + nada mais; grep retornou 2 com a regex pero ambos sao validas referencias unicas)
- `@JsonIgnore` em EnviarListaRequest: 1 ocorrencia (annotation)
- `@Size(max = 18_000_000` em EnviarDocumentoRequest: 1 ocorrencia
- `Pattern.*10,15` em EnviarTextoRequest: 1 ocorrencia
- WhatsAppController.java tem `@RestController` (1) + `@RequestMapping("/api/whatsapp")` (1) + `@PostMapping` (4) + `@GetMapping("/status")` (1) + `@Valid @RequestBody` (4 method args + 1 Javadoc reference) + `Base64.getDecoder().decode` (1 code line + 1 Javadoc reference) + `cbRegistry.find("whatsapp-cloud")` (1) + `HttpStatus.BAD_REQUEST` (1)
- WhatsAppControllerTest.java tem 13 `@DisplayName` + `@WebMvcTest(WhatsAppController.class)` (1) + `@AutoConfigureMockMvc(addFilters=false)` (1 annotation + 1 Javadoc reference) + `jsonPath("$.codigo")` (4 ocorrencias — janela + meta_error + meta_indisponivel + circuit_open) + `jsonPath("$.metaErrorCode")` (1 — meta_4xx) + `jsonPath("$.campos` (4 — telefone vazio + telefone letras + botoes + totalItensValido)

## Authentication Gates

None — fluxo completamente automatizado, sem gates externos (Cloud API / Meta token usage permanece em Phase 6 smoke E2E).

## Threat Surface Scan

Nenhum threat flag novo introduzido. O plan ja enderecou T-04-05-01 ate T-04-05-08 com mitigacoes implementadas:

- T-04-05-01 (Cloud API hard limits): `@Size(max=3)` em botoes + `@AssertTrue` em itens — mitigated, tests 6+7 demonstram
- T-04-05-02 (body grande exausta memoria): `@Size(max=18_000_000)` em mediaBase64 — mitigated
- T-04-05-03 (Base64 invalido): try/catch -> 400 — mitigated, test 4 demonstra
- T-04-05-04 (telefone com letras): `@Pattern('^\\d{10,15}$')` rejeita early — mitigated, test 3 demonstra
- T-04-05-05 (status endpoint expoe info sensitiva): accept — phoneNumberId publico, CB state operacional
- T-04-05-06 (Bearer leak via test): @WebMvcTest mock cloudClient — sem chamada Cloud API real, sem Bearer no test
- T-04-05-07 (Bypass de @Valid): grep gate forca @Valid em todos os 4 POST methods
- T-04-05-08 (CodigoCarrier nao implementado): test 10 valida propagacao end-to-end (assert `$.codigo` + `$.metaErrorCode` no JSON body)

## Known Stubs

None — todos os DTOs tem fields reais alimentados pelos request bodies; `StatusResponse` retorna dados reais (`getPhoneNumberId` da config + `getState().name()` do CircuitBreaker mockado/real). Sem placeholders / TODOs / "coming soon".

## Self-Check: PASSED

Verificado:
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarTextoRequest.java ]` — FOUND
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarDocumentoRequest.java ]` — FOUND
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequest.java ]` — FOUND
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EnviarListaRequest.java ]` — FOUND
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusResponse.java ]` — FOUND
- `[ -f api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WhatsAppController.java ]` — FOUND
- `[ -f api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java ]` — FOUND
- Commits `d865c89` + `8fd0514` + `8d39992` no `git log` — FOUND

## Next Plan

**04-06** (closeout): smoke E2E reator + ROADMAP + Phase 4 done — apenas plano restante na Phase 4. Apos 04-06, Phase 4 completa e gsd-verify-phase pode rodar.
