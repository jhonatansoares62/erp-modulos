package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Partida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartidaRepository extends JpaRepository<Partida, Long> {

    List<Partida> findByLancamentoId(Long lancamentoId);

    List<Partida> findByContaId(Long contaId);
}
