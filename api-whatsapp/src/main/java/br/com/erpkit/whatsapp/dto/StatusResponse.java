package br.com.erpkit.whatsapp.dto;

/**
 * Response do GET /api/whatsapp/status (OUT-11 + D-04 minimal CONTEXT.md).
 * Phase 6 pode expandir se feedback do piloto pedir (subscribed_apps via
 * Graph API — PITFALLS C-12).
 *
 * @param status              "UP" | "DOWN" — saude geral simples
 * @param circuitBreakerState "CLOSED" | "OPEN" | "HALF_OPEN" | "UNKNOWN"
 * @param phoneNumberId       sanity check para operador comparar com env var
 */
public record StatusResponse(
        String status,
        String circuitBreakerState,
        String phoneNumberId
) {
}
