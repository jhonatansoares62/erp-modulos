package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ClienteZap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository do {@link ClienteZap}. Phase 2 Plan 01 (Wave A) deixou o esqueleto;
 * Plan 04 (Wave 3) adicionou os metodos abaixo, consumidos pelo
 * {@code ClienteZapService} e (Phase 4 Wave 2) pelo {@code WindowEnforcementService}.
 *
 * <ul>
 *   <li>{@link #findByTelefone(String)} — derived query, telefone JA NORMALIZADO
 *       (caller chama {@code TelefoneBR.normalizar} antes).</li>
 *   <li>{@link #atualizarUltimaMensagemEm(String)} — native @Query usando
 *       {@code NOW()} do banco, NUNCA {@code Instant.now()} da JVM
 *       (PITFALLS C-01 — clock skew JVM-DB perto do boundary 24h vira bug).</li>
 *   <li>{@link #buscarUltimaMensagemEm(String)} — native @Query SELECT que pula
 *       o JPA L1 cache, garantindo committed read fresco. Necessario para a
 *       trava 24h da Phase 4 ({@code WindowEnforcementService}) que le de outra
 *       transacao apos o webhook escrever via REQUIRES_NEW.</li>
 * </ul>
 */
public interface ClienteZapRepository extends JpaRepository<ClienteZap, Long> {

    /**
     * Busca por telefone JA NORMALIZADO (D-03 — caller deve passar
     * {@code TelefoneBR.normalizar(telefone)}). UNIQUE constraint em
     * {@code telefone} garante 0 ou 1 row.
     */
    Optional<ClienteZap> findByTelefone(String telefone);

    /**
     * Atualiza {@code ultima_mensagem_em} usando o relogio do BANCO
     * ({@code NOW()}), NAO {@code Instant.now()} da JVM (PITFALLS C-01).
     * Native query porque JPQL nao suporta {@code NOW()} portavelmente entre
     * H2 (test) e PostgreSQL (prod).
     *
     * <p>Chamada deve estar em {@code @Transactional(REQUIRES_NEW)} —
     * ver {@link br.com.erpkit.whatsapp.service.ClienteZapService#atualizarUltimaMensagemEm(String)}.
     *
     * @return numero de linhas afetadas (0 se telefone nao existe, 1 se atualizou)
     */
    @Modifying
    @Query(value =
        "UPDATE whatsapp.clientes_zap " +
        "SET ultima_mensagem_em = NOW() " +
        "WHERE telefone = :telefone",
        nativeQuery = true)
    int atualizarUltimaMensagemEm(@Param("telefone") String telefone);

    /**
     * Le {@code ultima_mensagem_em} via native query — pula JPA L1 cache,
     * garantindo committed read fresco. Necessario para a trava 24h da Phase 4
     * (PITFALLS C-01): webhook (Phase 2 PER-07) escreve via REQUIRES_NEW + NOW();
     * Phase 4 le aqui de outra transacao (potencialmente sem transacao se
     * chamado de aspect outside Resilience4j chain).
     *
     * <p>Native query (vs JPQL) garante:
     * <ul>
     *   <li>Pula L1 cache (queries derivadas {@code findByTelefone} podem retornar
     *       o snapshot Hibernate antigo se a entity ja foi carregada na sessao).</li>
     *   <li>Retorna {@code Optional.empty()} se telefone nao existe (sem 0 row mismatch).</li>
     *   <li>Retorna {@code Optional.empty()} tambem se a coluna estiver NULL
     *       (cliente registrado mas sem mensagem entrante computada — improvavel
     *       no fluxo Phase 2 PER-07, mas defendido aqui).</li>
     * </ul>
     *
     * @param telefone JA NORMALIZADO via {@code TelefoneBR.normalizar} (caller responsavel)
     * @return Instant da ultima mensagem entrante; empty se cliente nao registrado
     *         ou se {@code ultima_mensagem_em} for NULL
     */
    @Query(value =
        "SELECT ultima_mensagem_em " +
        "FROM whatsapp.clientes_zap " +
        "WHERE telefone = :telefone",
        nativeQuery = true)
    Optional<Instant> buscarUltimaMensagemEm(@Param("telefone") String telefone);
}
