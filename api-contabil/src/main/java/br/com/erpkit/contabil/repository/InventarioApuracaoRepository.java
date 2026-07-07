package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.InventarioApuracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventarioApuracaoRepository extends JpaRepository<InventarioApuracao, Long> {

    Optional<InventarioApuracao> findByPeriodoDeAndPeriodoAteAndAtivoTrue(LocalDate periodoDe, LocalDate periodoAte);

    List<InventarioApuracao> findAllByOrderByApuradoEmDesc();

    /** Apurações vigentes, em ordem de período (período anterior primeiro). */
    List<InventarioApuracao> findByAtivoTrueOrderByPeriodoAteAsc();
}
