package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test das agregacoes de relatorio (V7 §12) contra H2 (PostgreSQL mode)
 * com Flyway aplicado — valida as @Query GROUP BY + o mapeamento do {@link RelatorioUsoService}.
 *
 * <p>{@code @Transactional} isola: {@code deleteAll} da slate limpa e o rollback evita
 * poluir outras suites (H2 in-memory compartilhado no fork do surefire).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class RelatorioUsoServiceIntegrationTest {

    @Autowired MensagemLogRepository repository;
    @Autowired RelatorioUsoService service;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    private MensagemLog msg(String wamid, Direcao dir, String tipo) {
        return new MensagemLog(wamid, "554784178525", dir, tipo, "x", null);
    }

    @Test
    @DisplayName("resumo agrega direcao/tipo/status/categoria + conta faturaveis")
    void resumo_agrega() {
        MensagemLog in1 = msg("in.1", Direcao.in, "text");
        in1.setResultado("respondido");
        repository.save(in1);

        MensagemLog s1 = msg("out.1", Direcao.out, "text");
        s1.setStatus("delivered"); s1.setCategoria("service"); s1.setBillable(true);
        MensagemLog s2 = msg("out.2", Direcao.out, "interactive_list");
        s2.setStatus("read"); s2.setCategoria("service"); s2.setBillable(true);
        MensagemLog s3 = msg("out.3", Direcao.out, "text");
        s3.setStatus("failed"); // sem categoria/billable
        repository.save(s1);
        repository.save(s2);
        repository.save(s3);
        repository.flush();

        Instant de = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant ate = Instant.now().plus(1, ChronoUnit.DAYS);
        ResumoUsoResponse r = service.resumo(de, ate);

        assertThat(r.entrada()).isEqualTo(1);
        assertThat(r.saida()).isEqualTo(3);
        assertThat(r.total()).isEqualTo(4);
        assertThat(r.faturaveis()).isEqualTo(2);
        assertThat(r.porTipo()).containsEntry("text", 3L).containsEntry("interactive_list", 1L);
        assertThat(r.statusSaida())
            .containsEntry("delivered", 1L).containsEntry("read", 1L).containsEntry("failed", 1L);
        assertThat(r.categoriaSaida())
            .containsEntry("service", 2L).containsEntry("sem_categoria", 1L);
        assertThat(r.porResultado()).containsEntry("respondido", 1L);
    }

    @Test
    @DisplayName("periodo fora do range: contagens zeradas")
    void resumo_fora_do_range() {
        repository.save(msg("in.1", Direcao.in, "text"));
        repository.flush();

        Instant de = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant ate = Instant.now().minus(5, ChronoUnit.DAYS);
        ResumoUsoResponse r = service.resumo(de, ate);

        assertThat(r.total()).isZero();
        assertThat(r.porTipo()).isEmpty();
    }
}
