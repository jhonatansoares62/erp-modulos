package br.com.erpkit.backup.repository;

import br.com.erpkit.backup.model.BackupHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupHistoricoRepository extends JpaRepository<BackupHistorico, Long> {

    List<BackupHistorico> findTop100ByOrderByCreatedAtDesc();

    Optional<BackupHistorico> findFirstByOrderByCreatedAtDesc();

    Optional<BackupHistorico> findFirstByStatusOrderByCreatedAtDesc(String status);

    Optional<BackupHistorico> findFirstByBancoAndStatusOrderByCreatedAtDesc(String banco, String status);
}
