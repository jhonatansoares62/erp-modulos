package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Painel de monitoramento AO VIVO (dev/meta apenas) — expoe o feed de mensagens
 * (in + out) do {@code mensagens_log} para a pagina {@code /monitor.html}.
 *
 * <p>Ativo somente nos profiles {@code dev} e {@code meta} ({@link Profile}) — nao
 * entra no runtime de producao. O path {@code /monitor} e liberado no
 * {@code ApiKeyFilter} (SecurityConfig) para a pagina consumir sem X-API-Key.
 */
@RestController
@Profile({"dev", "meta"})
@RequestMapping("/monitor")
public class MonitorController {

    private final MensagemLogRepository repository;
    private final CircuitBreakerRegistry cbRegistry;
    private final WhatsAppProperties properties;

    public MonitorController(MensagemLogRepository repository,
                             CircuitBreakerRegistry cbRegistry,
                             WhatsAppProperties properties) {
        this.repository = repository;
        this.cbRegistry = cbRegistry;
        this.properties = properties;
    }

    @GetMapping("/feed")
    public MonitorFeed feed() {
        String cb = cbRegistry.find("whatsapp-cloud")
                .map(c -> c.getState().name())
                .orElse("UNKNOWN");

        List<MonitorMensagem> mensagens = repository.findTop200ByOrderByIdDesc().stream()
                .map(m -> new MonitorMensagem(
                        m.getId(),
                        m.getDirecao() == null ? null : m.getDirecao().name(),
                        m.getTelefone(),
                        m.getTipo(),
                        m.getConteudo(),
                        m.getCriadoEm() == null ? null : m.getCriadoEm().toEpochMilli()
                ))
                .toList();

        return new MonitorFeed(properties.getPhoneNumberId(), cb, repository.count(), mensagens);
    }

    public record MonitorFeed(
            String phoneNumberId,
            String circuitBreakerState,
            long total,
            List<MonitorMensagem> mensagens
    ) { }

    public record MonitorMensagem(
            Long id,
            String direcao,
            String telefone,
            String tipo,
            String conteudo,
            Long criadoEm
    ) { }
}
