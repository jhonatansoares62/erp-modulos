package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoints publicos do webhook do Meta (validados por HMAC, nao por API key).
 *
 * <ul>
 *   <li>GET {@code /webhook/whatsapp} — handshake do Meta. Ecoa {@code hub.challenge}
 *       como {@code text/plain} (PITFALLS C-10). {@code verifyToken} comparado
 *       via {@link MessageDigest#isEqual(byte[], byte[])} em UTF-8 (consistencia
 *       com HMAC, custo zero).</li>
 *   <li>POST {@code /webhook/whatsapp} — stub minimo (D-04 do CONTEXT.md). HMAC
 *       ja foi validado pelo {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter}
 *       (HIGHEST_PRECEDENCE). Retorna 200 imediato, sem parsing, sem log de body
 *       (Phase 2 substitui por parser + idempotency + dispatch async).</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties properties;

    public WebhookController(WhatsAppProperties properties) {
        this.properties = properties;
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
     * POST do Meta. HMAC ja validado pelo {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter}
     * (ordem {@code HIGHEST_PRECEDENCE}). Phase 1 = stub minimo.
     *
     * <p>Phase 2 substitui por: parse → idempotency check (wamid) → return 200
     * → {@code @Async} dispatch para callback do ERP.
     *
     * <p>NUNCA logar body — phone numbers, message content, PII.
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receber() {
        log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)");
        return ResponseEntity.ok().build();
    }
}
