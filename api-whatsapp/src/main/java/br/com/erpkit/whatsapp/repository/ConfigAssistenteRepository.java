package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ConfigAssistente;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository do {@link ConfigAssistente}. Linha unica ({@code id = 1}); todo acesso
 * via {@code findById(AssistenteService.SINGLETON_ID)}.
 */
public interface ConfigAssistenteRepository extends JpaRepository<ConfigAssistente, Integer> {
}
