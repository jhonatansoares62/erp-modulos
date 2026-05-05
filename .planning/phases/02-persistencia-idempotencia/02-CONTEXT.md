# Phase 2: Persistencia + Idempotencia - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning
**Mode:** `--auto` (user delegated end-to-end execution)

<domain>
## Phase Boundary

Adicionar a camada de persistencia ao `api-whatsapp`: 3 entities JPA (`ClienteZap`, `MensagemLog`, `MediaCache`) com seus repositories, parser do envelope Meta para extrair mensagens entrantes (text, button_reply, list_reply, document, statuses, desconhecido), idempotency baseada em `INSERT ... ON CONFLICT (wamid) DO NOTHING` + row-count gate, normalizacao de telefone brasileiro (DDDs SP/RJ/ES mantem 9o digito, demais estados strip), e atualizacao de `ultima_mensagem_em` em transacao `REQUIRES_NEW` separada usando relogio do banco (`NOW()`).

Phase 2 expande o `POST /webhook/whatsapp` da Phase 1: ainda **sincrono**, mas agora faz parse + idempotency + persistencia + atualizacao de `ultima_mensagem_em` antes de retornar 200. Phase 3 refatora pra async (ack 200 → @Async dispatch).

**Em escopo:**
- Entities: `ClienteZap`, `MensagemLog`, `MediaCache`
- Repositories: `ClienteZapRepository`, `MensagemLogRepository`, `MediaCacheRepository`
- Enum `Direcao { in, out }` mapeado via `@Enumerated(EnumType.STRING)` espelhando o CHECK constraint do DB
- Enum `TipoMensagem { text, interactive_button, interactive_list, document, status, desconhecido }`
- DTOs: `WebhookPayloadDTO`, `MensagemEntranteDTO`, `StatusEntranteDTO` (parser output)
- Service `WebhookPayloadParser` — Jackson para extrair lista de `MensagemEntranteDTO`/`StatusEntranteDTO` de bytes do body
- Service `IdempotencyService` — wrap de native query `INSERT ... ON CONFLICT DO NOTHING` retornando `boolean novo` (true se row count == 1)
- Utility `TelefoneBR.normalizar(String)` — strip 9o digito condicional por DDD
- Service `ClienteZapService` — `identificar(telefone)` cria `id_cliente_erp=null` se nao existe, `atualizarUltimaMensagemEm(telefone)` em `@Transactional(REQUIRES_NEW)` usando native `UPDATE` com `NOW()` do banco
- Service `MensagemService` — orquestrador sincrono: parse → idempotency check → persistencia em massa → atualizacao de timestamp; usado pelo `WebhookController.POST`
- WebhookController POST: agora delega para `mensagemService.processarWebhook(rawBody)` em vez de retornar 200 vazio

**Fora de escopo (Phase 3+):**
- `@Async` boundary apos ack 200 — Phase 3 (ROU-01)
- `ErpCallbackClient`, `MessageRouter` — Phase 3 (ROU-02..05)
- Download de media entrante — Phase 3 (ROU-05)
- `WhatsAppCloudClient`, envio outbound — Phase 4
- `WindowEnforcementService` — Phase 4 (depende de `ultima_mensagem_em` ja persistido por Phase 2, mas a checagem em si e Phase 4)
- `MediaCacheService` — Phase 4 (so a entity + repo + migration na Phase 2)

</domain>

<decisions>
## Implementation Decisions

### D-01: JPA Entities com `@Table(schema = "whatsapp")` + `Instant` para timestamps + sem Lombok

`ClienteZap`, `MensagemLog`, `MediaCache` definidos como `@Entity` com `@Table(schema = "whatsapp", name = "...")`. Campos:

```java
@Entity
@Table(schema = "whatsapp", name = "clientes_zap")
public class ClienteZap {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_cliente_erp")
    private Long idClienteErp;  // null para clientes nao mapeados

    @Column(nullable = false, unique = true, length = 20)
    private String telefone;  // normalizado

    @Column(name = "ultima_mensagem_em")
    private Instant ultimaMensagemEm;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;  // DEFAULT NOW() do DB
    
    // getters/setters explicitos
}
```

`MensagemLog` similar com `wamid VARCHAR(255) UNIQUE`, `direcao` mapeada por `@Enumerated(EnumType.STRING)` para `enum Direcao { in, out }`, `tipo` como String (nao enum — flexivel pra `desconhecido`), `conteudo` como `@Lob String`, `mediaId` opcional.

`MediaCache` com PK `arquivoHash CHAR(64)` (sha256 hex) — `@Id String arquivoHash`.

**Por que `Instant` e nao `LocalDateTime`:** Logica de janela 24h (Phase 4) precisa comparar com `Instant.now()` ou similar. `Instant` e UTC-anchored, sem ambiguidade de timezone. PostgreSQL `TIMESTAMP` (without time zone) mapeia limpo via Hibernate quando JPA dialeto e PostgreSQL.

**Por que sem Lombok:** Convencao do monorepo (CONVENTIONS.md) — getters/setters explicitos. Mantem alinhamento.

**Por que `IDENTITY` e nao `SEQUENCE`:** Phase 1 ja decidiu via D-06 do `01-CONTEXT.md` que migrations usam `BIGINT GENERATED ALWAYS AS IDENTITY` (validado empiricamente em H2 PG-mode na Wave 4 do Phase 1). JPA `GenerationType.IDENTITY` e o match correto.

### D-02: Idempotency via native `INSERT ... ON CONFLICT (wamid) DO NOTHING` + row-count gate

`MensagemLogRepository` define metodo nativo:

```java
@Modifying
@Query(value = """
    INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao, tipo, conteudo, media_id)
    VALUES (:wamid, :telefone, :direcao, :tipo, :conteudo, :mediaId)
    ON CONFLICT (wamid) DO NOTHING
    """, nativeQuery = true)
int inserirSeNovo(@Param("wamid") String wamid,
                  @Param("telefone") String telefone,
                  @Param("direcao") String direcao,
                  @Param("tipo") String tipo,
                  @Param("conteudo") String conteudo,
                  @Param("mediaId") String mediaId);
```

Retorno: 1 se inseriu, 0 se conflito (wamid ja existia). `IdempotencyService.tentarPersistir(MensagemLog) -> boolean novo` envolve a chamada e retorna se a entidade e nova ou duplicata. **Sem `SELECT` antes** — gate atomico per PITFALLS C-06.

**Validacao H2 PG-mode:** H2 v2.x com `MODE=PostgreSQL` suporta `ON CONFLICT DO NOTHING` (sintaxe Postgres-native). **Spike obrigatorio em Wave 1 de Phase 2** (mesmo padrao da Wave 4 do Phase 1 com BIGINT IDENTITY) — se falhar, fallback documentado: catch `DataIntegrityViolationException` apos `repo.save(...)` (menos elegante, mas portavel).

**Por que nao usar `Optional<MensagemLog> findByWamid` antes de save:** TOCTOU race window (PITFALLS C-06): dois POSTs simultaneos passariam o SELECT, ambos tentariam INSERT, um ganharia UNIQUE violation. O ON CONFLICT DO NOTHING e atomico no nivel do banco e elimina a janela.

### D-03: Normalizacao de telefone BR em utility puro `TelefoneBR.normalizar(String)`

```java
public final class TelefoneBR {
    private static final Set<String> DDDS_COM_NONO_DIGITO =
        Set.of("11","12","13","14","15","16","17","18","19",  // SP
               "21","22","24",                                  // RJ
               "27","28");                                      // ES

    public static String normalizar(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");  // strip nao-digitos
        // formato esperado apos clean: 5511987654321 (13 digitos com 9) ou 551187654321 (12 digitos sem 9)
        if (!digitos.startsWith("55") || digitos.length() < 12 || digitos.length() > 13) {
            return digitos;  // nao-Brasil: retornar como veio (sanitizado)
        }
        String ddd = digitos.substring(2, 4);
        String numero = digitos.substring(4);
        if (DDDS_COM_NONO_DIGITO.contains(ddd)) {
            return digitos;  // SP/RJ/ES mantem 9o digito
        }
        // Outros DDDs: strip 9o digito se presente (numero comeca com '9' e tem 9 digitos)
        if (numero.length() == 9 && numero.startsWith("9")) {
            return "55" + ddd + numero.substring(1);
        }
        return digitos;  // ja sem 9o digito
    }
    
    private TelefoneBR() {}
}
```

**Decisoes:**
- Normaliza no INSERT em `clientes_zap` E em qualquer lookup (`findByTelefone`).
- DDDs SP/RJ/ES per PITFALLS C-13 mantem o 9o digito (regra ANATEL 2010 mas WhatsApp manteve formato antigo nesses estados).
- Numeros nao-Brasil (sem prefixo `55`): retorna sanitizado sem alteracao.
- Pure utility (private constructor) — testavel sem Spring.
- Nao usa `@Component` — chamado diretamente das services.

**Por que armazenar normalizado e nao buscar bidirecional:** Simpler. UNIQUE constraint funciona naturalmente. Testes garantem normalizacao consistente.

### D-04: `atualizarUltimaMensagemEm` em `@Transactional(REQUIRES_NEW)` usando native `NOW()` do banco

```java
@Service
public class ClienteZapService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarUltimaMensagemEm(String telefone) {
        repository.atualizarUltimaMensagemEm(telefone);  // chamada native
    }
}

// repository
@Modifying
@Query(value = "UPDATE whatsapp.clientes_zap SET ultima_mensagem_em = NOW() WHERE telefone = :telefone",
       nativeQuery = true)
int atualizarUltimaMensagemEm(@Param("telefone") String telefone);
```

**Por que REQUIRES_NEW:** PITFALLS C-01 — TOCTOU race entre webhook persistence (transacao A) e leitura de `ultima_mensagem_em` na trava 24h (transacao B, Phase 4). Transacao separada commita imediatamente apos o INSERT da mensagem entrante; a leitura subsequente sempre ve o valor atualizado.

**Por que `NOW()` do DB e nao `Instant.now()` da JVM:** Clock skew JVM-DB pode ser de ate segundos. Em casos limite (perto do boundary de 24h), JVM clock atrasada permite envio fora da janela. DB clock e a fonte de verdade.

**Trade-off:** REQUIRES_NEW custa 1 transacao adicional (commit + nova). Em on-premise com baixa concorrencia, nao e gargalo. Para o ganho de correcao, vale.

### D-05: Parser do envelope Meta com Jackson + DTO hierarchy + tipo `desconhecido`

`WebhookPayloadParser` recebe `byte[] rawBody` (do `CachedBodyHttpServletRequest.getCachedBody()`) e retorna `ParsedWebhook { List<MensagemEntranteDTO> mensagens, List<StatusEntranteDTO> statuses }`.

Estrutura Meta esperada (simplificada):
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "...",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5547999999999",
          "id": "wamid.HBgN...",
          "timestamp": "1735689600",
          "type": "text",                          // text | interactive | document | image | audio | ...
          "text": { "body": "Ola" },
          "interactive": {
            "type": "button_reply" | "list_reply",
            "button_reply": { "id": "aprovar_1234", "title": "Aprovar" },
            "list_reply": { "id": "boleto", "title": "Ver boleto" }
          },
          "document": { "id": "media-id", "mime_type": "application/pdf", "filename": "doc.pdf" }
        }],
        "statuses": [{
          "id": "wamid.HBgN...",
          "status": "sent" | "delivered" | "read" | "failed",
          "recipient_id": "5547999999999"
        }]
      },
      "field": "messages"
    }]
  }]
}
```

Parser:
- Usa Jackson `ObjectMapper` (`spring-boot-starter-web` ja inclui).
- Tolera ausencia de campos opcionais — `messages[]`, `statuses[]`, `interactive` podem nao existir.
- Para `messages[]` com `type` desconhecido (nao em `{text, interactive, document, image, audio, video, sticker, location, contacts}`): cria `MensagemEntranteDTO` com `tipo="desconhecido"`, `conteudo=null`, `mediaId=null`. Persiste sem erro per WEB-07.
- Para `interactive` sem `button_reply` nem `list_reply`: tipo `desconhecido`.
- Loga quantidade de mensagens e statuses extraidos (DEBUG level), nao o conteudo.

`TipoMensagem` como String constants em vez de enum estrito — flexibilidade para tipos novos do Meta sem forcar release. Constants em interface `TipoMensagem`:
```java
public final class TipoMensagem {
    public static final String TEXT = "text";
    public static final String INTERACTIVE_BUTTON = "interactive_button";
    public static final String INTERACTIVE_LIST = "interactive_list";
    public static final String DOCUMENT = "document";
    public static final String IMAGE = "image";
    public static final String AUDIO = "audio";
    public static final String STATUS = "status";
    public static final String DESCONHECIDO = "desconhecido";
    private TipoMensagem() {}
}
```

### D-06: WebhookController.POST agora delega ao MensagemService sincrono

Phase 1 deixou `POST /webhook/whatsapp` retornando `ResponseEntity.ok().build()` apos HMAC validado pelo Filter. Phase 2 expande:

```java
@PostMapping("/whatsapp")
public ResponseEntity<Void> receberWebhook(HttpServletRequest request) throws IOException {
    // HMAC ja validado pelo Filter. Body ja cacheado no CachedBodyHttpServletRequest.
    byte[] rawBody;
    if (request instanceof CachedBodyHttpServletRequest cached) {
        rawBody = cached.getCachedBody();
    } else {
        rawBody = request.getInputStream().readAllBytes();  // fallback defensivo (nao deve acontecer)
    }
    mensagemService.processarWebhook(rawBody);  // sincrono na Phase 2; Phase 3 vira @Async
    return ResponseEntity.ok().build();
}
```

`mensagemService.processarWebhook(byte[])`:
1. Parse via `webhookPayloadParser.extrair(rawBody)` → `ParsedWebhook`
2. Para cada mensagem entrante: `idempotencyService.tentarPersistir(MensagemLog)` → se nova, segue; se duplicata, log debug e proximo
3. Para cada mensagem nova persistida com sucesso: `clienteZapService.identificar(telefone)` (cria ou recupera ClienteZap) e `clienteZapService.atualizarUltimaMensagemEm(telefone)` (REQUIRES_NEW)
4. Para statuses: opcional persistir (Phase 2 NAO persiste statuses ainda; loga DEBUG e ignora — entra em phase futura se necessario; PROJECT.md nao exige persistencia de statuses)

**Por que sincrono em Phase 2:** Manter blast radius pequeno. SC-1 do ROADMAP ("dois POSTs com mesmo wamid resultam em exatamente 1 linha") exige observabilidade do estado do DB depois do return 200 — testavel sincronamente. Phase 3 introduz `@Async` mantendo a mesma logica.

**Por que statuses nao persistidos em Phase 2:** Statuses (`sent/delivered/read/failed`) sao callbacks Meta sobre mensagens de SAIDA — fazem mais sentido quando ha persistencia outbound (Phase 4). Por enquanto, parser extrai e service ignora, evitando esquema de status que pode mudar quando outbound chegar.

### D-07: Identificacao de cliente — auto-create com `id_cliente_erp=null`

`ClienteZapService.identificar(String telefone)`:
1. Normaliza o telefone via `TelefoneBR.normalizar(telefone)`
2. Busca por `repository.findByTelefone(telefoneNormalizado)`
3. Se existe: retorna entity
4. Se nao: cria entity nova com `idClienteErp = null` (cliente nao mapeado), salva, retorna

```java
@Service
public class ClienteZapService {
    
    @Transactional
    public ClienteZap identificar(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        return repository.findByTelefone(normalizado).orElseGet(() -> {
            ClienteZap novo = new ClienteZap();
            novo.setTelefone(normalizado);
            novo.setIdClienteErp(null);
            return repository.save(novo);
        });
    }
}
```

**Por que auto-create:** Permite o flow continuar mesmo para clientes nao previamente cadastrados no ERP. O campo `id_cliente_erp = null` sinaliza pendente; um job futuro (fora desta milestone) pode reconciliar. Per PER-06 do REQUIREMENTS.md.

**Race condition em criar concorrente:** Dois telefones simultaneos do mesmo numero novo → dois INSERTs concorrentes em `clientes_zap` → UNIQUE constraint em `telefone` causa `DataIntegrityViolationException`. Tratamento: catch a excecao e retorna o registro existente:
```java
try {
    return repository.save(novo);
} catch (DataIntegrityViolationException e) {
    return repository.findByTelefone(normalizado)
        .orElseThrow(() -> new IllegalStateException("Race no INSERT mas registro nao existe"));
}
```

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pitfalls de seguranca/correcao
- `.planning/research/PITFALLS.md` §C-01 — TOCTOU race com janela 24h (REQUIRES_NEW + NOW() do DB)
- `.planning/research/PITFALLS.md` §C-06 — wamid concurrent delivery race (ON CONFLICT DO NOTHING + row-count)
- `.planning/research/PITFALLS.md` §C-08 — media URL 5min expiry (Phase 3 territory; Phase 2 so persiste media_id se vier no payload)
- `.planning/research/PITFALLS.md` §C-13 — Brazilian 9th-digit normalization (tabela DDDs)

### Arquitetura e contratos
- `.planning/research/ARCHITECTURE.md` §"Component Responsibilities" — entity + repository definitions
- `.planning/research/ARCHITECTURE.md` §"Data Flow — Fluxo Inbound Completo" steps 6-7 (persistencia + atualiza ultima_mensagem_em)
- `.planning/research/ARCHITECTURE.md` §"Anti-Pattern 1" (NAO sincrono ao Meta — mas Phase 2 e sincrono ao webhook por enquanto; Phase 3 refatora)
- `.planning/PROJECT.md` §"Active" — WHATS-02..06, LIB-01 mapeados parcialmente
- `.planning/REQUIREMENTS.md` §"Webhook" §"Persistencia" — WEB-05/06/07, PER-02..07 (locked)
- `.planning/ROADMAP.md` §"Phase 2" — 5 success criteria
- `.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md` — decisoes herdadas (D-01 filter, D-04 stub, D-06 SQL portavel)
- `.planning/phases/01-fundacao-hmac-webhook/01-VERIFICATION.md` — confirma que Phase 1 esta sound (Phase 2 usa `CachedBodyHttpServletRequest`, `WhatsAppProperties`, schema whatsapp + 4 tabelas)

### Padroes do codebase
- `api-email/src/main/java/br/com/erpkit/email/model/Email.java` — modelo de `@Entity` no monorepo
- `api-email/src/main/java/br/com/erpkit/email/repository/EmailRepository.java` — modelo de Spring Data JPA + `@Query`
- `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java` — modelo de service que coordena entity + repo
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java` — base exception para erros de negocio (Phase 2 nao precisa criar mais)
- `api-whatsapp/src/main/resources/db/migration/V1..V4__*.sql` (Phase 1) — esquema DDL ja aplicado

### Convencoes
- `.planning/codebase/CONVENTIONS.md` — PT-BR, sem Lombok, packages por camada, `@Transactional` so quando explicito (REQUIRES_NEW e o caso onde precisa)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CachedBodyHttpServletRequest.getCachedBody()` (Phase 1) — fornece bytes para o parser sem reler InputStream
- `HmacSignatureFilter` (Phase 1) — passa o wrapper como request para o controller via `chain.doFilter(cached, response)`. Phase 2 controller pode fazer `instanceof CachedBodyHttpServletRequest` para acessar o body.
- `WhatsAppProperties` (Phase 1) — sem novos campos em Phase 2; `appSecret` ja foi consumido pelo Filter.
- `ModuloException` (lib-shared) — para qualquer erro de negocio que vire HTTP code.
- Migrations V1..V4 (Phase 1) — esquema ja deployado, entities apenas validam.
- H2 PG-mode no test profile — Phase 2 reutiliza, valida ON CONFLICT na primeira wave.

### Established Patterns
- **`@Entity` + `@Table(schema = "whatsapp")`** — mapeia para schema isolado.
- **Service layer sem `@Transactional` explicito** salvo casos especiais (REQUIRES_NEW). Spring Data JPA wrap automatico cobre o trivial.
- **Native query para ON CONFLICT**: JPA nao suporta nativamente; usar `@Modifying @Query(nativeQuery=true)`.
- **Bean Validation no boundary** (DTOs com `@NotBlank` etc.). Phase 2 DTOs de parser (input) nao precisam de validation extensa — vem do Meta, ja validado pelo HMAC; mais defensivo no controller layer da phase 4 quando ERP envia request.
- **Tests JPA usando H2 PG-mode** — `@SpringBootTest(@ActiveProfiles="test")` + `@Sql` ou data setup direto via repository.
- **Hibernate `ddl-auto: validate`** — schema vem 100% das migrations. Entities devem bater com colunas/tipos.

### Integration Points
- **Phase 1 → Phase 2:** `WebhookController.receberWebhook` atualiza para chamar `mensagemService.processarWebhook(rawBody)`.
- **Phase 2 → Phase 3:** `MensagemService.processarWebhook` sera quebrado em parte sincrona (parse + idempotency check fast-path) e parte `@Async` (persistir + ClienteZapService + callback ERP). Phase 3 territory.
- **Phase 2 → Phase 4:** `ClienteZap.ultimaMensagemEm` atualizado com REQUIRES_NEW Phase 2 e o que `WindowEnforcementService` (Phase 4) le antes de cada envio.
- **DB clock vs JVM clock:** `atualizarUltimaMensagemEm` usa `NOW()` (DB) — Phase 4 lera tambem do DB para consistencia.

</code_context>

<specifics>
## Specific Ideas

- **Telefone normalizado armazenado no banco** — single source of truth. Lookups sempre normalizam input antes de buscar.
- **Statuses do Meta NAO persistidos em Phase 2** — escopo enxuto; entram em Phase 4 ou posteriormente quando outbound estiver presente.
- **Idempotency em duas camadas:** Application code do `IdempotencyService` (row-count gate) + UNIQUE constraint do DB (safety net). Ambas funcionam juntas.
- **Spike obrigatorio em Wave 1 do Phase 2** — validar que H2 PG-mode aceita `INSERT ... ON CONFLICT (wamid) DO NOTHING`. Se falhar, fallback documentado e catch DataIntegrityViolationException apos save. RESEARCH ja deve cobrir esse fallback.
- **`@Lob` no `conteudo`** — alguns payloads de texto podem exceder 65k chars (lista interactive com descricao longa); usar `TEXT` no DB + `@Lob` no Java mantem compatibilidade. Ja esta em V2 migration.
- **`MediaCache` entity criada nesta phase** mesmo sendo populada so em Phase 4 — agrupar entities + repos cohesivamente. `MediaCacheService` (Phase 4) opera contra ela.
- **Sem persistencia explicita de statuses Meta** (sent/delivered/read/failed). Fica em backlog opcional.

</specifics>

<deferred>
## Deferred Ideas

- **`@Async` boundary apos ack 200** — Phase 3 (ROU-01)
- **`ErpCallbackClient`, `MessageRouter`** — Phase 3 (ROU-02..05)
- **Download eager de media entrante** — Phase 3 (ROU-05) — URL Meta expira em 5min
- **`MediaCacheService` (sha256 → media_id, TTL 30d)** — Phase 4 (consume MediaCache entity criada aqui)
- **`WindowEnforcementService` 24h** — Phase 4 (consume ultima_mensagem_em populado aqui)
- **`WhatsAppCloudClient`** — Phase 4 (envio outbound)
- **Persistencia de statuses Meta** (`sent/delivered/read/failed`) — opcional, possivel feature de Phase 4 ou backlog
- **Reconciliation job para `id_cliente_erp = null`** — fora desta milestone (operacional, ERP-MUDAS territory)
- **Bidirectional phone matching** (busca tanto formato 13 quanto 14 digitos) — overhead minimo evitado por armazenar normalizado; pode entrar em backlog se ERP-MUDAS Cliente.telefone vier em formato cru.

</deferred>

---

*Phase: 2-Persistencia + Idempotencia*
*Context gathered: 2026-05-05*
