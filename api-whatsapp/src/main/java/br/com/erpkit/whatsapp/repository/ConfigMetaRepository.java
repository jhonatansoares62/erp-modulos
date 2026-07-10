package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ConfigMeta;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository do {@link ConfigMeta}. Linha unica ({@code id = 1}); todo acesso via
 * {@code findById(MetaConfigService.SINGLETON_ID)}.
 */
public interface ConfigMetaRepository extends JpaRepository<ConfigMeta, Integer> {
}
