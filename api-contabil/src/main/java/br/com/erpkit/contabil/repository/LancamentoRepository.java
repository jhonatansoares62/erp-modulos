package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByOrigemDocumentoAndStatus(String origemDocumento, String status);

    /** Lançamentos de um tipo/status no intervalo de competência (ex.: encerramentos de um ano, para reabrir). */
    List<Lancamento> findByTipoAndStatusAndDataCompetenciaBetween(String tipo, String status,
                                                                  LocalDate de, LocalDate ate);
}
