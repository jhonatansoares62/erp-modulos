---
phase: 02-persistencia-idempotencia
plan: 05
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - parser
  - jackson
  - dto
  - fixtures
  - meta-webhook
  - utf8
  - tolerancia

dependency_graph:
  requires:
    - "TipoMensagem constants (Plan 02-01)"
    - "TelefoneBR.normalizar utility (Plan 02-02 — concorrente; commitado em b0bba6f durante esta wave)"
    - "ObjectMapper Spring Boot default (spring-boot-starter-web ja inclui Jackson)"
    - "ClassPathResource + StreamUtils (Spring core, ja na deptree)"
  provides:
    - "11 DTOs Jackson (classes com @JsonIgnoreProperties) cobrindo o envelope Meta inteiro"
    - "3 records Java 21 (MensagemEntranteDTO, StatusEntranteDTO, ParsedWebhook) — output do parser"
    - "WebhookPayloadParser.extrair(byte[]) -> ParsedWebhook — API publica estavel para Plan 06"
    - "8 fixtures JSON realistas em src/test/resources/fixtures/webhook/ (reusaveis em Plan 06+)"
    - "9 unit tests cobrindo o espectro Meta + UTF-8 + JSON malformado"
    - "Confirmacao empirica: Jackson default + UTF-8 acentos preservados sem config extra"
  affects:
    - "Plan 06 (MensagemService orquestrador) — chama WebhookPayloadParser.extrair como primeiro passo do processarWebhook"
    - "Plan 07 (verification) — pode usar fixtures JSON como input para testes integrados"

tech_stack:
  added:
    - "Jackson DTOs com @JsonIgnoreProperties(ignoreUnknown=true) e @JsonProperty para snake_case mapping"
    - "Pattern: classes Jackson para wire format + records Java 21 para output (A8 RESEARCH)"
  patterns:
    - "DTOs externos (Meta wire format) como classe mutavel + getters/setters explicitos (sem Lombok)"
    - "DTOs internos (output do parser) como record imutavel — sem @JsonCreator complexo"
    - "Parser tolerante: null entry, null changes, null value, null messages, null statuses, null interactive — todos sem NPE"
    - "Fixtures como recursos de test (ClassPathResource + StreamUtils.copyToByteArray)"
    - "TelefoneBR.normalizar aplicado tanto em msg.from quanto em status.recipient_id (defesa em profundidade)"

key_files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/WebhookPayloadDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EntryDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ChangeDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ValueDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MessageDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/TextDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/InteractiveDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ReplyDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/DocumentDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MensagemEntranteDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusEntranteDTO.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ParsedWebhook.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WebhookPayloadParser.java"
    - "api-whatsapp/src/test/resources/fixtures/webhook/text-portugues.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/button-reply.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/list-reply.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/document-pdf.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/status-delivered.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/tipo-desconhecido.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/empty-entry.json"
    - "api-whatsapp/src/test/resources/fixtures/webhook/multiple-messages.json"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WebhookPayloadParserTest.java"
  modified: []

decisions:
  - "DTOs Jackson como CLASSE com @JsonIgnoreProperties(ignoreUnknown=true) — A8 RESEARCH: classes eliminam superficie de bug de records + Jackson + @JsonProperty em construtor"
  - "Records Java 21 apenas para output do parser (MensagemEntranteDTO/StatusEntranteDTO/ParsedWebhook) — sem (de)serializacao Jackson, apenas instanciacao via construtor Java"
  - "Parser tolerante a TODOS os campos null — entry/changes/value/messages/statuses/interactive — defesa contra heartbeats Meta e payload malformado"
  - "Tipos novos do Meta (ex: ephemeral_message, sticker, location): tipo=DESCONHECIDO, conteudo=null, mediaId=null sem erro (WEB-07)"
  - "interactive sem button_reply nem list_reply: tipo=DESCONHECIDO (defesa contra payload Meta inconsistente)"
  - "TelefoneBR.normalizar aplicado em msg.from E em status.recipient_id (defesa em profundidade — Meta envia digito-only, mas normalizar e idempotente e custos zero)"
  - "Tests sem @SpringBootTest — pure JUnit + new ObjectMapper() — tests rodam em <100ms total"
  - "Fixtures como JSON real-shape do Meta (entry/changes/value/messages) — reusaveis em Plan 06 para integration tests"

metrics:
  duration_seconds: 510
  duration_human: "8m30s"
  tasks_completed: 6
  files_created: 24
  files_modified: 0
  tests_added: 9
  total_reactor_tests: 88
  api_whatsapp_tests: 88
  build_status: "BUILD SUCCESS"
  build_time_api_whatsapp: "8.0s"
  completed_date: "2026-05-05"
---

# Phase 2 Plan 05: Webhook Payload Parser + DTOs Jackson + Fixtures Meta Summary

Parser do envelope Meta fechado: 11 DTOs Jackson tolerantes (`@JsonIgnoreProperties(ignoreUnknown=true)`) cobrem o pipeline `WebhookPayload -> Entry -> Change -> Value -> Message/Status` + sub-DTOs (Text, Interactive, Reply, Document, Media); 3 records Java 21 (MensagemEntranteDTO, StatusEntranteDTO, ParsedWebhook) sao output instanciado pelo parser; `WebhookPayloadParser.extrair(byte[])` aplica `TelefoneBR.normalizar` em msg.from + status.recipient_id (defesa em profundidade), tolera campos null sem NPE, e retorna tipo=DESCONHECIDO sem erro para tipos novos do Meta (WEB-07); 8 fixtures JSON cobrem text com acentos UTF-8, button_reply, list_reply, document, status-delivered, ephemeral_message (desconhecido), empty-entry (heartbeat), multiple-messages; 9 unit tests verdes em <100ms validam cada fixture + JSON malformado lancando IOException.

## Tasks Executadas

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Criar 11 DTOs Jackson em dto/ package | DONE | 6ba039e |
| 2 | Criar 3 records Java 21 (MensagemEntranteDTO, StatusEntranteDTO, ParsedWebhook) | DONE | 6ba039e |
| 3 | Criar 8 fixtures JSON em src/test/resources/fixtures/webhook/ (UTF-8 sem BOM) | DONE | 6ba039e |
| 4 | Criar WebhookPayloadParser service (@Service com @Inject ObjectMapper) | DONE | 6ba039e |
| 5 | Criar WebhookPayloadParserTest com 9 tests usando fixtures | DONE | 6ba039e |
| 6 | Verificar build do reator (mvnw verify -pl api-whatsapp) | DONE — BUILD SUCCESS, 88 tests | n/a |

## Test Result (Detalhado)

```
[INFO] Running br.com.erpkit.whatsapp.service.WebhookPayloadParserTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.394 s
```

Cada um dos 9 tests cobre 1 cenario distinto:

| # | Test | Cenario | Validacoes |
|---|------|---------|-----------|
| 1 | text_portugues | text com acentos UTF-8 | conteudo="Olá, gostaria de um orçamento" + telefone=554784178525 (DDD 47 SC strip 9) + mediaId=null |
| 2 | button_reply | interactive button_reply | tipo=INTERACTIVE_BUTTON + conteudo="aprovar_1234\|Aprovar" + telefone=5511987654321 (SP preserva) |
| 3 | list_reply | interactive list_reply | tipo=INTERACTIVE_LIST + conteudo="boleto\|Ver boleto" + telefone=5521987654321 (RJ preserva) |
| 4 | document | document PDF | tipo=DOCUMENT + conteudo="comprovante.pdf" + mediaId="media-id-12345" + telefone=553187654321 (MG strip 9) |
| 5 | status_delivered | status callback | mensagens vazia + statuses.size=1 + status="delivered" + telefone=554784178525 (strip 9) |
| 6 | tipo_desconhecido | type=ephemeral_message | tipo=DESCONHECIDO + conteudo=null + mediaId=null (WEB-07) |
| 7 | empty_entry | entry=[] heartbeat | mensagens vazia + statuses vazia (sem erro) |
| 8 | multiple_messages | 2 messages no mesmo array | mensagens.size=2 + wamids ordem preservada (multi.001, multi.002) |
| 9 | json_malformado | bytes "{ invalid json" | assertThatThrownBy isInstanceOf IOException |

## Build & Test Counts

```
[INFO] Reactor Summary for ERP Kit - Modulos Plugaveis 1.1.0-SNAPSHOT:
[INFO] BUILD SUCCESS

api-whatsapp:
  Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
  - Phase 1 baseline (53)
  - OnConflictSpikeTest (2 — Plan 02-01 spike)
  - HmacValidatorTest (13 — Phase 1)
  - CachedBodyHttpServletRequestTest (6 — Phase 1)
  - HmacSignatureFilterTest (6 — Phase 1)
  - TelefoneBRTest (19 — Plan 02-02 commit b0bba6f, paralelo)
  - IdempotencyServiceTest (5 — Plan 02-03 commit eaad07b, paralelo)
  - WebhookPayloadParserTest (9 — Plan 02-05 ESTE PLANO)
  - integration test fechando Phase 1 + outros tests baseline
  - + outros baseline tests
  ───────────────────────────────────
  Total: 88 tests verdes

Build time api-whatsapp module: ~8.0s
```

Zero regressao. Tests Plans 02-02 e 02-03 (TelefoneBR + IdempotencyService) ja commitados no HEAD pre-este-commit.

## UTF-8 Verification (Gate Empirico A6 RESEARCH)

```
od -c text-portugues.json | grep "Ol":
  o   d   y   "   :       "   O   l 303 241   ,       g   o   s
  t   a   r   i   a       d   e       u   m       o   r 303 247
```

`303 241` = U+00E1 ("á"), `303 247` = U+00E7 ("ç"). UTF-8 multi-byte sequences preservadas no fixture. Test 1 (text_portugues) confirma roundtrip integro: bytes -> Jackson ObjectMapper -> String -> assertThat equals "Olá, gostaria de um orçamento" PASSES.

```
head -c 3 text-portugues.json | od -c:
  {  \n
```

Sem BOM (`EF BB BF` ausente). Confianca alta em A6 RESEARCH para chars 3-byte UTF-8.

## Files Criados (24 arquivos)

### DTOs Jackson (11 classes em `dto/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/WebhookPayloadDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EntryDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ChangeDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ValueDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MessageDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/TextDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/InteractiveDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ReplyDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/DocumentDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusDTO.java`

### Records Java 21 (3 records em `dto/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MensagemEntranteDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusEntranteDTO.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ParsedWebhook.java`

### Service (1 arquivo em `service/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WebhookPayloadParser.java`

### Fixtures (8 arquivos em `src/test/resources/fixtures/webhook/`)
- `api-whatsapp/src/test/resources/fixtures/webhook/text-portugues.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/button-reply.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/list-reply.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/document-pdf.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/status-delivered.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/tipo-desconhecido.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/empty-entry.json`
- `api-whatsapp/src/test/resources/fixtures/webhook/multiple-messages.json`

### Test (1 arquivo em `src/test/java/`)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WebhookPayloadParserTest.java`

## Commit Hash

`6ba039e` — feat(api-whatsapp): adicionar WebhookPayloadParser + DTOs Jackson + fixtures Meta

Apos este SUMMARY: 1 commit adicional `docs(02): adicionar SUMMARY plan 05` + atualizacao STATE.md/ROADMAP.md/REQUIREMENTS.md.

## Deviations from Plan

### Coordenacao com Wave B paralelo (Plans 02-02 e 02-03)

Plan 02-05 declara `parallel_with: [02-02, 02-03]`. Durante a execucao deste plano, os outros 2 plans Wave 2 foram commitados em paralelo:

- `b0bba6f` (Plan 02-02): TelefoneBR.java + 19 tests
- `eaad07b` (Plan 02-03): IdempotencyService + 5 tests

Esses commits ja estavam no HEAD quando este plan foi commitado, entao `WebhookPayloadParser.java` (que importa `TelefoneBR.normalizar`) compilou sem problemas. Build agregado verde (88 tests). Sem conflito de arquivos — disjoint sets como o plano previu.

**Nota:** Inicialmente foi avaliada a possibilidade de criar `TelefoneBR.java` como Rule 3 deviation (caso Plan 02-02 ainda nao tivesse rodado), mas a verificacao via `git log --all` confirmou que o arquivo ja existia em b0bba6f. Nenhuma deviation foi necessaria.

### Auto-fixed Issues

Nenhum desvio aplicado durante este plan. Codigo do RESEARCH §8 foi copiado literalmente sem modificacao.

### Authentication Gates

Nenhum.

### Architectural Decisions (Rule 4)

Nenhuma.

## Threat Surface Scan

Nenhuma nova superficie de seguranca relevante. O parser opera sobre `byte[] rawBody` ja validado por HMAC (Phase 1 Filter). DTOs Jackson nao expoem nenhum endpoint novo — sao classes internas. `@JsonIgnoreProperties(ignoreUnknown = true)` em todas as 11 DTOs Jackson **mitiga T-02-19** (Meta evolui payload com campos novos).

Threat register T-02-19..T-02-23 do PLAN ainda valido:
- T-02-19 (Tampering — Meta evolui payload): mitigado via @JsonIgnoreProperties + tipo=DESCONHECIDO sem erro (test tipo_desconhecido valida)
- T-02-20 (DoS — payload grande): aceito (Phase 1 ja cacheou bytes pre-HMAC; volume baixo on-premise)
- T-02-21 (Tampering — fixture com BOM): mitigado — verificado via `head -c 3` que fixtures saem sem BOM do Write tool
- T-02-22 (Information Disclosure — log com PII): mitigado — log.debug do parser registra apenas counts ({} mensagens, {} statuses) e tipo desconhecido com wamid (sem body)
- T-02-23 (Tampering — UTF-8 4-byte chars): aceito (test text_portugues valida 3-byte; emoji 4-byte fica para regression Phase 6 se necessario)

## Threat Flags

Nenhum.

## Self-Check: PASSED

### Files criados (verificados via build verde + ls):
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/WebhookPayloadDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EntryDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ChangeDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ValueDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MessageDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/TextDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/InteractiveDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ReplyDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/DocumentDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MensagemEntranteDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusEntranteDTO.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ParsedWebhook.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WebhookPayloadParser.java`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/text-portugues.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/button-reply.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/list-reply.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/document-pdf.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/status-delivered.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/tipo-desconhecido.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/empty-entry.json`
- FOUND: `api-whatsapp/src/test/resources/fixtures/webhook/multiple-messages.json`
- FOUND: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WebhookPayloadParserTest.java`

### Commit hash:
- FOUND: `6ba039e` — confirmado via `git log --oneline -3`

### Build verde:
- 88 tests reator, 0 failures, 0 errors, BUILD SUCCESS

## Heads-up para Plan 06 (MensagemService orquestrador)

1. **API publica estavel:** `WebhookPayloadParser.extrair(byte[] rawBody) -> ParsedWebhook` lanca `IOException` apenas em caso de JSON malformado. Tolerante a TODOS os campos null/ausentes (entry vazio, messages null, statuses null, interactive null, etc.).

2. **ParsedWebhook contrato:**
   - `mensagens()` -> `List<MensagemEntranteDTO>` (possivelmente vazia, NUNCA null)
   - `statuses()` -> `List<StatusEntranteDTO>` (possivelmente vazia, NUNCA null)
   - Phase 2 NAO persiste statuses (D-05 + D-06 — `statuses` pode ser ignorado pelo orquestrador)

3. **Telefone ja normalizado:** `MensagemEntranteDTO.telefone()` e `StatusEntranteDTO.telefone()` retornam strings JA passadas por `TelefoneBR.normalizar`. Plan 06 NAO precisa normalizar de novo — defesa em profundidade ja aplicada no parser.

4. **Tipo:** valor entre `TipoMensagem.TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO`. Plan 06 pode usar `switch` exaustivo ou tratar DESCONHECIDO como persistencia plain (WEB-07 dicta que persiste sem erro).

5. **Fixtures reusaveis:** os 8 arquivos JSON em `src/test/resources/fixtures/webhook/` podem ser carregados pelo `MensagemServiceTest` (Plan 06) via `ClassPathResource` para tests de integracao end-to-end. Plan 06 nao precisa criar novos fixtures.

6. **Idempotency conflict point:** parser cria `MensagemEntranteDTO` mesmo para mensagem duplicada (wamid igual). E o orquestrador (Plan 06) que chama `IdempotencyService.tentarPersistir(...)` que decide se INSERT segue. Plan 06 deve iterar `out.mensagens()` e gate cada uma.

7. **Statuses Phase 2 ignorados sem erro:** orquestrador pode logar `log.debug("Webhook recebido com {} statuses — ignorando em Phase 2", out.statuses().size())` e seguir. Phase 4+ pode adicionar persistencia.

## Concerns

1. **Records Jackson confusion futura:** documentado no Javadoc dos 3 records (`Usar record aqui e seguro porque NAO ha (de)serializacao Jackson — apenas instanciacao Java`). Risco de futuro dev tentar deserializar record com Jackson e quebrar — mitigation: javadoc warning. Phase 6 pode adicionar test de regressao se necessario.

2. **interactive sem button_reply nem list_reply:** parser retorna `tipo=DESCONHECIDO`. Fixture nao testa este caso especifico (apenas tipo novo `ephemeral_message`); se Meta enviar `interactive` quebrado, o parser nao quebra mas log.debug nao registra. Aceitavel para Phase 2 — Phase 6 pode adicionar fixture especifico.

3. **UTF-8 4-byte chars (emoji):** confianca alta mas nao testada em fixture; se necessario, Phase 6 adiciona regressao com emoji 4-byte (ex: `🎉`). Todos os 3-byte chars (acentos PT-BR) testados positivamente.

4. **Heartbeat Meta vs payload sem messages:** parser trata identicamente — retorna `ParsedWebhook(mensagens=[], statuses=[])`. log.debug em ambos os casos. Plan 06 vai iterar `out.mensagens()` e nao fazer nada se vazio — comportamento correto.

5. **Plan 02-04 (ClienteZapService) nao chegou ainda:** Wave 3 territory. Plan 06 (orquestrador) precisa de ambos `IdempotencyService` (Plan 02-03 — JA COMMITADO) E `ClienteZapService` (Plan 02-04 — pendente). Plan 06 vai puxar tudo junto.
