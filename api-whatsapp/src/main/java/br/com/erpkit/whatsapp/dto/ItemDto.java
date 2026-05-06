package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Item de secao em mensagem interactive list (OUT-04). Limites Cloud API:
 * <ul>
 *   <li>{@code id}: max 200 chars</li>
 *   <li>{@code title}: max 24 chars</li>
 *   <li>{@code description}: opcional, max 72 chars</li>
 * </ul>
 */
public record ItemDto(
        @NotBlank(message = "id do item obrigatorio")
        @Size(max = 200, message = "id do item max 200 chars")
        String id,

        @NotBlank(message = "title do item obrigatorio")
        @Size(max = 24, message = "title do item max 24 chars (Cloud API limit)")
        String title,

        @Size(max = 72, message = "description max 72 chars (Cloud API limit)")
        String description
) {
}
