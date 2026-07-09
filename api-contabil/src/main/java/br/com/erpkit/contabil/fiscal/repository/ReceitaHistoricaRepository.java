package br.com.erpkit.contabil.fiscal.repository;

import br.com.erpkit.contabil.fiscal.model.ReceitaHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceitaHistoricaRepository extends JpaRepository<ReceitaHistorica, String> {

    List<ReceitaHistorica> findAllByOrderByCompetenciaAsc();
}
