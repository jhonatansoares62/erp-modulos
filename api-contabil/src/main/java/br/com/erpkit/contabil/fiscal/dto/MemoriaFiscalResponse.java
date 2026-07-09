package br.com.erpkit.contabil.fiscal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Memória de cálculo do Simples por competência (LC 123/2006, art. 18): RBT12 (12 meses anteriores),
 * Fator R, anexo efetivo, faixa, alíquota efetiva e imposto do mês — com a janela mês a mês (fonte
 * escriturado/informado) e os passos legíveis. Reusa os mesmos métodos da apuração (fonte da verdade).
 */
public class MemoriaFiscalResponse {

    private String competencia;                 // 'YYYY-MM'
    private String regime;
    private long rbt12Centavos;
    private boolean proporcionalizado;
    private int mesesAtividade;                  // nº de meses que compõem o RBT12
    private List<ItemJanela> janela;
    private long folha12mCentavos;
    private BigDecimal fatorR;                   // fração (folha ÷ RBT12)
    private BigDecimal fatorRLimite;             // 0.28
    private String anexoConfigurado;
    private String anexoEfetivo;
    private Integer faixa;
    private long rbt12FaixaDeCentavos;
    private long rbt12FaixaAteCentavos;
    private BigDecimal aliquotaNominal;          // %
    private long parcelaDeduzirCentavos;
    private BigDecimal aliquotaEfetiva;          // %
    private long baseCalculoCentavos;            // receita escriturada do próprio mês da competência
    private long impostoCentavos;                // base × alíquota efetiva
    private List<Passo> passos;

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public long getRbt12Centavos() { return rbt12Centavos; }
    public void setRbt12Centavos(long rbt12Centavos) { this.rbt12Centavos = rbt12Centavos; }
    public boolean isProporcionalizado() { return proporcionalizado; }
    public void setProporcionalizado(boolean proporcionalizado) { this.proporcionalizado = proporcionalizado; }
    public int getMesesAtividade() { return mesesAtividade; }
    public void setMesesAtividade(int mesesAtividade) { this.mesesAtividade = mesesAtividade; }
    public List<ItemJanela> getJanela() { return janela; }
    public void setJanela(List<ItemJanela> janela) { this.janela = janela; }
    public long getFolha12mCentavos() { return folha12mCentavos; }
    public void setFolha12mCentavos(long folha12mCentavos) { this.folha12mCentavos = folha12mCentavos; }
    public BigDecimal getFatorR() { return fatorR; }
    public void setFatorR(BigDecimal fatorR) { this.fatorR = fatorR; }
    public BigDecimal getFatorRLimite() { return fatorRLimite; }
    public void setFatorRLimite(BigDecimal fatorRLimite) { this.fatorRLimite = fatorRLimite; }
    public String getAnexoConfigurado() { return anexoConfigurado; }
    public void setAnexoConfigurado(String anexoConfigurado) { this.anexoConfigurado = anexoConfigurado; }
    public String getAnexoEfetivo() { return anexoEfetivo; }
    public void setAnexoEfetivo(String anexoEfetivo) { this.anexoEfetivo = anexoEfetivo; }
    public Integer getFaixa() { return faixa; }
    public void setFaixa(Integer faixa) { this.faixa = faixa; }
    public long getRbt12FaixaDeCentavos() { return rbt12FaixaDeCentavos; }
    public void setRbt12FaixaDeCentavos(long rbt12FaixaDeCentavos) { this.rbt12FaixaDeCentavos = rbt12FaixaDeCentavos; }
    public long getRbt12FaixaAteCentavos() { return rbt12FaixaAteCentavos; }
    public void setRbt12FaixaAteCentavos(long rbt12FaixaAteCentavos) { this.rbt12FaixaAteCentavos = rbt12FaixaAteCentavos; }
    public BigDecimal getAliquotaNominal() { return aliquotaNominal; }
    public void setAliquotaNominal(BigDecimal aliquotaNominal) { this.aliquotaNominal = aliquotaNominal; }
    public long getParcelaDeduzirCentavos() { return parcelaDeduzirCentavos; }
    public void setParcelaDeduzirCentavos(long parcelaDeduzirCentavos) { this.parcelaDeduzirCentavos = parcelaDeduzirCentavos; }
    public BigDecimal getAliquotaEfetiva() { return aliquotaEfetiva; }
    public void setAliquotaEfetiva(BigDecimal aliquotaEfetiva) { this.aliquotaEfetiva = aliquotaEfetiva; }
    public long getBaseCalculoCentavos() { return baseCalculoCentavos; }
    public void setBaseCalculoCentavos(long baseCalculoCentavos) { this.baseCalculoCentavos = baseCalculoCentavos; }
    public long getImpostoCentavos() { return impostoCentavos; }
    public void setImpostoCentavos(long impostoCentavos) { this.impostoCentavos = impostoCentavos; }
    public List<Passo> getPassos() { return passos; }
    public void setPassos(List<Passo> passos) { this.passos = passos; }

    /** Um mês da janela do RBT12: receita e fonte ("escriturado" do razão ou "informado" no histórico). */
    public static class ItemJanela {
        private String competencia;   // 'YYYY-MM'
        private long receitaCentavos;
        private String fonte;         // escriturado | informado

        public ItemJanela() {
        }

        public ItemJanela(String competencia, long receitaCentavos, String fonte) {
            this.competencia = competencia;
            this.receitaCentavos = receitaCentavos;
            this.fonte = fonte;
        }

        public String getCompetencia() { return competencia; }
        public void setCompetencia(String competencia) { this.competencia = competencia; }
        public long getReceitaCentavos() { return receitaCentavos; }
        public void setReceitaCentavos(long receitaCentavos) { this.receitaCentavos = receitaCentavos; }
        public String getFonte() { return fonte; }
        public void setFonte(String fonte) { this.fonte = fonte; }
    }

    /** Passo legível da memória de cálculo (título · fórmula · valor formatado). */
    public static class Passo {
        private int ordem;
        private String titulo;
        private String formula;
        private String valor;

        public Passo() {
        }

        public Passo(int ordem, String titulo, String formula, String valor) {
            this.ordem = ordem;
            this.titulo = titulo;
            this.formula = formula;
            this.valor = valor;
        }

        public int getOrdem() { return ordem; }
        public void setOrdem(int ordem) { this.ordem = ordem; }
        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        public String getValor() { return valor; }
        public void setValor(String valor) { this.valor = valor; }
    }
}
