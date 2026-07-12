package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.DiagnosticoResponse;
import br.com.erpkit.whatsapp.dto.EnviarBotoesRequest;
import br.com.erpkit.whatsapp.dto.EnviarDocumentoRequest;
import br.com.erpkit.whatsapp.dto.EnviarListaRequest;
import br.com.erpkit.whatsapp.dto.EnviarTextoRequest;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.dto.FeedRecentesResponse;
import br.com.erpkit.whatsapp.dto.StatusResponse;
import br.com.erpkit.whatsapp.service.MonitorService;
import br.com.erpkit.whatsapp.service.WhatsAppCloudClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Endpoints REST para o ERP enviar mensagens outbound (OUT-11). 5 endpoints sob
 * {@code /api/whatsapp/*} — protegidos por {@code ApiKeyFilter} (Phase 1
 * SecurityConfig herdado). Webhook publico {@code /webhook/whatsapp/*} fica a
 * parte (HMAC, Phase 1).
 *
 * <p><b>Thin wrapper (pattern monorepo):</b> cada endpoint valida via
 * {@code @Valid @RequestBody}, delega ao {@link WhatsAppCloudClient}, retorna
 * {@code ResponseEntity}. Excecoes ({@code JanelaConversaFechadaException} →
 * 409, {@code MetaApiException} → 422/502/504/503, {@code MethodArgumentNotValidException}
 * → 400) sao capturadas pelo {@code GlobalExceptionHandler} (lib-shared 04-01)
 * que propaga {@code codigo+metaErrorCode} via {@code CodigoCarrier}.
 *
 * <p><b>Decodificacao base64 (D-01):</b> {@code enviarDocumento} decodifica
 * {@code mediaBase64} via {@code Base64.getDecoder().decode} dentro de try/catch
 * {@code IllegalArgumentException} → 400 com mensagem clara. Bytes resultantes
 * sao passados para {@code WhatsAppCloudClient.enviarDocumento(byte[])} que
 * cuida do cache + upload multipart Meta-side.
 *
 * <p><b>Status endpoint minimal (D-04):</b> retorna {@code phoneNumberId} (sanity
 * check vs env var) + {@code circuitBreakerState} (operador diagnostica circuit
 * aberto). Sem subscribed_apps validation — Phase 6 territory (PITFALLS C-12).
 *
 * <p><b>Suporte a tela de Testes (autenticado):</b> {@code GET /mensagens/recentes}
 * (feed das ultimas N mensagens) e {@code GET /diagnostico} expoem, sob
 * {@code /api/whatsapp/**} (protegido pelo {@code WhatsappAuthFilter} — Bearer do
 * atendente), a MESMA logica do painel {@code /monitor/*} de dev/meta, delegando ao
 * {@link MonitorService}. Assim a tela funciona em PRODUCAO, onde o {@code MonitorController}
 * ({@code @Profile dev/meta}) nao existe. O envio de teste reusa os endpoints ja
 * existentes {@code /enviar-texto} e {@code /enviar-botoes}.
 */
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private final WhatsAppCloudClient cloudClient;
    private final WhatsAppProperties properties;
    private final CircuitBreakerRegistry cbRegistry;
    private final MonitorService monitorService;

    public WhatsAppController(WhatsAppCloudClient cloudClient,
                              WhatsAppProperties properties,
                              CircuitBreakerRegistry cbRegistry,
                              MonitorService monitorService) {
        this.cloudClient = cloudClient;
        this.properties = properties;
        this.cbRegistry = cbRegistry;
        this.monitorService = monitorService;
    }

    @PostMapping("/enviar-texto")
    public ResponseEntity<EnvioResponse> enviarTexto(@Valid @RequestBody EnviarTextoRequest req) {
        return ResponseEntity.ok(cloudClient.enviarTexto(req.telefone(), req.texto()));
    }

    @PostMapping("/enviar-documento")
    public ResponseEntity<EnvioResponse> enviarDocumento(@Valid @RequestBody EnviarDocumentoRequest req) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.mediaBase64());
        } catch (IllegalArgumentException e) {
            // D-01: ERP envia base64 dentro de JSON — falha de decode = bug do ERP, 400
            throw new ModuloException("mediaBase64 invalido: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(cloudClient.enviarDocumento(
                req.telefone(), bytes, req.filename(), req.mimeType(), req.caption()));
    }

    @PostMapping("/enviar-botoes")
    public ResponseEntity<EnvioResponse> enviarBotoes(@Valid @RequestBody EnviarBotoesRequest req) {
        return ResponseEntity.ok(cloudClient.enviarBotoes(req.telefone(), req.texto(), req.botoes()));
    }

    @PostMapping("/enviar-lista")
    public ResponseEntity<EnvioResponse> enviarLista(@Valid @RequestBody EnviarListaRequest req) {
        return ResponseEntity.ok(cloudClient.enviarLista(req.telefone(), req.texto(), req.secoes()));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        String state = cbRegistry.find("whatsapp-cloud")
                .map(cb -> cb.getState().name())
                .orElse("UNKNOWN");
        return ResponseEntity.ok(new StatusResponse("UP", state, properties.getPhoneNumberId()));
    }

    /**
     * Feed das ultimas mensagens (entrada + saida) para a tela de Testes. Equivalente
     * autenticado de {@code GET /monitor/feed} (dev/meta) — mesma logica via
     * {@link MonitorService}. O {@code limit} e opcional (default/teto no service).
     */
    @GetMapping("/mensagens/recentes")
    public ResponseEntity<FeedRecentesResponse> mensagensRecentes(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(monitorService.feed(limit));
    }

    /**
     * Diagnostico do modulo (token Meta + alcancabilidade do ERP + circuit breaker) para
     * a tela de Testes. Equivalente autenticado de {@code GET /monitor/diagnostico}
     * (dev/meta) — mesma logica via {@link MonitorService}.
     */
    @GetMapping("/diagnostico")
    public ResponseEntity<DiagnosticoResponse> diagnostico() {
        return ResponseEntity.ok(monitorService.diagnostico());
    }
}
