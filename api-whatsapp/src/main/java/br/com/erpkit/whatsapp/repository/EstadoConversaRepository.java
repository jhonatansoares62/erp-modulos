package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.EstadoConversa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoConversaRepository extends JpaRepository<EstadoConversa, String> {
}
