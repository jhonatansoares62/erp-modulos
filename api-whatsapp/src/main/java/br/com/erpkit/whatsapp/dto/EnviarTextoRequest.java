package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body para POST /api/whatsapp/enviar-texto (OUT-01 + OUT-11).
 * <ul>
 *   <li>{@code telefone}: 10-15 digitos (formato Cloud API E.164 sem +)</li>
 *   <li>{@code texto}: max 4096 chars (Cloud API limit text/body)</li>
 * </ul>
 */
public record EnviarTextoRequest(
        @NotBlank(message = "telefone obrigatorio")
        @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
        String telefone,

        @NotBlank(message = "texto obrigatorio")
        @Size(max = 4096, message = "texto excede 4096 chars (Cloud API limit)")
        String texto
) {
}
