package br.com.erpkit.contabil.fiscal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Apuração fiscal do Simples: RBT12 (dos 12 meses do razão), Fator R, anexo efetivo, faixa e
 * alíquota efetiva. Também ecoa a config vigente para a tela.
 */
public class ApuracaoFiscalResponse {

    // Config
    private boolean automatico;
    private String regime;
    private String anexoConfigurado;
    private LocalDate dataInicioAtividade;

    // Apuração
    private long rbt12Centavos;
    private long folha12mCentavos;
    private BigDecimal fatorR;              // folha / RBT12 (fração, ex.: 0.2920)
    private String anexoEfetivo;            // III/V (ou o configurado)
    private Integer faixa;
    private BigDecimal aliquotaNominal;     // %
    private long parcelaDeduzirCentavos;
    private BigDecimal aliquotaEfetiva;     // %
    private int mesesAtividade;
    private boolean proporcionalizado;
    private boolean excedeSimples;

    public boolean isAutomatico() { return automatico; }
    public void setAutomatico(boolean automatico) { this.automatico = automatico; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public String getAnexoConfigurado() { return anexoConfigurado; }
    public void setAnexoConfigurado(String anexoConfigurado) { this.anexoConfigurado = anexoConfigurado; }
    public LocalDate getDataInicioAtividade() { return dataInicioAtividade; }
    public void setDataInicioAtividade(LocalDate dataInicioAtividade) { this.dataInicioAtividade = dataInicioAtividade; }
    public long getRbt12Centavos() { return rbt12Centavos; }
    public void setRbt12Centavos(long rbt12Centavos) { this.rbt12Centavos = rbt12Centavos; }
    public long getFolha12mCentavos() { return folha12mCentavos; }
    public void setFolha12mCentavos(long folha12mCentavos) { this.folha12mCentavos = folha12mCentavos; }
    public BigDecimal getFatorR() { return fatorR; }
    public void setFatorR(BigDecimal fatorR) { this.fatorR = fatorR; }
    public String getAnexoEfetivo() { return anexoEfetivo; }
    public void setAnexoEfetivo(String anexoEfetivo) { this.anexoEfetivo = anexoEfetivo; }
    public Integer getFaixa() { return faixa; }
    public void setFaixa(Integer faixa) { this.faixa = faixa; }
    public BigDecimal getAliquotaNominal() { return aliquotaNominal; }
    public void setAliquotaNominal(BigDecimal aliquotaNominal) { this.aliquotaNominal = aliquotaNominal; }
    public long getParcelaDeduzirCentavos() { return parcelaDeduzirCentavos; }
    public void setParcelaDeduzirCentavos(long parcelaDeduzirCentavos) { this.parcelaDeduzirCentavos = parcelaDeduzirCentavos; }
    public BigDecimal getAliquotaEfetiva() { return aliquotaEfetiva; }
    public void setAliquotaEfetiva(BigDecimal aliquotaEfetiva) { this.aliquotaEfetiva = aliquotaEfetiva; }
    public int getMesesAtividade() { return mesesAtividade; }
    public void setMesesAtividade(int mesesAtividade) { this.mesesAtividade = mesesAtividade; }
    public boolean isProporcionalizado() { return proporcionalizado; }
    public void setProporcionalizado(boolean proporcionalizado) { this.proporcionalizado = proporcionalizado; }
    public boolean isExcedeSimples() { return excedeSimples; }
    public void setExcedeSimples(boolean excedeSimples) { this.excedeSimples = excedeSimples; }
}
