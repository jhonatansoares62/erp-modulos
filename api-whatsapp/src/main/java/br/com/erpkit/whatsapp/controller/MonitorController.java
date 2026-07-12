package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.BotaoDto;
import br.com.erpkit.whatsapp.dto.DiagnosticoResponse;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.dto.FeedRecentesResponse;
import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import br.com.erpkit.whatsapp.service.MonitorService;
import br.com.erpkit.whatsapp.service.WhatsAppCloudClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console de monitoramento + TESTE + DIAGNOSTICO (dev/meta apenas) para a pagina
 * {@code /monitor.html}. Um lugar unico para acompanhar mensagens ao vivo, enviar
 * testes e diagnosticar problemas (token Meta, circuit breaker, conexao com o ERP).
 *
 * <p>Ativo somente nos profiles {@code dev} e {@code meta} ({@link Profile}) — nao
 * entra no runtime de producao. Path {@code /monitor} liberado no {@code ApiKeyFilter}.
 *
 * <p>A logica de feed e diagnostico vive no {@link MonitorService} (sem profile), que os
 * endpoints AUTENTICADOS {@code /api/whatsapp/mensagens/recentes} e
 * {@code /api/whatsapp/diagnostico} tambem usam — para o painel de dev e a tela de Testes
 * de producao devolverem exatamente o mesmo shape sem duplicar codigo. O {@code /monitor/enviar}
 * abaixo continua sendo o atalho de teste do painel; em producao a tela usa os endpoints
 * ja existentes {@code POST /api/whatsapp/enviar-texto} e {@code /enviar-botoes}.
 */
@RestController
@Profile({"dev", "meta"})
@RequestMapping("/monitor")
public class MonitorController {

    private final MonitorService monitorService;
    private final WhatsAppCloudClient cloudClient;

    public MonitorController(MonitorService monitorService, WhatsAppCloudClient cloudClient) {
        this.monitorService = monitorService;
        this.cloudClient = cloudClient;
    }

    // ---- Feed ao vivo -----------------------------------------------------

    @GetMapping("/feed")
    public FeedRecentesResponse feed() {
        return monitorService.feed(MonitorService.LIMITE_MAXIMO_FEED);
    }

    // ---- Enviar teste (texto ou botoes) -----------------------------------

    @PostMapping("/enviar")
    public EnvioResultado enviar(@RequestBody EnviarTeste req) {
        try {
            EnvioResponse resp;
            if ("botoes".equalsIgnoreCase(req.tipo())) {
                resp = cloudClient.enviarBotoes(req.telefone(), req.texto(), req.botoes());
            } else {
                resp = cloudClient.enviarTexto(req.telefone(), req.texto());
            }
            return EnvioResultado.ok(resp.wamid());
        } catch (JanelaConversaFechadaException e) {
            return EnvioResultado.erro(e.getCodigo(), null, e.getMessage());
        } catch (MetaApiException e) {
            return EnvioResultado.erro(e.getCodigo(), e.getMetaErrorCode(), e.getMessage());
        } catch (Exception e) {
            return EnvioResultado.erro("ERRO", null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- Diagnostico ------------------------------------------------------

    @GetMapping("/diagnostico")
    public DiagnosticoResponse diagnostico() {
        return monitorService.diagnostico();
    }

    // ---- Records (apenas do envio de teste do painel dev/meta) ------------

    public record EnviarTeste(String telefone, String tipo, String texto, List<BotaoDto> botoes) { }

    public record EnvioResultado(boolean ok, String wamid, String codigo, Integer metaErrorCode, String mensagem) {
        static EnvioResultado ok(String wamid) { return new EnvioResultado(true, wamid, null, null, null); }
        static EnvioResultado erro(String codigo, Integer metaErrorCode, String mensagem) {
            return new EnvioResultado(false, null, codigo, metaErrorCode, mensagem);
        }
    }
}
