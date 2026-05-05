package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests do {@link ClienteZapService} — D-04 + D-07 + PER-05 + PER-06 + PER-07.
 *
 * <p>Cobertura (7 tests):
 * <ol>
 *   <li>auto-create com {@code id_cliente_erp = null} (PER-06)</li>
 *   <li>recovery de telefone existente (idempotencia)</li>
 *   <li>normalizacao DDD 47 SC strip 9 antes de buscar</li>
 *   <li>normalizacao DDD 11 SP preserva 9</li>
 *   <li>concorrencia 2 threads em UNIQUE telefone — apenas 1 row</li>
 *   <li>REQUIRES_NEW commit imediato — visivel via 2a conexao do pool (JdbcTemplate)
 *       fora da transacao do Hibernate</li>
 *   <li>atualizar telefone inexistente — false sem criar registro</li>
 * </ol>
 *
 * <p><b>Telefones distintos por test</b> — cada test usa um numero unico (sem
 * {@code @Transactional} de teste, todo save commita realmente; cache de
 * SpringContext reusa o mesmo H2 entre @Test methods).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class ClienteZapServiceTest {

    @Autowired ClienteZapService service;
    @Autowired ClienteZapRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("identificar com telefone novo cria registro com id_cliente_erp=null")
    void identificar_cria_telefone_novo() {
        ClienteZap criado = service.identificar("+5511987654321");
        assertThat(criado.getId()).isNotNull();
        assertThat(criado.getTelefone()).isEqualTo("5511987654321");
        assertThat(criado.getIdClienteErp()).isNull();
    }

    @Test
    @DisplayName("identificar com telefone existente recupera mesmo registro")
    void identificar_recupera_existente() {
        ClienteZap primeiro = service.identificar("+5511111222333");
        ClienteZap segundo = service.identificar("+5511111222333");
        assertThat(segundo.getId()).isEqualTo(primeiro.getId());
    }

    @Test
    @DisplayName("identificar normaliza antes de buscar — DDD 47 SC strip 9")
    void identificar_normaliza_antes_de_buscar() {
        ClienteZap criado = service.identificar("+5547984178525");  // 13 digitos
        assertThat(criado.getTelefone()).isEqualTo("554784178525");  // 12 digitos (sem 9)

        // Lookup subsequente com formato diferente (mas mesmo numero BR completo)
        // recupera a mesma row. NOTA: prefixo "55" e obrigatorio para o
        // TelefoneBR.normalizar reconhecer como BR e aplicar strip do 9o digito;
        // input sem "55" e tratado como nao-BR (early return) e geraria outra row.
        ClienteZap rebusca = service.identificar("+55 (47) 98417-8525");
        assertThat(rebusca.getId()).isEqualTo(criado.getId());
    }

    @Test
    @DisplayName("identificar normaliza — DDD 11 SP preserva 9")
    void identificar_normaliza_sp_preserva_9() {
        ClienteZap criado = service.identificar("+5511987654322");
        assertThat(criado.getTelefone()).isEqualTo("5511987654322");  // 13 digitos preservados
    }

    @Test
    @DisplayName("identificar concorrente (2 threads, mesmo telefone novo) — apenas 1 row no DB")
    void identificar_concorrente_unique() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Runnable r = () -> {
                try {
                    start.await();
                    service.identificar("+5599988777666");
                } catch (Exception ignore) {
                    // Service captura DataIntegrityViolationException via try/catch interno.
                    // Outras excecoes sao perda silenciosa de tentativa — o contrato sob
                    // teste e: exatamente 1 row no banco apos a corrida.
                }
            };
            executor.submit(r);
            executor.submit(r);
            start.countDown();  // dispara as 2 threads simultaneamente
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            // "+5599988777666" → 13 digitos: 55-99-988777666 → DDD 99 NAO em
            // DDDS_COM_NONO_DIGITO + numero local 988777666 comeca com 9 →
            // strip 9 do numero local → 559988777666 (12 digitos)
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
                Integer.class, "559988777666"
            );
            assertThat(count).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("atualizarUltimaMensagemEm com REQUIRES_NEW commita imediato (visivel via 2a conexao)")
    void atualizar_em_nova_transacao_commit_imediato() {
        // Setup: criar cliente
        service.identificar("+5511777666555");

        Instant antes = Instant.now().minusSeconds(2);
        boolean atualizado = service.atualizarUltimaMensagemEm("+5511777666555");
        assertThat(atualizado).isTrue();

        // Le via JdbcTemplate (conexao do pool diferente da que o service usou)
        // — se REQUIRES_NEW funcionou, este SELECT ve o UPDATE comittado.
        Timestamp tsRaw = jdbc.queryForObject(
            "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?",
            Timestamp.class, "5511777666555"
        );
        assertThat(tsRaw).isNotNull();
        Instant ts = tsRaw.toInstant();
        assertThat(ts).isAfter(antes);
        assertThat(ts).isBeforeOrEqualTo(Instant.now().plusSeconds(2));
    }

    @Test
    @DisplayName("atualizarUltimaMensagemEm com telefone inexistente retorna false (0 rows)")
    void atualizar_telefone_inexistente() {
        boolean atualizado = service.atualizarUltimaMensagemEm("+5511000000000");
        assertThat(atualizado).isFalse();
        // E nao criou registro
        Optional<ClienteZap> persistido = repository.findByTelefone("5511000000000");
        assertThat(persistido).isEmpty();
    }
}
