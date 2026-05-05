# Phase 4: Outbound + Trava 24h + WhatsAppController - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 04-outbound-trava-24h-whatsappcontroller
**Areas discussed:** todas as 4 (delegadas a Claude apos apresentacao do menu)

---

## Gate de selecao das gray areas

Pergunta: "Quais gray areas da Phase 4 voce quer discutir? (Os requirements OUT-01..11 estao locked — so vamos definir o COMO.)"

| Opcao | Descricao | Selecionada |
|-------|-----------|-------------|
| Contrato `enviar-documento` | multipart/form-data vs JSON+base64 — afeta interface lib-whatsapp-client (Phase 5) | Delegada a Claude |
| Mapeamento erro Meta → ERP | pass-through vs translated com `codigo` (4xx→422, 5xx→502, circuit→503, 24h→409 locked) | Delegada a Claude |
| Estilo do enforcement aspect | annotation marker `@JanelaProtegida` vs pointcut `execution(* enviar*(..))` | Delegada a Claude |
| Status endpoint + media cache | minimo vs richer / TTL estrito vs sliding | Delegada a Claude |

**Resposta literal do usuario:** "pode implementar da melhor maneira que encontrar"

---

## Area 1: Contrato `enviar-documento` (REQ OUT-02)

| Opcao | Trade-offs | Selecionada |
|-------|-----------|-------------|
| `multipart/form-data` (telefone + file + caption) | Payload menor (sem inflation 33%); native `@RequestPart` Spring; pattern aligned com Meta upload mas exige `HttpMessageConverter` config; testes mais hassle |  |
| **JSON + base64** (telefone, mediaBase64, mimeType, filename, caption) | +33% payload (10MB→13MB) mas localhost; alinhado com `ComandoCallbackDTO.mediaBase64` da Phase 3 D-06; RestClient + Resilience4j retry funciona de fabrica (body byte[] reusable); Bean Validation `@NotBlank` natural; Test pattern `MockMvc.content(om.writeValueAsString(...))` simples | ✓ |

**Escolha de Claude:** D-01 JSON+base64.

**Rationale:** consistencia com D-06 da Phase 3 (mediaBase64 ja e padrao no ComandoCallbackDTO inbound), reduz cognitive load para developers do ERP, RestClient/Resilience4j integration natural. Multipart entre Meta e api-whatsapp **continua existindo** como detalhe interno do `WhatsAppCloudClient.uploadMedia`. Trade-off de inflation 33% e irrelevante em loopback localhost.

**Reverter para multipart se:** PDFs piloto MUDAS forem >15MB (configurar `spring.servlet.multipart.max-request-size` em Phase 6 RUNBOOK e adiar). Listed em deferred.

---

## Area 2: Mapeamento erro Meta → ERP (REQ OUT-10)

| Opcao | Descricao | Selecionada |
|-------|-----------|-------------|
| Pass-through | 4xx Meta → 4xx api-whatsapp; 5xx Meta → 5xx api-whatsapp. Simpler. Confunde semantica HTTP no ERP (401 do api-whatsapp deveria significar API key, nao token Meta) |  |
| Tudo 502 exceto 409 | 4xx/5xx/timeout/circuit-open todos → 502. Simpler ainda. ERP nao consegue distinguir "meu request errado" vs "Meta caiu" |  |
| **Translated com `codigo`** | 4xx Meta → 422 `META_ERROR` + metaErrorCode; 5xx esgotado → 502 `META_INDISPONIVEL`; circuit-open → 503 `CIRCUIT_OPEN`; timeout esgotado → 504 `META_TIMEOUT`; janela → 409 `JANELA_24H_FECHADA` (locked); validation → 400 `VALIDATION_ERROR`. `metaErrorCode` Integer no body (Meta error codes 131026 etc.) | ✓ |

**Escolha de Claude:** D-02 translated com `codigo`.

**Rationale:** alinhamento com `ModuloException` pattern do monorepo (`codigo` field permite diferenciacao programatica no ERP); operador de RUNBOOK precisa do `metaErrorCode` numerico do Meta para escalar suporte; semantica HTTP fica limpa (401 do api-whatsapp = API key errada, nao Meta token expirado).

**Implementacao:** `MetaApiException(tipo enum, metaErrorCode, mensagem)` com tipo `{CATEGORIA_4XX, INDISPONIVEL_5XX, TIMEOUT, CIRCUIT_OPEN}`. `GlobalExceptionHandler` no lib-shared mapeia. Verificar antes de planejar: `lib-shared/ErrorResponse` ja tem `codigo` field? Se nao, adicionar (mudanca compativel com api-email/api-storage/api-consultas).

**Reverter para tudo-502 se:** ERP MUDAS tiver dificuldade processando os 4 codes diferentes. Listed em deferred.

---

## Area 3: Estilo do enforcement aspect (REQ OUT-07)

| Opcao | Trade-offs | Selecionada |
|-------|-----------|-------------|
| Pointcut por nome `execution(* WhatsAppCloudClient.enviar*(..))` | Invisible no call site; novo metodo `enviar*` entra automaticamente no enforcement (bom ou ruim — risco de captura silenciosa); telefone via JoinPoint args[0] |  |
| **Annotation marker `@JanelaProtegida` + telefone posicional `args[0]`** | Visivel no call site (3 anotacoes na mesma linha junto com Resilience4j); hard to forget se nao anotar (gate de grep no Phase 6); telefone forced primeiro arg (convencao forte); sem atributo (`telefoneArgIndex` complicaria) | ✓ |
| Annotation com atributo `@JanelaProtegida(telefoneArgIndex = N)` | Mais flexivel para metodos com signature diferente; complexidade desnecessaria para v1 (todos os 4 metodos tem telefone como first arg) |  |

**Escolha de Claude:** D-03 annotation marker `@JanelaProtegida` + convencao posicional `args[0] instanceof String`.

**Rationale:** alinhamento com Resilience4j pattern do monorepo (annotation-driven `@CircuitBreaker(name=...)`); explicita declaracao de protecao na assinatura do metodo; gate de grep no Phase 6 garante regression test contra refactor que esqueca anotacao; `IllegalStateException` runtime se signature violar convencao = fail-fast em test, nao em prod.

**Order:** `@Order(Ordered.HIGHEST_PRECEDENCE)` crucial para rodar **fora** dos retries Resilience4j (1 verificacao por chamada do ERP, nao 1 por tentativa). Validacao empirica em test: contar `windowService.verificarJanela` invocations em scenario 3-retries — esperar 1.

**Reverter para pointcut se:** aspect annotation tiver bug em Spring AOP self-call. Listed em deferred.

---

## Area 4: Status endpoint + MediaCache hit semantics

### Status endpoint scope

| Opcao | Campos | Selecionada |
|-------|--------|-------------|
| **Minimal** | `{status, circuitBreakerState, phoneNumberId}` | ✓ |
| Richer | `{status, circuitBreakerState, phoneNumberId, lastMessageMeta, totalMidiaCacheada, recentErrorCount}` |  |

**Escolha de Claude:** D-04 minimal.

**Rationale:** YAGNI v1; operador da ERPKit precisa diagnosticar 2 coisas — circuit aberto (sistema rejeitando) + phoneNumberId correto (env var nao errada). Mais campos = mais surface a manter sincronizada. Phase 6 expande se feedback de operador piloto pedir (incluir PITFALLS C-12 subscribed_apps validation tambem).

### MediaCache hit semantics (REQ OUT-08)

| Opcao | Descricao | Selecionada |
|-------|-----------|-------------|
| **TTL estrito** | Hit se `expira_em > now()`. Sem extender expira_em. Reupload natural quando expira renova TTL para now+30d. Tabela bounded (turnover de 30 em 30 dias) | ✓ |
| Sliding TTL | Hit estende `expira_em` em +30d. Mantem media_id em cache indefinidamente se reusado. Risco: media_id pode invalidar Meta-side (Meta TTL real 30d nao prolongado por sliding nosso) → 4xx surpresa |  |
| Hit com buffer | Hit se `expira_em > now() + 1 dia` (buffer). Forca reupload preventivo perto do limite |  |

**Escolha de Claude:** D-04 TTL estrito.

**Rationale:** previsibilidade (turnover bounded), alinhamento com Meta TTL real (sliding mascararia expirar Meta-side levando a 4xx surpresa que nao retentariamos). Custo: 1 reupload extra a cada 4 semanas se cliente envia mesmo PDF semanalmente — negligible.

**Reverter para sliding se:** monitor de Phase 6 mostrar reupload mensal causando custo. Listed em deferred.

---

## Claude's Discretion

User delegou todas as 4 areas para Claude apos apresentar o menu. Decisoes documentadas como D-01 a D-04 em CONTEXT.md, todas reversiveis com fallback explicito em deferred.

## Deferred Ideas

Listadas em `<deferred>` do CONTEXT.md. Resumo:
- 16 itens, principalmente Phase 5 (lib-whatsapp-client), Phase 6 (testes/RUNBOOK/SpringDoc), v2 milestone (DIFF-* + OPS-V2-*), e fallbacks reversiveis das 4 decisoes (multipart, pass-through, pointcut, sliding TTL).
