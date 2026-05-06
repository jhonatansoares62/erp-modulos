package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Botao reply em mensagem interactive (OUT-03). Limites Cloud API v22.0:
 * <ul>
 *   <li>{@code id}: max 256 chars (Meta enforced)</li>
 *   <li>{@code title}: max 20 chars (Meta enforced — texto exibido no botao)</li>
 * </ul>
 */
public record BotaoDto(
        @NotBlank(message = "id do botao obrigatorio")
        @Size(max = 256, message = "id do botao max 256 chars")
        String id,

        @NotBlank(message = "title do botao obrigatorio")
        @Size(max = 20, message = "title do botao max 20 chars (Cloud API limit)")
        String title
) {
}
