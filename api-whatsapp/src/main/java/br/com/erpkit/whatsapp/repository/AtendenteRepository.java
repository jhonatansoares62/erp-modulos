package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.Atendente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AtendenteRepository extends JpaRepository<Atendente, Long> {

    @Query("select a from Atendente a where lower(a.email) = lower(:email) and a.ativo = true")
    Optional<Atendente> findAtivoByEmail(@Param("email") String email);
}
