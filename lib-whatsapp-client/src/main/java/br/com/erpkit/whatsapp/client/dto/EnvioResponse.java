package br.com.erpkit.whatsapp.client.dto;

/**
 * Resposta dos endpoints de envio outbound do api-whatsapp. Contem o {@code wamid}
 * retornado pelo Meta. Espelha {@code br.com.erpkit.whatsapp.dto.EnvioResponse}.
 *
 * @param wamid id da mensagem no WhatsApp (Meta)
 */
public record EnvioResponse(String wamid) {
}
