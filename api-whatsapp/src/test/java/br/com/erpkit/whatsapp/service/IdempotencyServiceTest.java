package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests do {@link IdempotencyService} — contrato {@code boolean novo} (true=inseriu,
 * false=duplicate) via fallback save+catch DataIntegrityViolationException
 * (caminho do Plan 03 confirmado pela decisao Wave 1 — ON CONFLICT NAO suportado
 * em H2 v2.3.232 PG-mode, ver {@code OnConflictSpikeTest} + 02-01-SUMMARY.md).
 *
 * <p>Cobertura: primeira insercao, duplicata silenciada (UNIQUE preserva original),
 * wamid distintos, concorrencia (2 threads simultaneos = exatamente 1 vence E
 * exatamente 1 row no DB), tipo desconhecido com nulls.
 *
 * <p><b>Wamids distintos por test</b> — {@code wamid.test.001..unknown} — evita
 * contaminacao cross-test (sem {@code @Transactional}, cada save commita
 * realmente; cache de SpringContext reusa o mesmo H2 in-memory entre tests).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class IdempotencyServiceTest {

    @Autowired IdempotencyService idempotency;
    @Autowired MensagemLogRepository repository;

    @Test
    @DisplayName("Inserir wamid novo retorna true e persiste com fields corretos")
    void inserir_primeira_vez_retorna_true() {
        boolean novo = idempotency.tentarPersistir(
            "wamid.test.001", "5511987654321", Direcao.in,
            "text", "Olá", null
        );
        assertThat(novo).isTrue();
        Optional<MensagemLog> persistido = repository.findByWamid("wamid.test.001");
        assertThat(persistido).isPresent();
        assertThat(persistido.get().getTelefone()).isEqualTo("5511987654321");
        assertThat(persistido.get().getDirecao()).isEqualTo(Direcao.in);
        assertThat(persistido.get().getTipo()).isEqualTo("text");
        assertThat(persistido.get().getConteudo()).isEqualTo("Olá");
        assertThat(persistido.get().getMediaId()).isNull();
    }

    @Test
    @DisplayName("Inserir mesmo wamid duas vezes — segunda retorna false e original preservado")
    void inserir_segunda_vez_retorna_false() {
        idempotency.tentarPersistir("wamid.test.002", "5511111111111", Direcao.in, "text", "primeira", null);
        boolean segunda = idempotency.tentarPersistir("wamid.test.002", "5511222222222", Direcao.in, "text", "segunda", null);
        assertThat(segunda).isFalse();
        // UNIQUE constraint dispara antes do UPDATE — original preservado (DO NOTHING semantics)
        assertThat(repository.findByWamid("wamid.test.002")).get()
            .extracting(MensagemLog::getConteudo).isEqualTo("primeira");
        assertThat(repository.findByWamid("wamid.test.002")).get()
            .extracting(MensagemLog::getTelefone).isEqualTo("5511111111111");
    }

    @Test
    @DisplayName("Wamid diferentes — ambos retornam true e ambos persistidos")
    void inserir_dois_wamid_diferentes() {
        assertThat(idempotency.tentarPersistir("wamid.test.003a", "5511333333333", Direcao.in, "text", "a", null)).isTrue();
        assertThat(idempotency.tentarPersistir("wamid.test.003b", "5511333333333", Direcao.in, "text", "b", null)).isTrue();
        assertThat(repository.findByWamid("wamid.test.003a")).isPresent();
        assertThat(repository.findByWamid("wamid.test.003b")).isPresent();
    }

    @Test
    @DisplayName("Concorrencia: 2 threads inserem mesmo wamid simultaneamente — exatamente 1 retorna true")
    void concorrencia_2_threads_mesmo_wamid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger truthCount = new AtomicInteger(0);

            Runnable tentativa = () -> {
                try {
                    start.await();
                    if (idempotency.tentarPersistir(
                            "wamid.test.race", "5511444444444", Direcao.in, "text", "x", null)) {
                        truthCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Permitir excecoes nao tratadas — o Service ja captura
                    // DataIntegrityViolationException via catch interno (caminho fallback);
                    // qualquer outra excecao nao deveria acontecer, mas se acontecer,
                    // a thread perde a tentativa silenciosamente. O contrato sob teste e:
                    // exatamente 1 row no banco apos a corrida.
                }
            };

            executor.submit(tentativa);
            executor.submit(tentativa);
            // Libera ambas as threads simultaneamente — start gate sincroniza o disparo
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            // Exatamente 1 thread deve ter retornado true (UNIQUE constraint = gate atomico)
            assertThat(truthCount.get()).isEqualTo(1);
            // E exatamente 1 row no banco
            assertThat(repository.findByWamid("wamid.test.race")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Conteudo null + mediaId null — persiste OK (caso desconhecido / WEB-07)")
    void desconhecido_com_nulls() {
        boolean novo = idempotency.tentarPersistir(
            "wamid.test.unknown", "5511555555555", Direcao.in,
            "desconhecido", null, null
        );
        assertThat(novo).isTrue();
        assertThat(repository.findByWamid("wamid.test.unknown")).get()
            .satisfies(m -> {
                assertThat(m.getConteudo()).isNull();
                assertThat(m.getMediaId()).isNull();
                assertThat(m.getTipo()).isEqualTo("desconhecido");
            });
    }
}
