package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persona do assistente virtual — mapeia 1:1 com {@code whatsapp.config_assistente}
 * (V11). Linha unica: {@code id} SEMPRE 1 (singleton, CHECK no banco). Config
 * GENERICA (identidade + mensagens de canal), reusavel entre ERPs; o ERP consome a
 * persona pelo bloco "assistente" do callback e renderiza as mensagens de dominio.
 *
 * <p>Sem {@code @GeneratedValue}: o id e atribuido pela aplicacao (=1). Spring Data
 * trata a entity como "nao nova" (id != null) e faz merge — INSERT na primeira vez,
 * UPDATE nas seguintes. Sem secrets aqui: todos os campos voltam em claro.
 */
@Entity
@Table(schema = "whatsapp", name = "config_assistente")
public class ConfigAssistente {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nome", length = 60)
    private String nome;

    @Column(name = "emoji", length = 16)
    private String emoji;

    @Column(name = "tom", length = 16, nullable = false)
    private String tom;

    @Column(name = "saudacao", length = 1000)
    private String saudacao;

    @Column(name = "mensagem_nao_entendi", length = 1000)
    private String mensagemNaoEntendi;

    @Column(name = "mensagem_fora_horario", length = 1000)
    private String mensagemForaHorario;

    @Column(name = "horario_inicio", length = 5)
    private String horarioInicio;

    @Column(name = "horario_fim", length = 5)
    private String horarioFim;

    @Column(name = "dias_atendimento", length = 32)
    private String diasAtendimento;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public ConfigAssistente() {
        // JPA exige construtor padrao
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getTom() { return tom; }
    public void setTom(String tom) { this.tom = tom; }

    public String getSaudacao() { return saudacao; }
    public void setSaudacao(String saudacao) { this.saudacao = saudacao; }

    public String getMensagemNaoEntendi() { return mensagemNaoEntendi; }
    public void setMensagemNaoEntendi(String mensagemNaoEntendi) { this.mensagemNaoEntendi = mensagemNaoEntendi; }

    public String getMensagemForaHorario() { return mensagemForaHorario; }
    public void setMensagemForaHorario(String mensagemForaHorario) { this.mensagemForaHorario = mensagemForaHorario; }

    public String getHorarioInicio() { return horarioInicio; }
    public void setHorarioInicio(String horarioInicio) { this.horarioInicio = horarioInicio; }

    public String getHorarioFim() { return horarioFim; }
    public void setHorarioFim(String horarioFim) { this.horarioFim = horarioFim; }

    public String getDiasAtendimento() { return diasAtendimento; }
    public void setDiasAtendimento(String diasAtendimento) { this.diasAtendimento = diasAtendimento; }

    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    @Override
    public String toString() {
        return "ConfigAssistente{id=" + id
             + ", nome=" + nome
             + ", tom=" + tom
             + ", atualizadoEm=" + atualizadoEm + "}";
    }
}
