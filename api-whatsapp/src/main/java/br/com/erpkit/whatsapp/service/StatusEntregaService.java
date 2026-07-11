package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Persiste o status de entrega do Meta (sent/delivered/read/failed) na mensagem de
 * SAIDA correspondente — antes descartado (D-06). V7 / observabilidade §12.
 *
 * <p><b>Por wamid (UPDATE):</b> o status refere-se a uma saida ja logada; casamos por
 * {@code wamid} (UNIQUE) e enriquecemos a row com status + conversa/pricing/erro.
 *
 * <p><b>Latest-wins com rank ({@code sent < delivered < read}; {@code failed} terminal):</b>
 * webhooks do Meta podem chegar fora de ordem — nao regredimos {@code read} para
 * {@code delivered}. {@code failed} sempre aplica (e nao e sobrescrito depois).
 *
 * <p><b>Defensivo / nao-fatal:</b> {@code REQUIRES_NEW} isola do fast-path do webhook;
 * {@code wamid} desconhecido (status de mensagem que nao enviamos, ou corrida) => skip
 * silencioso. Nunca derruba o ack 200 do webhook.
 */
@Service
public class StatusEntregaService {

    private static final Logger log = LoggerFactory.getLogger(StatusEntregaService.class);

    /** Ordem de progresso; {@code failed} tratado a parte (terminal). */
    private static final Map<String, Integer> RANK = Map.of("sent", 1, "delivered", 2, "read", 3);
    private static final String FAILED = "failed";

    private final MensagemLogRepository repository;

    public StatusEntregaService(MensagemLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(StatusEntranteDTO s) {
        if (s == null || s.wamid() == null) {
            return;
        }
        Optional<MensagemLog> alvo = repository.findByWamid(s.wamid());
        if (alvo.isEmpty()) {
            log.debug("Status p/ wamid desconhecido — skip: wamid={} status={}", s.wamid(), s.status());
            return;
        }
        MensagemLog m = alvo.get();
        if (!deveAplicar(m.getStatus(), s.status())) {
            log.debug("Status fora de ordem ignorado: wamid={} atual={} novo={}",
                    s.wamid(), m.getStatus(), s.status());
            return;
        }

        m.setStatus(s.status());
        if (s.timestamp() != null)       m.setStatusEm(s.timestamp());
        if (s.conversationId() != null)  m.setConversationId(s.conversationId());
        if (s.conversaOrigem() != null)  m.setConversaOrigem(s.conversaOrigem());
        if (s.categoria() != null)       m.setCategoria(s.categoria());
        if (s.billable() != null)        m.setBillable(s.billable());
        if (s.erroCodigo() != null)      m.setErroCodigo(String.valueOf(s.erroCodigo()));
        if (s.erroTitulo() != null)      m.setErroTitulo(s.erroTitulo());
        repository.save(m);
        log.debug("Status registrado: wamid={} status={} categoria={} billable={}",
                s.wamid(), s.status(), s.categoria(), s.billable());
    }

    /** {@code failed} sempre aplica e trava; senao, so avanca no rank (nao regride). */
    private boolean deveAplicar(String atual, String novo) {
        if (FAILED.equals(novo)) return true;
        if (FAILED.equals(atual)) return false;
        return rank(novo) >= rank(atual);
    }

    /** Rank null-safe (RANK e um Map.of, que NPE em chave null). {@code null}/desconhecido = 0. */
    private static int rank(String status) {
        return status == null ? 0 : RANK.getOrDefault(status, 0);
    }
}
