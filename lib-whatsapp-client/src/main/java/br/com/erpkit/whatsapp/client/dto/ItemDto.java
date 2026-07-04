package br.com.erpkit.whatsapp.client.dto;

/**
 * Item de secao em mensagem interactive list. Espelha o wire format de
 * {@code br.com.erpkit.whatsapp.dto.ItemDto} do api-whatsapp.
 *
 * @param id          identificador do item (max 200 chars no Meta)
 * @param title       titulo do item (max 24 chars no Meta)
 * @param description descricao opcional (max 72 chars no Meta)
 */
public record ItemDto(String id, String title, String description) {
}
