package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Conteudo de mensagem do tipo {@code document} — PDF, DOCX, etc. enviados pelo cliente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentDTO {
    private String id;
    @JsonProperty("mime_type") private String mimeType;
    private String filename;
    private String sha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
