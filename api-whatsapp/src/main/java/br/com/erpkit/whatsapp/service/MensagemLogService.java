package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Enriquecimento da linha de ENTRADA em {@code mensagens_log} com dados que so
 * ficam disponiveis no fluxo async (V7 / observabilidade §12): o paciente do ERP
 * ({@code id_cliente_erp}, resolvido tarde), a intencao extraida ({@code comando}) e
 * o timestamp real do evento na Meta ({@code evento_em}) — antes descartados.
 *
 * <p><b>Por wamid (UPDATE):</b> a row ja foi inserida no fast-path; aqui so
 * completamos. {@code REQUIRES_NEW} isola do {@link MensagemAsyncListener} (que roda
 * sem transacao, {@code NOT_SUPPORTED}). Campos {@code null} NAO sobrescrevem o que
 * ja houver (COALESCE manual). {@code wamid} inexistente = no-op.
 */
@Service
public class MensagemLogService {

    private static final Logger log = LoggerFactory.getLogger(MensagemLogService.class);

    private final MensagemLogRepository repository;

    public MensagemLogService(MensagemLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enriquecerEntrada(String wamid, Long idClienteErp, String comando, Instant eventoEm) {
        if (wamid == null) {
            return;
        }
        repository.findByWamid(wamid).ifPresentOrElse(m -> {
            if (idClienteErp != null) m.setIdClienteErp(idClienteErp);
            if (comando != null)      m.setComando(comando);
            if (eventoEm != null)     m.setEventoEm(eventoEm);
            repository.save(m);
            log.debug("Entrada enriquecida: wamid={} idClienteErp={} comando={}", wamid, idClienteErp, comando);
        }, () -> log.debug("Enriquecimento p/ wamid desconhecido — skip: {}", wamid));
    }
}
