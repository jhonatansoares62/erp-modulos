package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Status callback do Meta — sent/delivered/read/failed para mensagens de SAIDA.
 *
 * <p>V7 (observabilidade §12): alem de {@code id/status/recipient_id/timestamp}, agora
 * lemos os blocos {@code conversation} (id + origin.type), {@code pricing} (billable +
 * category) e {@code errors[]} (code + title) — antes descartados. Ver
 * {@link br.com.erpkit.whatsapp.service.StatusEntregaService} (persiste por wamid).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusDTO {
    private String id;
    private String status;
    @JsonProperty("recipient_id") private String recipientId;
    private String timestamp;
    private Conversation conversation;
    private Pricing pricing;
    private List<Erro> errors;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public Conversation getConversation() { return conversation; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }
    public Pricing getPricing() { return pricing; }
    public void setPricing(Pricing pricing) { this.pricing = pricing; }
    public List<Erro> getErrors() { return errors; }
    public void setErrors(List<Erro> errors) { this.errors = errors; }

    /** {@code conversation}: id + {@code origin.type} (service/utility/marketing/authentication). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conversation {
        private String id;
        private Origin origin;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Origin getOrigin() { return origin; }
        public void setOrigin(Origin origin) { this.origin = origin; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Origin {
        private String type;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    /** {@code pricing}: billable + category (categoria faturavel da Meta). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pricing {
        private Boolean billable;
        private String category;
        @JsonProperty("pricing_model") private String pricingModel;
        public Boolean getBillable() { return billable; }
        public void setBillable(Boolean billable) { this.billable = billable; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getPricingModel() { return pricingModel; }
        public void setPricingModel(String pricingModel) { this.pricingModel = pricingModel; }
    }

    /** {@code errors[]}: code + title (motivo da falha em status=failed). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Erro {
        private Integer code;
        private String title;
        public Integer getCode() { return code; }
        public void setCode(Integer code) { this.code = code; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
