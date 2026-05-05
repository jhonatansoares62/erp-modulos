package br.com.erpkit.whatsapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios puros (sem Spring context) do {@link HmacValidator}.
 *
 * <p>Cobertura via 13 cenarios — alinhada com tabela de edge cases do RESEARCH §3.1
 * e PITFALLS C-02/C-03/C-04:
 * <ul>
 *   <li>Caminho positivo: body valido + signature correta + secret correto</li>
 *   <li>Tampering: body modificado em 1 byte deixa a HMAC invalida</li>
 *   <li>UTF-8 portugues: "Olá, gostaria de um orçamento" valida corretamente
 *       (PITFALLS C-04 — HMAC sobre bytes brutos UTF-8, nunca String intermediario)</li>
 *   <li>Defensive: header ausente, sem prefixo, hex invalido, hex tamanho errado,
 *       secret null/blank, body null — todos retornam false sem throw</li>
 *   <li>Empty body: rejeita signature de OUTRO body (cobre o gate "skip se vazio"
 *       que e bug critico de PITFALLS C-02)</li>
 *   <li>Secret errado: same body + signature, secret diferente → false</li>
 * </ul>
 */
class HmacValidatorTest {

    private static final String SECRET = "test-app-secret-xyz";
    private HmacValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HmacValidator();
    }

    /** Helper: produz "sha256=&lt;hex&gt;" sobre body+secret usando o mesmo algoritmo do HmacValidator. */
    private String computeHeader(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }

    @Test
    @DisplayName("Body valido + signature correta + secret correto retorna true")
    void assinatura_valida_retorna_true() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String header = computeHeader(body, SECRET);
        assertThat(validator.isValid(body, header, SECRET)).isTrue();
    }

    @Test
    @DisplayName("Body modificado em 1 byte rejeita signature original (tampering detection)")
    void body_modificado_em_um_byte_retorna_false() throws Exception {
        byte[] originalBody = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String header = computeHeader(originalBody, SECRET);
        byte[] modified = originalBody.clone();
        modified[0] ^= 0x01; // flip 1 bit
        assertThat(validator.isValid(modified, header, SECRET)).isFalse();
    }

    @Test
    @DisplayName("Payload em portugues UTF-8 (Ola, gostaria de um orcamento) valida — PITFALLS C-04")
    void payload_portugues_utf8_valida_corretamente() throws Exception {
        byte[] body = "{\"text\":\"Olá, gostaria de um orçamento\"}".getBytes(StandardCharsets.UTF_8);
        String header = computeHeader(body, SECRET);
        assertThat(validator.isValid(body, header, SECRET)).isTrue();
    }

    @Test
    @DisplayName("Header null retorna false")
    void header_ausente_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        assertThat(validator.isValid(body, null, SECRET)).isFalse();
    }

    @Test
    @DisplayName("Header sem prefixo sha256= retorna false")
    void header_sem_prefixo_sha256_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        // Hex valido de 64 chars MAS sem o prefixo "sha256="
        String headerSemPrefix = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        assertThat(validator.isValid(body, headerSemPrefix, SECRET)).isFalse();
    }

    @Test
    @DisplayName("Header com hex invalido (ZZ...) retorna false sem throw")
    void header_com_hex_invalido_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        // 64 chars mas 'z' nao e hex
        String malformed = "sha256=" + "z".repeat(64);
        assertThat(validator.isValid(body, malformed, SECRET)).isFalse();
    }

    @Test
    @DisplayName("Header com tamanho de hex errado retorna false (sha256 sempre 64 chars)")
    void header_com_tamanho_errado_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        assertThat(validator.isValid(body, "sha256=ab", SECRET)).isFalse();           // 2 chars (1 byte)
        assertThat(validator.isValid(body, "sha256=" + "a".repeat(63), SECRET)).isFalse(); // impar
        assertThat(validator.isValid(body, "sha256=" + "a".repeat(66), SECRET)).isFalse(); // par mas != 64
    }

    @Test
    @DisplayName("Secret diferente do que assinou retorna false")
    void secret_diferente_retorna_false() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String headerComSecretA = computeHeader(body, "secret-A");
        assertThat(validator.isValid(body, headerComSecretA, "secret-B")).isFalse();
    }

    @Test
    @DisplayName("Body vazio com signature correta de empty array valida — NAO ha shortcut empty (PITFALLS C-02)")
    void body_vazio_valida_quando_signature_corresponde() throws Exception {
        byte[] empty = new byte[0];
        // HMAC e matematicamente definido sobre 0 bytes — o validator NUNCA deve ter
        // shortcut "se body empty, skip". Se computHeader+isValid retornarem true para
        // empty body, o validator esta correto. Se retornar false sem o "skip" antipattern,
        // estaria errado. Este test enforce o caminho matematico estrito.
        String header = computeHeader(empty, SECRET);
        assertThat(validator.isValid(empty, header, SECRET)).isTrue();
    }

    @Test
    @DisplayName("Body vazio com signature de OUTRO body retorna false")
    void body_vazio_com_signature_invalida_retorna_false() throws Exception {
        byte[] empty = new byte[0];
        // Signature gerada sobre body diferente — empty body deve rejeitar
        String headerDeOutroBody = computeHeader("x".getBytes(StandardCharsets.UTF_8), SECRET);
        assertThat(validator.isValid(empty, headerDeOutroBody, SECRET)).isFalse();
    }

    @Test
    @DisplayName("Secret null retorna false (defensive)")
    void secret_null_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + "a".repeat(64);
        assertThat(validator.isValid(body, header, null)).isFalse();
    }

    @Test
    @DisplayName("Secret blank retorna false (defensive)")
    void secret_vazio_retorna_false() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + "a".repeat(64);
        assertThat(validator.isValid(body, header, "")).isFalse();
        assertThat(validator.isValid(body, header, "   ")).isFalse();
    }

    @Test
    @DisplayName("Body null retorna false (defensive)")
    void body_null_retorna_false() {
        String header = "sha256=" + "a".repeat(64);
        assertThat(validator.isValid(null, header, SECRET)).isFalse();
    }
}
