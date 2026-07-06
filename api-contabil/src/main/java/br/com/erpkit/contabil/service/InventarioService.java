package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.ApurarInventarioRequest;
import br.com.erpkit.contabil.dto.InventarioApuracaoResponse;
import br.com.erpkit.contabil.dto.InventarioBaseResponse;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.model.InventarioApuracao;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.repository.InventarioApuracaoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Apuração de CMV por inventário periódico. CMV = EI + Compras − EF, onde EI = saldo do
 * estoque no início do período e Compras = entradas do período (compra.recebida). Posta
 * D 3.2.1.01 CMV / C 1.1.3.01 Estoque, deixando o estoque no valor contado (EF).
 * Idempotente por período: reapurar desativa a apuração anterior e estorna seu lançamento.
 */
@Service
public class InventarioService {

    private static final String CONTA_ESTOQUE = "1.1.3.01";
    private static final String CONTA_CMV = "3.2.1.01";
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);

    private final ContaContabilService contaService;
    private final PartidaRepository partidaRepository;
    private final LancamentoService lancamentoService;
    private final PeriodoService periodoService;
    private final InventarioApuracaoRepository apuracaoRepository;
    private final EntityManager entityManager;

    public InventarioService(ContaContabilService contaService,
                             PartidaRepository partidaRepository,
                             LancamentoService lancamentoService,
                             PeriodoService periodoService,
                             InventarioApuracaoRepository apuracaoRepository,
                             EntityManager entityManager) {
        this.contaService = contaService;
        this.partidaRepository = partidaRepository;
        this.lancamentoService = lancamentoService;
        this.periodoService = periodoService;
        this.apuracaoRepository = apuracaoRepository;
        this.entityManager = entityManager;
    }

    /** Base informativa do período: EI, Compras (entradas) e Estoque disponível (EI + Compras). */
    public InventarioBaseResponse base(LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        Long estoqueId = contaService.buscarPorCodigo(CONTA_ESTOQUE).getId();
        long ei = saldoNet(estoqueId, MIN, de.minusDays(1));
        long compras = debito(estoqueId, de, ate);
        return new InventarioBaseResponse(de, ate, ei, compras, ei + compras);
    }

    @Transactional
    public InventarioApuracaoResponse apurar(ApurarInventarioRequest req) {
        LocalDate de = req.getDe();
        LocalDate ate = req.getAte();
        validarPeriodo(de, ate);
        long estoqueFinal = req.getEstoqueFinalCentavos();
        if (estoqueFinal < 0) {
            throw new ModuloException("Estoque final não pode ser negativo", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        periodoService.validarPeriodoAberto(ate);

        Long estoqueId = contaService.buscarPorCodigo(CONTA_ESTOQUE).getId();
        Long cmvId = contaService.buscarPorCodigo(CONTA_CMV).getId();

        // Idempotência: reapurar o período estorna e desativa a apuração anterior.
        apuracaoRepository.findByPeriodoDeAndPeriodoAteAndAtivoTrue(de, ate).ifPresent(anterior -> {
            if (anterior.getLancamentoId() != null) {
                lancamentoService.estornar(anterior.getLancamentoId(), "Reapuração de CMV do período");
            }
            anterior.setAtivo(false);
            apuracaoRepository.save(anterior);
            entityManager.flush();   // torna o estorno visível às somas por conta abaixo
        });

        long ei = saldoNet(estoqueId, MIN, de.minusDays(1));
        long disponivel = saldoNet(estoqueId, MIN, ate);   // = EI + Compras (estorno anterior já cancelado)
        long compras = disponivel - ei;

        if (estoqueFinal > disponivel) {
            throw new ModuloException(String.format(
                    "Estoque final contado (%d) maior que o disponível EI+Compras (%d) — CMV ficaria negativo",
                    estoqueFinal, disponivel), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        long cmv = disponivel - estoqueFinal;
        Long lancamentoId = null;
        if (cmv > 0) {
            Lancamento lanc = lancamentoService.postar(ate,
                    "Apuracao de CMV do periodo " + de + " a " + ate,
                    List.of(new PartidaSpec(cmvId, "D", cmv), new PartidaSpec(estoqueId, "C", cmv)));
            lancamentoId = lanc.getId();
        }

        InventarioApuracao apuracao = new InventarioApuracao();
        apuracao.setPeriodoDe(de);
        apuracao.setPeriodoAte(ate);
        apuracao.setEstoqueInicialCentavos(ei);
        apuracao.setComprasCentavos(compras);
        apuracao.setEstoqueFinalCentavos(estoqueFinal);
        apuracao.setCmvCentavos(cmv);
        apuracao.setLancamentoId(lancamentoId);
        apuracao.setApuradoPor(req.getApuradoPor());
        apuracao.setAtivo(true);
        return InventarioApuracaoResponse.de(apuracaoRepository.save(apuracao));
    }

    public List<InventarioApuracaoResponse> listar() {
        return apuracaoRepository.findAllByOrderByApuradoEmDesc().stream()
                .map(InventarioApuracaoResponse::de).toList();
    }

    private void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de)) {
            throw new ModuloException("Período inválido: 'de' deve ser menor ou igual a 'ate'",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Saldo devedor (débito − crédito) de uma conta na janela, a partir das partidas não-rascunho. */
    private long saldoNet(Long contaId, LocalDate de, LocalDate ate) {
        for (Object[] row : partidaRepository.somarPorConta(de, ate)) {
            if (num(row[0]) == contaId) {
                return num(row[1]) - num(row[2]);
            }
        }
        return 0L;
    }

    /** Soma dos débitos de uma conta na janela (entradas de estoque = compras). */
    private long debito(Long contaId, LocalDate de, LocalDate ate) {
        for (Object[] row : partidaRepository.somarPorConta(de, ate)) {
            if (num(row[0]) == contaId) {
                return num(row[1]);
            }
        }
        return 0L;
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
