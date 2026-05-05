package br.com.erpkit.whatsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida HMAC-SHA256 do header X-Hub-Signature-256 contra os bytes brutos do body
 * usando o appSecret da WhatsAppProperties.
 *
 * <p>Pure function ({@code @Service} sem dependencia de Servlet API) para facilitar
 * unit-test sem subir contexto Spring. NUNCA lanca excecao por input malformado —
 * sempre retorna boolean (PITFALLS C-02 — empty-array shortcut e bug critico).
 *
 * <p>Comparacao timing-safe via {@link MessageDigest#isEqual(byte[], byte[])}
 * (PITFALLS C-03). NUNCA usar {@link java.util.Arrays#equals(byte[], byte[])} nem
 * {@link String#equals(Object)} — ambos fazem short-circuit e abrem timing oracle.
 *
 * <p>HMAC e computado sobre os bytes brutos do body (PITFALLS C-04). NUNCA passar
 * por {@code new String(body)} — webhook do Meta e UTF-8 e {@code getCharacterEncoding()}
 * pode retornar ISO-8859-1 quebrando assinatura para texto portugues acentuado.
 *
 * <p>Logs nivel WARN/ERROR em falhas, sem incluir o valor do header nem do body
 * (potencial PII / dados de attacker).
 */
@Service
public class HmacValidator {

    private static final Logger log = LoggerFactory.getLogger(HmacValidator.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int EXPECTED_HEX_LENGTH = 64; // SHA-256 = 32 bytes = 64 hex chars

    /**
     * Valida assinatura HMAC-SHA256 contra o body bruto.
     *
     * @param rawBody         bytes brutos do body (NAO converter para String — PITFALLS C-04)
     * @param signatureHeader valor de "X-Hub-Signature-256" (formato esperado: "sha256=&lt;hex&gt;")
     * @param appSecret       App Secret do Meta (de WhatsAppProperties.appSecret)
     * @return true sse HMAC computado bate com signature decodificada; false em qualquer outro caso
     */
    public boolean isValid(byte[] rawBody, String signatureHeader, String appSecret) {
        // Guards de input — todos retornam false (NAO short-circuit "skip se vazio")
        if (rawBody == null) return false;
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        if (appSecret == null || appSecret.isBlank()) return false;

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("HMAC header sem prefixo sha256=");
            return false;
        }

        String hex = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();
        if (hex.length() != EXPECTED_HEX_LENGTH) {
            log.warn("HMAC header com tamanho hex inesperado: {}", hex.length());
            return false;
        }

        byte[] received;
        try {
            received = hexDecode(hex);
        } catch (IllegalArgumentException ex) {
            log.warn("HMAC header com hex invalido");
            return false;
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            expected = mac.doFinal(rawBody);
        } catch (Exception ex) {
            log.error("Erro computando HMAC", ex);
            return false;
        }

        // CONSTANT-TIME comparison — comparacao via short-circuit (Arrays#equals, String#equals)
        // abre timing oracle e permite brute-force do appSecret (PITFALLS C-03)
        return MessageDigest.isEqual(expected, received);
    }

    /**
     * Hex decode estrito — caracteres invalidos lancam {@link IllegalArgumentException}
     * que e capturada externamente e convertida em {@code false}.
     */
    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("hex length impar");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("char hex invalido");
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
