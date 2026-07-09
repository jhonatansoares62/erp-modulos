package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.EncerramentoResponse;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.PeriodoFechadoRepository;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.PeriodoService;
import br.com.erpkit.shared.exception.ModuloException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EncerramentoExercicioTest extends AbstractPostgresIT {

    @Autowired EventoService eventoService;
    @Autowired PeriodoService periodoService;
    @Autowired PartidaRepository partidaRepository;
    @Autowired ContaContabilRepository contaRepository;
    @Autowired PeriodoFechadoRepository periodoFechadoRepository;

    @Test
    void encerraExercicioZeraResultadoETransfereLucroParaPL() {
        // Receita: venda à vista PIX 120000 → D Bancos (1.1.1.02) · C Receita (3.1.1.01).
        EventoRecebidoResponse venda = eventoService.receber(venda(120000));
        assertThat(venda.getStatus()).isEqualTo("processado");
        // Despesa: 90000 → D Despesas administrativas (3.2.2.01) · C Fornecedores a Pagar (2.1.1.01), por V9.
        EventoRecebidoResponse despesa = eventoService.receber(despesa(90000));
        assertThat(despesa.getStatus()).isEqualTo("processado");

        EncerramentoResponse resumo = periodoService.encerrarExercicio(2026, "contador");

        assertThat(resumo.getTotalReceitas()).isEqualTo(120000);
        assertThat(resumo.getTotalDespesas()).isEqualTo(90000);
        assertThat(resumo.getResultado()).isEqualTo(30000);   // lucro
        assertThat(resumo.getLancamentoIds()).hasSize(3);

        Map<Long, long[]> saldos = saldos2026();
        Long receitaId = contaRepository.findByCodigo("3.1.1.01").orElseThrow().getId();
        Long despesaId = contaRepository.findByCodigo("3.2.2.01").orElseThrow().getId();
        Long lucrosId = contaRepository.findByCodigo("2.3.3.01").orElseThrow().getId();

        // Contas de resultado zeradas: débitos == créditos após o encerramento.
        assertThat(saldos.get(receitaId)[0]).isEqualTo(saldos.get(receitaId)[1]);
        assertThat(saldos.get(despesaId)[0]).isEqualTo(saldos.get(despesaId)[1]);

        // Lucros Acumulados com crédito líquido de 30000 (lucro do exercício).
        long[] lucros = saldos.get(lucrosId);
        assertThat(lucros[1] - lucros[0]).isEqualTo(30000);

        assertThat(periodoFechadoRepository.existsByCompetenciaAndTipo("2026", "exercicio")).isTrue();
    }

    @Test
    void naoEncerraDuasVezesOMesmoExercicio() {
        eventoService.receber(venda(50000));
        periodoService.encerrarExercicio(2026, "contador");

        assertThatThrownBy(() -> periodoService.encerrarExercicio(2026, "contador"))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("encerrado");
    }

    /** Saldos [debito, credito] por contaId, dos lançamentos postados de 2026. */
    private Map<Long, long[]> saldos2026() {
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : partidaRepository.somarPorConta(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))) {
            map.put(((Number) row[0]).longValue(),
                    new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    private EventoContabilRequest venda(long valor) {
        EventoContabilRequest req = base(UUID.randomUUID().toString(), "venda.finalizada", valor);
        req.setContexto(Map.of("meioPagamento", "pix", "condicao", "avista"));
        return req;
    }

    private EventoContabilRequest despesa(long valor) {
        EventoContabilRequest req = base(UUID.randomUUID().toString(), "despesa.incorrida", valor);
        req.setContexto(Map.of("contaResultado", "3.2.2.01"));   // V9: despesa por conta de resultado (D 3.2.2.01 / C Fornecedores)
        return req;
    }

    private EventoContabilRequest base(String eventoId, String tipo, long valor) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(eventoId);
        req.setTipo(tipo);
        req.setOrigem("erp-teste");
        req.setDataEvento(LocalDate.of(2026, 7, 6));
        req.setValorCentavos(valor);
        EventoContabilRequest.Referencia ref = new EventoContabilRequest.Referencia();
        ref.setEntidade("teste");
        ref.setNumero("1");
        req.setReferencia(ref);
        return req;
    }
}
