package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.ContaContabil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaContabilRepository extends JpaRepository<ContaContabil, Long> {

    Optional<ContaContabil> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<ContaContabil> findByAtivoTrueOrderByCodigo();

    List<ContaContabil> findByPaiId(Long paiId);
}
