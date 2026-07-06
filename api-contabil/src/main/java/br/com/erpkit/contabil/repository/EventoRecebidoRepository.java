package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.EventoRecebido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoRecebidoRepository extends JpaRepository<EventoRecebido, UUID> {

    List<EventoRecebido> findByStatusOrderByRecebidoEm(String status);
}
