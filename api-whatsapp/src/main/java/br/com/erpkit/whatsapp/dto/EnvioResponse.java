package br.com.erpkit.whatsapp.dto;

/**
 * Resposta dos endpoints de envio outbound (OUT-09 + OUT-11). Contem somente o
 * {@code wamid} retornado pelo Meta. Phase 5+ pode adicionar campos sem quebrar
 * contrato (record permite adicionar componentes em ordem).
 */
public record EnvioResponse(String wamid) {
}
