package br.com.erpkit.contabil.fiscal.service;

import br.com.erpkit.contabil.fiscal.dto.ApuracaoFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.FiscalConfigDTO;
import br.com.erpkit.contabil.fiscal.dto.MemoriaFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.ReceitaHistoricaDTO;
import br.com.erpkit.contabil.fiscal.model.FaixaSimples;
import br.com.erpkit.contabil.fiscal.model.FiscalConfig;
import br.com.erpkit.contabil.fiscal.model.ReceitaHistorica;
import br.com.erpkit.contabil.fiscal.repository.FaixaSimplesRepository;
import br.com.erpkit.contabil.fiscal.repository.FiscalConfigRepository;
import br.com.erpkit.contabil.fiscal.repository.ReceitaHistoricaRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.service.ContaContabilService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pacote fiscal (Simples Nacional). O RBT12 é o dos 12 meses ANTERIORES à competência (LC 123/2006,
 * art. 18): empresa estabelecida usa a janela plena [comp-12 .. comp-1] (exclui o mês corrente);
 * início de atividade (<= 12 meses) proporcionaliza a receita escriturada × 12 ÷ meses. Empresa
 * migrando compõe a janela por híbrido: escriturado (>= corte) + histórico informado (< corte).
 * Fator R (folha/RBT12) escolhe III/V quando AUTO; alíquota efetiva = (RBT12 × nominal − parcela) ÷ RBT12.
 * Arredondamentos HALF_UP. Sem NF/SPED — só o parâmetro que alimenta o imposto.apurado.
 */
@Service
public class FiscalService {

    private static final String CONTA_RECEITA = "3.1.1.01";                  // receita bruta operacional
    private static final BigDecimal LIMITE_FATOR_R = new BigDecimal("0.28"); // >=28% → Anexo III
    private static final long TETO_SIMPLES_CENTAVOS = 480000000L;            // R$ 4.800.000,00

    /** Separadores pt-BR explícitos (não depende de locale data do JDK do container). */
    private static final DecimalFormatSymbols SIMBOLOS_PT_BR;
    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ROOT);
        s.setDecimalSeparator(',');
        s.setGroupingSeparator('.');
        SIMBOLOS_PT_BR = s;
    }

    private final FiscalConfigRepository configRepository;
    private final FaixaSimplesRepository faixaRepository;
    private final ReceitaHistoricaRepository receitaHistoricaRepository;
    private final ContaContabilService contaService;
    private final PartidaRepository partidaRepository;

    public FiscalService(FiscalConfigRepository configRepository, FaixaSimplesRepository faixaRepository,
                         ReceitaHistoricaRepository receitaHistoricaRepository,
                         ContaContabilService contaService, PartidaRepository partidaRepository) {
        this.configRepository = configRepository;
        this.faixaRepository = faixaRepository;
        this.receitaHistoricaRepository = receitaHistoricaRepository;
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
        c.setDataEntradaSistema(dto.getDataEntradaSistema());
        if (dto.getFolha12mCentavos() != null) c.setFolha12mCentavos(Math.max(0, dto.getFolha12mCentavos()));
        c.setAtualizadoEm(Instant.now());
        return configRepository.save(c);
    }

    // ── Receita histórica (parâmetro fiscal de empresa migrando) ──

    @Transactional(readOnly = true)
    public List<ReceitaHistoricaDTO> listarReceitaHistorica() {
        return receitaHistoricaRepository.findAllByOrderByCompetenciaAsc().stream()
                .map(h -> new ReceitaHistoricaDTO(h.getCompetencia(), h.getReceitaBrutaCentavos()))
                .toList();
    }

    /** REPLACE-ALL (upsert por competência): remove o que não veio no payload e atualiza/insere o resto
     *  (negativos viram 0; competência vazia é ignorada). Upsert em vez de delete-all+insert para não
     *  esbarrar no merge de entidade de ID atribuído com o contexto de persistência. */
    @Transactional
    public List<ReceitaHistoricaDTO> salvarReceitaHistorica(List<ReceitaHistoricaDTO> itens) {
        Map<String, Long> novos = new LinkedHashMap<>();
        if (itens != null) {
            for (ReceitaHistoricaDTO dto : itens) {
                if (dto.getCompetencia() == null || dto.getCompetencia().isBlank()) continue;
                novos.put(dto.getCompetencia().trim(), Math.max(0, dto.getReceitaBrutaCentavos()));
            }
        }
        for (ReceitaHistorica existente : receitaHistoricaRepository.findAll()) {
            if (!novos.containsKey(existente.getCompetencia())) {
                receitaHistoricaRepository.delete(existente);
            }
        }
        for (Map.Entry<String, Long> e : novos.entrySet()) {
            ReceitaHistorica rh = receitaHistoricaRepository.findById(e.getKey())
                    .orElseGet(() -> new ReceitaHistorica(e.getKey(), 0));
            rh.setReceitaBrutaCentavos(e.getValue());
            receitaHistoricaRepository.save(rh);
        }
        return listarReceitaHistorica();
    }

    // ── RBT12 (fonte da verdade) ──

    /** RBT12 (centavos) da competência: janela plena [comp-12..comp-1] da estabelecida, ou proporcionalizado no início. */
    @Transactional
    public long rbt12(YearMonth competencia) {
        return rbt12DaJanela(janela(competencia), mesesAtividade(competencia));
    }

    private long rbt12DaJanela(List<MemoriaFiscalResponse.ItemJanela> janela, int meses) {
        long soma = 0;
        for (MemoriaFiscalResponse.ItemJanela i : janela) soma += i.getReceitaCentavos();
        if (meses <= 12) {   // início de atividade: proporcionaliza receita × 12 ÷ meses (inclui o mês da competência)
            return BigDecimal.valueOf(soma).multiply(BigDecimal.valueOf(12))
                    .divide(BigDecimal.valueOf(Math.max(1, meses)), 0, RoundingMode.HALF_UP).longValueExact();
        }
        return soma;
    }

    /** Meses que compõem o RBT12, cada um com receita e fonte (escriturado do razão ou informado no histórico). */
    @Transactional
    public List<MemoriaFiscalResponse.ItemJanela> janela(YearMonth competencia) {
        FiscalConfig c = getConfig();
        int meses = mesesAtividade(competencia);
        List<MemoriaFiscalResponse.ItemJanela> itens = new ArrayList<>();
        if (meses <= 12) {
            // início de atividade: [inicio .. competencia] inclusive, só escriturado.
            YearMonth inicio = YearMonth.from(c.getDataInicioAtividade());
            for (YearMonth m = inicio; !m.isAfter(competencia); m = m.plusMonths(1)) {
                itens.add(new MemoriaFiscalResponse.ItemJanela(m.toString(), escrituradoMes(m), "escriturado"));
            }
        } else {
            // estabelecida: [competencia-12 .. competencia-1], híbrido pelo corte de migração.
            YearMonth corte = c.getDataEntradaSistema() != null ? YearMonth.from(c.getDataEntradaSistema()) : null;
            for (YearMonth m = competencia.minusMonths(12); m.isBefore(competencia); m = m.plusMonths(1)) {
                if (corte != null && m.isBefore(corte)) {
                    itens.add(new MemoriaFiscalResponse.ItemJanela(m.toString(), historicoMes(m), "informado"));
                } else {
                    itens.add(new MemoriaFiscalResponse.ItemJanela(m.toString(), escrituradoMes(m), "escriturado"));
                }
            }
        }
        return itens;
    }

    /** Nº de meses de atividade até a competência (mínimo 1). Sem data de início = estabelecida (janela plena). */
    private int mesesAtividade(YearMonth competencia) {
        LocalDate inicio = getConfig().getDataInicioAtividade();
        if (inicio == null) return 13;   // estabelecida
        return Math.max(1, (int) ChronoUnit.MONTHS.between(YearMonth.from(inicio), competencia) + 1);
    }

    /** Σ receita escriturada (conta 3.1.1.01, crédito − débito) do 1º dia de mesDe ao último de mesAte. */
    @Transactional
    public long somaReceitaEscriturada(YearMonth mesDe, YearMonth mesAte) {
        return receitaBruta(mesDe.atDay(1), mesAte.atEndOfMonth());
    }

    private long escrituradoMes(YearMonth m) {
        return somaReceitaEscriturada(m, m);
    }

    private long historicoMes(YearMonth m) {
        return receitaHistoricaRepository.findById(m.toString())
                .map(ReceitaHistorica::getReceitaBrutaCentavos).orElse(0L);
    }

    // ── Apuração ──

    /** Apuração do mês corrente (endpoint sem parâmetro). */
    @Transactional
    public ApuracaoFiscalResponse apurar() {
        return apurar(YearMonth.now());
    }

    @Transactional
    public ApuracaoFiscalResponse apurar(YearMonth competencia) {
        FiscalConfig c = getConfig();
        int meses = mesesAtividade(competencia);
        long rbt12 = rbt12(competencia);
        long folha = c.getFolha12mCentavos();
        BigDecimal fatorR = fatorR(folha, rbt12);
        String anexoEfetivo = anexoEfetivo(c, fatorR);

        ApuracaoFiscalResponse r = new ApuracaoFiscalResponse();
        r.setAutomatico(c.isCalculoAutomatico());
        r.setRegime(c.getRegime());
        r.setAnexoConfigurado(c.getAnexo());
        r.setDataInicioAtividade(c.getDataInicioAtividade());
        r.setRbt12Centavos(rbt12);
        r.setFolha12mCentavos(folha);
        r.setFatorR(fatorR);
        r.setAnexoEfetivo(anexoEfetivo);
        r.setMesesAtividade(Math.min(meses, 12));
        r.setProporcionalizado(meses <= 12);
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

    // ── Memória de cálculo ──

    @Transactional
    public MemoriaFiscalResponse memoria(YearMonth competencia) {
        FiscalConfig c = getConfig();
        List<MemoriaFiscalResponse.ItemJanela> janela = janela(competencia);
        int meses = mesesAtividade(competencia);
        boolean proporcionalizado = meses <= 12;
        long rbt12 = rbt12DaJanela(janela, meses);
        long folha = c.getFolha12mCentavos();
        BigDecimal fatorR = fatorR(folha, rbt12);
        String anexoEfetivo = anexoEfetivo(c, fatorR);
        long base = escrituradoMes(competencia);   // base do imposto do mês = receita escriturada da competência

        MemoriaFiscalResponse r = new MemoriaFiscalResponse();
        r.setCompetencia(competencia.toString());
        r.setRegime(c.getRegime());
        r.setRbt12Centavos(rbt12);
        r.setProporcionalizado(proporcionalizado);
        r.setMesesAtividade(janela.size());
        r.setJanela(janela);
        r.setFolha12mCentavos(folha);
        r.setFatorR(fatorR);
        r.setFatorRLimite(LIMITE_FATOR_R);
        r.setAnexoConfigurado(c.getAnexo());
        r.setAnexoEfetivo(anexoEfetivo);
        r.setBaseCalculoCentavos(base);

        List<MemoriaFiscalResponse.Passo> passos = new ArrayList<>();
        passos.add(passoRbt12(1, proporcionalizado, meses, janela, rbt12));
        passos.add(new MemoriaFiscalResponse.Passo(2, "Fator R",
                "Folha 12m ÷ RBT12 = " + brl(folha) + " ÷ " + brl(rbt12), pct(fatorR.multiply(BigDecimal.valueOf(100)))));
        passos.add(passoAnexo(3, c, fatorR, anexoEfetivo));

        FaixaSimples faixa = faixaDe(anexoEfetivo, rbt12);
        if (faixa == null || rbt12 <= 0) {
            r.setFaixa(null);
            r.setAliquotaNominal(BigDecimal.ZERO);
            r.setParcelaDeduzirCentavos(0);
            r.setAliquotaEfetiva(BigDecimal.ZERO);
            r.setImpostoCentavos(0);
            r.setPassos(passos);
            return r;
        }

        BigDecimal aliqEfetiva = aliquotaEfetiva(rbt12, faixa);
        long imposto = BigDecimal.valueOf(base).multiply(aliqEfetiva)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        long faixaDeCentavos = faixaDeCentavos(anexoEfetivo, faixa);

        r.setFaixa(faixa.getFaixa());
        r.setRbt12FaixaDeCentavos(faixaDeCentavos);
        r.setRbt12FaixaAteCentavos(faixa.getRbt12AteCentavos());
        r.setAliquotaNominal(faixa.getAliquotaNominal());
        r.setParcelaDeduzirCentavos(faixa.getParcelaDeduzirCentavos());
        r.setAliquotaEfetiva(aliqEfetiva);
        r.setImpostoCentavos(imposto);

        passos.add(new MemoriaFiscalResponse.Passo(4, "Faixa",
                "RBT12 ∈ (" + brl(faixaDeCentavos) + " .. " + brl(faixa.getRbt12AteCentavos()) + "]",
                "Faixa " + faixa.getFaixa()));
        passos.add(new MemoriaFiscalResponse.Passo(5, "Alíquota efetiva",
                "(RBT12 × nominal − parcela) ÷ RBT12", pct(aliqEfetiva)));
        passos.add(new MemoriaFiscalResponse.Passo(6, "Imposto da competência",
                "Base " + brl(base) + " × " + pct(aliqEfetiva), brl(imposto)));
        r.setPassos(passos);
        return r;
    }

    private MemoriaFiscalResponse.Passo passoRbt12(int ordem, boolean proporcionalizado, int meses,
                                                   List<MemoriaFiscalResponse.ItemJanela> janela, long rbt12) {
        String range = janela.isEmpty() ? "—"
                : janela.get(0).getCompetencia() + ".." + janela.get(janela.size() - 1).getCompetencia();
        String titulo;
        String formula;
        if (proporcionalizado) {
            titulo = "RBT12 (proporcionalizado, " + meses + "m)";
            formula = "Σ receita " + range + " × 12 ÷ " + meses;
        } else {
            long informados = janela.stream().filter(i -> "informado".equals(i.getFonte())).count();
            titulo = "RBT12 (12 meses anteriores)";
            formula = "Σ receita bruta " + range;
            if (informados > 0) {
                formula += " (" + informados + " informados + " + (janela.size() - informados) + " escriturados)";
            }
        }
        return new MemoriaFiscalResponse.Passo(ordem, titulo, formula, brl(rbt12));
    }

    private MemoriaFiscalResponse.Passo passoAnexo(int ordem, FiscalConfig c, BigDecimal fatorR, String anexoEfetivo) {
        String formula;
        if ("AUTO".equalsIgnoreCase(c.getAnexo())) {
            String cmp = fatorR.compareTo(LIMITE_FATOR_R) >= 0 ? "≥" : "<";
            formula = "Fator R " + pct(fatorR.multiply(BigDecimal.valueOf(100))) + " " + cmp + " 28% → Anexo " + anexoEfetivo;
        } else {
            formula = "Anexo fixo configurado";
        }
        return new MemoriaFiscalResponse.Passo(ordem, "Anexo efetivo", formula, anexoEfetivo);
    }

    // ── Cálculos base (fonte da verdade compartilhada) ──

    private BigDecimal fatorR(long folha, long rbt12) {
        return rbt12 > 0
                ? BigDecimal.valueOf(folha).divide(BigDecimal.valueOf(rbt12), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private String anexoEfetivo(FiscalConfig c, BigDecimal fatorR) {
        return "AUTO".equalsIgnoreCase(c.getAnexo())
                ? (fatorR.compareTo(LIMITE_FATOR_R) >= 0 ? "III" : "V")
                : c.getAnexo();
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

    /** Piso de RBT12 da faixa: teto da faixa anterior + 1 (0 na faixa 1). */
    private long faixaDeCentavos(String anexo, FaixaSimples faixa) {
        long anteriorAte = 0;
        for (FaixaSimples f : faixaRepository.findByAnexoOrderByFaixaAsc(anexo)) {
            if (f.getFaixa() >= faixa.getFaixa()) break;
            anteriorAte = f.getRbt12AteCentavos();
        }
        return faixa.getFaixa() <= 1 ? 0 : anteriorAte + 1;
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

    private static String brl(long centavos) {
        return "R$ " + new DecimalFormat("#,##0.00", SIMBOLOS_PT_BR)
                .format(BigDecimal.valueOf(centavos).movePointLeft(2));
    }

    private static String pct(BigDecimal percent) {
        return new DecimalFormat("#,##0.00", SIMBOLOS_PT_BR).format(percent) + "%";
    }
}
