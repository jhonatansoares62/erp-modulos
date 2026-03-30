package br.com.erpkit.email.repository;

import br.com.erpkit.email.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {

    Page<Email> findByStatus(String status, Pageable pageable);

    Page<Email> findByOrigem(String origem, Pageable pageable);

    @Query("SELECT e FROM Email e WHERE e.status = 'pendente' AND (e.agendadoPara IS NULL OR e.agendadoPara <= :agora) AND e.tentativas < :maxTentativas ORDER BY e.criadoEm ASC")
    List<Email> buscarPendentesParaEnvio(@Param("agora") LocalDateTime agora, @Param("maxTentativas") int maxTentativas);

    @Query("SELECT e.status, COUNT(e) FROM Email e GROUP BY e.status")
    List<Object[]> contarPorStatus();
}
