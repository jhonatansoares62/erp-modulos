package br.com.erpkit.whatsapp.web;

import br.com.erpkit.whatsapp.security.WhatsappAuthFilter;
import br.com.erpkit.whatsapp.service.AuditoriaAcessoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Grava a trilha de auditoria (LGPD item 3) dos acessos do inbox ao dado do paciente.
 * Registrado no {@link br.com.erpkit.whatsapp.config.WebConfig} para {@code /api/whatsapp/conversas/**}.
 *
 * <p>Roda em {@code afterCompletion} e so audita respostas 2xx. Mapeia a rota -> acao:
 * <ul>
 *   <li>{@code GET .../{telefone}/mensagens} -> {@code abriu_chat} (com dedup no service)</li>
 *   <li>{@code POST .../{telefone}/assumir}   -> {@code assumiu}</li>
 *   <li>{@code POST .../{telefone}/encerrar}  -> {@code encerrou}</li>
 * </ul>
 * A LISTA de conversas ({@code GET /conversas}) e o detalhe NAO sao auditados — a lista sofre
 * polling (~5s) e inundaria a trilha; o dado sensivel de fato e o historico do chat.
 *
 * <p>O e-mail do atendente vem do atributo setado pelo {@link WhatsappAuthFilter}
 * (null quando o acesso e via X-API-Key = ERP/sistema).
 *
 * <p>O service e injetado via {@link ObjectProvider} (resolvido lazy): o {@code WebConfig}
 * carrega este interceptor mesmo em slices {@code @WebMvcTest} (que nao trazem @Service);
 * ausente o bean, {@link #afterCompletion} vira no-op.
 */
@Component
public class AuditoriaAcessoInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AuditoriaAcessoService> auditoriaProvider;

    public AuditoriaAcessoInterceptor(ObjectProvider<AuditoriaAcessoService> auditoriaProvider) {
        this.auditoriaProvider = auditoriaProvider;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }
        String acao = acaoDe(request.getMethod(), request.getRequestURI());
        if (acao == null) {
            return;
        }
        AuditoriaAcessoService auditoria = auditoriaProvider.getIfAvailable();
        if (auditoria == null) {
            return;
        }
        String telefone = telefoneDe(request);
        String email = (String) request.getAttribute(WhatsappAuthFilter.ATTR_EMAIL);
        auditoria.registrar(email, acao, telefone);
    }

    private String acaoDe(String metodo, String uri) {
        if ("GET".equals(metodo) && uri.endsWith("/mensagens")) {
            return AuditoriaAcessoService.ABRIU_CHAT;
        }
        if ("POST".equals(metodo) && uri.endsWith("/assumir")) {
            return "assumiu";
        }
        if ("POST".equals(metodo) && uri.endsWith("/encerrar")) {
            return "encerrou";
        }
        // DSAR (LGPD item 4): auditar acesso/eliminação do titular — sempre (sem dedup).
        if ("GET".equals(metodo) && uri.endsWith("/exportar")) {
            return "exportou_dados";
        }
        if ("POST".equals(metodo) && uri.endsWith("/esquecer")) {
            return "esqueceu_titular";
        }
        return null;
    }

    private String telefoneDe(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> mapa) {
            Object telefone = mapa.get("telefone");
            return telefone == null ? null : telefone.toString();
        }
        return null;
    }
}
