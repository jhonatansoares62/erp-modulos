package br.com.erpkit.email.repository;

import br.com.erpkit.email.model.ContaEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaEmailRepository extends JpaRepository<ContaEmail, Long> {

    List<ContaEmail> findByAtivoTrue();

    Optional<ContaEmail> findByPadraoTrueAndAtivoTrue();
}
