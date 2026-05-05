package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Value do envelope Meta — contem messages (entrantes) e/ou statuses (callbacks de saida).
 * Ambos sao opcionais — heartbeats nao tem nem messages nem statuses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueDTO {
    @JsonProperty("messaging_product") private String messagingProduct;
    private List<MessageDTO> messages;
    private List<StatusDTO> statuses;

    public String getMessagingProduct() { return messagingProduct; }
    public void setMessagingProduct(String s) { this.messagingProduct = s; }
    public List<MessageDTO> getMessages() { return messages; }
    public void setMessages(List<MessageDTO> messages) { this.messages = messages; }
    public List<StatusDTO> getStatuses() { return statuses; }
    public void setStatuses(List<StatusDTO> statuses) { this.statuses = statuses; }
}
