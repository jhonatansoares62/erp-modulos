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
 * Roteiro contábil: regra evento → partidas. Mapeia contabil.regra_lancamento (V1).
 * condicoes é JSON (TEXT) avaliado contra o contexto do evento. Versionada por vigência.
 */
@Entity
@Table(schema = "contabil", name = "regra_lancamento")
public class RegraLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_tipo", nullable = false, length = 60)
    private String eventoTipo;

    @Column(name = "prioridade", nullable = false)
    private int prioridade;

    /** JSON de condições sobre o contexto do evento (ex.: {"meioPagamento":"pix"}). */
    @Column(name = "condicoes")
    private String condicoes;

    @Column(name = "historico_template", length = 300)
    private String historicoTemplate;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "versao", nullable = false)
    private int versao = 1;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public RegraLancamento() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventoTipo() { return eventoTipo; }
    public void setEventoTipo(String eventoTipo) { this.eventoTipo = eventoTipo; }

    public int getPrioridade() { return prioridade; }
    public void setPrioridade(int prioridade) { this.prioridade = prioridade; }

    public String getCondicoes() { return condicoes; }
    public void setCondicoes(String condicoes) { this.condicoes = condicoes; }

    public String getHistoricoTemplate() { return historicoTemplate; }
    public void setHistoricoTemplate(String historicoTemplate) { this.historicoTemplate = historicoTemplate; }

    public LocalDate getVigenciaInicio() { return vigenciaInicio; }
    public void setVigenciaInicio(LocalDate vigenciaInicio) { this.vigenciaInicio = vigenciaInicio; }

    public LocalDate getVigenciaFim() { return vigenciaFim; }
    public void setVigenciaFim(LocalDate vigenciaFim) { this.vigenciaFim = vigenciaFim; }

    public int getVersao() { return versao; }
    public void setVersao(int versao) { this.versao = versao; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Instant getCreatedAt() { return createdAt; }
}
