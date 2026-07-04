package br.com.erpkit.whatsapp.client.dto;

/**
 * Botao reply em mensagem interactive. Espelha o wire format de
 * {@code br.com.erpkit.whatsapp.dto.BotaoDto} do api-whatsapp (sem as anotacoes
 * Jakarta — a validacao vive no Controller do api-whatsapp, aqui e apenas transporte).
 *
 * @param id    identificador do botao (max 256 chars no Meta)
 * @param title texto exibido no botao (max 20 chars no Meta)
 */
public record BotaoDto(String id, String title) {
}
