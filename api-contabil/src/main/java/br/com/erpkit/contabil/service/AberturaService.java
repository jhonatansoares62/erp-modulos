package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aporte de capital / abertura de saldos: quando uma conta de liquidez (Caixa/Bancos/Cartões)
 * fica com saldo credor (Ativo negativo — impossível), significa que os pagamentos superaram
 * os recebimentos sem um saldo inicial. Posta D <conta negativa> / C 2.3.1.01 Capital Social
 * para trazer cada uma a zero, representando o capital inicial que sustentou a operação.
 * Idempotente: reaplicar recomputa e estorna-e-refaz só quando o aporte necessário muda.
 */
@Service
public class AberturaService {

    private static final String DOC = "abertura-capital";
    private static final String CONTA_CAPITAL = "2.3.1.01";
    private static final List<String> CONTAS_LIQUIDEZ = List.of("1.1.1.01", "1.1.1.02", "1.1.2.02");
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

    private final ContaContabilService contaService;
    private final PartidaRepository partidaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentoService;
    private final EntityManager entityManager;

    public AberturaService(ContaContabilService contaService, PartidaRepository partidaRepository,
                           LancamentoRepository lancamentoRepository, LancamentoService lancamentoService,
                           EntityManager entityManager) {
        this.contaService = contaService;
        this.partidaRepository = partidaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentoService = lancamentoService;
        this.entityManager = entityManager;
    }

    @Transactional
    public int reaplicar() {
        Long capitalId = contaService.buscarPorCodigo(CONTA_CAPITAL).getId();

        // Aporte anterior (para excluir do cálculo e reaplicar só se mudou).
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

        // Déficit de cada conta de liquidez, DESCONTANDO o aporte anterior (posição sem abertura).
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

        // No-op se o aporte vigente já bate exatamente com o necessário.
        if (anterior != null && lancamentoService.partidasConferem(anterior.getId(), specs)) {
            return 0;
        }
        if (anterior != null) {
            lancamentoService.estornar(anterior.getId(), "Reaplicação da abertura de capital");
            entityManager.flush();
        }
        if (total <= 0) {
            return 0;   // nenhuma conta negativa — sem aporte
        }
        lancamentoService.postar(LocalDate.now().withDayOfYear(1),
                "Aporte de capital (abertura de saldos)", specs, DOC);
        return 1;
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
