package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.model.Direcao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Orquestrador sincrono do fluxo de webhook entrante (D-06).
 *
 * <p><b>Phase 2:</b> sincrono — {@code processarWebhook(byte[])} parseia,
 * persiste cada mensagem nova de forma idempotente, identifica o cliente e
 * atualiza {@code ultima_mensagem_em} em transacao separada. Statuses sao
 * parseados mas IGNORADOS (D-06: backlog/Phase 4).
 *
 * <p><b>Phase 3:</b> esta classe sera quebrada em fast-path (parse +
 * idempotency gate) sincrono + dispatch {@code @Async} (identificar cliente
 * + callback ERP).
 *
 * <p><b>Erro de parsing:</b> propaga {@link IOException} para o controller
 * decidir (D-06 da RESEARCH: controller captura e retorna 200 — defensivo,
 * alinhado com Phase 3 async).
 *
 * <p><b>Cross-bean call obrigatorio (A3 RESEARCH):</b> esta classe NAO usa
 * {@code @Transactional} explicito; cada chamada de
 * {@link IdempotencyService#tentarPersistir} ou
 * {@link ClienteZapService#atualizarUltimaMensagemEm} aciona o proxy AOP do
 * Spring para a propagacao correta (REQUIRES_NEW no segundo). Self-call dentro
 * desta classe NAO ativaria o proxy — entao a injecao via constructor de
 * {@code ClienteZapService} (outro bean) e essencial.
 */
@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);

    private final WebhookPayloadParser parser;
    private final IdempotencyService idempotency;
    private final ClienteZapService clienteZap;

    public MensagemService(WebhookPayloadParser parser,
                           IdempotencyService idempotency,
                           ClienteZapService clienteZap) {
        this.parser = parser;
        this.idempotency = idempotency;
        this.clienteZap = clienteZap;
    }

    /**
     * Processa o body bruto de um webhook do Meta (apos HMAC validado pelo Filter).
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Parse via {@link WebhookPayloadParser#extrair}</li>
     *   <li>Para cada mensagem: idempotency tentarPersistir → se nova,
     *       identifica cliente + atualiza ultima_mensagem_em (REQUIRES_NEW)</li>
     *   <li>Statuses: log debug e ignora (Phase 2 escopo — D-06)</li>
     * </ol>
     *
     * @param rawBody bytes do body (UTF-8) do webhook do Meta
     * @throws IOException se o JSON for malformado (Jackson) — caller (controller)
     *                     captura e retorna 200 (ack-first defensivo)
     */
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
                continue;
            }
            // Mensagem nova: identificar cliente (auto-create) + atualizar
            // ultima_mensagem_em em transacao separada (REQUIRES_NEW para commit
            // imediato — PITFALLS C-01).
            clienteZap.identificar(m.telefone());
            clienteZap.atualizarUltimaMensagemEm(m.telefone());
        }

        // Statuses: Phase 2 nao persiste (D-06). Apenas log para visibilidade.
        for (StatusEntranteDTO s : parsed.statuses()) {
            log.debug("Status callback ignorado em Phase 2: wamid={} status={} telefone={}",
                      s.wamid(), s.status(), s.telefone());
        }
    }
}
