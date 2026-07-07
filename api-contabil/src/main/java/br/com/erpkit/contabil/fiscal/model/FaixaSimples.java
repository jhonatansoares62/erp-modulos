package br.com.erpkit.contabil.fiscal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Faixa do Simples Nacional (lookup versionável, V16). Uma linha por anexo/faixa. */
@Entity
@Table(schema = "contabil", name = "fiscal_faixa_simples")
public class FaixaSimples {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4)
    private String anexo;

    @Column(nullable = false)
    private int faixa;

    @Column(name = "rbt12_ate_centavos", nullable = false)
    private long rbt12AteCentavos;

    @Column(name = "aliquota_nominal", nullable = false, precision = 6, scale = 4)
    private BigDecimal aliquotaNominal;

    @Column(name = "parcela_deduzir_centavos", nullable = false)
    private long parcelaDeduzirCentavos;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    public Long getId() { return id; }
    public String getAnexo() { return anexo; }
    public int getFaixa() { return faixa; }
    public long getRbt12AteCentavos() { return rbt12AteCentavos; }
    public BigDecimal getAliquotaNominal() { return aliquotaNominal; }
    public long getParcelaDeduzirCentavos() { return parcelaDeduzirCentavos; }
    public LocalDate getVigenciaInicio() { return vigenciaInicio; }
}
