package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.service.MensagemService;
import br.com.erpkit.whatsapp.web.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoints publicos do webhook do Meta (validados por HMAC, nao por API key).
 *
 * <ul>
 *   <li>GET {@code /webhook/whatsapp} — handshake do Meta. Ecoa {@code hub.challenge}
 *       como {@code text/plain} (PITFALLS C-10). {@code verifyToken} comparado
 *       via {@link MessageDigest#isEqual(byte[], byte[])} em UTF-8 (consistencia
 *       com HMAC, custo zero). INALTERADO da Phase 1.</li>
 *   <li>POST {@code /webhook/whatsapp} — Phase 2: delega ao
 *       {@link MensagemService} (parse + idempotency + persist + atualiza
 *       ultima_mensagem_em). HMAC ja validado pelo
 *       {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter}
 *       (HIGHEST_PRECEDENCE) e body cacheado em
 *       {@link CachedBodyHttpServletRequest}. Erro de parse ou runtime e
 *       capturado e respondido com 200 (ack-first defensivo — PITFALLS C-05;
 *       evita Meta retry storm em payload malformado).</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties properties;
    private final MensagemService mensagemService;

    public WebhookController(WhatsAppProperties properties, MensagemService mensagemService) {
        this.properties = properties;
        this.mensagemService = mensagemService;
    }

    /**
     * GET handshake do Meta. Retorna {@code hub.challenge} como {@code text/plain}
     * (PITFALLS C-10 — sem JSON wrap). Comparacao do {@code verifyToken} via
     * {@link MessageDigest#isEqual(byte[], byte[])} em UTF-8 — constant-time,
     * consistente com HMAC.
     *
     * <p>Os parametros tem ponto no nome (Meta padrao), exigindo string literal no
     * {@code @RequestParam}. Spring MVC suporta — sem o nome explicito,
     * {@code @RequestParam String hubMode} tentaria casar com nome de variavel
     * Java e falharia.
     *
     * <p><b>INALTERADO da Phase 1.</b>
     */
    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        boolean modeOk = "subscribe".equals(mode);
        // Constant-time comparison — verifyToken so aparece em handshake mas custo zero
        byte[] expected = properties.getVerifyToken().getBytes(StandardCharsets.UTF_8);
        byte[] received = (verifyToken == null ? new byte[0] : verifyToken.getBytes(StandardCharsets.UTF_8));
        boolean tokenOk = MessageDigest.isEqual(expected, received);

        if (modeOk && tokenOk) {
            log.info("Webhook verificado pelo Meta — hub.challenge ecoado");
            return ResponseEntity.ok(challenge);
        }
        // NUNCA logar verifyToken received (potencial vazamento)
        log.warn("Verificacao do webhook rejeitada — mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * POST do Meta. Phase 2 substitui o stub da Phase 1: agora delega ao
     * {@link MensagemService} (parse + idempotency + persist + atualizar
     * ultima_mensagem_em). Sincrono em Phase 2 — Phase 3 vira {@code @Async}.
     *
     * <p>HMAC ja validado pelo
     * {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter}. Body cacheado
     * em {@link CachedBodyHttpServletRequest}.
     *
     * <p><b>Erro handling (ack-first defensivo, RESEARCH §10.2):</b> capturar
     * excecao de parse e logar como ERROR; retornar 200 mesmo assim. Alinhamento
     * com Phase 3 async (que NAO podera propagar pro Meta apos ack) + defesa
     * contra Meta retry storm (PITFALLS C-05) — JSON malformado nao deve causar
     * reentrega ad eternum. Trade-off: mascara bugs em producao em troca de
     * estabilidade. Mitigacao: log.error com stack trace + Phase 6 pode adicionar
     * metric counter.
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receber(HttpServletRequest request) {
        byte[] rawBody;
        if (request instanceof CachedBodyHttpServletRequest cached) {
            rawBody = cached.getCachedBody();
        } else {
            // Fallback defensivo — em runtime, HmacSignatureFilter (HIGHEST_PRECEDENCE)
            // sempre embrulha o request em CachedBodyHttpServletRequest. Mas se o filter
            // for desabilitado por config ou tests usarem MockMvc sem filter chain,
            // este branch garante que getInputStream ainda funciona (defesa T-02-29).
            log.warn("Request nao e CachedBodyHttpServletRequest — fallback para getInputStream");
            try {
                rawBody = request.getInputStream().readAllBytes();
            } catch (IOException e) {
                log.error("Erro lendo body do webhook (fallback)", e);
                return ResponseEntity.ok().build();
            }
        }

        try {
            mensagemService.processarWebhook(rawBody);
        } catch (IOException e) {
            log.error("Erro parseando webhook do Meta — payload sera descartado", e);
        } catch (RuntimeException e) {
            log.error("Erro processando webhook — descartando", e);
        }
        return ResponseEntity.ok().build();
    }
}
