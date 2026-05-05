package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Compartilhado por {@code image} e {@code audio} (mesma estrutura: id + mime_type + sha256). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaDTO {
    private String id;
    @JsonProperty("mime_type") private String mimeType;
    private String sha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
