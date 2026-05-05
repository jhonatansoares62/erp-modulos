package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usado para {@code button_reply} E {@code list_reply} (mesma estrutura: id + title). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplyDTO {
    private String id;
    private String title;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
