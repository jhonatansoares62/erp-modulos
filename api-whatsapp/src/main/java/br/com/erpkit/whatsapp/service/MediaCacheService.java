package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.MediaCache;
import br.com.erpkit.whatsapp.repository.MediaCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Cache de {@code media_id} do Meta por sha256 do conteudo binario (OUT-08 + D-04
 * CONTEXT.md + PITFALLS C-07).
 *
 * <p><b>TTL ESTRITO 30 dias (D-04):</b> hit NAO estende {@code expira_em} (sem
 * sliding). Reupload natural quando entrada expira renova TTL para
 * {@code now + 30d}. Tabela {@code media_cache} bounded — turnover de 30 em 30
 * dias garantido. Meta documenta {@code media_id} valido por ate 30 dias —
 * sliding mascara expiracao real, levando a 4xx Meta surpresa.
 *
 * <p><b>Race protection:</b> {@code registrarUpload} envolve {@code repository.save}
 * em try/catch {@link DataIntegrityViolationException} — UNIQUE PK
 * {@code arquivo_hash} e o gate atomico portavel H2/PostgreSQL. Pattern Phase 2
 * (IdempotencyService) — concurrent upload do mesmo arquivo de 2 threads:
 * uma vence (INSERT), outra catch + log.debug (silenciada). Proxima leitura ve
 * registro existente.
 *
 * <p><b>sha256 hex:</b> {@code MessageDigest} + {@code HexFormat.of().formatHex}
 * (Java 17+ standard). PK {@code arquivo_hash CHAR(64)} aceita exatamente 64
 * chars hex.
 */
@Service
public class MediaCacheService {

    private static final Logger log = LoggerFactory.getLogger(MediaCacheService.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final MediaCacheRepository repository;

    public MediaCacheService(MediaCacheRepository repository) {
        this.repository = repository;
    }

    /**
     * Busca {@code media_id} cacheado por sha256(bytes). Hit somente se
     * {@code expira_em > now()} — entrada expirada retorna empty (TTL estrito).
     *
     * @param bytes conteudo binario do arquivo (PDF/imagem/etc.)
     * @return media_id valido se hit dentro do TTL; empty se miss ou expirado
     */
    public Optional<String> buscarMediaId(byte[] bytes) {
        String hash = sha256Hex(bytes);
        return repository.findByArquivoHashAndExpiraEmAfter(hash, Instant.now())
                .map(MediaCache::getMediaId);
    }

    /**
     * Registra novo upload no cache com {@code expira_em = now + 30d}. Race em
     * concurrent reupload do mesmo arquivo silenciado — UNIQUE PK
     * {@code arquivo_hash} e o gate (Phase 2 pattern).
     *
     * <p>Se ja existe entrada (mesmo expirada) com o mesmo hash, faz UPSERT:
     * delete + save. Garante que a entrada apos chamada esta com
     * {@code expira_em} renovado.
     *
     * @param bytes   conteudo binario (mesmo passado a {@code uploadMedia} no Cloud API)
     * @param mediaId media_id retornado pelo Meta
     */
    public void registrarUpload(byte[] bytes, String mediaId) {
        String hash = sha256Hex(bytes);
        Instant expira = Instant.now().plus(TTL);
        try {
            // Upsert simples: deleta antigo (se houver) + save novo. Atomico do ponto
            // de vista do thread; race com outro thread silenciada via catch abaixo.
            repository.findById(hash).ifPresent(repository::delete);
            repository.save(new MediaCache(hash, mediaId, expira));
            log.debug("MediaCache registrado: hash={} mediaId={} expira={}", hash, mediaId, expira);
        } catch (DataIntegrityViolationException e) {
            // Race: outro thread fez upload do mesmo arquivo concorrentemente. PK
            // arquivo_hash disparou. Silenciar — proxima leitura ve registro existente.
            // Pattern Phase 2 (IdempotencyService.tentarPersistir).
            log.debug("MediaCache race em registrarUpload: hash={} — outro thread ja registrou", hash);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e built-in da JDK desde Java 1.4 — nunca acontece em prod
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
