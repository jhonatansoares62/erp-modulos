package br.com.erpkit.whatsapp.client.dto;

/**
 * Resposta de {@code GET/PUT /api/whatsapp/config}. Reflete a config Meta efetiva
 * do api-whatsapp com os secrets mascarados: accessToken/appSecret/verifyToken
 * voltam apenas como boolean "esta preenchido?". {@code phoneNumberId} nao e secret
 * e volta em claro. {@code configurado} = os 4 preenchidos.
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
