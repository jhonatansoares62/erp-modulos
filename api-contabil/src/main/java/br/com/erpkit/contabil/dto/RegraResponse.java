package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/** Roteiro (regra evento → partidas) com as partidas resolvidas, para o painel do contador. */
public class RegraResponse {

    private Long id;
    private String eventoTipo;
    private int prioridade;
    private String condicoes;   // JSON cru ou null
    private String historicoTemplate;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFim;
    private boolean ativo;
    private List<RegraPartidaResponse> partidas;

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

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public List<RegraPartidaResponse> getPartidas() { return partidas; }
    public void setPartidas(List<RegraPartidaResponse> partidas) { this.partidas = partidas; }
}
