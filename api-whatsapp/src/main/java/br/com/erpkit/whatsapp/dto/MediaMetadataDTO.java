package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jackson DTO do step 1 do download de media Meta:
 * {@code GET https://graph.facebook.com/v22.0/{media_id}}.
 *
 * <p>Resposta do Graph API tem campos em snake_case que mapeamos para camelCase
 * via {@code @JsonProperty}. {@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * deixa o DTO resiliente a campos novos que Meta possa adicionar sem aviso.
 *
 * <p>NAO usar record — Spring Boot 3 + Jackson 2.18 funciona com record para
 * deserializacao, mas a convencao do monorepo (api-email/api-storage/Phase 2
 * Webhook envelope DTOs) e POJO com getters/setters explicitos. Consistencia &gt;
 * conciseness aqui.
 *
 * <p>Wave 3 ({@code MetaMediaClient.baixar}) usa via
 * {@code restClient.get().retrieve().body(MediaMetadataDTO.class)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMetadataDTO {

    private String url;

    @JsonProperty("mime_type")
    private String mimeType;

    private String filename;

    private String sha256;

    @JsonProperty("file_size")
    private Long fileSize;

    private String id;

    @JsonProperty("messaging_product")
    private String messagingProduct;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }
}
