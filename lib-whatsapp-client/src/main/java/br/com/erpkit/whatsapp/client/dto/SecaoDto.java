package br.com.erpkit.whatsapp.client.dto;

import java.util.List;

/**
 * Secao em mensagem interactive list. Espelha o wire format de
 * {@code br.com.erpkit.whatsapp.dto.SecaoDto} do api-whatsapp. Soma total de itens
 * de todas as secoes &le; 10 (limite Meta) — a validacao cross-secao vive no
 * api-whatsapp; aqui e apenas transporte.
 *
 * @param titulo titulo da secao (max 24 chars no Meta)
 * @param itens  itens da secao
 */
public record SecaoDto(String titulo, List<ItemDto> itens) {
}
