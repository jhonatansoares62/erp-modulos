package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.event.MensagemPersistidaEvent;
import br.com.erpkit.whatsapp.model.Direcao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Orquestrador FAST-PATH do webhook entrante (Phase 3 — refatorado de Phase 2).
 *
 * <p><b>Phase 3:</b> sincrono so para parse + idempotency + persist + publishEvent.
 * Operacoes lentas (media + identificar + atualizar + comando + callback ERP)
 * rodam em {@link MensagemAsyncListener} via {@code @Async @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p><b>{@link Transactional} CRITICO (Risk A1 do RESEARCH):</b> AFTER_COMMIT listener
 * dispara apenas se houver transacao ATIVA quando publishEvent acontece. Spring docs
 * explicito: "If no transaction is running, the listener is not invoked at all". Sem
 * {@code @Transactional} aqui, mensagem persiste mas ERP NUNCA recebe callback — bug
 * silencioso.
 *
 * <p>Trade-off: 1 transacao envolve TODO o loop. Se uma mensagem da metade do batch
 * lanca, rollback desfaz mensagens anteriores DESTE batch — mas como
 * {@link IdempotencyService#tentarPersistir} ja captura {@code DataIntegrityViolation}
 * (UNIQUE wamid), erros mid-loop sao raros na pratica.
 *
 * <p><b>Erro de parsing:</b> propaga {@link IOException} para o controller decidir
 * (D-06 da RESEARCH: controller captura e retorna 200 — defensivo, alinhado com
 * Phase 3 async).
 */
@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);

    private final WebhookPayloadParser parser;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher eventPublisher;

    public MensagemService(WebhookPayloadParser parser,
                           IdempotencyService idempotency,
                           ApplicationEventPublisher eventPublisher) {
        this.parser = parser;
        this.idempotency = idempotency;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processa o body bruto de um webhook do Meta (apos HMAC validado pelo Filter).
     *
     * <p>Fluxo Phase 3 (fast-path):
     * <ol>
     *   <li>Parse via {@link WebhookPayloadParser#extrair}</li>
     *   <li>Para cada mensagem: idempotency tentarPersistir → se nova,
     *       publica {@link MensagemPersistidaEvent} (listener async faz o resto:
     *       media → identificar → atualizar → comando → callback ERP)</li>
     *   <li>Statuses: log debug e ignora (Phase 3 escopo — D-06)</li>
     * </ol>
     *
     * @param rawBody bytes do body (UTF-8) do webhook do Meta
     * @throws IOException se o JSON for malformado (Jackson) — caller (controller)
     *                     captura e retorna 200 (ack-first defensivo)
     */
    @Transactional
    public void processarWebhook(byte[] rawBody) throws IOException {
        ParsedWebhook parsed = parser.extrair(rawBody);
        log.info("Webhook recebido: {} mensagens, {} statuses",
                 parsed.mensagens().size(), parsed.statuses().size());

        for (MensagemEntranteDTO m : parsed.mensagens()) {
            boolean novo = idempotency.tentarPersistir(
                m.wamid(), m.telefone(), Direcao.in, m.tipo(), m.conteudo(), m.mediaId()
            );
            if (!novo) {
                // Meta reenviou — ja persistido. Sem efeito colateral.
                log.debug("wamid={} duplicado — Meta reenviou, ignorando dispatch", m.wamid());
                continue;
            }
            // Mensagem nova: disparar evento — listener async fara: media -> identificar
            // -> atualizar -> comando -> callback ERP. AFTER_COMMIT garante invariant:
            // nao dispara se transacao falhar. idClienteErp = null sempre — listener
            // resolve via ClienteZapService.identificar.
            eventPublisher.publishEvent(new MensagemPersistidaEvent(
                m.wamid(), m.telefone(), m.telefoneWaId(), m.tipo(), m.conteudo(), m.mediaId(), null
            ));
        }

        // Statuses: Phase 3 nao persiste (D-06). Apenas log para visibilidade.
        for (StatusEntranteDTO s : parsed.statuses()) {
            log.debug("Status callback ignorado em Phase 3: wamid={} status={} telefone={}",
                      s.wamid(), s.status(), s.telefone());
        }
    }
}
