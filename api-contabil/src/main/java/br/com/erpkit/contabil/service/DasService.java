package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.BalanceteResponse;
import br.com.erpkit.contabil.dto.DasResponse;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.dto.PagamentoDasResponse;
import br.com.erpkit.contabil.dto.PagarDasRequest;
import br.com.erpkit.contabil.model.PagamentoDas;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.PagamentoDasRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DAS (Simples Nacional) NATIVO do módulo. É a autoridade única do pagamento por competência:
 * o app standalone chama direto e o ERP delega — ambos passam por {@link #pagar} e pela trava
 * UNIQUE(competencia) em pagamento_das, então o passivo 2.1.3.01 nunca é baixado em dobro.
 *
 * <p>O lançamento (D 2.1.3.01 · C conta de liquidação) é postado reusando o roteiro
 * {@code das.pago} (V12) via {@link EventoService}, que já trata período fechado, numeração e
 * idempotência por eventoId (determinístico pela competência).</p>
 */
@Service
public class DasService {

    private static final Logger log = LoggerFactory.getLogger(DasService.class);

    /** Passivo Simples Nacional a Recolher. */
    private static final String CONTA_SIMPLES = "2.1.3.01";
    /** Contas de liquidação suportadas pelo roteiro das.pago (V12): Caixa e Bancos. */
    private static final Set<String> CONTAS_LIQUIDACAO = Set.of("1.1.1.01", "1.1.1.02");
    private static final LocalDate INICIO = LocalDate.of(2000, 1, 1);

    private final PagamentoDasRepository pagamentoRepository;
    private final EventoService eventoService;
    private final RelatorioService relatorioService;
    private final ContaContabilRepository contaRepository;

    public DasService(PagamentoDasRepository pagamentoRepository, EventoService eventoService,
                      RelatorioService relatorioService, ContaContabilRepository contaRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.eventoService = eventoService;
        this.relatorioService = relatorioService;
        this.contaRepository = contaRepository;
    }

    /** Saldo a recolher (total) + sugestão da competência (movimento líquido do mês) + histórico. */
    @Transactional(readOnly = true)
    public DasResponse consultar(String competencia) {
        long total = saldoCredorSimples(INICIO, LocalDate.now());
        String comp = null;
        Long saldoComp = null;
        boolean paga = false;
        if (competencia != null && !competencia.isBlank()) {
            YearMonth ym = parseCompetencia(competencia);
            comp = ym.toString();
            saldoComp = saldoCredorSimples(ym.atDay(1), ym.atEndOfMonth());
            paga = pagamentoRepository.existsByCompetencia(comp);
        }
        List<PagamentoDasResponse> historico = pagamentoRepository.findAllByOrderByCompetenciaDescIdDesc()
                .stream().map(p -> new PagamentoDasResponse(p, nomeConta(p.getContaLiquidacao()))).toList();
        return new DasResponse(total, comp, saldoComp, paga, historico);
    }

    /**
     * Registra o pagamento e contabiliza a baixa. Trava por competência (UNIQUE) — a segunda
     * tentativa (app OU erp) recebe 409. Tudo numa transação: se o registro falhar, o lançamento
     * também é desfeito.
     */
    @Transactional
    public PagamentoDasResponse pagar(PagarDasRequest req, String origem) {
        YearMonth ym = parseCompetencia(req.getCompetencia());
        String comp = ym.toString();

        if (req.getValorCentavos() <= 0) {
            throw new ModuloException("Valor do DAS deve ser maior que zero", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String conta = req.getContaLiquidacao() == null ? "" : req.getContaLiquidacao().trim();
        if (!CONTAS_LIQUIDACAO.contains(conta)) {
            throw new ModuloException(
                    "Conta de liquidação deve ser Caixa (1.1.1.01) ou Bancos (1.1.1.02).",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (pagamentoRepository.existsByCompetencia(comp)) {
            throw new ModuloException(
                    "O DAS da competência " + comp + " já foi registrado como pago.", HttpStatus.CONFLICT);
        }

        LocalDate dataPagamento = req.getDataPagamento() != null ? req.getDataPagamento() : LocalDate.now();

        // Reusa o roteiro das.pago (V12): D 2.1.3.01 · C <conta de liquidação>. eventoId ALEATÓRIO
        // por pagamento — a trava de duplicidade é o UNIQUE(competencia) em pagamento_das, checado
        // acima. (Um id determinístico por competência colidiria com um evento já estornado e o
        // roteiro devolveria o lançamento antigo em vez de postar a nova baixa.)
        UUID eventoId = UUID.randomUUID();
        EventoContabilRequest evento = new EventoContabilRequest();
        evento.setEventoId(eventoId.toString());
        evento.setTipo("das.pago");
        evento.setOrigem(origem);
        evento.setDataEvento(dataPagamento);
        evento.setValorCentavos(req.getValorCentavos());
        EventoContabilRequest.Referencia ref = new EventoContabilRequest.Referencia();
        ref.setEntidade("pagamento_das");
        ref.setNumero(comp);
        evento.setReferencia(ref);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("contaLiquidacao", conta);
        evento.setContexto(ctx);

        EventoRecebidoResponse resp = eventoService.receber(evento);
        if (resp.getLancamentoId() == null) {
            throw new ModuloException(
                    "Não foi possível contabilizar o DAS (roteiro das.pago ausente ou período fechado).",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        PagamentoDas pagamento = new PagamentoDas();
        pagamento.setCompetencia(comp);
        pagamento.setValorCentavos(req.getValorCentavos());
        pagamento.setDataPagamento(dataPagamento);
        pagamento.setContaLiquidacao(conta);
        pagamento.setLancamentoId(resp.getLancamentoId());
        pagamento.setEventoId(eventoId);
        pagamento.setOrigem("erp".equalsIgnoreCase(origem) ? "erp" : "app");
        pagamento = pagamentoRepository.save(pagamento);

        log.info("DAS {} pago ({}): {} centavos, C {} — lançamento {}", comp, pagamento.getOrigem(),
                req.getValorCentavos(), conta, resp.getLancamentoId());
        return new PagamentoDasResponse(pagamento, nomeConta(conta));
    }

    /** Saldo credor (créditos − débitos) da 2.1.3.01 no período. */
    private long saldoCredorSimples(LocalDate de, LocalDate ate) {
        BalanceteResponse balancete = relatorioService.balancete(de, ate);
        for (BalanceteResponse.Linha linha : balancete.getLinhas()) {
            if (CONTA_SIMPLES.equals(linha.getCodigo())) {
                return linha.getCreditos() - linha.getDebitos();
            }
        }
        return 0L;
    }

    private String nomeConta(String codigo) {
        return contaRepository.findByCodigo(codigo).map(c -> c.getNome()).orElse(codigo);
    }

    private YearMonth parseCompetencia(String competencia) {
        try {
            return YearMonth.parse(competencia.trim());
        } catch (DateTimeParseException e) {
            throw new ModuloException("Competência inválida. Use o formato AAAA-MM.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
