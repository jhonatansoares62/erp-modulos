package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository do {@link MensagemLog}. Phase 2 Plan 01 — esqueleto apenas com
 * findByWamid + findByTelefoneOrderByCriadoEmDesc (helpers para tests + futuras
 * consultas).
 *
 * <p>Plan 03 adiciona o gate atomico de idempotencia. Spike Wave 1
 * ({@code OnConflictSpikeTest}) provou empiricamente que H2 v2.3.232 NAO suporta
 * {@code ON CONFLICT}; portanto Plan 03 implementa o fallback documentado em
 * RESEARCH §2.4: {@code save()} envolvido em try/catch de
 * {@link org.springframework.dao.DataIntegrityViolationException}, com a UNIQUE
 * constraint do banco como gate atomico real.
 */
public interface MensagemLogRepository extends JpaRepository<MensagemLog, Long> {

    /** Helper para tests + futuras consultas (Phase 4 historico). */
    Optional<MensagemLog> findByWamid(String wamid);

    /** Helper para tests + listagem cronologica futura. */
    Page<MensagemLog> findByTelefoneOrderByCriadoEmDesc(String telefone, Pageable pageable);

    /** Ultimas mensagens (in + out) para o painel de monitoramento (dev/meta). */
    List<MensagemLog> findTop200ByOrderByIdDesc();

    // ── Inbox/Chat do atendente (leitura). ──

    /**
     * Historico de um telefone em ordem CRONOLOGICA (ASC) — natural para renderizar
     * um chat de cima para baixo. Paginado.
     */
    Page<MensagemLog> findByTelefoneOrderByCriadoEmAsc(String telefone, Pageable pageable);

    /**
     * Ultima mensagem (qualquer direcao) de um telefone — usada para o preview,
     * direcao e tipo do card de conversa na lista de Inbox.
     */
    Optional<MensagemLog> findFirstByTelefoneOrderByCriadoEmDesc(String telefone);

    /** Total de mensagens (in + out) de um telefone. */
    long countByTelefone(String telefone);

    /** Existe ao menos uma mensagem para o telefone? (decide 404 no detalhe). */
    boolean existsByTelefone(String telefone);

    /**
     * Pagina de conversas (uma linha por telefone) ordenada por ULTIMA ATIVIDADE
     * (max criado_em de qualquer direcao) DESC. Retorna {@code Object[]{telefone,
     * ultimaAtividade, total}}. A projecao evita carregar todas as mensagens; o
     * preview/direcao da ultima mensagem e resolvido depois por telefone da pagina
     * ({@link #findFirstByTelefoneOrderByCriadoEmDesc}) — N limitado ao tamanho da pagina.
     *
     * <p>{@code countQuery} conta telefones DISTINTOS (nao linhas de mensagem), para
     * o total de paginas bater com a granularidade "por conversa".
     */
    @Query(value =
        "SELECT m.telefone, MAX(m.criadoEm), COUNT(m) FROM MensagemLog m "
      + "GROUP BY m.telefone ORDER BY MAX(m.criadoEm) DESC",
        countQuery =
        "SELECT COUNT(DISTINCT m.telefone) FROM MensagemLog m")
    Page<Object[]> listarConversas(Pageable pageable);

    // ── Agregacoes de relatorio (V7 §12). GROUP BY em janela [de, ate] (criado_em). ──
    // Retornam List<Object[]>{chave, count} no padrao de api-email (contarPorStatus).

    @Query("SELECT m.direcao, COUNT(m) FROM MensagemLog m "
         + "WHERE m.criadoEm BETWEEN :de AND :ate GROUP BY m.direcao")
    List<Object[]> contarPorDirecao(@Param("de") Instant de, @Param("ate") Instant ate);

    @Query("SELECT m.tipo, COUNT(m) FROM MensagemLog m "
         + "WHERE m.criadoEm BETWEEN :de AND :ate GROUP BY m.tipo")
    List<Object[]> contarPorTipo(@Param("de") Instant de, @Param("ate") Instant ate);

    @Query("SELECT m.status, COUNT(m) FROM MensagemLog m "
         + "WHERE m.direcao = :direcao AND m.criadoEm BETWEEN :de AND :ate GROUP BY m.status")
    List<Object[]> contarPorStatus(@Param("direcao") Direcao direcao,
                                   @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("SELECT m.categoria, COUNT(m) FROM MensagemLog m "
         + "WHERE m.direcao = :direcao AND m.criadoEm BETWEEN :de AND :ate GROUP BY m.categoria")
    List<Object[]> contarPorCategoria(@Param("direcao") Direcao direcao,
                                      @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("SELECT COUNT(m) FROM MensagemLog m WHERE m.direcao = :direcao "
         + "AND m.billable = TRUE AND m.criadoEm BETWEEN :de AND :ate")
    long contarFaturaveis(@Param("direcao") Direcao direcao,
                          @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("SELECT m.resultado, COUNT(m) FROM MensagemLog m "
         + "WHERE m.direcao = :direcao AND m.criadoEm BETWEEN :de AND :ate GROUP BY m.resultado")
    List<Object[]> contarPorResultado(@Param("direcao") Direcao direcao,
                                      @Param("de") Instant de, @Param("ate") Instant ate);

    // ── Retenção (LGPD item 4): anonimiza o conteúdo/telefone das mensagens antigas,
    //    preservando metadados (direcao/tipo/criado_em/status) — as métricas não usam
    //    conteudo/telefone. Idempotente (pula as já anonimizadas). Bulk, sem carregar entity.
    @Modifying
    @Transactional
    @Query("UPDATE MensagemLog m SET m.conteudo = NULL, m.waId = NULL, m.telefone = 'ANONIMIZADO' "
         + "WHERE m.criadoEm < :limite AND m.telefone <> 'ANONIMIZADO'")
    int anonimizarAntigas(@Param("limite") Instant limite);

    // ── DSAR (LGPD item 4): por titular (telefone JÁ NORMALIZADO pelo caller). ──

    /** Histórico completo de um telefone (cronológico) para EXPORTAR ao titular. */
    List<MensagemLog> findByTelefoneOrderByCriadoEmAsc(String telefone);

    /** ESQUECER: anonimiza todas as mensagens de um titular (conteúdo/telefone). */
    @Modifying
    @Query("UPDATE MensagemLog m SET m.conteudo = NULL, m.waId = NULL, m.telefone = 'ANONIMIZADO' "
         + "WHERE m.telefone = :telefone")
    int anonimizarPorTelefone(@Param("telefone") String telefone);
}
