package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.EventoRecebidoRepository;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.LancamentoService;
import br.com.erpkit.contabil.service.PeriodoService;
import br.com.erpkit.contabil.service.RoteiroService;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import br.com.erpkit.shared.exception.ModuloException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Trava de EXERCÍCIO ENCERRADO no lançamento operacional (bug: venda datada em ano encerrado postava
 * receita/imposto/recebimento sem bloqueio). Cada teste é @Transactional (rollback) sobre o Postgres
 * real do {@link AbstractPostgresIT}, com os roteiros/plano de contas seed das migrations.
 *
 * <p>Venda casada: roteiro 'venda.finalizada' à vista (prioridade 20) → D Caixa 1.1.1.01 · C Receita
 * 3.1.1.01 (seed V3). Nos testes que chamam postarDeEvento direto o eventoId fica nulo (sem FK
 * lancamento→evento_recebido); nos que passam por receber() o evento é persistido antes (FK ok).
 */
@Transactional
class ExercicioEncerradoTravaIT extends AbstractPostgresIT {

    @Autowired PeriodoService periodoService;
    @Autowired LancamentoService lancamentoService;
    @Autowired EventoService eventoService;
    @Autowired RoteiroService roteiroService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired EventoRecebidoRepository eventoRepository;

    // (a) Evento com competência em ANO ENCERRADO → rejeitado.
    @Test
    void postarDeEvento_emAnoEncerrado_eRejeitado() {
        periodoService.encerrarExercicio(2026, "teste");

        EventoContabilRequest venda = venda(null, LocalDate.of(2026, 6, 15), 100_000);
        RegraLancamento regra = casarVenda(venda);

        assertThatThrownBy(() -> lancamentoService.postarDeEvento(venda, regra))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("Exercício 2026")
                .hasMessageContaining("encerrado");
    }

    // (b) Evento no ANO ABERTO seguinte → posta normal.
    @Test
    void postarDeEvento_emAnoAbertoSeguinte_posta() {
        periodoService.encerrarExercicio(2026, "teste");

        EventoContabilRequest venda = venda(null, LocalDate.of(2027, 5, 20), 100_000);
        Lancamento lanc = lancamentoService.postarDeEvento(venda, casarVenda(venda));

        assertThat(lanc.getId()).isNotNull();
        assertThat(lanc.getStatus()).isEqualTo("lancado");
        assertThat(lanc.getDataCompetencia()).isEqualTo(LocalDate.of(2027, 5, 20));
    }

    // (c) Trava MENSAL continua funcionando (independente do exercício).
    @Test
    void travaMensal_continuaFuncionando() {
        periodoService.fecharMensal("2027-03", "teste");
        RegraLancamento regra = casarVenda(venda(null, LocalDate.of(2027, 4, 1), 100_000));

        // Mês fechado (2027-03) → rejeita.
        EventoContabilRequest emMesFechado = venda(null, LocalDate.of(2027, 3, 10), 100_000);
        assertThatThrownBy(() -> lancamentoService.postarDeEvento(emMesFechado, regra))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("Período 2027-03")
                .hasMessageContaining("fechado");

        // Mês aberto seguinte (2027-04) → posta.
        Lancamento lanc = lancamentoService.postarDeEvento(venda(null, LocalDate.of(2027, 4, 1), 100_000), regra);
        assertThat(lanc.getStatus()).isEqualTo("lancado");
    }

    // (d) encerrarExercicio ainda posta a ARE em 31/12 SEM se autobloquear.
    @Test
    void encerrarExercicio_postaAreEm31Dez_semAutobloqueio() {
        // Movimento de receita em 2026 (período aberto), para o encerramento ter o que apurar.
        EventoContabilRequest venda = venda(null, LocalDate.of(2026, 6, 15), 100_000);
        lancamentoService.postarDeEvento(venda, casarVenda(venda));

        assertThatCode(() -> periodoService.encerrarExercicio(2026, "teste")).doesNotThrowAnyException();

        // A ARE do encerramento nasce em 31/12/2026 (tipo 'encerramento'), apesar do exercício já estar
        // sendo encerrado — postarEncerramento não passa por validarPeriodoAberto.
        boolean areEm31Dez = lancamentoRepository.findAll().stream().anyMatch(l ->
                "encerramento".equals(l.getTipo()) && LocalDate.of(2026, 12, 31).equals(l.getDataCompetencia()));
        assertThat(areEm31Dez).as("lançamento de encerramento em 31/12/2026").isTrue();

        // E o exercício ficou de fato encerrado (a trava agora pega qualquer competência de 2026).
        assertThat(periodoService.motivoPeriodoFechado(LocalDate.of(2026, 6, 1))).isPresent();
    }

    // (e) Ingestão do ERP em ano encerrado NÃO estoura: vira pendência 'periodo_fechado' (ERP não trava).
    @Test
    void receber_emAnoEncerrado_viraPendenciaPeriodoFechado() {
        periodoService.encerrarExercicio(2026, "teste");

        UUID id = UUID.randomUUID();
        EventoRecebidoResponse resp = eventoService.receber(venda(id.toString(), LocalDate.of(2026, 6, 15), 100_000));

        assertThat(resp.getStatus()).isEqualTo("periodo_fechado");
        assertThat(resp.getLancamentoId()).isNull();

        var evento = eventoRepository.findById(id).orElseThrow();
        assertThat(evento.getStatus()).isEqualTo("periodo_fechado");
        assertThat(evento.getErroMensagem()).contains("Exercício 2026");
        // Nada foi contabilizado no ano fechado.
        assertThat(lancamentoRepository.findAll()).noneMatch(l -> id.equals(l.getOrigemEventoId()));
    }

    // (f) Ingestão do ERP no ano aberto seguinte posta normal (status processado).
    @Test
    void receber_emAnoAbertoSeguinte_posta() {
        periodoService.encerrarExercicio(2026, "teste");

        UUID id = UUID.randomUUID();
        EventoRecebidoResponse resp = eventoService.receber(venda(id.toString(), LocalDate.of(2027, 6, 15), 100_000));

        assertThat(resp.getStatus()).isEqualTo("processado");
        assertThat(resp.getLancamentoId()).isNotNull();
    }

    private RegraLancamento casarVenda(EventoContabilRequest venda) {
        return roteiroService.casar(venda.getTipo(), venda.getContexto(), venda.getDataEvento())
                .orElseThrow(() -> new IllegalStateException("roteiro de venda à vista não casou (seed V3)"));
    }

    private EventoContabilRequest venda(String eventoId, LocalDate data, long valorCentavos) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(eventoId);
        req.setTipo("venda.finalizada");
        req.setOrigem("teste");
        req.setDataEvento(data);
        req.setValorCentavos(valorCentavos);
        req.setContexto(Map.of("condicao", "avista"));
        return req;
    }
}
