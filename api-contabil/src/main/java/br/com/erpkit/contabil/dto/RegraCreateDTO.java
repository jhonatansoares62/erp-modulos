package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Criação de um roteiro (regra evento → partidas). */
public class RegraCreateDTO {

    /** Tipo do evento (ex.: venda.finalizada). No fluxo de pendência é preenchido a partir do evento. */
    private String eventoTipo;

    private int prioridade;

    /** Condições sobre o contexto do evento (ex.: {"meioPagamento":"pix"}). Vazio = regra genérica. */
    private Map<String, Object> condicoes;

    private String historicoTemplate;

    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFim;

    @NotEmpty(message = "A regra precisa de ao menos duas partidas (um débito e um crédito)")
    private List<RegraPartidaDTO> partidas;

    public String getEventoTipo() { return eventoTipo; }
    public void setEventoTipo(String eventoTipo) { this.eventoTipo = eventoTipo; }

    public int getPrioridade() { return prioridade; }
    public void setPrioridade(int prioridade) { this.prioridade = prioridade; }

    public Map<String, Object> getCondicoes() { return condicoes; }
    public void setCondicoes(Map<String, Object> condicoes) { this.condicoes = condicoes; }

    public String getHistoricoTemplate() { return historicoTemplate; }
    public void setHistoricoTemplate(String historicoTemplate) { this.historicoTemplate = historicoTemplate; }

    public LocalDate getVigenciaInicio() { return vigenciaInicio; }
    public void setVigenciaInicio(LocalDate vigenciaInicio) { this.vigenciaInicio = vigenciaInicio; }

    public LocalDate getVigenciaFim() { return vigenciaFim; }
    public void setVigenciaFim(LocalDate vigenciaFim) { this.vigenciaFim = vigenciaFim; }

    public List<RegraPartidaDTO> getPartidas() { return partidas; }
    public void setPartidas(List<RegraPartidaDTO> partidas) { this.partidas = partidas; }
}
