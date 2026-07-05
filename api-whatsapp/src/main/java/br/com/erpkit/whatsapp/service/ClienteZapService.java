package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identifica/auto-cria {@link ClienteZap} pelo telefone, e atualiza
 * {@code ultima_mensagem_em} com o relogio do banco em transacao separada
 * (PITFALLS C-01).
 *
 * <p><b>D-04 do CONTEXT.md:</b> {@link #atualizarUltimaMensagemEm(String)} usa
 * {@code @Transactional(REQUIRES_NEW)} para que o UPDATE commite imediatamente
 * apos o INSERT da mensagem entrante — eliminando TOCTOU race com a trava 24h
 * da Phase 4 que le {@code ultima_mensagem_em} fora da transacao do webhook.
 *
 * <p><b>D-07 do CONTEXT.md:</b> {@link #identificar(String)} cria
 * {@code id_cliente_erp = null} para clientes nao mapeados no ERP, preservando
 * o flow. Race em criacao concorrente tratada via try/catch
 * {@link DataIntegrityViolationException} + re-fetch (UNIQUE constraint em
 * {@code telefone} e o gate atomico portavel H2/PostgreSQL).
 *
 * <p><b>A3 RESEARCH (Spring AOP cross-bean):</b> {@code @Transactional(REQUIRES_NEW)}
 * so ativa quando o metodo e chamado a partir de OUTRO bean. Self-call
 * ({@code this.atualizarUltimaMensagemEm} dentro do proprio service) NAO ativa o
 * proxy AOP — a transacao corrente e reusada e a propagacao vira no-op.
 * No fluxo desenhado, {@code MensagemService} (Plan 06) e o caller — outro bean,
 * proxy ativa.
 */
@Service
public class ClienteZapService {

    private static final Logger log = LoggerFactory.getLogger(ClienteZapService.class);

    private final ClienteZapRepository repository;

    public ClienteZapService(ClienteZapRepository repository) {
        this.repository = repository;
    }

    /**
     * Recupera ou cria registro de cliente WhatsApp pelo telefone.
     *
     * <p>Normaliza via {@link TelefoneBR#normalizar}, busca em
     * {@code clientes_zap}, retorna se existe, ou cria com
     * {@code idClienteErp = null} (cliente nao mapeado ainda — PER-06). Race em
     * INSERT concorrente: catch {@link DataIntegrityViolationException} +
     * re-fetch — UNIQUE constraint em {@code telefone} garante consistencia.
     *
     * @param telefone numero em qualquer formato (com ou sem +, parenteses, hifen, espacos)
     * @return {@link ClienteZap} existente ou recem-criado (nunca {@code null})
     */
    @Transactional
    public ClienteZap identificar(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        return repository.findByTelefone(normalizado).orElseGet(() -> criarNovo(normalizado));
    }

    private ClienteZap criarNovo(String normalizado) {
        ClienteZap novo = new ClienteZap(normalizado, null);
        try {
            ClienteZap salvo = repository.save(novo);
            log.debug("ClienteZap auto-criado: telefone={} (id_cliente_erp=null)", normalizado);
            return salvo;
        } catch (DataIntegrityViolationException e) {
            // Race: outro thread criou o mesmo telefone concorrentemente. UNIQUE
            // constraint em telefone disparou. Re-fetch e devolve o registro existente.
            log.debug("Race em criar ClienteZap telefone={} — usando registro existente", normalizado);
            return repository.findByTelefone(normalizado)
                .orElseThrow(() -> new IllegalStateException(
                    "Race no INSERT em clientes_zap mas registro nao existe (impossivel)", e));
        }
    }

    /**
     * Atualiza {@code ultima_mensagem_em} para {@code NOW()} do banco em uma
     * transacao SEPARADA. Critico para a trava 24h da Phase 4 (PITFALLS C-01).
     *
     * <p><b>{@code REQUIRES_NEW}:</b> suspende a transacao corrente (se houver),
     * abre uma NOVA, executa o UPDATE, comita imediatamente. Apos o retorno,
     * qualquer leitor fora da transacao chamadora ja ve o valor atualizado.
     *
     * <p><b>{@code NOW()} do banco</b> (native query) e a fonte de verdade
     * temporal — clock skew JVM-DB pode ser de segundos, e perto do boundary de
     * 24h isso vira bug (envio fora da janela aceito porque JVM clock atrasou).
     *
     * @param telefone numero em qualquer formato (sera normalizado)
     * @return {@code true} se atualizou alguma linha; {@code false} se telefone
     *         nao existe em {@code clientes_zap} (nao deveria acontecer se
     *         {@link #identificar} foi chamado antes — mas {@code MensagemService}
     *         chama em ordem garantida)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean atualizarUltimaMensagemEm(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        int rows = repository.atualizarUltimaMensagemEm(normalizado);
        if (rows == 0) {
            log.warn("atualizarUltimaMensagemEm: telefone={} nao encontrado em clientes_zap", normalizado);
            return false;
        }
        return true;
    }

    /**
     * Grava o {@code id_cliente_erp} resolvido no ERP para o telefone (D-07: registros
     * nascem com id NULL; aqui vinculamos quando o ERP resolve o paciente pelo numero).
     * Normaliza o telefone antes. Idempotente — re-vincular o mesmo id nao tem efeito.
     *
     * @return {@code true} se atualizou alguma linha (telefone existe em clientes_zap)
     */
    @Transactional
    public boolean vincularClienteErp(String telefone, Long idClienteErp) {
        String normalizado = TelefoneBR.normalizar(telefone);
        int rows = repository.vincularIdClienteErp(normalizado, idClienteErp);
        if (rows > 0) {
            log.info("ClienteZap vinculado ao ERP: telefone={} id_cliente_erp={}", normalizado, idClienteErp);
        }
        return rows > 0;
    }
}
