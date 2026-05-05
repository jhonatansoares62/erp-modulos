package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do orquestrador {@link MensagemService} via {@link SpringBootTest} —
 * exercicio E2E real do fluxo parser + idempotency + cliente, bootando o
 * Spring context inteiro e usando o H2 in-memory do profile {@code test}.
 *
 * <p>Por que {@link SpringBootTest} e nao Mockito puro:
 * <ul>
 *   <li>{@code @Transactional(REQUIRES_NEW)} de
 *       {@link ClienteZapService#atualizarUltimaMensagemEm} so ativa via proxy
 *       AOP do Spring, que exige bean real, nao mock (A3 RESEARCH).</li>
 *   <li>UNIQUE constraint do banco e o gate de idempotencia do
 *       {@link IdempotencyService} — testar com mock substitui logica de banco
 *       por logica de mock e nao garante que o pattern funciona em runtime.</li>
 *   <li>Cobertura E2E: validar que parser real (Jackson) + service real +
 *       repository real + H2 PG-mode interagem como esperado, sem stubs entre.</li>
 * </ul>
 *
 * <p>Os 4 testes usam fixtures JSON do Plan 02-05
 * ({@code src/test/resources/fixtures/webhook/}). Tests NAO sao
 * {@code @Transactional} — cada {@code processarWebhook} commita rows; tests
 * usam wamids distintos (.001, .002, .multi.001, .multi.002, .status.001) para
 * evitar contaminacao cross-test.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class MensagemServiceTest {

    @Autowired MensagemService service;
    @Autowired MensagemLogRepository logRepo;
    @Autowired ClienteZapRepository clienteRepo;

    private byte[] fixture(String nome) throws IOException {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    @Test
    @DisplayName("Webhook text persiste 1 row em mensagens_log + cria cliente com ultima_mensagem_em populada")
    void webhook_text_persiste() throws Exception {
        service.processarWebhook(fixture("text-portugues.json"));

        MensagemLog persistido = logRepo.findByWamid("wamid.HBgN.text.001").orElseThrow();
        assertThat(persistido.getTelefone()).isEqualTo("554784178525");
        assertThat(persistido.getTipo()).isEqualTo("text");
        assertThat(persistido.getConteudo()).isEqualTo("Olá, gostaria de um orçamento");

        ClienteZap cliente = clienteRepo.findByTelefone("554784178525").orElseThrow();
        assertThat(cliente.getIdClienteErp()).isNull();
        // ultima_mensagem_em populado via REQUIRES_NEW commit imediato (D-04 + PITFALLS C-01).
        // Validacao temporal precisa via JdbcTemplate ja existe em
        // ClienteZapServiceTest.atualizar_em_nova_transacao_commit_imediato (Plan 02-04).
        // Aqui validamos apenas que o campo foi preenchido — ClienteZapService.identificar
        // cria com null e ClienteZapService.atualizarUltimaMensagemEm seta NOW(). Comparacao
        // direta com Instant.now() falha por timezone-naive mapping H2 NOW() -> Instant
        // (H2 retorna LOCAL como UTC; reproduzido em PG real teria offset correto).
        assertThat(cliente.getUltimaMensagemEm())
            .as("REQUIRES_NEW deve ter committed o UPDATE; campo nao deve mais ser null")
            .isNotNull();
    }

    @Test
    @DisplayName("Webhook duplicado (mesmo wamid 2x) — apenas 1 row em mensagens_log (idempotency gate)")
    void webhook_duplicado_apenas_1_row() throws Exception {
        service.processarWebhook(fixture("text-portugues.json"));
        service.processarWebhook(fixture("text-portugues.json"));

        assertThat(logRepo.findByWamid("wamid.HBgN.text.001")).isPresent();

        // Filter por wamid para evitar contagem contaminada por outros tests
        // (tests do mesmo SpringContext compartilham H2 in-memory).
        long countWamid = logRepo.findAll().stream()
            .filter(m -> "wamid.HBgN.text.001".equals(m.getWamid()))
            .count();
        assertThat(countWamid)
            .as("UNIQUE constraint + DataIntegrityViolation catch garante exatamente 1 row")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("Webhook multiple messages persiste todas (loop do orquestrador)")
    void webhook_multiple_persiste_todas() throws Exception {
        service.processarWebhook(fixture("multiple-messages.json"));

        assertThat(logRepo.findByWamid("wamid.multi.001")).isPresent();
        assertThat(logRepo.findByWamid("wamid.multi.002")).isPresent();
    }

    @Test
    @DisplayName("Webhook status delivered — log debug only, NAO persiste em mensagens_log (D-06)")
    void webhook_status_nao_persiste() throws Exception {
        service.processarWebhook(fixture("status-delivered.json"));

        assertThat(logRepo.findByWamid("wamid.HBgN.status.001"))
            .as("Statuses Meta sao parseados mas Phase 2 NAO persiste em mensagens_log — D-06")
            .isEmpty();
    }
}
