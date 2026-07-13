package br.com.erpkit.whatsapp.web;

import br.com.erpkit.whatsapp.security.WhatsappAuthFilter;
import br.com.erpkit.whatsapp.service.AuditoriaAcessoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do mapeamento rota -> acao do interceptor de auditoria (LGPD item 3).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaAcessoInterceptorTest {

    @Mock AuditoriaAcessoService auditoria;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    /** ObjectProvider fixo (o interceptor resolve o service lazy). */
    private AuditoriaAcessoInterceptor interceptor() {
        ObjectProvider<AuditoriaAcessoService> provider = new ObjectProvider<>() {
            @Override public AuditoriaAcessoService getObject() { return auditoria; }
            @Override public AuditoriaAcessoService getObject(Object... args) { return auditoria; }
        };
        return new AuditoriaAcessoInterceptor(provider);
    }

    private void prepararRequest(String metodo, String uri, String telefone, String email, int status) {
        lenient().when(request.getMethod()).thenReturn(metodo);
        lenient().when(request.getRequestURI()).thenReturn(uri);
        lenient().when(response.getStatus()).thenReturn(status);
        lenient().when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(telefone == null ? null : Map.of("telefone", telefone));
        lenient().when(request.getAttribute(WhatsappAuthFilter.ATTR_EMAIL)).thenReturn(email);
    }

    @Test
    @DisplayName("GET .../{telefone}/mensagens (200) -> registra abriu_chat com o telefone e email")
    void audita_abrir_chat() {
        prepararRequest("GET", "/api/whatsapp/conversas/5546920009012/mensagens", "5546920009012", "ana@clinica", 200);

        interceptor().afterCompletion(request, response, new Object(), null);

        verify(auditoria).registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");
    }

    @Test
    @DisplayName("POST .../{telefone}/assumir -> registra assumiu")
    void audita_assumir() {
        prepararRequest("POST", "/api/whatsapp/conversas/5546920009012/assumir", "5546920009012", "ana@clinica", 200);

        interceptor().afterCompletion(request, response, new Object(), null);

        verify(auditoria).registrar("ana@clinica", "assumiu", "5546920009012");
    }

    @Test
    @DisplayName("GET /conversas (lista, polling) NAO e auditada")
    void nao_audita_lista() {
        prepararRequest("GET", "/api/whatsapp/conversas", null, "ana@clinica", 200);

        interceptor().afterCompletion(request, response, new Object(), null);

        verify(auditoria, never()).registrar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("resposta nao-2xx (ex.: 500) NAO e auditada")
    void nao_audita_erro() {
        prepararRequest("GET", "/api/whatsapp/conversas/5546920009012/mensagens", "5546920009012", "ana@clinica", 500);

        interceptor().afterCompletion(request, response, new Object(), null);

        verify(auditoria, never()).registrar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
