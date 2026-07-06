package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.RegraPartida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraPartidaRepository extends JpaRepository<RegraPartida, Long> {

    List<RegraPartida> findByRegraIdOrderByOrdem(Long regraId);
}
