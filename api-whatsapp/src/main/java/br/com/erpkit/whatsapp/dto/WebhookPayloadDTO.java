package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope raiz do webhook do Meta. Tolerante a campos extras
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) — Meta pode adicionar campos novos
 * sem release nosso.
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/payload-examples">Meta payload examples</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayloadDTO {
    private String object;
    private List<EntryDTO> entry;

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public List<EntryDTO> getEntry() { return entry; }
    public void setEntry(List<EntryDTO> entry) { this.entry = entry; }
}
