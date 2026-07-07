package br.com.erpkit.contabil.fiscal.service;

import br.com.erpkit.contabil.fiscal.dto.ApuracaoFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.FiscalConfigDTO;
import br.com.erpkit.contabil.fiscal.model.FaixaSimples;
import br.com.erpkit.contabil.fiscal.model.FiscalConfig;
import br.com.erpkit.contabil.fiscal.repository.FaixaSimplesRepository;
import br.com.erpkit.contabil.fiscal.repository.FiscalConfigRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.service.ContaContabilService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pacote fiscal (Simples Nacional). Lê a receita do próprio razão para apurar RBT12, aplica o
 * Fator R (folha/RBT12) para escolher o anexo III/V quando configurado como automático, e calcula
 * a alíquota efetiva pela faixa: (RBT12 × nominal − parcela) ÷ RBT12. Arredondamentos HALF_UP.
 * Sem emissão de NF/SPED — só o parâmetro que alimenta o imposto.apurado.
 */
@Service
public class FiscalService {

    private static final String CONTA_RECEITA = "3.1.1.01";                  // receita bruta operacional
    private static final BigDecimal LIMITE_FATOR_R = new BigDecimal("0.28"); // >=28% → Anexo III
    private static final long TETO_SIMPLES_CENTAVOS = 480000000L;            // R$ 4.800.000,00

    private final FiscalConfigRepository configRepository;
    private final FaixaSimplesRepository faixaRepository;
    private final ContaContabilService contaService;
    private final PartidaRepository partidaRepository;

    public FiscalService(FiscalConfigRepository configRepository, FaixaSimplesRepository faixaRepository,
                         ContaContabilService contaService, PartidaRepository partidaRepository) {
        this.configRepository = configRepository;
        this.faixaRepository = faixaRepository;
        this.contaService = contaService;
        this.partidaRepository = partidaRepository;
    }

    @Transactional
    public FiscalConfig getConfig() {
        return configRepository.findById(1).orElseGet(() -> configRepository.save(new FiscalConfig()));
    }

    @Transactional
    public FiscalConfig salvar(FiscalConfigDTO dto) {
        FiscalConfig c = getConfig();
        if (dto.getRegime() != null) c.setRegime(dto.getRegime());
        if (dto.getCalculoAutomatico() != null) c.setCalculoAutomatico(dto.getCalculoAutomatico());
        if (dto.getAnexo() != null) c.setAnexo(dto.getAnexo());
        c.setDataInicioAtividade(dto.getDataInicioAtividade());
        if (dto.getFolha12mCentavos() != null) c.setFolha12mCentavos(Math.max(0, dto.getFolha12mCentavos()));
        c.setAtualizadoEm(Instant.now());
        return configRepository.save(c);
    }

    @Transactional
    public ApuracaoFiscalResponse apurar() {
        FiscalConfig c = getConfig();
        LocalDate ref = LocalDate.now();

        // Meses de atividade e RBT12 (proporcionaliza se < 12 meses de atividade).
        int meses = 12;
        boolean proporcionalizado = false;
        long rbt12;
        if (c.getDataInicioAtividade() != null) {
            int m = (int) ChronoUnit.MONTHS.between(YearMonth.from(c.getDataInicioAtividade()), YearMonth.from(ref)) + 1;
            meses = Math.max(1, Math.min(12, m));
            if (meses < 12) {
                long receita = receitaBruta(c.getDataInicioAtividade(), ref);
                rbt12 = BigDecimal.valueOf(receita)
                        .multiply(BigDecimal.valueOf(12))
                        .divide(BigDecimal.valueOf(meses), 0, RoundingMode.HALF_UP)
                        .longValueExact();
                proporcionalizado = true;
            } else {
                rbt12 = receitaBruta(ref.minusMonths(12), ref);
            }
        } else {
            rbt12 = receitaBruta(ref.minusMonths(12), ref);
        }

        long folha = c.getFolha12mCentavos();
        BigDecimal fatorR = rbt12 > 0
                ? BigDecimal.valueOf(folha).divide(BigDecimal.valueOf(rbt12), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String anexoEfetivo = "AUTO".equalsIgnoreCase(c.getAnexo())
                ? (fatorR.compareTo(LIMITE_FATOR_R) >= 0 ? "III" : "V")
                : c.getAnexo();

        ApuracaoFiscalResponse r = new ApuracaoFiscalResponse();
        r.setAutomatico(c.isCalculoAutomatico());
        r.setRegime(c.getRegime());
        r.setAnexoConfigurado(c.getAnexo());
        r.setDataInicioAtividade(c.getDataInicioAtividade());
        r.setRbt12Centavos(rbt12);
        r.setFolha12mCentavos(folha);
        r.setFatorR(fatorR);
        r.setAnexoEfetivo(anexoEfetivo);
        r.setMesesAtividade(meses);
        r.setProporcionalizado(proporcionalizado);
        r.setExcedeSimples(rbt12 > TETO_SIMPLES_CENTAVOS);

        FaixaSimples faixa = faixaDe(anexoEfetivo, rbt12);
        if (faixa == null || rbt12 <= 0) {
            r.setAliquotaNominal(BigDecimal.ZERO);
            r.setParcelaDeduzirCentavos(0);
            r.setAliquotaEfetiva(BigDecimal.ZERO);
            return r;
        }
        r.setFaixa(faixa.getFaixa());
        r.setAliquotaNominal(faixa.getAliquotaNominal());
        r.setParcelaDeduzirCentavos(faixa.getParcelaDeduzirCentavos());
        r.setAliquotaEfetiva(aliquotaEfetiva(rbt12, faixa));
        return r;
    }

    /** Alíquota efetiva (%) = (RBT12 × nominal% − parcela) ÷ RBT12 × 100, HALF_UP em 4 casas. */
    public BigDecimal aliquotaEfetiva(long rbt12Centavos, FaixaSimples faixa) {
        if (rbt12Centavos <= 0) return BigDecimal.ZERO;
        BigDecimal rbt12 = BigDecimal.valueOf(rbt12Centavos);
        BigDecimal impostoDevido = rbt12.multiply(faixa.getAliquotaNominal())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .subtract(BigDecimal.valueOf(faixa.getParcelaDeduzirCentavos()));
        if (impostoDevido.signum() < 0) impostoDevido = BigDecimal.ZERO;
        return impostoDevido.multiply(BigDecimal.valueOf(100))
                .divide(rbt12, 4, RoundingMode.HALF_UP);
    }

    /** Menor faixa cujo teto de RBT12 comporta o valor; a última se exceder o teto. */
    private FaixaSimples faixaDe(String anexo, long rbt12) {
        List<FaixaSimples> faixas = faixaRepository.findByAnexoOrderByFaixaAsc(anexo);
        if (faixas.isEmpty()) return null;
        for (FaixaSimples f : faixas) {
            if (rbt12 <= f.getRbt12AteCentavos()) return f;
        }
        return faixas.get(faixas.size() - 1);
    }

    /** Receita bruta (crédito − débito) da conta operacional no período. */
    private long receitaBruta(LocalDate de, LocalDate ate) {
        Long contaId = contaService.buscarPorCodigo(CONTA_RECEITA).getId();
        List<Object[]> r = partidaRepository.somarConta(contaId, de, ate);
        if (r.isEmpty()) return 0;
        long debito = ((Number) r.get(0)[0]).longValue();
        long credito = ((Number) r.get(0)[1]).longValue();
        return credito - debito;
    }
}
