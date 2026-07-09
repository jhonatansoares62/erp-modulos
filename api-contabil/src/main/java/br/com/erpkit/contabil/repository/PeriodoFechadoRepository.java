package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.PeriodoFechado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodoFechadoRepository extends JpaRepository<PeriodoFechado, Long> {

    /** Último período mensal fechado (maior competência 'YYYY-MM'). */
    Optional<PeriodoFechado> findFirstByTipoOrderByCompetenciaDesc(String tipo);

    boolean existsByCompetenciaAndTipo(String competencia, String tipo);

    /** Registro de fechamento por competência e tipo (ex.: exercício de um ano), para reabrir. */
    Optional<PeriodoFechado> findByCompetenciaAndTipo(String competencia, String tipo);

    List<PeriodoFechado> findAllByOrderByCompetenciaDesc();
}
