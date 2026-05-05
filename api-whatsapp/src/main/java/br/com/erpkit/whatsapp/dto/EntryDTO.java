package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Entry do envelope Meta — agrupa changes para um WABA-ID especifico.
 * Tolera campos extras ({@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntryDTO {
    private String id;
    private List<ChangeDTO> changes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<ChangeDTO> getChanges() { return changes; }
    public void setChanges(List<ChangeDTO> changes) { this.changes = changes; }
}
