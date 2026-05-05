package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.MensagemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
