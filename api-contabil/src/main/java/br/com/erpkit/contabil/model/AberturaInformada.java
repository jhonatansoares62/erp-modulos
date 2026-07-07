package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Saldo de abertura INFORMADO pelo usuário para uma conta (V14). O conjunto de linhas define
 * a abertura real da clínica; havendo linhas, o AberturaService posta a abertura informada e
 * não usa o auto-aporte de capital. Uma linha por conta (conta_codigo único).
 */
@Entity
@Table(schema = "contabil", name = "abertura_informada")
public class AberturaInformada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_abertura", nullable = false)
    private LocalDate dataAbertura;

    @Column(name = "conta_codigo", nullable = false, length = 20)
    private String contaCodigo;

    @Column(name = "saldo_centavos", nullable = false)
    private long saldoCentavos;

    @Column(name = "informado_por", length = 120)
    private String informadoPor;

    @Column(name = "informado_em", insertable = false, updatable = false)
    private Instant informadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(LocalDate dataAbertura) { this.dataAbertura = dataAbertura; }
    public String getContaCodigo() { return contaCodigo; }
    public void setContaCodigo(String contaCodigo) { this.contaCodigo = contaCodigo; }
    public long getSaldoCentavos() { return saldoCentavos; }
    public void setSaldoCentavos(long saldoCentavos) { this.saldoCentavos = saldoCentavos; }
    public String getInformadoPor() { return informadoPor; }
    public void setInformadoPor(String informadoPor) { this.informadoPor = informadoPor; }
    public Instant getInformadoEm() { return informadoEm; }
}
