package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.RegraLancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraLancamentoRepository extends JpaRepository<RegraLancamento, Long> {

    /** Regras ativas de um tipo de evento, mais específicas (maior prioridade) primeiro. */
    List<RegraLancamento> findByEventoTipoAndAtivoTrueOrderByPrioridadeDesc(String eventoTipo);

    /** Todas as regras ativas, agrupadas por tipo de evento (prioridade desc dentro do tipo). */
    List<RegraLancamento> findByAtivoTrueOrderByEventoTipoAscPrioridadeDesc();
}
