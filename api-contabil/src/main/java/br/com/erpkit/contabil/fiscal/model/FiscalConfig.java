package br.com.erpkit.contabil.fiscal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Config fiscal single-row (V16). anexo: I|II|III|V|AUTO (AUTO = III/V por Fator R). */
@Entity
@Table(schema = "contabil", name = "fiscal_config")
public class FiscalConfig {

    @Id
    private Integer id = 1;

    @Column(nullable = false, length = 30)
    private String regime = "simples_nacional";

    @Column(name = "calculo_automatico", nullable = false)
    private boolean calculoAutomatico = false;

    @Column(nullable = false, length = 4)
    private String anexo = "AUTO";

    @Column(name = "data_inicio_atividade")
    private LocalDate dataInicioAtividade;

    /** Corte de migração (V21): a partir desta competência a receita é escriturada; antes, é a histórica informada. */
    @Column(name = "data_entrada_sistema")
    private LocalDate dataEntradaSistema;

    @Column(name = "folha12m_centavos", nullable = false)
    private long folha12mCentavos = 0;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public boolean isCalculoAutomatico() { return calculoAutomatico; }
    public void setCalculoAutomatico(boolean calculoAutomatico) { this.calculoAutomatico = calculoAutomatico; }
    public String getAnexo() { return anexo; }
    public void setAnexo(String anexo) { this.anexo = anexo; }
    public LocalDate getDataInicioAtividade() { return dataInicioAtividade; }
    public void setDataInicioAtividade(LocalDate dataInicioAtividade) { this.dataInicioAtividade = dataInicioAtividade; }
    public LocalDate getDataEntradaSistema() { return dataEntradaSistema; }
    public void setDataEntradaSistema(LocalDate dataEntradaSistema) { this.dataEntradaSistema = dataEntradaSistema; }
    public long getFolha12mCentavos() { return folha12mCentavos; }
    public void setFolha12mCentavos(long folha12mCentavos) { this.folha12mCentavos = folha12mCentavos; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
