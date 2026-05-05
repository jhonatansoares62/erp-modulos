# Architecture Research

**Domain:** WhatsApp Cloud API integration — reactive service module (api-whatsapp + lib-whatsapp-client)
**Researched:** 2026-05-05
**Confidence:** HIGH (based on existing codebase + Meta Cloud API documentation)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         EXTERNAL WORLD                                           │
│  Meta Cloud API (POST webhook)          ERP-MUDAS (localhost:8091)               │
│  https://zap-<slug>.erpkit.com.br  ←──────────────── /api/modulos/whatsapp/*    │
└──────────────────────────────┬───────────────────────────────────────────────────┘
                               │ Cloudflare Tunnel
                               ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    api-whatsapp (porta 9193, Windows Service)                    │
│                                                                                  │
│  ┌─────────────────────┐   ┌───────────────────────────────────────────────────┐ │
│  │  WebhookController  │   │      WhatsAppController  (endpoints internos ERP) │ │
│  │  GET  /webhook/wha… │   │  GET  /api/whatsapp/status                        │ │
│  │  POST /webhook/wha… │   │  POST /api/whatsapp/enviar-texto                  │ │
│  └──────────┬──────────┘   │  POST /api/whatsapp/enviar-documento              │ │
│             │              │  POST /api/whatsapp/enviar-botoes                 │ │
│             │              │  POST /api/whatsapp/enviar-lista                  │ │
│             │              └────────────────────┬──────────────────────────────┘ │
│             │ (inbound)                         │ (outbound direto do ERP)       │
│             ▼                                   ▼                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │                      MensagemService (orquestrador central)              │    │
│  │                                                                          │    │
│  │  ┌──────────────┐  ┌───────────────────┐  ┌──────────────────────────┐  │    │
│  │  │ HmacValidator│  │IdempotencyService │  │WindowEnforcementService  │  │    │
│  │  │ (appSecret)  │  │ (wamid UNIQUE)    │  │ (24h check)              │  │    │
│  │  └──────────────┘  └───────────────────┘  └──────────────────────────┘  │    │
│  │                                                                          │    │
│  │  ┌─────────────────────────────────────────────────────────────────┐    │    │
│  │  │              MessageRouter (identifica cliente + roteia)        │    │    │
│  │  │   ClienteZapService.identificarPorTelefone()                    │    │    │
│  │  │   ErpCallbackClient.despacharComando()                         │    │    │
│  │  └─────────────────────────────────────────────────────────────────┘    │    │
│  │                                                                          │    │
│  │  ┌───────────────────────────────────────────────────────────────────┐  │    │
│  │  │              WhatsAppCloudClient (outbound)                       │  │    │
│  │  │  enviarTexto() / enviarDocumento() / enviarBotoes() / enviarLista│  │    │
│  │  │  MediaCacheService.resolverMediaId(sha256)                        │  │    │
│  │  └───────────────────────────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │                   Repositories (Spring Data JPA)                          │   │
│  │  ClienteZapRepository   MensagemLogRepository   MediaCacheRepository      │   │
│  └──────────────────────────────┬────────────────────────────────────────────┘   │
│                                 │                                                │
│  ┌──────────────────────────────▼────────────────────────────────────────────┐   │
│  │         PostgreSQL — schema whatsapp (mesmo servidor do ERP-MUDAS)        │   │
│  │   clientes_zap   mensagens_log   media_cache                               │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│                 lib-whatsapp-client (starter — vive em ERP-MUDAS)                │
│                                                                                  │
│  WhatsAppClientAutoConfiguration  (@ConditionalOnProperty)                       │
│  WhatsAppProperties               (app.modulos.whatsapp.{url,apiKey,timeout})    │
│  WhatsAppClient (interface)       isOnline() / enviarTexto() / status()          │
│  WhatsAppClientImpl               RestTemplate + Resilience4j CB + Retry         │
│  WhatsAppCommandHandler (SPI)     interface implementada pelo ERP por comando     │
│  WhatsAppCommandRegistry          coleta todos os beans WhatsAppCommandHandler   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

### api-whatsapp — componentes

| Componente | Responsabilidade | Camada | Arquivo sugerido |
|------------|-----------------|--------|-----------------|
| `WebhookController` | GET hub.challenge + POST webhook do Meta; responde 200 em <5s; delega ao MensagemService | controller | `webhook/WebhookController.java` |
| `WhatsAppController` | Endpoints internos para o ERP: enviar-texto, enviar-documento, enviar-botoes, enviar-lista, status | controller | `controller/WhatsAppController.java` |
| `HealthController` | GET /health — espelha api-consultas | controller | `controller/HealthController.java` |
| `MensagemService` | Orquestrador do fluxo inbound: chama HmacValidator → IdempotencyService → persiste → roteia → despacha saida | service | `service/MensagemService.java` |
| `HmacValidator` | Valida `X-Hub-Signature-256` com HMAC-SHA256 usando `appSecret` | service | `service/HmacValidator.java` |
| `IdempotencyService` | Checa se wamid já existe em mensagens_log (UNIQUE constraint); retorna true se duplicata | service | `service/IdempotencyService.java` |
| `WindowEnforcementService` | Lê `ultima_mensagem_em` de clientes_zap; rejeita com 409 se > 24h | service | `service/WindowEnforcementService.java` |
| `MessageRouter` | Identifica cliente_erp pelo telefone; invoca ErpCallbackClient; analisa resposta do ERP para escolher tipo de saida | service | `service/MessageRouter.java` |
| `ClienteZapService` | CRUD de clientes_zap: identificarPorTelefone, registrarOuAtualizar, atualizarUltimaMensagem | service | `service/ClienteZapService.java` |
| `ErpCallbackClient` | HTTP POST para `erpCallbackUrl` (configuravel); timeout + fallback log; retorna `ComandoRespostaDTO` | service | `service/ErpCallbackClient.java` |
| `WhatsAppCloudClient` | Chamadas à Graph API do Meta: upload media, enviar texto/documento/botoes/lista; recebe accessToken + phoneNumberId | service | `service/WhatsAppCloudClient.java` |
| `MediaCacheService` | sha256 → media_id com TTL 30d; lookup no DB antes de upload; persiste após upload bem-sucedido | service | `service/MediaCacheService.java` |
| `WhatsAppProperties` | `@ConfigurationProperties("app.modulos.whatsapp")` local: phoneNumberId, accessToken, appSecret, verifyToken, erpCallbackUrl — falha no boot se ausentes | config | `config/WhatsAppProperties.java` |
| `SecurityConfig` | Estende ApiKeyFilter de lib-shared; rota `/webhook/whatsapp` exposta sem API Key (validacao via HMAC) | config | `config/SecurityConfig.java` |
| `ClienteZapRepository` | extends JpaRepository\<ClienteZap, Long\>; findByTelefone | repository | `repository/ClienteZapRepository.java` |
| `MensagemLogRepository` | extends JpaRepository\<MensagemLog, Long\>; existsByWamid | repository | `repository/MensagemLogRepository.java` |
| `MediaCacheRepository` | extends JpaRepository\<MediaCache, String\> (PK = sha256); findByArquivoHash; deleteByExpiraEmBefore | repository | `repository/MediaCacheRepository.java` |
| `ClienteZap` | @Entity: id, idClienteErp (FK externa), telefone UNIQUE, ultimaMensagemEm, estadoConversa | model | `model/ClienteZap.java` |
| `MensagemLog` | @Entity: id, telefone, direcao (in/out), tipo, conteudo, mediaId, wamid UNIQUE, criadoEm | model | `model/MensagemLog.java` |
| `MediaCache` | @Entity: arquivoHash (PK sha256), mediaId, expiraEm | model | `model/MediaCache.java` |
| Flyway migrations | V1..V4 no schema whatsapp | resources | `db/migration/` |

### lib-whatsapp-client — componentes

| Componente | Responsabilidade | Espelho em lib-consultas-client |
|------------|-----------------|--------------------------------|
| `WhatsAppClientAutoConfiguration` | `@AutoConfiguration` + `@ConditionalOnProperty("app.modulos.whatsapp.enabled")` + `@EnableConfigurationProperties` | `ConsultasClientAutoConfiguration` |
| `WhatsAppProperties` | `@ConfigurationProperties("app.modulos.whatsapp")` do lado do ERP: url, apiKey, timeout | `ConsultasProperties` |
| `WhatsAppClient` (interface) | `isOnline()`, `isHabilitado()`, `getCircuitBreakerState()`, `enviarTexto(...)`, `status()` | `ConsultasClient` |
| `WhatsAppClientImpl` | RestTemplate + CircuitBreaker (10-call window, 50%, 60s) + Retry (3, 1s/2.0x) — idêntico ao ConsultasClientImpl | `ConsultasClientImpl` |
| `WhatsAppCommandHandler` (SPI) | Interface implementada pelo ERP, 1 bean por comando | — (novo conceito, sem análogo) |
| `WhatsAppCommandRegistry` | Coleta todos os `WhatsAppCommandHandler` beans; resolve handler pelo keyword | — (novo conceito) |
| `ComandoRequest` / `ComandoResposta` DTOs | Contrato de payload do callback | — |
| `WhatsAppException` / `WhatsAppIndisponivelException` | Espelha ConsultasException / ConsultasIndisponivelException | `ConsultasException` |

---

## Recommended Project Structure

```
api-whatsapp/
└── src/main/java/br/com/erpkit/whatsapp/
    ├── WhatsAppApplication.java
    ├── controller/
    │   ├── WebhookController.java         # GET hub.challenge + POST inbound
    │   ├── WhatsAppController.java        # Endpoints internos ERP (enviar-*)
    │   └── HealthController.java          # GET /health
    ├── service/
    │   ├── MensagemService.java           # Orquestrador inbound
    │   ├── HmacValidator.java             # X-Hub-Signature-256
    │   ├── IdempotencyService.java        # wamid UNIQUE check
    │   ├── WindowEnforcementService.java  # trava 24h
    │   ├── MessageRouter.java             # identifica cliente + callback ERP
    │   ├── ClienteZapService.java         # CRUD clientes_zap
    │   ├── ErpCallbackClient.java         # HTTP → ERP erpCallbackUrl
    │   ├── WhatsAppCloudClient.java       # chamadas Graph API Meta
    │   └── MediaCacheService.java         # sha256 → media_id, TTL 30d
    ├── model/
    │   ├── ClienteZap.java
    │   ├── MensagemLog.java
    │   └── MediaCache.java
    ├── repository/
    │   ├── ClienteZapRepository.java
    │   ├── MensagemLogRepository.java
    │   └── MediaCacheRepository.java
    ├── dto/
    │   ├── WebhookPayloadDTO.java         # envelope completo do Meta
    │   ├── MensagemEntranteDTO.java       # view extraida do envelope
    │   ├── EnviarTextoRequest.java
    │   ├── EnviarDocumentoRequest.java
    │   ├── EnviarBotoesRequest.java
    │   ├── EnviarListaRequest.java
    │   └── WhatsAppStatusResponse.java
    ├── vo/
    │   └── TipoMensagem.java              # enum: TEXT, DOCUMENT, INTERACTIVE_BUTTON, INTERACTIVE_LIST
    ├── config/
    │   ├── WhatsAppProperties.java        # @ConfigurationProperties("app.modulos.whatsapp") local
    │   └── SecurityConfig.java            # expõe /webhook sem API key
    └── exception/
        └── JanelaFechadaException.java    # 409 quando janela > 24h

api-whatsapp/src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__criar_tabela_clientes_zap.sql
        ├── V2__criar_tabela_mensagens_log.sql
        ├── V3__criar_tabela_media_cache.sql
        └── V4__criar_tabela_estado_conversa.sql

lib-whatsapp-client/
└── src/main/java/br/com/erpkit/whatsapp/client/
    ├── WhatsAppClientAutoConfiguration.java
    ├── WhatsAppProperties.java             # lado ERP: url, apiKey, timeout
    ├── WhatsAppClient.java                 # interface
    ├── WhatsAppClientImpl.java             # Resilience4j
    ├── WhatsAppCommandHandler.java         # SPI interface
    ├── WhatsAppCommandRegistry.java        # resolve handler por keyword
    ├── dto/
    │   ├── ComandoRequest.java
    │   ├── ComandoResposta.java
    │   └── EnviarTextoRequest.java         # re-exposto para o ERP usar
    └── exception/
        ├── WhatsAppException.java
        └── WhatsAppIndisponivelException.java

lib-whatsapp-client/src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Structure Rationale

- **controller/webhook vs controller/internal:** WebhookController e WhatsAppController separados porque têm naturezas opostas — webhook é publico (sem API key, validado por HMAC), endpoints internos exigem API key do ERP. SecurityConfig precisa tratar os dois de forma diferente.
- **service/ sem subpacotes:** Poucos componentes, dividir em sub-pacotes prematuramente aumenta navegação sem ganho. Se crescer, extrair `outbound/` e `inbound/` como subpacotes.
- **vo/TipoMensagem enum:** Centraliza os 4 tipos de mensagem de saida e os tipos de mensagem entrante do Meta em um único artefato, sem string literals espalhados.
- **lib-whatsapp-client/dto/ independente:** DTOs da lib não importam nada de api-whatsapp. O contrato `ComandoRequest/ComandoResposta` pertence à lib porque é o contrato do callback que o ERP expõe.

---

## Architectural Patterns

### Pattern 1: Webhook-First com Resposta 200 Imediata

**What:** WebhookController responde 200 OK assim que HMAC é validado e idempotência é verificada, antes de qualquer lógica de negócio. O processamento restante (persistência, callback ERP, envio de saída) acontece na mesma thread mas a resposta já foi committed.

**Motivação:** Meta reenvia o webhook se não receber 200 em <5s. Qualquer chamada ao ERP que demore mais que isso causa duplicação. A solução conservadora — e correta para este contexto on-premise onde não há filas de mensagens — é finalizar o processamento na mesma request mas garantir que a resposta HTTP precede as operações lentas via `ResponseEntity` retornado antes do corpo completo ser processado.

**Implementação correta:** Usar `@Async` ou `CompletableFuture.runAsync()` para o callback ao ERP e o envio de saída, retornando `ResponseEntity.ok()` imediatamente após persistir a mensagem entrante.

```java
// WebhookController.java
@PostMapping("/webhook/whatsapp")
public ResponseEntity<Void> receberWebhook(
        @RequestHeader("X-Hub-Signature-256") String signature,
        @RequestBody String corpo) {
    hmacValidator.validarOuRejeitar(signature, corpo);          // sync — fast
    MensagemEntranteDTO msg = parser.extrair(corpo);            // sync — fast
    if (idempotencyService.jaProcessado(msg.getWamid())) {
        return ResponseEntity.ok().build();                     // 200 para evitar reenvio Meta
    }
    mensagemService.processarAsync(msg);                        // async — ERP callback + saida
    return ResponseEntity.ok().build();                         // 200 retorna antes do async
}
```

**Trade-offs:** Processamento async significa que falhas pós-200 são perdidas silenciosamente se não houver log estruturado. Compensar com `log.error()` detalhado no handler do async. Não usar retry automático no async para evitar envios duplicados ao cliente final.

### Pattern 2: Trava de Custo Zero por Design (WindowEnforcementService)

**What:** Antes de qualquer chamada à Cloud API para envio de saída, `WindowEnforcementService` lê `ultima_mensagem_em` de `clientes_zap`. Se o instante atual − `ultima_mensagem_em` > 24h, lança `JanelaFechadaException` (HTTP 409) e registra log estruturado. A Cloud API nunca é chamada.

**Por que não é validação de negócio comum:** É uma trava arquitetural de custo zero. A ausência de `enviarTemplate()` (WHATS-12) é a 1ª linha de defesa; esta trava é a 2ª, protegendo contra bugs em handlers do ERP que tentem enviar fora da janela.

```java
// WindowEnforcementService.java
public void verificarOuRejeitar(String telefone) {
    ClienteZap cliente = clienteZapRepository.findByTelefone(telefone)
        .orElseThrow(() -> new ModuloException("Cliente não encontrado: " + telefone, NOT_FOUND));
    if (cliente.getUltimaMensagemEm() == null ||
        Duration.between(cliente.getUltimaMensagemEm(), Instant.now()).toHours() >= 24) {
        log.warn("Janela 24h fechada: telefone={}, ultima_mensagem_em={}", telefone, cliente.getUltimaMensagemEm());
        throw new JanelaFechadaException(telefone);
    }
}
```

### Pattern 3: Media Cache por sha256 (evita reupload)

**What:** MediaCacheService calcula sha256 dos bytes do arquivo antes de qualquer chamada à Graph API. Se encontrar `media_cache.arquivo_hash = sha256` com `expira_em` no futuro, retorna o `media_id` cacheado diretamente. Só faz upload se cache miss.

```java
// MediaCacheService.java
public String resolverMediaId(byte[] bytes, String mimeType, String nomeArquivo) {
    String hash = DigestUtils.sha256Hex(bytes);
    return mediaCacheRepository.findById(hash)
        .filter(c -> c.getExpiraEm().isAfter(Instant.now()))
        .map(MediaCache::getMediaId)
        .orElseGet(() -> {
            String mediaId = cloudClient.uploadMedia(bytes, mimeType, nomeArquivo);
            mediaCacheRepository.save(new MediaCache(hash, mediaId, Instant.now().plus(30, DAYS)));
            return mediaId;
        });
}
```

**Trade-offs:** TTL de 30 dias é conservador (Meta expira media_id em ~30 dias). Se a Graph API rejeitar um media_id expirado, o cache deve ser invalidado e refeito — tratar `HttpClientErrorException.BadRequest` no WhatsAppCloudClient como sinal para deletar entrada do cache e retentar.

### Pattern 4: SPI WhatsAppCommandHandler (lib-whatsapp-client)

**What:** Interface implementada pelo ERP, um bean por comando. O `WhatsAppCommandRegistry` (auto-configurado pela lib) coleta todos os beans `WhatsAppCommandHandler` presentes no contexto Spring do ERP e os indexa pelo keyword retornado por `getComando()`. Quando api-whatsapp chama `POST /api/modulos/whatsapp/comando`, o `ModulosController` do ERP delega ao registro.

```java
// lib-whatsapp-client — WhatsAppCommandHandler.java (SPI)
public interface WhatsAppCommandHandler {

    /**
     * Keyword exato ou prefixo que ativa este handler.
     * Exemplos: "orcamento", "boleto", "status", "nota", "aprovar", "recusar"
     */
    String getComando();

    /**
     * @param telefone  número do cliente (sem +, sem espaços)
     * @param payload   texto completo da mensagem ou button_reply.id / list_reply.id
     * @return          ComandoResposta descrevendo o que api-whatsapp deve enviar de volta
     */
    ComandoResposta processar(String telefone, String payload);
}

// lib-whatsapp-client — WhatsAppCommandRegistry.java
@Component
public class WhatsAppCommandRegistry {

    private final Map<String, WhatsAppCommandHandler> handlers;

    public WhatsAppCommandRegistry(List<WhatsAppCommandHandler> lista) {
        this.handlers = lista.stream()
            .collect(Collectors.toMap(
                h -> h.getComando().toLowerCase(Locale.ROOT),
                Function.identity()
            ));
    }

    public WhatsAppCommandHandler resolver(String comando) {
        String chave = comando.toLowerCase(Locale.ROOT).trim();
        // match exato primeiro; fallback para prefixo
        if (handlers.containsKey(chave)) return handlers.get(chave);
        return handlers.entrySet().stream()
            .filter(e -> chave.startsWith(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new ModuloException("Comando nao reconhecido: " + comando));
    }
}
```

**Registro de múltiplos handlers no ERP:**
```java
// ERP-MUDAS — OrcamentoCommandHandler.java
@Component
public class OrcamentoCommandHandler implements WhatsAppCommandHandler {
    @Override public String getComando() { return "orcamento"; }
    @Override public ComandoResposta processar(String telefone, String payload) { ... }
}
// BoletoCommandHandler → "boleto"
// StatusPedidoCommandHandler → "status"
// NotaFiscalCommandHandler → "nota"
// AprovarCommandHandler → "aprovar" (payload contém ID do orçamento: "aprovar 1234")
// RecusarCommandHandler → "recusar"
```

**ComandoResposta** descreve o que api-whatsapp deve enviar:
```java
// lib-whatsapp-client — ComandoResposta.java
public class ComandoResposta {
    private TipoResposta tipo;         // TEXTO, DOCUMENTO, BOTOES, LISTA, NENHUM
    private String texto;              // para TEXTO
    private byte[] bytes;              // para DOCUMENTO
    private String mimeType;           // ex: "application/pdf"
    private String nomeArquivo;        // ex: "orcamento-42.pdf"
    private String caption;            // legenda do documento
    private List<BotaoDTO> botoes;     // para BOTOES (max 3)
    private List<SecaoDTO> secoes;     // para LISTA (max 10 itens)
    // getters/setters...
}
```

---

## Data Flow

### Fluxo Inbound Completo (cliente → ERP → resposta)

```
[1] Meta POST /webhook/whatsapp
        │
        ▼
[2] HmacValidator.validarOuRejeitar(signature, corpo)
        │ ← rejeita com 401 se HMAC inválido (para aqui, sem log de negócio)
        ▼
[3] WebhookPayloadDTO parser.extrair(corpo)
        │ ← extrai: wamid, telefone (from), tipo (text/interactive), payload
        ▼
[4] IdempotencyService.jaProcessado(wamid)
        │ ← se true: return 200 imediatamente (Meta duplicou entrega, ignorar)
        ▼
[5] return ResponseEntity.ok().build()          ← HTTP 200 para o Meta AQUI
        + mensagemService.processarAsync(msg)   ← async daqui para baixo
        │
        ▼ (thread async)
[6] MensagemLogRepository.save(MensagemLog{direcao=in, ...})
        │ ← persiste ANTES de qualquer outra coisa (idempotência garantida por UNIQUE wamid)
        ▼
[7] ClienteZapService.atualizarUltimaMensagem(telefone, now())
        │ ← atualiza ultima_mensagem_em em clientes_zap
        ▼
[8] ClienteZapService.identificarPorTelefone(telefone)
        │ ← busca idClienteErp; se não encontrado: log.warn + fim (cliente não cadastrado)
        ▼
[9] ErpCallbackClient.despacharComando(idClienteErp, telefone, comando, payload)
        │ POST http://localhost:8091/api/modulos/whatsapp/comando
        │ ← timeout: 10s (configurável)
        │ ← se timeout ou 5xx: log.error + fim (sem retry automático, sem envio)
        ▼
[10] ComandoResposta resposta ← ERP retorna
        │ se tipo == NENHUM: fim
        ▼
[11] WindowEnforcementService.verificarOuRejeitar(telefone)
        │ ← lê ultima_mensagem_em (já atualizado no step 7 com a mensagem entrante)
        │ ← neste fluxo sempre vai passar (acabou de receber mensagem)
        │ ← a trava é mais relevante para envios diretos do ERP via WhatsAppController
        ▼
[12] switch(resposta.getTipo()):
        TEXTO     → WhatsAppCloudClient.enviarTexto(telefone, resposta.getTexto())
        DOCUMENTO → MediaCacheService.resolverMediaId(bytes) → enviarDocumento(...)
        BOTOES    → enviarBotoes(telefone, resposta.getBotoes(), resposta.getTexto())
        LISTA     → enviarLista(telefone, resposta.getSecoes(), ...)
        ▼
[13] MensagemLogRepository.save(MensagemLog{direcao=out, wamid=retornado_pelo_meta})
        ← persiste wamid de saída retornado pela Cloud API
```

### Fluxo Outbound Direto (ERP → enviar via WhatsAppController)

```
[1] ERP POST /api/whatsapp/enviar-texto  (com X-API-Key)
        │ (ou enviar-documento / enviar-botoes / enviar-lista)
        ▼
[2] WhatsAppController → MensagemService.enviar*(request)
        ▼
[3] WindowEnforcementService.verificarOuRejeitar(telefone)
        │ ← se > 24h: throws JanelaFechadaException → HTTP 409
        ▼
[4] WhatsAppCloudClient.enviar*(...)
        │ [DOCUMENTO] → MediaCacheService.resolverMediaId(sha256) antes do envio
        ▼
[5] MensagemLogRepository.save(MensagemLog{direcao=out, wamid=...})
        ▼
[6] HTTP 200 com wamid retornado
```

### Fluxo GET hub.challenge (verificação inicial do webhook)

```
[1] Meta GET /webhook/whatsapp?hub.mode=subscribe&hub.verify_token=X&hub.challenge=Y
        ▼
[2] WebhookController: verifica hub.verify_token == WhatsAppProperties.verifyToken
        ▼
[3] Retorna hub.challenge como plain text com HTTP 200
        ← Meta confirma o webhook como válido
```

---

## Transaction Boundaries e Error Semantics

### Fronteiras transacionais

**Regra geral:** Sem `@Transactional` explícito além do padrão Spring Data (cada `repository.save()` é atômico). Essa é a convenção existente no monorepo — `CONVENTIONS.md` confirma ausência de `@Transactional` explícito.

**Exceção crítica — idempotência de persistência (step 6 do fluxo inbound):**

```
MensagemLogRepository.save() com wamid UNIQUE
    └── se DataIntegrityViolationException → wamid já existe → silenciar + return
```

A UNIQUE constraint no banco é a fonte de verdade de idempotência. O `IdempotencyService` (step 4) é uma verificação de fast-path antes de retornar 200, mas a persistência é a guarda real. Se o step 4 passou mas o step 6 lançar `DataIntegrityViolationException`, isso é processamento duplicado depois do 200 — capturar, logar warn, retornar sem reprocessar.

### Semântica de erro do callback ao ERP (step 9)

| Situação | Comportamento | Motivo |
|----------|---------------|--------|
| ERP retorna 200 com `ComandoResposta` | Processa saída | Fluxo normal |
| ERP retorna 200 com `tipo=NENHUM` | Encerra sem envio | ERP decidiu não responder |
| ERP timeout (>10s) | `log.error` + encerra | Sem retry — evita envio duplicado ao cliente |
| ERP 4xx | `log.error` + encerra | Configuração errada no ERP, não retentar |
| ERP 5xx | `log.error` + encerra | ERP com erro, não retentar automaticamente |
| ERP indisponível (conexão recusada) | `log.error` + encerra | api-whatsapp vira no-op |

**Por que sem retry no callback?** O handler do ERP pode ter sido parcialmente executado (ex: marcou o orçamento como "visualizado" antes de cair). Retentar o callback pode executar lógica de negócio duas vezes.

### Semântica de erro do envio de saída (step 12-13)

| Situação | Comportamento |
|----------|---------------|
| Cloud API aceita + retorna wamid | Persiste MensagemLog{out, wamid} |
| Cloud API 4xx (telefone inválido) | `log.error` + `ModuloException(422)` — não persistir saída sem wamid |
| Cloud API 5xx / timeout | `log.error` + `ModuloException(503)` — não persistir |
| `enviarDocumento` + media_id expirado (4xx da Cloud API) | Invalida cache, reupload, retenta 1× |

**Media_id expirado:** Único caso com retry explícito (1 tentativa). TTL de 30d no cache é defensivo; a Cloud API pode expirar antes. A lógica está em `WhatsAppCloudClient.enviarDocumento()`.

---

## Espelho api-consultas → api-whatsapp / lib-consultas-client → lib-whatsapp-client

| Conceito | api-consultas / lib-consultas-client | api-whatsapp / lib-whatsapp-client |
|----------|--------------------------------------|-------------------------------------|
| Entry point | `ConsultasApplication.java` | `WhatsAppApplication.java` |
| Porta | 9192 | 9193 |
| Properties local | `@ConfigurationProperties("modulo.timeout-externo-ms")` | `WhatsAppProperties` com phoneNumberId, accessToken, appSecret, verifyToken, erpCallbackUrl |
| Controller | `ConsultasController` (GET /api/cep/{cep}) | `WebhookController` + `WhatsAppController` |
| Serviço de negócio | `CepService`, `CnpjService` | `MensagemService`, `ClienteZapService`, `MediaCacheService` |
| Cliente externo | `BrasilApiProvider`, `ViaCepProvider` | `WhatsAppCloudClient`, `ErpCallbackClient` |
| Cache | `@Cacheable` Caffeine (cep, cnpj) | `MediaCacheRepository` (JPA, TTL explícito) |
| Persistência | Sem (stateless) | PostgreSQL schema whatsapp, 3 tabelas, Flyway |
| Segurança | API key em todos os endpoints | API key em endpoints internos; HMAC em /webhook |
| Auto-config lib | `ConsultasClientAutoConfiguration` + `ConsultasProperties` | `WhatsAppClientAutoConfiguration` + `WhatsAppProperties` |
| Client lib interface | `ConsultasClient` | `WhatsAppClient` |
| Client lib impl | `ConsultasClientImpl` (Resilience4j) | `WhatsAppClientImpl` (Resilience4j — mesma config) |
| Exceções lib | `ConsultasException`, `ConsultasIndisponivelException` | `WhatsAppException`, `WhatsAppIndisponivelException` |
| Test auto-config | `ConsultasClientAutoConfigurationTest` | `WhatsAppClientAutoConfigurationTest` |
| SPI (novo) | — | `WhatsAppCommandHandler` + `WhatsAppCommandRegistry` |

---

## Build Order com Dependências Explícitas

O build order abaixo representa a sequência de implementação dentro da milestone, não fases Maven (o Maven compila tudo, a sequência é lógica de desenvolvimento).

```
Phase 1: Infraestrutura base + HMAC + webhook receive
    ├── WhatsAppProperties (local) — fail fast no boot se campos ausentes
    ├── SecurityConfig — expõe /webhook sem API key
    ├── HmacValidator — sem HmacValidator nada mais pode ser testado com segurança
    ├── WebhookController (GET hub.challenge + POST stub que retorna 200)
    └── Flyway V1-V4 migrations
    Depende de: lib-shared (já existe)
    Bloqueia: tudo abaixo

Phase 2: Persistência (modelos + repositories + idempotência)
    ├── ClienteZap, MensagemLog, MediaCache (@Entity)
    ├── ClienteZapRepository, MensagemLogRepository, MediaCacheRepository
    ├── IdempotencyService (usa MensagemLogRepository)
    └── ClienteZapService (usa ClienteZapRepository)
    Depende de: Phase 1 (migrations devem existir)
    Bloqueia: Phase 3 (roteamento precisa de repositórios)

Phase 3: Roteamento + callback ERP
    ├── ErpCallbackClient (HTTP POST para erpCallbackUrl)
    ├── MessageRouter (usa ClienteZapService + ErpCallbackClient)
    └── MensagemService.processarAsync() (integra Phase 1+2+3)
    Depende de: Phase 1 (HmacValidator), Phase 2 (repositórios)
    Bloqueia: Phase 4 (roteamento precisa existir para envio faz sentido)

Phase 4: Outbound (Cloud API + media cache + trava 24h)
    ├── WhatsAppCloudClient (texto, documento, botoes, lista)
    ├── MediaCacheService (usa MediaCacheRepository + WhatsAppCloudClient)
    ├── WindowEnforcementService (usa ClienteZapRepository)
    └── WhatsAppController (endpoints internos ERP)
    Depende de: Phase 2 (MediaCacheRepository, ClienteZapRepository), Phase 3 (MensagemService)
    Bloqueia: Phase 5 (lib precisa que api esteja completo)

Phase 5: lib-whatsapp-client
    ├── WhatsAppProperties (lado ERP)
    ├── WhatsAppClient interface + WhatsAppClientImpl (Resilience4j)
    ├── WhatsAppCommandHandler SPI + WhatsAppCommandRegistry
    ├── WhatsAppClientAutoConfiguration
    └── ComandoRequest / ComandoResposta DTOs
    Depende de: Phase 4 (api-whatsapp deve estar estável para definir contrato)
    Nota: Phase 5 pode ser desenvolvida em paralelo com Phase 4 se os DTOs forem acordados primeiro

Phase 6: Qualidade (testes + OpenAPI + README + RUNBOOK)
    ├── Unit tests: HmacValidator, IdempotencyService, MediaCacheService, WindowEnforcementService
    ├── Integration tests: WireMock para Cloud API (4 tipos + webhook + 5xx + timeout)
    ├── WhatsAppClientAutoConfigurationTest
    └── SpringDoc OpenAPI, README.md por módulo, RUNBOOK.md
    Depende de: Phases 1-5
```

**Dependência crítica de ordem:** HMAC + idempotência (Phase 1+2) devem preceder roteamento (Phase 3), porque o callback ao ERP não pode ser testado sem que a mensagem já tenha sido persistida (idempotência garante que testes com WireMock não processem a mesma mensagem duas vezes).

---

## O que fica em lib-shared vs lib-whatsapp-client

| Artefato | lib-shared | lib-whatsapp-client | Motivo |
|----------|-----------|---------------------|--------|
| `GlobalExceptionHandler` | Sim | — | Transversal a todos os módulos |
| `ApiKeyFilter` | Sim | — | Transversal a todos os módulos |
| `ModuloException` | Sim | — | Exceção base do monorepo |
| `ErrorResponse`, `HealthResponse` | Sim | — | DTOs compartilhados |
| `ComandoRequest` / `ComandoResposta` | — | Sim | Contrato específico do callback WhatsApp |
| `WhatsAppCommandHandler` (SPI) | — | Sim | Interface que o ERP implementa — não é transversal |
| `WhatsAppCommandRegistry` | — | Sim | Registry dos handlers — contexto WhatsApp |
| `WhatsAppException` | — | Sim | Exceção de comunicação com api-whatsapp |
| `BotaoDTO`, `SecaoDTO` | — | Sim | Tipos de dados de saída WhatsApp — não são genéricos |
| `TipoResposta` enum | — | Sim | Enum de tipos de mensagem WhatsApp |

**Regra:** lib-shared recebe apenas artefatos usados por `>=2 módulos api-*`. `ComandoRequest` é usado somente no contrato WhatsApp — fica na lib-whatsapp-client. Se no futuro outro módulo precisar de roteamento de comando similar, extrai para lib-shared.

---

## Integration Points

### External Services

| Serviço | Padrão de integração | Notas |
|---------|---------------------|-------|
| Meta Graph API (envio) | `WhatsAppCloudClient` via `RestTemplate` com `Bearer accessToken` | URL: `https://graph.facebook.com/v21.0/{phoneNumberId}/messages` |
| Meta Graph API (upload media) | `WhatsAppCloudClient.uploadMedia()` — multipart/form-data | Retorna `media_id`; reusar via MediaCacheService |
| Meta webhook (recebimento) | WebhookController — passivo, Meta empurra via POST | HMAC-SHA256 no header `X-Hub-Signature-256` |
| ERP callback (roteamento) | `ErpCallbackClient` — `RestTemplate` POST para `erpCallbackUrl` | Timeout 10s; sem Resilience4j (o ERP é local, não externo) |
| PostgreSQL local | Spring Data JPA + HikariCP, schema `whatsapp` | Mesmo servidor da porta 5433 do ERP-MUDAS, schema isolado |

### Internal Boundaries

| Fronteira | Comunicação | Considerações |
|-----------|-------------|---------------|
| WebhookController ↔ MensagemService | Direto (método Java) — async via `@Async` | MensagemService não deve lançar exceção propagando para o controller após o 200 ser retornado |
| WhatsAppController ↔ MensagemService | Direto (método Java) — síncrono | HTTP 409 de JanelaFechadaException deve chegar ao ERP |
| MensagemService ↔ WhatsAppCloudClient | Direto (método Java) | CloudClient lança ModuloException em falhas da Cloud API |
| ErpCallbackClient ↔ ERP-MUDAS | HTTP REST localhost | Contrato do payload: `ComandoRequest` → `ComandoResposta` |
| lib-whatsapp-client ↔ api-whatsapp | HTTP REST via `WhatsAppClientImpl` + Resilience4j | ERP não faz chamada direta ao Meta, sempre via api-whatsapp |

---

## Anti-Patterns

### Anti-Pattern 1: Enviar saída dentro da mesma transação do webhook

**What people do:** Receber webhook, persistir mensagem, chamar ERP callback e enviar resposta ao cliente — tudo no mesmo método síncrono da request HTTP do Meta.

**Why it's wrong:** Qualquer lentidão no ERP (ex: geração de PDF de orçamento) ou na Cloud API causa timeout no webhook do Meta. Meta reenvia após 5s sem resposta, gerando duplicações. Com o modelo síncrono, um timeout de 10s no ERP callback garante duplicação.

**Do this instead:** Responder 200 imediatamente após HMAC + idempotência (< 1ms). Processar callback e envio em `@Async` com log estruturado de falhas.

### Anti-Pattern 2: Implementar reenvio automático fora da janela 24h

**What people do:** Adicionar lógica de retry no `WindowEnforcementService` que tenta enviar um template pago quando a janela fecha.

**Why it's wrong:** Templates cobram por mensagem enviada (custo Meta). A constraint de custo zero do produto é garantida exatamente pela ausência desse comportamento. A trava deve ser um hard 409, sem fallback.

**Do this instead:** Rejeitar com `JanelaFechadaException` (HTTP 409), logar estruturado com `log.warn`, retornar sem enviar nada ao Meta.

### Anti-Pattern 3: `WhatsAppCommandHandler` com lógica de roteamento genérica no api-whatsapp

**What people do:** Colocar switches de `if comando == "orcamento"` dentro do `MessageRouter` do api-whatsapp.

**Why it's wrong:** api-whatsapp é um módulo reaproveitável entre ERPs. ERP-MUDAS tem "orçamento", outro ERP tem "pedido" — o api-whatsapp não pode conhecer os domínios de cada ERP.

**Do this instead:** `MessageRouter` invoca `ErpCallbackClient` que despacha para o ERP. O ERP usa `WhatsAppCommandRegistry` (da lib) para resolver qual `WhatsAppCommandHandler` processa o comando.

### Anti-Pattern 4: Expor `WhatsAppCloudClient` diretamente como bean injetável no ERP

**What people do:** Disponibilizar o cliente da Cloud API como bean público acessível do ERP, para o ERP enviar mensagens diretamente ao Meta.

**Why it's wrong:** Remove o ponto de controle centralizado (WindowEnforcementService, log de saída). O ERP poderia enviar templates ou mensagens fora da janela sem que api-whatsapp saiba.

**Do this instead:** ERP envia mensagens sempre via `POST /api/whatsapp/enviar-*` no api-whatsapp, que aplica todas as travas antes de chamar a Cloud API.

---

## Scaling Considerations

| Escala | Ajustes de arquitetura |
|--------|----------------------|
| 1 cliente on-premise | Arquitetura atual — sem ajustes necessários. Thread pool padrão Spring Boot suficiente. |
| 10-50 clientes on-premise | Sem mudança de arquitetura — cada cliente tem sua própria instância local. Considerar RUNBOOK para provisionamento automatizado. |
| Compartilhado (multi-tenant SaaS) | Requer multi-tenancy: schema por cliente ou row-level security + `phone_number_id` por tenant. Fora do escopo desta milestone. |

---

## Sources

- Codebase existente: `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java` — padrão Resilience4j confirmado com código real
- Codebase existente: `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java` — padrão auto-config confirmado
- `C:\projetos\erp-modulos\PLANO-WHATSAPP.md` — decisões arquiteturais D1-D10, modelo de dados, fluxo end-to-end
- `.planning/PROJECT.md` — requirements WHATS-01..18 e LIB-01..05
- Meta Cloud API webhook payload structure — [WebSearch: developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/reference/messages](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/reference/messages)
- WhatsApp interactive message types (button_reply, list_reply) — [WebSearch: hookdeck.com guide to WhatsApp webhooks](https://hookdeck.com/webhooks/platforms/guide-to-whatsapp-webhooks-features-and-best-practices)
- WhatsApp Cloud API at-least-once delivery and idempotency — confirmado por múltiplas fontes WebSearch (MEDIUM confidence)
- `.planning/codebase/CONVENTIONS.md` — convenções de nomenclatura, ausência de `@Transactional` explícito

---

*Architecture research for: api-whatsapp + lib-whatsapp-client (WhatsApp Cloud API integration module)*
*Researched: 2026-05-05*
