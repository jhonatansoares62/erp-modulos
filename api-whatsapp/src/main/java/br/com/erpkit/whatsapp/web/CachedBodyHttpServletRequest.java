package br.com.erpkit.whatsapp.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequestWrapper que captura os bytes brutos do body no construtor (EAGER),
 * permitindo leituras subsequentes (Filter para HMAC + Controller para parsing futuro).
 *
 * <p><b>NAO use o lazy wrapper {@code o.s.web.util.ContentCachingRequest...} do Spring</b> — o cache
 * fica vazio antes do downstream consumir o stream (PITFALLS C-02). Validacao HMAC
 * sobre body vazio e bug de seguranca P0.
 *
 * <p>{@link #getReader()} usa UTF-8 hardcoded, NAO {@code request.getCharacterEncoding()}
 * que pode retornar ISO-8859-1 (default Servlet spec) e quebra HMAC com texto portugues
 * acentuado (PITFALLS C-04). Notar: HMAC em si nao usa {@code getReader()} — usa
 * {@link #getCachedBody()} direto sobre os bytes brutos.
 *
 * <p>Multipart NAO suportado: este wrapper consome o body inteiro no construtor, o que
 * quebra o parsing de {@code multipart/form-data}. Webhook do Meta sempre chega como
 * {@code application/json}, entao isto e nao-problema para Phase 1.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // Eager read — bytes brutos preservados para HMAC sobre UTF-8 (PITFALLS C-02 + C-04)
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * Acesso direto aos bytes para HMAC computation (NAO converter para String).
     *
     * <p>Retorna copia defensiva — callers nao podem modificar o cache interno
     * acidentalmente. Custo extra de uma alocacao + arraycopy por chamada e
     * irrelevante face ao tamanho tipico de webhook do Meta (&lt;10KB).
     */
    public byte[] getCachedBody() {
        return cachedBody.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        // Cada chamada cria um novo stream — leituras multiplas funcionam.
        // DelegatingServletInputStream so existe em spring-test (org.springframework.mock.web),
        // entao para producao usamos ServletInputStream anonima sobre ByteArrayInputStream.
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public int read() {
                return byteStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("ReadListener nao suportado");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        // Sempre UTF-8 — webhook do Meta e UTF-8 garantido, nao confiar em getCharacterEncoding()
        // (Servlet spec default e ISO-8859-1 — quebra texto portugues acentuado, PITFALLS C-04)
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }
}
