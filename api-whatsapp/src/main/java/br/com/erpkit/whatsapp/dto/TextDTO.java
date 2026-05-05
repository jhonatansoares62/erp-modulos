package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Conteudo de mensagem do tipo {@code text}. Apenas o campo {@code body} eh relevante.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextDTO {
    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
