package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Change do envelope Meta — wrap de Value + field (sempre {@code "messages"} para WhatsApp).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeDTO {
    private ValueDTO value;
    private String field;

    public ValueDTO getValue() { return value; }
    public void setValue(ValueDTO value) { this.value = value; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
}
