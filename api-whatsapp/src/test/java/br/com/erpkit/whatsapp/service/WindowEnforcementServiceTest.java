package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests do {@link WindowEnforcementService} — D-03 + D-05 + OUT-06 + OUT-07.
 *
 * <p>Cobertura (3 tests obrigatorios pelo PLAN):
 * <ol>
 *   <li>23h: passa sem excecao (janela aberta)</li>
 *   <li>25h: lanca {@link JanelaConversaFechadaException} com {@code ultimaMensagemEm} preenchido</li>
 *   <li>cliente inexistente em {@code clientes_zap}: lanca com {@code ultimaMensagemEm == null}</li>
 * </ol>
 *
 * <p><b>Pattern espelhando {@link ClienteZapServiceTest}</b> (Phase 2):
 * {@code @SpringBootTest(classes = WhatsAppApplication.class)} + {@code @ActiveProfiles("test")} +
 * {@code JdbcTemplate} para fixture {@code INSERT INTO whatsapp.clientes_zap}.
 *
 * <p><b>Cleanup via @AfterEach</b> — H2 in-memory e compartilhado entre tests do
 * mesmo SpringContext; cada test usa telefones unicos no prefixo "5547" + "9999"
 * e o {@code DELETE} apos limpa para evitar pollution caso o test runner
 * reordene execucao.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class WindowEnforcementServiceTest {

    @Autowired WindowEnforcementService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        // Limpa fixtures usados nos 3 tests (telefones BR-DDD-47 normalizado +
        // DDD-99 normalizado para cliente_inexistente). Evita pollution.
        jdbc.update("DELETE FROM whatsapp.clientes_zap "
            + "WHERE telefone LIKE '5547%' OR telefone LIKE '5599%' OR telefone LIKE '999%'");
    }

    @Test
    @DisplayName("cliente com ultima_mensagem_em 23h atras passa sem excecao (janela aberta)")
    void cliente_com_ultima_em_23h_passa() {
        Instant ha23h = Instant.now().minus(23, ChronoUnit.HOURS);
        jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone, ultima_mensagem_em) VALUES (?, ?)",
            "554784178525", Timestamp.from(ha23h));

        // Sem excecao = janela aberta. Telefone passa em formato comum (com +,
        // espacos, parenteses) — service normaliza antes de comparar.
        assertThatCode(() -> service.verificarJanela("+55 (47) 8417-8525"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cliente com ultima_mensagem_em 25h atras lanca JanelaConversaFechadaException com Instant preenchido")
    void cliente_com_ultima_em_25h_lanca() {
        Instant ha25h = Instant.now().minus(25, ChronoUnit.HOURS);
        jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone, ultima_mensagem_em) VALUES (?, ?)",
            "554784178525", Timestamp.from(ha25h));

        assertThatThrownBy(() -> service.verificarJanela("554784178525"))
            .isInstanceOf(JanelaConversaFechadaException.class)
            .satisfies(t -> {
                JanelaConversaFechadaException ex = (JanelaConversaFechadaException) t;
                assertThat(ex.getCodigo()).isEqualTo("JANELA_24H_FECHADA");
                assertThat(ex.getTelefone()).isEqualTo("554784178525");
                assertThat(ex.getUltimaMensagemEm()).isNotNull();
                // Instant lido do banco deve estar proximo do que inserimos (25h atras)
                assertThat(ex.getUltimaMensagemEm())
                    .isBefore(Instant.now().minus(24, ChronoUnit.HOURS));
            });
    }

    @Test
    @DisplayName("cliente inexistente em clientes_zap lanca JanelaConversaFechadaException com ultimaMensagemEm null")
    void cliente_inexistente_lanca() {
        // SEM insert — cliente nao existe em clientes_zap. Telefone normalizado
        // pelo service: "+55 (99) 99988-7766" -> "5599988777666" -> DDD 99 NAO em
        // DDDS_COM_NONO_DIGITO + numero local 9-digit comecando com 9 -> strip 9 ->
        // "559988777666". Nao precisa garantir o exato output normalizado aqui;
        // basta que o service lance pois nao havera row no DB.
        assertThatThrownBy(() -> service.verificarJanela("+5599988777666"))
            .isInstanceOf(JanelaConversaFechadaException.class)
            .satisfies(t -> {
                JanelaConversaFechadaException ex = (JanelaConversaFechadaException) t;
                assertThat(ex.getCodigo()).isEqualTo("JANELA_24H_FECHADA");
                assertThat(ex.getUltimaMensagemEm()).isNull();
                // Telefone reportado deve estar normalizado (sem +, sem espacos)
                assertThat(ex.getTelefone()).matches("\\d+");
            });
    }
}
