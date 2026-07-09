package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.DreResponse;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.LancamentoService;
import br.com.erpkit.contabil.service.PeriodoService;
import br.com.erpkit.contabil.service.RelatorioService;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EstornoEFechamentoTest extends AbstractPostgresIT {

    private static final LocalDate DE = LocalDate.of(2026, 1, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 12, 31);

    @Autowired EventoService eventoService;
    @Autowired LancamentoService lancamentoService;
    @Autowired PeriodoService periodoService;
    @Autowired RelatorioService relatorioService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired EntityManager entityManager;

    @Test
    void estornoInverteAsPartidasENetaZero() {
        Long lancId = eventoService.receber(venda(45000)).getLancamentoId();

        Lancamento estorno = lancamentoService.estornar(lancId, "teste");
        entityManager.flush();

        assertThat(estorno.getEstornaId()).isEqualTo(lancId);
        Lancamento original = lancamentoRepository.findById(lancId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo("estornado");
        assertThat(original.getEstornadoPorId()).isEqualTo(estorno.getId());

        // Original (D bancos, C receita) + estorno (C bancos, D receita) → resultado líquido zero.
        DreResponse dre = relatorioService.dre(DE, ATE);
        assertThat(dre.getReceitaBruta()).isZero();
        assertThat(dre.getResultadoLiquido()).isZero();
    }

    @Test
    void naoEstornaLancamentoJaEstornado() {
        Long lancId = eventoService.receber(venda(10000)).getLancamentoId();
        lancamentoService.estornar(lancId, null);

        assertThatThrownBy(() -> lancamentoService.estornar(lancId, null))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("estornado");
    }

    @Test
    void periodoFechadoBloqueiaLancamento() {
        periodoService.fecharMensal("2026-07", "contador");

        // venda com competência 2026-07-06 cai em período fechado.
        assertThatThrownBy(() -> eventoService.receber(venda(20000)))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("fechado");
    }

    private EventoContabilRequest venda(long valor) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(UUID.randomUUID().toString());
        req.setTipo("venda.finalizada");
        req.setOrigem("erp-odonto");
        req.setDataEvento(LocalDate.of(2026, 7, 6));
        req.setValorCentavos(valor);
        req.setContexto(Map.of("meioPagamento", "pix", "condicao", "avista"));
        return req;
    }
}
