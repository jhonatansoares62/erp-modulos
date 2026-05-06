package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests do {@link MediaCacheService} — cobre OUT-08 + D-04 (TTL estrito 30d sem
 * sliding) + Pattern Phase 2 save+catch DataIntegrityViolationException (race em
 * concurrent reupload).
 *
 * <p>Cenarios:
 * <ol>
 *   <li>hit dentro do TTL: registrarUpload + buscarMediaId retorna media_id</li>
 *   <li>miss quando hash inexistente: buscarMediaId retorna empty</li>
 *   <li>miss quando entrada expirada (expira_em &lt; now via JdbcTemplate force):
 *       TTL estrito — empty mesmo existindo row no DB</li>
 *   <li>race em registrarUpload concorrente (2 threads): COUNT==1 row, nenhuma
 *       excecao propagada (UNIQUE PK gate atomico)</li>
 * </ol>
 *
 * <p>Hash sha256 calculado localmente via MessageDigest+HexFormat (mesma JDK do
 * service) — verifica no nivel de DB que o pattern bate.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class MediaCacheServiceTest {

    @Autowired MediaCacheService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM whatsapp.media_cache");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("buscarMediaId apos registrarUpload retorna media_id (hit dentro do TTL)")
    void hit_dentro_do_ttl_retorna_media_id() {
        byte[] bytes = "PDF orcamento 1234".getBytes();
        service.registrarUpload(bytes, "meta-id-abc-123");

        Optional<String> result = service.buscarMediaId(bytes);

        assertThat(result).contains("meta-id-abc-123");
    }

    @Test
    @DisplayName("buscarMediaId com hash inexistente retorna empty (miss)")
    void miss_quando_hash_nao_existe_retorna_empty() {
        Optional<String> result = service.buscarMediaId("sem-cache".getBytes());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buscarMediaId com entry expirada (expira_em < now) retorna empty (TTL estrito)")
    void miss_quando_expirado_retorna_empty() throws Exception {
        byte[] bytes = "PDF expirado".getBytes();
        String hash = sha256Hex(bytes);
        Instant haUmSegundo = Instant.now().minusSeconds(1);

        // Insert direto via JdbcTemplate forcando expira_em no passado.
        // TTL estrito (D-04) — entry existe no DB mas expira_em < now → empty.
        jdbc.update("INSERT INTO whatsapp.media_cache (arquivo_hash, media_id, expira_em) VALUES (?, ?, ?)",
                hash, "meta-id-old", Timestamp.from(haUmSegundo));

        Optional<String> result = service.buscarMediaId(bytes);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("registrarUpload concurrent silencia DataIntegrityViolationException (race PK)")
    void race_em_registrar_silencia_data_integrity_violation() throws Exception {
        byte[] bytes = "PDF concorrente".getBytes();
        String hash = sha256Hex(bytes);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger erros = new AtomicInteger();

        try {
            Runnable upload = () -> {
                try {
                    start.await();
                    service.registrarUpload(bytes, "meta-id-race-" + Thread.currentThread().getId());
                } catch (Exception e) {
                    erros.incrementAndGet();
                } finally {
                    done.countDown();
                }
            };

            pool.submit(upload);
            pool.submit(upload);
            // Libera ambas as threads simultaneamente — start gate sincroniza o disparo
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS))
                    .as("Ambas as threads devem terminar dentro de 5s")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(erros.get())
                .as("Nenhum thread deve propagar excecao — DataIntegrityViolationException silenciado")
                .isZero();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.media_cache WHERE arquivo_hash = ?",
                Integer.class, hash);
        assertThat(count)
                .as("UNIQUE PK garante exatamente 1 row apos race")
                .isEqualTo(1);
    }
}
