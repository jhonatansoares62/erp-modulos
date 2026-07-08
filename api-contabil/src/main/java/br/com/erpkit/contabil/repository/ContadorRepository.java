package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Contador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContadorRepository extends JpaRepository<Contador, Long> {

    @Query("select c from Contador c where lower(c.email) = lower(:email) and c.ativo = true")
    Optional<Contador> findAtivoByEmail(@Param("email") String email);
}
