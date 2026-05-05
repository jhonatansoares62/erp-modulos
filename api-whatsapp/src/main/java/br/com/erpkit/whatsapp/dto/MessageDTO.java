package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mensagem entrante do envelope Meta. Apenas o sub-DTO correspondente ao
 * {@code type} declarado sera populado (text -> text DTO, interactive -> interactive DTO, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDTO {
    private String from;
    private String id;
    private String timestamp;
    private String type;
    private TextDTO text;
    private InteractiveDTO interactive;
    private DocumentDTO document;
    private MediaDTO image;
    private MediaDTO audio;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public TextDTO getText() { return text; }
    public void setText(TextDTO text) { this.text = text; }
    public InteractiveDTO getInteractive() { return interactive; }
    public void setInteractive(InteractiveDTO interactive) { this.interactive = interactive; }
    public DocumentDTO getDocument() { return document; }
    public void setDocument(DocumentDTO document) { this.document = document; }
    public MediaDTO getImage() { return image; }
    public void setImage(MediaDTO image) { this.image = image; }
    public MediaDTO getAudio() { return audio; }
    public void setAudio(MediaDTO audio) { this.audio = audio; }
}
