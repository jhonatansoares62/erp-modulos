package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Cache de {@code media_id} do Meta por sha256 do arquivo. Mapeia
 * {@code whatsapp.media_cache} (V3 Phase 1).
 *
 * <p>Phase 2 cria a entity + repository, mas o servico que popula
 * ({@code MediaCacheService}) e Phase 4. Phase 2 NAO faz INSERT em
 * {@code media_cache} — entity + repository ficam disponiveis para Phase 4 consumir
 * coesivamente.
 *
 * <p>{@code arquivoHash} e {@code VARCHAR(64)} (sha256 hex digest, sempre 64 chars)
 * e e a propria PK — sem auto-increment. Era CHAR(64), mas Hibernate validate
 * mapeia String como VARCHAR e o CHAR (bpchar no Postgres) quebrava o boot; sem
 * {@code columnDefinition} o tipo casa com a migration V3 em H2 e Postgres.
 */
@Entity
@Table(schema = "whatsapp", name = "media_cache")
public class MediaCache {

    @Id
    @Column(name = "arquivo_hash", length = 64)
    private String arquivoHash;

    @Column(name = "media_id", nullable = false, length = 255)
    private String mediaId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    public MediaCache() {
        // JPA exige construtor padrao
    }

    public MediaCache(String arquivoHash, String mediaId, Instant expiraEm) {
        this.arquivoHash = arquivoHash;
        this.mediaId = mediaId;
        this.expiraEm = expiraEm;
    }

    public String getArquivoHash() { return arquivoHash; }
    public void setArquivoHash(String arquivoHash) { this.arquivoHash = arquivoHash; }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter

    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant expiraEm) { this.expiraEm = expiraEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaCache that)) return false;
        return Objects.equals(arquivoHash, that.arquivoHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arquivoHash);
    }

    @Override
    public String toString() {
        return "MediaCache{arquivoHash=" + arquivoHash
             + ", mediaId=" + mediaId
             + ", expiraEm=" + expiraEm + "}";
    }
}
