package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.BalancoResponse;
import br.com.erpkit.contabil.dto.BalanceteResponse;
import br.com.erpkit.contabil.dto.DreResponse;
import br.com.erpkit.contabil.dto.RazaoResponse;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.RelatorioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelatorioTest {

    private static final LocalDate DE = LocalDate.of(2026, 1, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 12, 31);

    @Autowired EventoService eventoService;
    @Autowired RelatorioService relatorioService;
    @Autowired EntityManager entityManager;

    @Test
    void balanceteFechaEDreCalculaResultado() {
        // Receita 450,00 (venda à vista PIX) e despesa 300,00.
        eventoService.receber(evento("venda.finalizada", 45000, Map.of("meioPagamento", "pix", "condicao", "avista")));
        eventoService.receber(evento("despesa.incorrida", 30000, Map.of("categoria", "aluguel")));
        entityManager.flush();   // garante que as queries nativas enxerguem as partidas

        BalanceteResponse bal = relatorioService.balancete(DE, ATE);
        assertThat(bal.isFecha()).isTrue();                       // Σ débitos = Σ créditos
        assertThat(bal.getTotalDebitos()).isEqualTo(75000);       // 45000 bancos + 30000 despesa
        assertThat(bal.getTotalCreditos()).isEqualTo(75000);      // 45000 receita + 30000 caixa

        DreResponse dre = relatorioService.dre(DE, ATE);
        assertThat(dre.getReceitaBruta()).isEqualTo(45000);
        assertThat(dre.getReceitaLiquida()).isEqualTo(45000);
        assertThat(dre.getDespesasOperacionais()).isEqualTo(30000);
        assertThat(dre.getResultadoLiquido()).isEqualTo(15000);   // 450 - 300
    }

    @Test
    void razaoAcumulaSaldoDaConta() {
        eventoService.receber(evento("venda.finalizada", 45000, Map.of("meioPagamento", "pix", "condicao", "avista")));
        entityManager.flush();

        RazaoResponse razao = relatorioService.razao("3.1.1.01", DE, ATE);   // Receita de vendas (credora)
        assertThat(razao.getLinhas()).hasSize(1);
        assertThat(razao.getLinhas().get(0).getTipo()).isEqualTo("C");
        assertThat(razao.getSaldoFinalCentavos()).isEqualTo(45000);
    }

    @Test
    void balancoFechaComResultadoNaoEncerradoNoPl() {
        // Venda à vista PIX 150,00 → D Bancos (1.1.1.02) · C Receita (3.1.1.01). Sem encerramento,
        // a receita aparece como resultado no PL, e Ativo = Passivo + PL.
        eventoService.receber(evento("venda.finalizada", 15000, Map.of("meioPagamento", "pix", "condicao", "avista")));
        entityManager.flush();

        BalancoResponse bal = relatorioService.balanco(ATE);

        assertThat(bal.getTotalAtivo()).isEqualTo(15000);
        assertThat(bal.getAtivo()).hasSize(1);
        assertThat(bal.getAtivo().get(0).getCodigo()).isEqualTo("1.1.1.02");
        assertThat(bal.getAtivo().get(0).getSaldoCentavos()).isEqualTo(15000);

        assertThat(bal.getResultadoExercicio()).isEqualTo(15000);
        assertThat(bal.getPassivoPl()).hasSize(1);   // só o resultado do exercício a apurar
        assertThat(bal.getPassivoPl().get(0).getNome()).contains("Resultado do Exerc");
        assertThat(bal.getPassivoPl().get(0).getSaldoCentavos()).isEqualTo(15000);
        assertThat(bal.getTotalPassivoPl()).isEqualTo(15000);

        assertThat(bal.isFecha()).isTrue();
    }

    private EventoContabilRequest evento(String tipo, long valor, Map<String, Object> contexto) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(UUID.randomUUID().toString());
        req.setTipo(tipo);
        req.setOrigem("erp-odonto");
        req.setDataEvento(LocalDate.of(2026, 7, 6));
        req.setValorCentavos(valor);
        req.setContexto(contexto);
        return req;
    }
}
