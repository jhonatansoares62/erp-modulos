package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ClienteZap;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository do {@link ClienteZap}. Phase 2 Plan 01 — esqueleto apenas
 * (extends JpaRepository com CRUD basico).
 *
 * <p>Plan 04 adiciona:
 * <ul>
 *   <li>{@code Optional<ClienteZap> findByTelefone(String telefone)}</li>
 *   <li>{@code int atualizarUltimaMensagemEm(String telefone)} — native @Query com NOW()</li>
 * </ul>
 */
public interface ClienteZapRepository extends JpaRepository<ClienteZap, Long> {
}
