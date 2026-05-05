package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status callback do Meta — sent/delivered/read/failed para mensagens de SAIDA. Phase 2 parseia
 * mas nao persiste (D-05 + D-06 do CONTEXT.md). Phase 4+ pode adicionar persistencia.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusDTO {
    private String id;
    private String status;
    @JsonProperty("recipient_id") private String recipientId;
    private String timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
