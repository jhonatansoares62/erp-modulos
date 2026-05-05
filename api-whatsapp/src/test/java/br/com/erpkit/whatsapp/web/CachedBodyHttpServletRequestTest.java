package br.com.erpkit.whatsapp.web;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios do {@link CachedBodyHttpServletRequest} usando
 * {@link MockHttpServletRequest} (do spring-test) — sem Spring context.
 *
 * <p>Foco no behavior critico: leitura multipla, charset UTF-8 hardcoded, e
 * imutabilidade do cache exposto via {@link CachedBodyHttpServletRequest#getCachedBody()}.
 */
class CachedBodyHttpServletRequestTest {

    @Test
    @DisplayName("getInputStream retorna os bytes do body original")
    void getInputStream_retorna_bytes_do_body_original() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(body);

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        ServletInputStream in = cached.getInputStream();
        byte[] read = StreamUtils.copyToByteArray(in);
        assertThat(read).isEqualTo(body);
    }

    @Test
    @DisplayName("getInputStream pode ser lido multiplas vezes (chave do cache eager — PITFALLS C-02)")
    void getInputStream_pode_ser_lido_multiplas_vezes() throws Exception {
        byte[] body = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(body);

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        for (int i = 0; i < 3; i++) {
            byte[] read = StreamUtils.copyToByteArray(cached.getInputStream());
            assertThat(read).as("leitura #%d deve retornar body completo", i + 1).isEqualTo(body);
        }
    }

    @Test
    @DisplayName("getReader retorna texto UTF-8 (PITFALLS C-04 — nao confia em getCharacterEncoding)")
    void getReader_retorna_texto_utf8() throws Exception {
        String texto = "{\"text\":\"Olá, gostaria de um orçamento\"}";
        byte[] body = texto.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(body);
        // Nao definimos charset explicito — getCharacterEncoding() retornaria null/ISO-8859-1.
        // O wrapper deve ignorar e usar UTF-8 hardcoded.

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        try (BufferedReader reader = cached.getReader()) {
            String lido = reader.lines().collect(Collectors.joining());
            assertThat(lido).isEqualTo(texto);
        }
    }

    @Test
    @DisplayName("getCachedBody retorna copia defensiva — modificacao externa nao afeta cache interno")
    void getCachedBody_retorna_copia_imutavel() throws Exception {
        byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(body);

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        byte[] copia1 = cached.getCachedBody();
        copia1[0] = (byte) 'X'; // mutacao externa

        byte[] copia2 = cached.getCachedBody();
        assertThat(copia2).isEqualTo(body); // cache interno permanece intacto
        assertThat(copia2[0]).isEqualTo((byte) 'a');
    }

    @Test
    @DisplayName("Body vazio funciona sem erro — getCachedBody retorna array de tamanho 0")
    void body_vazio_funciona() throws Exception {
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(new byte[0]);

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        assertThat(cached.getCachedBody()).hasSize(0);
        byte[] readFromStream = StreamUtils.copyToByteArray(cached.getInputStream());
        assertThat(readFromStream).hasSize(0);
    }

    @Test
    @DisplayName("Body grande (1MB) cacheia integralmente — smoke test do buffer eager")
    void body_grande_caching_funciona() throws Exception {
        byte[] body = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i % 256);
        }
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(body);

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(mock);

        assertThat(cached.getCachedBody()).hasSize(1024 * 1024);
        byte[] readFromStream = StreamUtils.copyToByteArray(cached.getInputStream());
        assertThat(readFromStream).isEqualTo(body);
    }
}
