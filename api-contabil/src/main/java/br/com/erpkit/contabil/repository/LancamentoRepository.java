package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByOrigemDocumentoAndStatus(String origemDocumento, String status);
}
