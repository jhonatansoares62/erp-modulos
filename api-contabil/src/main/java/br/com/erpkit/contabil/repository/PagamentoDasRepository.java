package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.PagamentoDas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagamentoDasRepository extends JpaRepository<PagamentoDas, Long> {

    boolean existsByCompetencia(String competencia);

    Optional<PagamentoDas> findByCompetencia(String competencia);

    List<PagamentoDas> findAllByOrderByCompetenciaDescIdDesc();
}
