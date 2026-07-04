package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Idempotencia atomica do log de mensagens (D-02 + WEB-05/WEB-06).
 *
 * <p><b>Caminho FALLBACK (Wave 1 spike falhou):</b> H2 v2.3.232 PG-mode NAO suporta
 * {@code INSERT ... ON CONFLICT (wamid) DO NOTHING} (sintaxe Postgres-native) — gate
 * empirico {@code OnConflictSpikeTest} confirmou. Implementacao usa
 * {@link MensagemLogRepository#save} envolvido em try/catch de
 * {@link DataIntegrityViolationException}. UNIQUE constraint do banco
 * ({@code mensagens_log.wamid UNIQUE NOT NULL}) e o gate atomico real — Spring
 * traduz a violacao para a excecao portavel via {@code SQLExceptionTranslator}.
 *
 * <p>Equivalencia funcional total ao caminho ON CONFLICT planejado: ambos retornam
 * {@code boolean novo} para o caller; o contrato {@link #tentarPersistir} e identico.
 * Em PostgreSQL real prod, o mesmo UNIQUE constraint dispara a mesma excecao —
 * fallback funciona em ambos os ambientes (H2 test + PG prod).
 *
 * <p><b>Contrato:</b> retorna {@code true} se a row foi inserida (mensagem nova),
 * {@code false} se {@code wamid} duplicate (Meta reenviou). Nunca lanca excecao
 * por causa de duplicate — o catch silencia.
 *
 * <p><b>Sem persistir conteudo em log debug</b> — apenas wamid + tipo + direcao.
 * Conteudo da mensagem e PII, nao deve aparecer em log nem em INFO.
 *
 * <p>Ref: D-02 (CONTEXT.md §decisions), WEB-05/WEB-06 (REQUIREMENTS.md),
 * 02-RESEARCH.md §2.4 + §6.1, 02-01-SUMMARY.md (decisao empirica do fallback).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final MensagemLogRepository repository;

    public IdempotencyService(MensagemLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Tenta persistir uma mensagem entrante de forma idempotente baseada em wamid.
     *
     * @param wamid    ID da mensagem do Meta (UNIQUE — gate de idempotencia)
     * @param telefone telefone JA NORMALIZADO (caller deve passar via
     *                 {@code TelefoneBR.normalizar})
     * @param direcao  in / out
     * @param tipo     "text", "interactive_button", "interactive_list", "document",
     *                 "image", "audio", "desconhecido", etc.
     * @param conteudo conteudo extraido do payload (pode ser null para tipos sem texto)
     * @param mediaId  ID do media no Meta (opcional, null se nao tem media)
     * @param waId     wa_id EXATO do Meta (com 9o digito) para resposta outbound;
     *                 {@code null} para saidas (telefone ja e o wa_id)
     * @return {@code true} se inseriu nova row; {@code false} se duplicate
     *         (Meta reenviou — silenciar, nao reprocessar)
     */
    public boolean tentarPersistir(String wamid, String telefone, Direcao direcao,
                                    String tipo, String conteudo, String mediaId, String waId) {
        MensagemLog mensagem = new MensagemLog(wamid, telefone, direcao, tipo, conteudo, mediaId);
        mensagem.setWaId(waId);
        try {
            repository.save(mensagem);
            log.debug("Idempotencia: wamid={} persistido (direcao={}, tipo={})", wamid, direcao, tipo);
            return true;
        } catch (DataIntegrityViolationException e) {
            // wamid duplicate — UNIQUE constraint disparou. Esperado em delivery duplicado
            // do Meta (PITFALLS C-06). Silenciar e retornar false; original preservado.
            log.debug("Idempotencia: wamid={} ja existe — Meta reenviou, silenciado", wamid);
            return false;
        }
    }
}
