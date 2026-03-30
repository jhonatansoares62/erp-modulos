package br.com.erpkit.storage.repository;

import br.com.erpkit.storage.model.Arquivo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArquivoRepository extends JpaRepository<Arquivo, Long> {

    Optional<Arquivo> findByNomeArmazenado(String nomeArmazenado);

    Page<Arquivo> findByAtivoTrue(Pageable pageable);

    Page<Arquivo> findByOrigemAndAtivoTrue(String origem, Pageable pageable);

    Page<Arquivo> findByCategoriaAndAtivoTrue(String categoria, Pageable pageable);

    List<Arquivo> findByReferenciaIdAndAtivoTrue(String referenciaId);

    @Query("SELECT a.categoria, COUNT(a), SUM(a.tamanho) FROM Arquivo a WHERE a.ativo = true GROUP BY a.categoria")
    List<Object[]> estatisticasPorCategoria();
}
