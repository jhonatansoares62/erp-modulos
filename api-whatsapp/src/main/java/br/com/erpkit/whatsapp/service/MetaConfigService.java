package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.model.ConfigMeta;
import br.com.erpkit.whatsapp.repository.ConfigMetaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Fonte de verdade das credenciais Meta em runtime. Faz a ponte entre a linha
 * persistida {@code whatsapp.config_meta} (V6) e o singleton
 * {@link WhatsAppProperties} que todos os consumidores leem por request.
 *
 * <ul>
 *   <li><b>Boot</b> ({@link #carregarDoBanco()}): se a linha existe, sobrepoe cada
 *       campo NAO-vazio no {@link WhatsAppProperties}. Assim o banco vence a env var
 *       quando presente; env var permanece como seed quando o banco esta vazio.</li>
 *   <li><b>PUT</b> ({@link #atualizar(MetaConfigRequest)}): upsert da linha (id=1) com
 *       semantica de atualizacao parcial (campo em branco = mantem) e reaplica no
 *       bean vivo — sem restart.</li>
 *   <li><b>GET</b> ({@link #atual()}): projeta a config EFETIVA com secrets mascarados.</li>
 * </ul>
 *
 * <p>Todos os consumidores ({@code WhatsAppCloudClient}, {@code HmacSignatureFilter},
 * {@code WebhookController}) leem {@code properties.getX()} por request, entao a
 * sobreposicao aqui passa a valer imediatamente para novas mensagens/webhooks.
 */
@Service
public class MetaConfigService {

    static final int SINGLETON_ID = 1;

    private static final Logger log = LoggerFactory.getLogger(MetaConfigService.class);

    private final ConfigMetaRepository repository;
    private final WhatsAppProperties properties;

    public MetaConfigService(ConfigMetaRepository repository, WhatsAppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * No boot: sobrepoe a config persistida sobre o seed de env var. Best-effort —
     * qualquer falha (banco indisponivel no boot) e logada como WARN e o modulo
     * segue com o que veio da env var (nunca derruba o boot por causa disso).
     */
    @PostConstruct
    void carregarDoBanco() {
        try {
            repository.findById(SINGLETON_ID).ifPresentOrElse(
                    c -> {
                        aplicarNoBean(c);
                        log.info("Config Meta carregada do banco (config_meta): configurado={}",
                                properties.isMetaConfigurado());
                    },
                    () -> log.info("Sem linha em config_meta — usando credenciais de env var (seed). configurado={}",
                            properties.isMetaConfigurado()));
        } catch (RuntimeException e) {
            log.warn("Falha ao carregar config Meta do banco no boot — mantendo seed de env var: {}", e.getMessage());
        }
    }

    /**
     * Grava as credenciais (upsert id=1, atualizacao parcial) e reaplica no bean vivo.
     *
     * @return a config efetiva resultante (secrets mascarados)
     */
    @Transactional
    public MetaConfigResponse atualizar(MetaConfigRequest req) {
        ConfigMeta c = repository.findById(SINGLETON_ID).orElseGet(() -> {
            ConfigMeta novo = new ConfigMeta();
            novo.setId(SINGLETON_ID);
            return novo;
        });

        // Atualizacao parcial: campo em branco MANTEM o valor atual (nao apaga secret).
        if (naoVazio(req.phoneNumberId())) c.setPhoneNumberId(req.phoneNumberId().trim());
        if (naoVazio(req.accessToken()))  c.setAccessToken(req.accessToken().trim());
        if (naoVazio(req.appSecret()))    c.setAppSecret(req.appSecret().trim());
        if (naoVazio(req.verifyToken()))  c.setVerifyToken(req.verifyToken().trim());
        c.setAtualizadoEm(Instant.now());

        repository.save(c);
        aplicarNoBean(c);
        log.info("Config Meta atualizada via API: configurado={}", properties.isMetaConfigurado());
        return montarResposta(c.getAtualizadoEm());
    }

    /** Projeta a config EFETIVA (bean vivo) com secrets mascarados. */
    @Transactional(readOnly = true)
    public MetaConfigResponse atual() {
        Instant atualizadoEm = repository.findById(SINGLETON_ID)
                .map(ConfigMeta::getAtualizadoEm)
                .orElse(null);
        return montarResposta(atualizadoEm);
    }

    private MetaConfigResponse montarResposta(Instant atualizadoEm) {
        return new MetaConfigResponse(
                properties.getPhoneNumberId(),
                naoVazio(properties.getAccessToken()),
                naoVazio(properties.getAppSecret()),
                naoVazio(properties.getVerifyToken()),
                properties.isMetaConfigurado(),
                atualizadoEm == null ? null : atualizadoEm.toString());
    }

    private void aplicarNoBean(ConfigMeta c) {
        if (naoVazio(c.getPhoneNumberId())) properties.setPhoneNumberId(c.getPhoneNumberId());
        if (naoVazio(c.getAccessToken()))   properties.setAccessToken(c.getAccessToken());
        if (naoVazio(c.getAppSecret()))     properties.setAppSecret(c.getAppSecret());
        if (naoVazio(c.getVerifyToken()))   properties.setVerifyToken(c.getVerifyToken());
    }

    private static boolean naoVazio(String v) {
        return v != null && !v.isBlank();
    }
}
