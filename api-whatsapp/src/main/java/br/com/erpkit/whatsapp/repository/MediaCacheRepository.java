package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.MediaCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository do {@link MediaCache}. Phase 2 NAO usa em runtime — Phase 4
 * ({@code MediaCacheService}) consome com TTL 30d.
 *
 * <p>Helper {@code findByArquivoHashAndExpiraEmAfter} declarado para Phase 4
 * ja achar a superficie pronta. Smoke test do Plan 01 valida que o repository
 * carrega no contexto Spring.
 */
public interface MediaCacheRepository extends JpaRepository<MediaCache, String> {

    Optional<MediaCache> findByArquivoHashAndExpiraEmAfter(String arquivoHash, Instant agora);
}
