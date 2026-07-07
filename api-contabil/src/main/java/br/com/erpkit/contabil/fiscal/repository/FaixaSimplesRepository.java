package br.com.erpkit.contabil.fiscal.repository;

import br.com.erpkit.contabil.fiscal.model.FaixaSimples;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaixaSimplesRepository extends JpaRepository<FaixaSimples, Long> {

    List<FaixaSimples> findByAnexoOrderByFaixaAsc(String anexo);
}
