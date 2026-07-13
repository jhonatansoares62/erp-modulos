package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.EstadoConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstadoConversaRepository extends JpaRepository<EstadoConversa, String> {

    /** DSAR (LGPD item 4): remove o estado de conversa de um titular ao "esquecer". */
    @Modifying
    @Query("DELETE FROM EstadoConversa e WHERE e.telefone = :telefone")
    int deletarPorTelefone(@Param("telefone") String telefone);
}
