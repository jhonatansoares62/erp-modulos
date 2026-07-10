package br.com.erpkit.whatsapp.dto;

/**
 * Resposta de {@code GET/PUT /api/whatsapp/config} — reflete a config Meta EFETIVA
 * (banco sobreposto ao seed de env var).
 *
 * <p><b>Secrets nunca voltam em claro:</b> accessToken/appSecret/verifyToken sao
 * expostos apenas como booleans "esta preenchido?". O {@code phoneNumberId} nao e
 * secret (identificador publico) e volta em claro para o operador conferir.
 * {@code configurado} = os 4 preenchidos. {@code atualizadoEm} = ISO-8601 da ultima
 * gravacao (nulo se nunca salvo no banco).
 */
public record MetaConfigResponse(
        String phoneNumberId,
        boolean accessTokenConfigurado,
        boolean appSecretConfigurado,
        boolean verifyTokenConfigurado,
        boolean configurado,
        String atualizadoEm
) {
}
