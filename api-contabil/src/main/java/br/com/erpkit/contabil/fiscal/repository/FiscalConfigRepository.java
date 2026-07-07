package br.com.erpkit.contabil.fiscal.repository;

import br.com.erpkit.contabil.fiscal.model.FiscalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalConfigRepository extends JpaRepository<FiscalConfig, Integer> {
}
