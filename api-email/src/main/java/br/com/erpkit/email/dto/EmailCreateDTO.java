package br.com.erpkit.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.Map;

public class EmailCreateDTO {

    @NotBlank(message = "Destinatário é obrigatório")
    @Email(message = "Email do destinatário inválido")
    private String destinatario;

    private String cc;

    @NotBlank(message = "Assunto é obrigatório")
    private String assunto;

    private String corpo;

    private boolean html;

    private String template;

    private Map<String, Object> templateVariaveis;

    private Long contaId;

    private String origem;

    private String referenciaId;

    private LocalDateTime agendadoPara;

    // Getters e Setters

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getAssunto() {
        return assunto;
    }

    public void setAssunto(String assunto) {
        this.assunto = assunto;
    }

    public String getCorpo() {
        return corpo;
    }

    public void setCorpo(String corpo) {
        this.corpo = corpo;
    }

    public boolean isHtml() {
        return html;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, Object> getTemplateVariaveis() {
        return templateVariaveis;
    }

    public void setTemplateVariaveis(Map<String, Object> templateVariaveis) {
        this.templateVariaveis = templateVariaveis;
    }

    public Long getContaId() {
        return contaId;
    }

    public void setContaId(Long contaId) {
        this.contaId = contaId;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public String getReferenciaId() {
        return referenciaId;
    }

    public void setReferenciaId(String referenciaId) {
        this.referenciaId = referenciaId;
    }

    public LocalDateTime getAgendadoPara() {
        return agendadoPara;
    }

    public void setAgendadoPara(LocalDateTime agendadoPara) {
        this.agendadoPara = agendadoPara;
    }
}
