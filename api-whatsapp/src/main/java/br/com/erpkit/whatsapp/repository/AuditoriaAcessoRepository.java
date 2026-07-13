package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.AuditoriaAcesso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaAcessoRepository extends JpaRepository<AuditoriaAcesso, Long> {

    /** Trilha em ordem cronologica reversa (mais recente primeiro) para a tela de revisao. */
    Page<AuditoriaAcesso> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
