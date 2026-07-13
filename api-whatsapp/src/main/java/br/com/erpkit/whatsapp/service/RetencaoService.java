package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.RetencaoProperties;
import br.com.erpkit.whatsapp.dto.ResultadoRetencao;
import br.com.erpkit.whatsapp.repository.MediaCacheRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Motor de retenção operacional (LGPD item 4). Como o bot é operacional-only (não trafega
 * conteúdo clínico), o dado do módulo não cai na guarda de prontuário (≥20 anos, do ERP) —
 * passado o prazo ({@link RetencaoProperties#getMensagensMeses()}, default 24 meses) o
 * conteúdo/telefone das mensagens é <b>anonimizado</b> (metadados ficam → métricas seguem).
 * Também expurga o {@code media_cache} já expirado (a V3 tinha TTL, faltava o job).
 *
 * <p>Cada passo é bulk (sem carregar entity) e transacional no repositório; o
 * {@link #executar()} é best-effort por passo (um erro não impede o outro nem derruba o job).
 * Roda diário via {@link Scheduled} e também sob demanda pelo {@code RetencaoController}.
 */
@Service
public class RetencaoService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoService.class);

    private final MensagemLogRepository mensagemRepository;
    private final MediaCacheRepository mediaCacheRepository;
    private final RetencaoProperties props;

    public RetencaoService(MensagemLogRepository mensagemRepository,
                           MediaCacheRepository mediaCacheRepository,
                           RetencaoProperties props) {
        this.mensagemRepository = mensagemRepository;
        this.mediaCacheRepository = mediaCacheRepository;
        this.props = props;
    }

    /** Job diário (03:30 default; cron configurável por {@code whatsapp.retencao.cron}). */
    @Scheduled(cron = "${whatsapp.retencao.cron:0 30 3 * * *}")
    public void jobDiario() {
        if (!props.isHabilitado()) {
            log.debug("Retenção desabilitada (whatsapp.retencao.habilitado=false).");
            return;
        }
        ResultadoRetencao r = executar();
        log.info("Retenção diária: {} mensagens anonimizadas, {} mídias expiradas purgadas.",
                r.mensagensAnonimizadas(), r.midiasPurgadas());
    }

    /** Executa a retenção agora. Best-effort por passo. */
    public ResultadoRetencao executar() {
        Instant limite = ZonedDateTime.now(ZoneOffset.UTC)
                .minusMonths(props.getMensagensMeses()).toInstant();

        int anonimizadas = 0;
        try {
            anonimizadas = mensagemRepository.anonimizarAntigas(limite);
        } catch (RuntimeException e) {
            log.error("Falha ao anonimizar mensagens antigas (limite={}): {}", limite, e.getMessage());
        }

        int midias = 0;
        try {
            midias = mediaCacheRepository.purgarExpiradas(Instant.now());
        } catch (RuntimeException e) {
            log.error("Falha ao purgar mídia expirada: {}", e.getMessage());
        }

        return new ResultadoRetencao(anonimizadas, midias);
    }
}
