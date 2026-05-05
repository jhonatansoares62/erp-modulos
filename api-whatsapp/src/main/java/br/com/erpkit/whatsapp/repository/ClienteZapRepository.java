package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ClienteZap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository do {@link ClienteZap}. Phase 2 Plan 01 (Wave A) deixou o esqueleto;
 * Plan 04 (Wave 3) adiciona os 2 metodos abaixo, consumidos pelo
 * {@code ClienteZapService}.
 *
 * <ul>
 *   <li>{@link #findByTelefone(String)} — derived query, telefone JA NORMALIZADO
 *       (caller chama {@code TelefoneBR.normalizar} antes).</li>
 *   <li>{@link #atualizarUltimaMensagemEm(String)} — native @Query usando
 *       {@code NOW()} do banco, NUNCA {@code Instant.now()} da JVM
 *       (PITFALLS C-01 — clock skew JVM-DB perto do boundary 24h vira bug).</li>
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
}
