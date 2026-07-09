package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.fiscal.dto.FiscalConfigDTO;
import br.com.erpkit.contabil.fiscal.dto.MemoriaFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.ReceitaHistoricaDTO;
import br.com.erpkit.contabil.fiscal.service.FiscalService;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class FiscalTest extends AbstractPostgresIT {

    private static final YearMonth COMP = YearMonth.of(2026, 7);

    @Autowired FiscalService fiscalService;
    @Autowired EventoService eventoService;
    @Autowired EntityManager entityManager;

    @Test
    void rbt12ProporcionalizaNoPrimeiroMesDeAtividade() {
        // Início no próprio mês da competência → 1 mês de atividade → RBT12 = receita × 12.
        config(LocalDate.of(2026, 7, 1), null);
        eventoService.receber(venda(10000, LocalDate.of(2026, 7, 10)));   // R$ 100,00 escriturado
        entityManager.flush();

        assertThat(fiscalService.rbt12(COMP)).isEqualTo(120000);   // 10000 × 12 ÷ 1
    }

    @Test
    void rbt12EstabelecidaUsaJanelaAnteriorSemOMesCorrente() {
        // Início antigo → estabelecida → janela [2025-07 .. 2026-06]; o mês corrente (jul/2026) NÃO entra.
        config(LocalDate.of(2023, 1, 1), null);
        eventoService.receber(venda(40000, LocalDate.of(2026, 6, 15)));   // dentro da janela → conta
        eventoService.receber(venda(99999, LocalDate.of(2026, 7, 15)));   // mês corrente → EXCLUÍDO
        entityManager.flush();

        assertThat(fiscalService.rbt12(COMP)).isEqualTo(40000);
    }

    @Test
    void memoriaHibridaUsaHistoricoAntesDoCorteECaiNaFaixaCorreta() {
        // Empresa migrando: início antigo + corte = jul/2026 → toda a janela [2025-07..2026-06] é < corte
        // (histórico informado). Histórico de 12 meses × R$ 40.000 = R$ 480.000. Sem venda escriturada.
        config(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 7, 1));
        List<ReceitaHistoricaDTO> hist = new ArrayList<>();
        for (int i = 12; i >= 1; i--) {
            hist.add(new ReceitaHistoricaDTO(COMP.minusMonths(i).toString(), 4000000));   // R$ 40.000,00
        }
        fiscalService.salvarReceitaHistorica(hist);
        entityManager.flush();

        assertThat(fiscalService.rbt12(COMP)).isEqualTo(48000000);   // 12 × 40.000 = 480.000

        MemoriaFiscalResponse mem = fiscalService.memoria(COMP);
        assertThat(mem.getRbt12Centavos()).isEqualTo(48000000);
        assertThat(mem.isProporcionalizado()).isFalse();
        assertThat(mem.getJanela()).hasSize(12);
        assertThat(mem.getJanela()).allSatisfy(j -> {
            assertThat(j.getFonte()).isEqualTo("informado");
            assertThat(j.getReceitaCentavos()).isEqualTo(4000000);
        });
        // Faixa/alíquota corretas (NÃO a faixa 1) já antes de qualquer venda escriturada.
        assertThat(mem.getFaixa()).isNotNull();
        assertThat(mem.getFaixa()).isGreaterThan(1);
        assertThat(mem.getAliquotaEfetiva().signum()).isPositive();
        // Sem venda escriturada no mês → base e imposto zerados.
        assertThat(mem.getBaseCalculoCentavos()).isZero();
        assertThat(mem.getImpostoCentavos()).isZero();
    }

    @Test
    void receitaHistoricaRoundTripPersisteERecarregaComSoma() {
        // Salva 12 competências com valores distintos; ao recarregar (listar), a grade repovoa e o total bate.
        List<ReceitaHistoricaDTO> itens = new ArrayList<>();
        long somaEsperada = 0;
        for (int i = 12; i >= 1; i--) {
            long v = 3000000 + i * 100000L;
            itens.add(new ReceitaHistoricaDTO(COMP.minusMonths(i).toString(), v));
            somaEsperada += v;
        }
        fiscalService.salvarReceitaHistorica(itens);

        List<ReceitaHistoricaDTO> lidos = fiscalService.listarReceitaHistorica();
        assertThat(lidos).hasSize(12);
        assertThat(lidos).isSortedAccordingTo(Comparator.comparing(ReceitaHistoricaDTO::getCompetencia));

        long soma = 0;
        for (ReceitaHistoricaDTO enviado : itens) {
            ReceitaHistoricaDTO lido = lidos.stream()
                    .filter(l -> l.getCompetencia().equals(enviado.getCompetencia())).findFirst().orElseThrow();
            assertThat(lido.getReceitaBrutaCentavos()).isEqualTo(enviado.getReceitaBrutaCentavos());
            soma += lido.getReceitaBrutaCentavos();
        }
        assertThat(soma).isEqualTo(somaEsperada);

        // REPLACE-ALL: salvar de novo substitui tudo (não acumula).
        fiscalService.salvarReceitaHistorica(List.of(new ReceitaHistoricaDTO(COMP.minusMonths(1).toString(), 500000)));
        assertThat(fiscalService.listarReceitaHistorica()).hasSize(1);
    }

    private void config(LocalDate inicioAtividade, LocalDate corte) {
        FiscalConfigDTO cfg = new FiscalConfigDTO();
        cfg.setDataInicioAtividade(inicioAtividade);
        cfg.setDataEntradaSistema(corte);
        fiscalService.salvar(cfg);
    }

    private EventoContabilRequest venda(long valor, LocalDate data) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(UUID.randomUUID().toString());
        req.setTipo("venda.finalizada");
        req.setOrigem("erp-teste");
        req.setDataEvento(data);
        req.setValorCentavos(valor);
        req.setContexto(Map.of("meioPagamento", "pix", "condicao", "avista"));
        return req;
    }
}
