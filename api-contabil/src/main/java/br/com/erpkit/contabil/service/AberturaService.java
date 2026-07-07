package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.AberturaInformadaRequest;
import br.com.erpkit.contabil.dto.AberturaResponse;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.model.AberturaInformada;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.repository.AberturaInformadaRepository;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abertura de saldos. Dois modos, com precedência do informado:
 * <ol>
 *   <li><b>Informado</b> (onboarding real): o usuário informa os saldos iniciais (Caixa, Bancos,
 *       Capital, ...) numa data de abertura. Posta D ativos / C Capital, balanceando o residual
 *       em Lucros/Prejuízos Acumulados. origem_documento 'abertura-informada'.</li>
 *   <li><b>Auto-aporte</b> (fallback): quando NADA é informado, infere um Capital Social que zera
 *       as contas de liquidez negativas (pagamentos &gt; recebimentos sem saldo inicial).
 *       origem_documento 'abertura-capital'.</li>
 * </ol>
 * Havendo saldos informados, o auto-aporte é estornado e não roda. Idempotente: reaplicar
 * (backfill) recomputa e estorna-e-refaz só quando muda.
 */
@Service
public class AberturaService {

    private static final String DOC = "abertura-capital";
    private static final String DOC_INFORMADA = "abertura-informada";
    private static final String CONTA_CAPITAL = "2.3.1.01";
    private static final String CONTA_LUCROS = "2.3.3.01";       // credora — sobra de ativo vira lucro acumulado
    private static final String CONTA_PREJUIZOS = "2.3.3.02";    // devedora — capital acima do ativo vira prejuízo
    private static final List<String> CONTAS_LIQUIDEZ = List.of("1.1.1.01", "1.1.1.02", "1.1.2.02");
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

    private final ContaContabilService contaService;
    private final PartidaRepository partidaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentoService;
    private final AberturaInformadaRepository aberturaInformadaRepository;
    private final EntityManager entityManager;

    public AberturaService(ContaContabilService contaService, PartidaRepository partidaRepository,
                           LancamentoRepository lancamentoRepository, LancamentoService lancamentoService,
                           AberturaInformadaRepository aberturaInformadaRepository, EntityManager entityManager) {
        this.contaService = contaService;
        this.partidaRepository = partidaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentoService = lancamentoService;
        this.aberturaInformadaRepository = aberturaInformadaRepository;
        this.entityManager = entityManager;
    }

    // === Saldos informados (onboarding) ===

    /** Estado atual dos saldos informados, para a tela. informada=false quando não há nenhum. */
    @Transactional(readOnly = true)
    public AberturaResponse consultar() {
        List<AberturaInformada> informados = aberturaInformadaRepository.findAll();
        if (informados.isEmpty()) {
            return new AberturaResponse(false, null, List.of());
        }
        List<AberturaResponse.Saldo> saldos = new ArrayList<>();
        for (AberturaInformada a : informados) {
            ContaContabil conta = contaService.buscarPorCodigo(a.getContaCodigo());
            saldos.add(new AberturaResponse.Saldo(conta.getCodigo(), conta.getNome(), a.getSaldoCentavos()));
        }
        return new AberturaResponse(true, informados.get(0).getDataAbertura(), saldos);
    }

    /** Grava (substitui) os saldos informados e reaplica a abertura real. */
    @Transactional
    public AberturaResponse informar(AberturaInformadaRequest req) {
        if (req.getDataAbertura() == null) {
            throw new ModuloException("Data de abertura é obrigatória", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (req.getSaldos() == null || req.getSaldos().isEmpty()) {
            throw new ModuloException("Informe ao menos um saldo de abertura", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        aberturaInformadaRepository.deleteAllInBatch();
        aberturaInformadaRepository.flush();
        for (AberturaInformadaRequest.Saldo s : req.getSaldos()) {
            if (s.getValorCentavos() < 0) {
                throw new ModuloException("Saldo de abertura não pode ser negativo: " + s.getCodigo(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (s.getValorCentavos() == 0) {
                continue;   // zero não posta nem guarda
            }
            contaService.buscarPorCodigo(s.getCodigo());   // valida existência
            AberturaInformada a = new AberturaInformada();
            a.setDataAbertura(req.getDataAbertura());
            a.setContaCodigo(s.getCodigo());
            a.setSaldoCentavos(s.getValorCentavos());
            a.setInformadoPor(req.getInformadoPor());
            aberturaInformadaRepository.save(a);
        }
        aberturaInformadaRepository.flush();
        reaplicar();
        return consultar();
    }

    // === Reaplicação (backfill/idempotente) ===

    @Transactional
    public int reaplicar() {
        List<AberturaInformada> informados = aberturaInformadaRepository.findAll();
        if (!informados.isEmpty()) {
            return reaplicarInformada(informados);
        }
        return reaplicarAutoAporte();
    }

    /**
     * Abertura informada: D ativos (natureza) / C Capital (natureza), com o residual balanceado
     * em Lucros (crédito) ou Prejuízos (débito). Estorna o auto-aporte, se existir. No-op quando
     * o lançamento informado vigente já bate com o conjunto atual.
     */
    private int reaplicarInformada(List<AberturaInformada> informados) {
        // O informado tem precedência: o auto-aporte de capital é estornado e não roda.
        estornarVigente(DOC);

        LocalDate dataAbertura = informados.get(0).getDataAbertura();
        List<PartidaSpec> specs = new ArrayList<>();
        long totalDebito = 0;
        long totalCredito = 0;
        for (AberturaInformada a : informados) {
            if (a.getSaldoCentavos() <= 0) continue;
            ContaContabil conta = contaService.buscarPorCodigo(a.getContaCodigo());
            String tipo = conta.getNatureza();   // ativo=D, PL/passivo=C
            specs.add(new PartidaSpec(conta.getId(), tipo, a.getSaldoCentavos()));
            if ("D".equals(tipo)) totalDebito += a.getSaldoCentavos(); else totalCredito += a.getSaldoCentavos();
        }

        // Balanceia o residual: sobra de ativo -> Lucros Acumulados (C); capital acima do
        // ativo -> (-) Prejuízos Acumulados (D). Assim débitos = créditos com qualquer entrada.
        long residual = totalDebito - totalCredito;
        if (residual > 0) {
            specs.add(new PartidaSpec(contaService.buscarPorCodigo(CONTA_LUCROS).getId(), "C", residual));
        } else if (residual < 0) {
            specs.add(new PartidaSpec(contaService.buscarPorCodigo(CONTA_PREJUIZOS).getId(), "D", -residual));
        }

        Lancamento anterior = lancamentoRepository.findByOrigemDocumentoAndStatus(DOC_INFORMADA, "lancado")
                .stream().findFirst().orElse(null);
        if (anterior != null
                && anterior.getDataCompetencia().equals(dataAbertura)
                && lancamentoService.partidasConferem(anterior.getId(), specs)) {
            return 0;   // nada mudou
        }
        if (anterior != null) {
            lancamentoService.estornar(anterior.getId(), "Reaplicação dos saldos de abertura informados");
            entityManager.flush();
        }
        if (specs.isEmpty()) {
            return 0;
        }
        lancamentoService.postar(dataAbertura, "Saldos de abertura informados", specs, DOC_INFORMADA);
        return 1;
    }

    /** Auto-aporte (fallback): infere Capital que zera contas de liquidez negativas. */
    private int reaplicarAutoAporte() {
        Long capitalId = contaService.buscarPorCodigo(CONTA_CAPITAL).getId();

        Lancamento anterior = lancamentoRepository.findByOrigemDocumentoAndStatus(DOC, "lancado")
                .stream().findFirst().orElse(null);
        Map<Long, Long> aporteAnterior = new HashMap<>();
        if (anterior != null) {
            for (Partida p : partidaRepository.findByLancamentoId(anterior.getId())) {
                if ("D".equals(p.getTipo())) {
                    aporteAnterior.merge(p.getContaId(), p.getValorCentavos(), Long::sum);
                }
            }
        }

        List<PartidaSpec> specs = new ArrayList<>();
        long total = 0;
        for (String codigo : CONTAS_LIQUIDEZ) {
            Long contaId = contaService.buscarPorCodigo(codigo).getId();
            long saldoSemAporte = saldoDevedor(contaId) - aporteAnterior.getOrDefault(contaId, 0L);
            if (saldoSemAporte < 0) {
                long deficit = -saldoSemAporte;
                specs.add(new PartidaSpec(contaId, "D", deficit));
                total += deficit;
            }
        }
        if (total > 0) {
            specs.add(new PartidaSpec(capitalId, "C", total));
        }

        if (anterior != null && lancamentoService.partidasConferem(anterior.getId(), specs)) {
            return 0;
        }
        if (anterior != null) {
            lancamentoService.estornar(anterior.getId(), "Reaplicação da abertura de capital");
            entityManager.flush();
        }
        if (total <= 0) {
            return 0;
        }
        lancamentoService.postar(LocalDate.now().withDayOfYear(1),
                "Aporte de capital (abertura de saldos)", specs, DOC);
        return 1;
    }

    /** Estorna o lançamento vigente (lancado) de um origem_documento, se houver. */
    private void estornarVigente(String origemDocumento) {
        Lancamento vigente = lancamentoRepository.findByOrigemDocumentoAndStatus(origemDocumento, "lancado")
                .stream().findFirst().orElse(null);
        if (vigente != null) {
            lancamentoService.estornar(vigente.getId(), "Substituído por saldos de abertura informados");
            entityManager.flush();
        }
    }

    /** Saldo devedor (D − C) acumulado da conta (toda a série). */
    private long saldoDevedor(Long contaId) {
        List<Object[]> r = partidaRepository.somarConta(contaId, MIN, MAX);
        if (r.isEmpty()) return 0;
        return num(r.get(0)[0]) - num(r.get(0)[1]);
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
