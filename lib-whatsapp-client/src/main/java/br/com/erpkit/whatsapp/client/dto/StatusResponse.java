package br.com.erpkit.whatsapp.client.dto;

/**
 * Resposta do {@code GET /api/whatsapp/status} do api-whatsapp. Espelha
 * {@code br.com.erpkit.whatsapp.dto.StatusResponse}.
 *
 * @param status              "UP" | "DOWN" — saude geral do modulo
 * @param circuitBreakerState "CLOSED" | "OPEN" | "HALF_OPEN" | "UNKNOWN"
 * @param phoneNumberId       sanity check do numero configurado
 */
public record StatusResponse(String status, String circuitBreakerState, String phoneNumberId) {
}
