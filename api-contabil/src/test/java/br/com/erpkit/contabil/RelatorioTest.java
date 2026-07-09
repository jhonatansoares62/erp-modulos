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
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RelatorioTest extends AbstractPostgresIT {

    private static final LocalDate DE = LocalDate.of(2026, 1, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 12, 31);

    @Autowired EventoService eventoService;
    @Autowired RelatorioService relatorioService;
    @Autowired EntityManager entityManager;

    @Test
    void balanceteFechaEDreCalculaResultado() {
        // Receita 450,00 (venda à vista PIX) e despesa 300,00 (D 3.2.2.01 · C 2.1.1.01 Fornecedores, por V9).
        eventoService.receber(evento("venda.finalizada", 45000, Map.of("meioPagamento", "pix", "condicao", "avista")));
        eventoService.receber(evento("despesa.incorrida", 30000, Map.of("contaResultado", "3.2.2.01")));
        entityManager.flush();   // garante que as queries nativas enxerguem as partidas

        BalanceteResponse bal = relatorioService.balancete(DE, ATE);
        assertThat(bal.isFecha()).isTrue();                       // Σ débitos = Σ créditos
        assertThat(bal.getTotalDebitos()).isEqualTo(75000);       // 45000 bancos + 30000 despesa
        assertThat(bal.getTotalCreditos()).isEqualTo(75000);      // 45000 receita + 30000 fornecedores

        DreResponse dre = relatorioService.dre(DE, ATE);
        assertThat(dre.getReceitaBruta()).isEqualTo(45000);
        assertThat(dre.getReceitaLiquida()).isEqualTo(45000);
        assertThat(dre.getDespesasOperacionais()).isEqualTo(30000);
        assertThat(dre.getResultadoLiquido()).isEqualTo(15000);   // 450 - 300
    }

    @Test
    void balanceteDe6ColunasComSaldoAnteriorFechaMovimentoESaldos() {
        // Movimento ANTES do período (vira saldo anterior): venda 200,00 em jun/2025.
        eventoService.receber(evento("venda.finalizada", 20000,
                Map.of("meioPagamento", "pix", "condicao", "avista"), LocalDate.of(2025, 6, 1)));
        // Movimento NO período: venda 450,00 em jul/2026.
        eventoService.receber(evento("venda.finalizada", 45000,
                Map.of("meioPagamento", "pix", "condicao", "avista"), LocalDate.of(2026, 7, 6)));
        entityManager.flush();

        BalanceteResponse bal = relatorioService.balancete(DE, ATE);   // 2026-01-01..2026-12-31

        // Conferência 1 (movimento do período): só a venda de 2026 conta.
        assertThat(bal.getTotalDebitos()).isEqualTo(45000);
        assertThat(bal.getTotalCreditos()).isEqualTo(45000);
        assertThat(bal.isFecha()).isTrue();

        // Conferência 2 (saldos atuais = acumulado 2025+2026 = 650,00): números DIFERENTES da conf. 1,
        // provando que a 2ª regra não é trivialmente igual à 1ª.
        assertThat(bal.getTotalSaldoAtualDevedorCentavos()).isEqualTo(65000);
        assertThat(bal.getTotalSaldoAtualCredorCentavos()).isEqualTo(65000);
        assertThat(bal.isFechaSaldos()).isTrue();
        assertThat(bal.getTotalSaldoAtualDevedorCentavos()).isNotEqualTo(bal.getTotalDebitos());

        // Bancos (1.1.1.02, devedora): anterior 200 D · período D 450 · atual 650 D.
        BalanceteResponse.Linha bancos = linha(bal, "1.1.1.02");
        assertThat(bancos.getSaldoAnteriorCentavos()).isEqualTo(20000);
        assertThat(bancos.getSaldoAnteriorNatureza()).isEqualTo("D");
        assertThat(bancos.getDebitos()).isEqualTo(45000);
        assertThat(bancos.getCreditos()).isZero();
        assertThat(bancos.getSaldoAtualCentavos()).isEqualTo(65000);
        assertThat(bancos.getSaldoAtualNatureza()).isEqualTo("D");

        // Receita (3.1.1.01, credora): anterior 200 C · período C 450 · atual 650 C.
        BalanceteResponse.Linha receita = linha(bal, "3.1.1.01");
        assertThat(receita.getSaldoAnteriorCentavos()).isEqualTo(20000);
        assertThat(receita.getSaldoAnteriorNatureza()).isEqualTo("C");
        assertThat(receita.getCreditos()).isEqualTo(45000);
        assertThat(receita.getSaldoAtualCentavos()).isEqualTo(65000);
        assertThat(receita.getSaldoAtualNatureza()).isEqualTo("C");

        // Saldo atual amarra com o Balanço Patrimonial na mesma data.
        BalancoResponse balanco = relatorioService.balanco(ATE);
        long bancosNoBalanco = balanco.getAtivo().stream()
                .filter(l -> l.getCodigo().equals("1.1.1.02")).findFirst().orElseThrow().getSaldoCentavos();
        assertThat(bancosNoBalanco).isEqualTo(bancos.getSaldoAtualCentavos());
    }

    private static BalanceteResponse.Linha linha(BalanceteResponse bal, String codigo) {
        return bal.getLinhas().stream()
                .filter(l -> l.getCodigo().equals(codigo)).findFirst().orElseThrow();
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
        return evento(tipo, valor, contexto, LocalDate.of(2026, 7, 6));
    }

    private EventoContabilRequest evento(String tipo, long valor, Map<String, Object> contexto, LocalDate data) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(UUID.randomUUID().toString());
        req.setTipo(tipo);
        req.setOrigem("erp-odonto");
        req.setDataEvento(data);
        req.setValorCentavos(valor);
        req.setContexto(contexto);
        return req;
    }
}
