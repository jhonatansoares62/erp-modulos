package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Conteudo de mensagem do tipo {@code interactive}. {@code type} pode ser
 * {@code button_reply} ou {@code list_reply} — apenas o sub-DTO correspondente sera populado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractiveDTO {
    private String type;  // "button_reply" | "list_reply"
    @JsonProperty("button_reply") private ReplyDTO buttonReply;
    @JsonProperty("list_reply") private ReplyDTO listReply;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public ReplyDTO getButtonReply() { return buttonReply; }
    public void setButtonReply(ReplyDTO buttonReply) { this.buttonReply = buttonReply; }
    public ReplyDTO getListReply() { return listReply; }
    public void setListReply(ReplyDTO listReply) { this.listReply = listReply; }
}
