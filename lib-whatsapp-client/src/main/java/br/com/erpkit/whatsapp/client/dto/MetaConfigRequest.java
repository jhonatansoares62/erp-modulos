package br.com.erpkit.whatsapp.client.dto;

/**
 * Body do {@code PUT /api/whatsapp/config} — grava as credenciais Meta no
 * api-whatsapp. As chaves JSON espelham o request DTO do modulo. Campo em
 * branco/nulo MANTEM o valor atual (atualizacao parcial — nao apaga secret).
 */
public record MetaConfigRequest(
        String phoneNumberId,
        String accessToken,
        String appSecret,
        String verifyToken
) {
}
