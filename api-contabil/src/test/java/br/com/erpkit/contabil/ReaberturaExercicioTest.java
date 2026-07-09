package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.DreResponse;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.dto.PendenciaResponse;
import br.com.erpkit.contabil.dto.ReaberturaResponse;
import br.com.erpkit.contabil.model.EventoRecebido;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.PeriodoFechadoRepository;
import br.com.erpkit.contabil.repository.EventoRecebidoRepository;
import br.com.erpkit.contabil.service.ContaContabilService;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.LancamentoService;
import br.com.erpkit.contabil.service.PendenciaService;
import br.com.erpkit.contabil.service.PeriodoService;
import br.com.erpkit.contabil.service.RelatorioService;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loop de reabertura de exercício (fecha o Fix #1): reabrir estorna o encerramento, destrava o ano e
 * reprocessa as pendências 'periodo_fechado', que então postam. @Transactional (rollback) sobre o
 * Postgres real do {@link AbstractPostgresIT}, com os seeds das migrations.
 */
@Transactional
class ReaberturaExercicioTest extends AbstractPostgresIT {

    private static final LocalDate DE_2026 = LocalDate.of(2026, 1, 1);
    private static final LocalDate ATE_2026 = LocalDate.of(2026, 12, 31);

    @Autowired PeriodoService periodoService;
    @Autowired LancamentoService lancamentoService;
    @Autowired EventoService eventoService;
    @Autowired PendenciaService pendenciaService;
    @Autowired RelatorioService relatorioService;
    @Autowired ContaContabilService contaService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired PartidaRepository partidaRepository;
    @Autowired PeriodoFechadoRepository periodoRepository;
    @Autowired EventoRecebidoRepository eventoRepository;
    @Autowired EntityManager em;

    // Parte 0: estorno herda o tipo do original.
    @Test
    void estornarPreservaTipoNormalEEncerramento() {
        // normal: estorno de venda contabilizada continua 'normal'.
        Long vendaLanc = eventoService.receber(venda(UUID.randomUUID().toString(), 5000, LocalDate.of(2027, 3, 10)))
                .getLancamentoId();
        assertThat(lancamentoService.estornar(vendaLanc, "teste").getTipo()).isEqualTo("normal");

        // encerramento: estorno de um lançamento 'encerramento' (período aberto) continua 'encerramento'.
        Long areId = contaService.buscarPorCodigo("3.9.9.01").getId();
        Long receitaId = contaService.buscarPorCodigo("3.1.1.01").getId();
        Lancamento enc = lancamentoService.postarEncerramento(LocalDate.of(2027, 12, 31), "teste enc",
                List.of(new PartidaSpec(areId, "D", 10000), new PartidaSpec(receitaId, "C", 10000)));
        assertThat(enc.getTipo()).isEqualTo("encerramento");
        assertThat(lancamentoService.estornar(enc.getId(), "teste").getTipo()).isEqualTo("encerramento");
    }

    // Parte 1: reabrir reverte o encerramento, remove a trava e desfaz o transporte pro PL.
    @Test
    void reabrirEstornaEncerramentoRemoveTravaEZeraTransporte() {
        eventoService.receber(venda(UUID.randomUUID().toString(), 100000, LocalDate.of(2026, 7, 10)));
        periodoService.encerrarExercicio(2026, "contador");
        em.flush();
        assertThat(saldoCredor("2.3.3.01", DE_2026, ATE_2026)).isEqualTo(100000);   // Lucros com o transporte

        ReaberturaResponse res = periodoService.reabrirExercicio(2026, "contador", "correção de lançamento");
        em.flush();

        assertThat(res.getLancamentosEstornados()).isEqualTo(2);   // encerramento das receitas + apuração
        assertThat(periodoRepository.findByCompetenciaAndTipo("2026", "exercicio")).isEmpty();   // trava removida
        assertThat(saldoCredor("2.3.3.01", DE_2026, ATE_2026)).isZero();            // transporte desfeito
        // as reversões saíram tipo='encerramento' (estornaId != null).
        long reversoes = lancamentoRepository
                .findByTipoAndStatusAndDataCompetenciaBetween("encerramento", "lancado", DE_2026, ATE_2026)
                .stream().filter(l -> l.getEstornaId() != null).count();
        assertThat(reversoes).isEqualTo(2);
        // DRE do ano segue = movimento (sempre exclui 'encerramento').
        DreResponse dre = relatorioService.dre(DE_2026, ATE_2026);
        assertThat(dre.getReceitaBruta()).isEqualTo(100000);
    }

    // Parte 1+2 (aceite): pendência presa no ano encerrado posta ao reabrir.
    @Test
    void reabrirReprocessaPendenciaPresaDoAno() {
        periodoService.encerrarExercicio(2026, "contador");
        UUID id = UUID.randomUUID();
        assertThat(eventoService.receber(venda(id.toString(), 30000, LocalDate.of(2026, 7, 20))).getStatus())
                .isEqualTo("periodo_fechado");
        em.flush();

        ReaberturaResponse res = periodoService.reabrirExercicio(2026, "contador", "reprocessar presas");
        em.flush();

        assertThat(res.getPendenciasReprocessadas()).isEqualTo(1);
        EventoRecebido evento = eventoRepository.findById(id).orElseThrow();
        assertThat(evento.getStatus()).isEqualTo("processado");
        assertThat(evento.getLancamentoId()).isNotNull();
    }

    // Idempotência/pré-condição: reabrir de ano não encerrado → 409.
    @Test
    void reabrirDeAnoNaoEncerradoRetorna409() {
        assertThatThrownBy(() -> periodoService.reabrirExercicio(2030, "contador", "x"))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("não está encerrado");
    }

    // Parte 0 (essencial): re-encerrar depois de reabrir recomputa limpo (não dobra a ARE/PL).
    @Test
    void reencerrarAposReabrirNaoDobra() {
        eventoService.receber(venda(UUID.randomUUID().toString(), 100000, LocalDate.of(2026, 7, 10)));
        periodoService.encerrarExercicio(2026, "contador");
        periodoService.reabrirExercicio(2026, "contador", "correção");
        periodoService.encerrarExercicio(2026, "contador");   // re-encerra
        em.flush();

        // Resultado transportado = 100000 (não 200000): o recompute usa só 'normal'.
        assertThat(saldoCredor("2.3.3.01", DE_2026, ATE_2026)).isEqualTo(100000);
    }

    // Guarda estornaId: reabrir múltiplas vezes não re-estorna as reversões anteriores.
    @Test
    void reabrirDuasVezesNaoDobra() {
        eventoService.receber(venda(UUID.randomUUID().toString(), 100000, LocalDate.of(2026, 7, 10)));
        periodoService.encerrarExercicio(2026, "contador");
        periodoService.reabrirExercicio(2026, "contador", "1ª");
        periodoService.encerrarExercicio(2026, "contador");

        ReaberturaResponse res2 = periodoService.reabrirExercicio(2026, "contador", "2ª");
        em.flush();

        assertThat(res2.getLancamentosEstornados()).isEqualTo(2);   // só os encerramentos NOVOS (originais)
        assertThat(saldoCredor("2.3.3.01", DE_2026, ATE_2026)).isZero();   // sem dobra/inversão
    }

    // Parte 2: reprocessar não posta em período ainda fechado e é idempotente.
    @Test
    void reprocessarEhIdempotenteEmPeriodoAindaFechado() {
        periodoService.encerrarExercicio(2026, "contador");
        UUID id = UUID.randomUUID();
        eventoService.receber(venda(id.toString(), 30000, LocalDate.of(2026, 7, 20)));
        em.flush();

        // Ano ainda fechado → não posta, segue periodo_fechado.
        assertThat(pendenciaService.reprocessar().getReprocessados()).isZero();
        assertThat(eventoRepository.findById(id).orElseThrow().getStatus()).isEqualTo("periodo_fechado");
        assertThat(pendenciaService.reprocessar().getReprocessados()).isZero();   // idempotente
    }

    // Parte 3: a fila de pendências passa a incluir periodo_fechado, com o motivo.
    @Test
    void listarIncluiPeriodoFechadoComMotivo() {
        periodoService.encerrarExercicio(2026, "contador");
        UUID id = UUID.randomUUID();
        eventoService.receber(venda(id.toString(), 30000, LocalDate.of(2026, 7, 20)));
        em.flush();

        PendenciaResponse pend = pendenciaService.listar().stream()
                .filter(p -> p.getEventoId().equals(id.toString())).findFirst().orElseThrow();
        assertThat(pend.getStatus()).isEqualTo("periodo_fechado");
        assertThat(pend.getMotivo()).contains("Exercício 2026");
    }

    private long saldoCredor(String codigo, LocalDate de, LocalDate ate) {
        Long id = contaService.buscarPorCodigo(codigo).getId();
        List<Object[]> r = partidaRepository.somarConta(id, de, ate);
        if (r.isEmpty()) return 0;
        long debito = ((Number) r.get(0)[0]).longValue();
        long credito = ((Number) r.get(0)[1]).longValue();
        return credito - debito;
    }

    private EventoContabilRequest venda(String eventoId, long valor, LocalDate data) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(eventoId);
        req.setTipo("venda.finalizada");
        req.setOrigem("erp-teste");
        req.setDataEvento(data);
        req.setValorCentavos(valor);
        req.setContexto(Map.of("meioPagamento", "pix", "condicao", "avista"));
        return req;
    }
}
