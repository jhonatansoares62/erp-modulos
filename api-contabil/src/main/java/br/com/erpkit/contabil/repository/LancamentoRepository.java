package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {
}
