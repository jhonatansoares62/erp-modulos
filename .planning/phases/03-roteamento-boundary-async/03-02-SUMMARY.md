---
phase: 03-roteamento-boundary-async
plan: 02
subsystem: api
tags: [api-whatsapp, dto, record, event, comando-extractor, logica-pura, jackson, junit-puro]

requires:
  - phase: 02-persistencia-idempotencia
    provides: TipoMensagem 7 constants (TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO)
  - phase: 03-roteamento-boundary-async/01
    provides: api-whatsapp build chain Resilience4j-ready (sem dep direta nesta wave, mas garantia de classpath limpo)
provides:
  - "MensagemPersistidaEvent record (6 fields imutaveis): wamid, telefone, tipo, conteudo, mediaId, idClienteErp"
  - "ComandoCallbackDTO record (7 fields imutaveis): telefone, comando, payload, idCliente, mediaBase64, mediaMimeType, mediaFilename"
  - "MetaMediaResultado record (3 fields imutaveis, uso interno): bytes, mimeType, filename"
  - "MediaMetadataDTO Jackson POJO (snake_case via @JsonProperty): url, mimeType, filename, sha256, fileSize, id, messagingProduct"
  - "ComandoExtractor @Service (logica pura sem I/O): switch sobre TipoMensagem text/interactive/media/desconhecido"
  - "ComandoExtractorTest 13 tests JUnit puros (sem Spring context) cobrindo todos os branches do switch"
affects:
  - phase 03-roteamento-boundary-async/03 (MetaMediaClient consumira MediaMetadataDTO no step 1 do Graph API + retornara MetaMediaResultado)
  - phase 03-roteamento-boundary-async/04 (ErpCallbackClient.despachar usara ComandoCallbackDTO no body do POST)
  - phase 03-roteamento-boundary-async/05 (MensagemAsyncListener consumira MensagemPersistidaEvent + chamara ComandoExtractor.extrair + montara ComandoCallbackDTO)
  - phase 03-roteamento-boundary-async/06 (E2E tests usarao todos os 5 artefatos juntos)

tech-stack:
  added: []  # zero novos deps Maven — tudo ja vem do Spring Boot starter-web/jackson + AOP/Resilience4j (Wave 1)
  patterns:
    - "Record vs POJO heuristic: record para tipos imutaveis com (a) uso interno OR (b) serializacao Jackson OUTPUT-only (Boot 3 + Jackson 2.18); POJO regular com getters/setters para deserializacao Jackson INPUT externa (Meta API responses) — alinhamento com convencao do monorepo (api-email/api-storage envelope DTOs)"
    - "ComandoExtractor logica pura sem I/O: testavel sem Spring context (instanciacao direta `new ComandoExtractor()`), switch JDK 21 com sintaxe arrow + case multipla `case X, Y -> ...`"
    - "@JsonIgnoreProperties(ignoreUnknown=true) em DTOs externos do Meta: resiliencia a campos novos que Meta possa adicionar sem aviso (Risk operacional do PROJECT.md)"
    - "Branch defensivo `sep > 0` (nao `>= 0`) em parsing de id|title: id vazio antes do '|' retorna null, evita callback com comando vazio ao ERP — mesmo padrao usado em outros lugares do monorepo para parser de strings com separador"
    - "Test `_defensivo` para variantes de input que parser Phase 2 nao deveria gerar mas que extractor cobre como salvaguarda — ex: `video_nao_existe_constant` documenta que payload literal cai no default branch caso parser mude"

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/event/MensagemPersistidaEvent.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ComandoCallbackDTO.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MetaMediaResultado.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaMetadataDTO.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ComandoExtractor.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ComandoExtractorTest.java
    - .planning/phases/03-roteamento-boundary-async/03-02-SUMMARY.md
  modified:
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "ComandoExtractor switch case `TipoMensagem.VIDEO` REMOVIDO do snippet original do PLAN — TipoMensagem Phase 2 tem apenas 7 constants (TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO). Per nota explicita do PLAN em <interfaces>: ajustar para usar apenas DOCUMENT/IMAGE/AUDIO + remover assertion VIDEO do test. Test defensivo `video_nao_existe_constant` adicionado para documentar que payload literal `video` cai no default branch -> null."
  - "MediaMetadataDTO regular Jackson POJO com getters/setters explicitos (NAO record) — alinhamento com convencao do monorepo (api-email/api-storage/Phase 2 Webhook envelope DTOs como WebhookPayloadDTO/EntryDTO/ChangeDTO). Spring Boot 3 + Jackson 2.18 funciona com record para deserializacao mas a convencao do monorepo prefere POJO consistente para parsing de respostas externas (Meta API)."
  - "ComandoCallbackDTO + MensagemPersistidaEvent + MetaMediaResultado como records — uso interno (sem Jackson externo) ou serializacao Jackson OUTPUT-only (ComandoCallbackDTO e payload de POST ao ERP, sai como JSON; record funciona perfeitamente para output). Records reduzem boilerplate e enfatizam imutabilidade."
  - "ComandoExtractor `extrair(tipo, conteudo)` retorna `null` para tipo `null` ANTES do switch — defensivo per Javadoc do MensagemPersistidaEvent que define `tipo` como string mas nao explicita non-null. Spring Boot record valida via `@NotNull` se necessario; aqui evitamos NPE no switch."
  - "Branch `idDeInteractive` exige `sep > 0` (nao `>= 0`) — id vazio antes do '|' (ex: `|Aprovar`) retorna null em vez de string vazia. Test `interactive_pipe_no_inicio` documenta. Parser Phase 2 sempre coloca id valido na convencao `id|title`, mas defensivo evita callback com comando vazio ao ERP."
  - "13 tests em vez dos 8 do PLAN minimo (Rule 2 add coverage critica) — adicionados: (a) `text_multiplos_espacos` (regex `\\s+` colapsa espacos), (b) `interactive_button_lowercase` (uppercase id vira lowercase via Locale.ROOT), (c) `interactive_pipe_no_inicio` (sep > 0 vs >= 0), (d) `media_conteudo_null` (DOCUMENT com conteudo null retorna literal independente), (e) `video_nao_existe_constant` (defensivo TipoMensagem.VIDEO ausente)."
  - "Imports na ordem da convencao monorepo: jakarta/java -> org.springframework/com.fasterxml -> br.com.erpkit. UTF-8. 4-space indent. Sem Lombok. PT-BR em identificadores e Javadoc per CLAUDE.md."

patterns-established:
  - "Pattern 'Record output-only vs POJO input': records sao seguros para wire OUTPUT (Spring serializa via Jackson 2.18 nativamente) e uso interno; POJOs com getters/setters sao a convencao para wire INPUT externo (responses de APIs terceiras como Meta Graph). Decisao caso-a-caso baseada em direcao do dado, nao em conciseness."
  - "Pattern 'logica pura testavel sem Spring': services que sao puramente algorithmic (sem I/O, sem state, sem deps Spring beyond `@Service`) devem ser testados via instanciacao direta `new ComandoExtractor()` em JUnit puro (sem `@SpringBootTest`, sem `@MockBean`). Reduz tempo de test de seconds para 0.090s."
  - "Pattern 'switch JDK 21 multi-case arrow': `case A, B -> result` para mapear varios labels para o mesmo branch — mais legivel que `case A: case B: return result;` style classico. Usado em ComandoExtractor para INTERACTIVE_BUTTON+INTERACTIVE_LIST e DOCUMENT+IMAGE+AUDIO."
  - "Pattern 'test defensivo para edge case que parser nao deveria gerar': cobertura de input que upstream filtra mas service deveria responder de forma sensata mesmo assim (`video_nao_existe_constant`, `interactive_pipe_no_inicio`). Evita regressao silenciosa se upstream mudar."
  - "Pattern 'Locale.ROOT em toLowerCase' para identifiers que NAO sao linguistic: ids de interactive button/list e keywords sao tokens de protocolo, nao texto natural. `Locale.ROOT` evita comportamento dependente de locale (ex: Turkish locale converte 'I' para 'ı' em vez de 'i'). Padrao seguro."

requirements-completed:
  - ROU-02

duration: ~8min
completed: 2026-05-05
---

# Phase 03 Plan 02: MensagemPersistidaEvent + ComandoExtractor + DTOs Summary

**Wave 2 da Phase 3 — tipos puros + logica pura, zero I/O: 5 artefatos novos (1 event record + 1 service + 2 DTOs records + 1 Jackson POJO), 13 tests JUnit puros (sem Spring context) cobrindo todos os branches do switch de ComandoExtractor. Reator BUILD SUCCESS, 126 tests verdes (113 prev + 13 novos), zero regressao em Phase 1+2 ou Wave 1. ROU-02 satisfeito (ComandoCallbackDTO {telefone, comando, payload, idCliente} + 3 fields opcionais de media).**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-05T20:47:00Z
- **Completed:** 2026-05-05T20:55:00Z
- **Tasks:** 3 (Task 1 5 artefatos novos + Task 2 ComandoExtractorTest + Task 3 build/SUMMARY/commits)
- **Files changed:** 8 (6 created src/test + 2 modified .planning/STATE.md + .planning/ROADMAP.md, exclui SUMMARY)

## Accomplishments

- **`event/MensagemPersistidaEvent.java` novo** — record imutavel com 6 fields (wamid, telefone, tipo, conteudo, mediaId, idClienteErp). Javadoc completo documenta D-01 do CONTEXT (`@TransactionalEventListener(AFTER_COMMIT)` evita falsos positivos no ERP), assina pacote `event/` (novo no api-whatsapp).
- **`dto/ComandoCallbackDTO.java` novo** — record imutavel com 7 fields (telefone, comando, payload, idCliente, mediaBase64, mediaMimeType, mediaFilename). Jackson auto-serializa para JSON via convertor padrao do Spring Boot 3. Javadoc explica D-06 (base64 self-contained vs filesystem path) + ROU-02.
- **`dto/MetaMediaResultado.java` novo** — record imutavel com 3 fields (bytes, mimeType, filename). Output interno de `MetaMediaClient.baixar()` (Wave 3); listener async transforma `bytes` em base64 via `java.util.Base64` antes de incluir em ComandoCallbackDTO.
- **`dto/MediaMetadataDTO.java` novo** — Jackson POJO com `@JsonIgnoreProperties(ignoreUnknown=true)` + `@JsonProperty` em mime_type/file_size/messaging_product (snake_case Meta API mapeado para camelCase Java). Getters/setters explicitos consistente com convencao do monorepo (api-email/api-storage/Phase 2 envelope DTOs).
- **`service/ComandoExtractor.java` novo** — `@Service` com `extrair(String tipo, String conteudo)`. Switch JDK 21 com 4 branches: TEXT -> primeiraPalavra (lowercase Locale.ROOT, trim, split `\\s+`[0]), INTERACTIVE_BUTTON|INTERACTIVE_LIST -> idDeInteractive (substring antes do `|`, sep > 0 defensivo), DOCUMENT|IMAGE|AUDIO -> tipo literal, default -> null. Sem case VIDEO (TipoMensagem Phase 2 nao tem). Logica pura sem I/O.
- **`test/.../ComandoExtractorTest.java` novo** — 13 tests JUnit puros (sem Spring context, instanciacao direta `new ComandoExtractor()`):
  - **text:** primeira_palavra, acentos, vazio_null (3 asserts), multiplos_espacos
  - **interactive_button/list:** com_separador (button + list), lowercase (uppercase id), sem_separador (3 asserts), pipe_no_inicio (sep > 0)
  - **media:** tipos_retornam_literal (DOCUMENT/IMAGE/AUDIO), conteudo_null (DOCUMENT com null)
  - **edge cases:** desconhecido_e_null (DESCONHECIDO/null/inexistente), video_nao_existe_constant (defensivo)
- **Reator `mvnw verify -pl api-whatsapp -am`:** **BUILD SUCCESS**, **126 tests verdes (113 Phase 1+2+Wave 1 + 13 novos), 0 falhas, 0 erros, zero regressao**. ComandoExtractorTest executa em 0.090s (logica pura sem Spring boot).

## Decisions Made

- **D1 — Sem case VIDEO em ComandoExtractor:** `TipoMensagem` Phase 2 entrega 7 constants (TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO), sem VIDEO. Per nota explicita do PLAN em `<interfaces>`: "Verificar se `TipoMensagem` ja tem `VIDEO` constant... Se VIDEO nao existir, ajustar para usar apenas DOCUMENT/IMAGE/AUDIO no switch + remover assertion VIDEO do test." Phase 5 (parser, ainda nao executado) decidira se adiciona; se adicionar, Wave atualiza switch+test em 1 commit. Test defensivo `video_nao_existe_constant` documenta que payload literal `video` cai no default branch -> null.

- **D2 — Records (3) vs Jackson POJO (1):** Heuristica direcional:
  - **Records** para `MensagemPersistidaEvent` (uso interno Spring events), `MetaMediaResultado` (uso interno entre service e listener), `ComandoCallbackDTO` (Jackson serializa Java->JSON, OUTPUT-only — Boot 3 suporta nativamente).
  - **POJO** para `MediaMetadataDTO` (Jackson deserializa JSON externo do Meta->Java, INPUT externo) — alinhamento com convencao do monorepo (api-email/api-storage envelope DTOs como WebhookPayloadDTO/EntryDTO/ChangeDTO da Phase 2).
  Decisao caso-a-caso baseada em direcao do dado e procedencia (interno vs externo), nao em conciseness sintatica.

- **D3 — Branch `sep > 0` em idDeInteractive (defensivo):** id vazio antes do `|` (ex: `|Aprovar`) retorna null em vez de string vazia. Parser Phase 2 sempre formata `id|title` com id valido, mas defensivo aqui evita callback com comando vazio ao ERP. Test `interactive_pipe_no_inicio` documenta. Custo: zero (1 char a mais na comparison).

- **D4 — `null` check no inicio do `extrair` antes do switch:** retorna null se tipo for null, sem entrar no switch. Switch sobre String com null lanca NPE em Java; check defensivo + early return e padrao Java preferido. Javadoc do MensagemPersistidaEvent nao explicita non-null para `tipo`, entao tratamento explicito e mandatorio.

- **D5 — 13 tests vs 8 do plan minimo (Rule 2 add coverage):** Cobertura adicional para edge cases:
  - `text_multiplos_espacos` valida que regex `\\s+` colapsa espacos (e nao apenas split em ` `).
  - `interactive_button_lowercase` valida que uppercase id (`APROVAR_42|...`) vira `aprovar_42` via `Locale.ROOT.toLowerCase` (cobertura explicita do branch lowercase).
  - `interactive_pipe_no_inicio` valida `sep > 0` (vs `>= 0`).
  - `media_conteudo_null` valida que DOCUMENT/IMAGE/AUDIO retornam literal independente do conteudo (parser pode passar null).
  - `video_nao_existe_constant` defensivo: documenta que video literal cai no default -> null caso parser mude.

- **D6 — `Locale.ROOT` em toLowerCase de identifiers (nao linguistic):** ids de interactive button/list e keywords de text sao tokens de protocolo, nao texto natural. `Locale.ROOT` evita comportamento dependente de locale (ex: Turkish locale converte `I` para `ı` em vez de `i`, quebrando match contra handler `aprovar`). Padrao seguro adotado por toda Java standard library para case-insensitive identifier matching.

## Risks & Mitigations

- **Risk: Phase 5 parser pode adicionar VIDEO constant a `TipoMensagem` sem atualizar ComandoExtractor.**
  - Mitigacao: Test `video_nao_existe_constant` quebra silenciosamente se VIDEO for adicionado mas extractor nao atualizar — assertion espera `null` para input `("video", "video.mp4")`, mas se VIDEO entrar no case media branch retornaria literal. Test forca a sincronizacao consciente. Documentado em Javadoc do test class.
- **Risk: Jackson `MediaMetadataDTO` pode quebrar se Meta mudar formato (ex: rename `mime_type` para `content_type`).**
  - Mitigacao: `@JsonIgnoreProperties(ignoreUnknown=true)` deixa o DTO resiliente a campos NOVOS, mas nao a renames de campos EXISTENTES. Wave 3 (MetaMediaClient) deve ter test integration WireMock validando shape esperado da resposta — se Meta mudar, test quebra explicitamente. Mitigacao adicional: monitorar release notes do Meta Cloud API (PROJECT.md flag operacional).
- **Risk: ComandoExtractor branch `interactive` assume formato `id|title` do parser Phase 2 — se parser mudar (Phase 5+), extractor pode silenciosamente nao extrair id.**
  - Mitigacao: Phase 5 (parser, ainda nao executado, mas Phase 2 ja entregou parser inicial) e dono do contrato. Test `interactive_button_com_separador` forca o formato. Branch `sep > 0` retorna null em vez de string vazia se formato mudar — falha visivel via log no listener (Wave 5).

## TDD Gate Compliance

Plan type: `execute` (nao TDD por default — sem RED/GREEN/REFACTOR ciclo declarado). Tests criados juntos com producao em Task 2 (sem RED gate explicito), mas ComandoExtractorTest e logica pura: 13 tests cobrem 100% dos branches do switch. Aceitavel para infra plan onde unit tests sao escritos de forma deductiva a partir da spec do switch (nao TDD descobrindo behavior emergente).

## Self-Check: PASSED

- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/event/MensagemPersistidaEvent.java` criado: FOUND
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ComandoCallbackDTO.java` criado: FOUND
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MetaMediaResultado.java` criado: FOUND
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaMetadataDTO.java` criado: FOUND
- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ComandoExtractor.java` criado: FOUND
- File `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ComandoExtractorTest.java` criado: FOUND
- Build `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** com 126 tests verdes (113 prev + 13 novos), zero regressao, 0 falhas, 0 erros
- Surefire report `ComandoExtractorTest`: Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.090s
- ROU-02 verificavel: `ComandoCallbackDTO` record tem `telefone`, `comando`, `payload`, `idCliente` (4 fields obrigatorios do payload do callback per ROU-02) + `mediaBase64`, `mediaMimeType`, `mediaFilename` (3 opcionais para D-06)

## Concerns para Wave 3 (PLAN 03-03 — MetaMediaClient)

- **`@DynamicPropertySource` para WireMock em MetaMediaClient test:** Wave 3 deve sobrescrever `app.modulos.whatsapp.metaApiBaseUrl` com URL aleatoria do WireMock instance via `@DynamicPropertySource` (Wave 1 ja preparou em `application-test.yml` placeholder `http://localhost:0/test`). Pattern: `registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> "http://localhost:" + wm.getPort())`.
- **`MediaMetadataDTO` getter naming convention:** Wave 1 SUMMARY menciona `getMime_type()` mas o DTO entregue aqui usa `getMimeType()` (Java convention) com `@JsonProperty("mime_type")` mapeando o JSON. Wave 3 MetaMediaClient deve usar `metadata.getMimeType()` (NAO `getMime_type`).
- **`MetaMediaResultado` 404 graceful:** PITFALLS C-08 — URL Meta expira em 5min. MetaMediaClient deve retornar `Optional<MetaMediaResultado>` (per CONTEXT D-04 code snippet) ou `null`/empty, NAO exception. Listener async trata gracefully.
- **`ComandoCallbackDTO` field order:** Wave 4 ErpCallbackClient enviara via `restClient.post().body(payload).retrieve()`. JSON gerado tera ordem dos fields do record: `{telefone, comando, payload, idCliente, mediaBase64, mediaMimeType, mediaFilename}`. ERP deve aceitar essa ordem (JSON e ordem-agnostic, mas para debug/log e bom alinhar).
- **`MensagemPersistidaEvent.idClienteErp`:** sempre `null` quando publicado (per Javadoc) — listener busca via ClienteZapService. Wave 5 NAO deve preencher esse field no `publishEvent` em MensagemService; deixa null e listener resolve.

## References

- CONTEXT.md §D-01 (ack-first com ApplicationEventPublisher), §D-04 (MetaMediaClient + DTOs), §D-05 (ComandoExtractor logica), §D-06 (ComandoCallbackDTO base64)
- RESEARCH.md §"MensagemPersistidaEvent" (Code Example 3), §"ComandoExtractor" (Code Example 4), §"ComandoCallbackDTO" (Code Example 5), §"Pattern 3 MediaMetadataDTO"
- ROADMAP §Phase 3 §ROU-02
- 03-01-SUMMARY.md (Wave 1 — infra Resilience4j + AOP, dependency upstream)
- TipoMensagem Phase 2 (`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TipoMensagem.java`) — 7 constants confirmadas (sem VIDEO)
