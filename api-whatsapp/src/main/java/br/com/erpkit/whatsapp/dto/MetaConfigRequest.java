package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body para {@code PUT /api/whatsapp/config} — grava as credenciais Meta.
 *
 * <p><b>Atualizacao parcial:</b> qualquer campo em branco/nulo MANTEM o valor atual
 * (nao apaga). Assim o operador pode trocar so o accessToken sem reenviar o
 * appSecret/verifyToken. Na primeira configuracao os 4 devem vir preenchidos para
 * o modulo ficar "configurado".
 */
public record MetaConfigRequest(
        @Size(max = 64, message = "phoneNumberId excede 64 chars")
        String phoneNumberId,

        @Size(max = 1024, message = "accessToken excede 1024 chars")
        String accessToken,

        @Size(max = 255, message = "appSecret excede 255 chars")
        String appSecret,

        @Size(max = 255, message = "verifyToken excede 255 chars")
        String verifyToken
) {
}
