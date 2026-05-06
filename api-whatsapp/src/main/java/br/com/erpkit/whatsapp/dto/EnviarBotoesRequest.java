package br.com.erpkit.whatsapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body para POST /api/whatsapp/enviar-botoes (OUT-03 + OUT-11). Cloud
 * API limits:
 * <ul>
 *   <li>{@code texto}: max 1024 chars (interactive body text)</li>
 *   <li>{@code botoes}: max 3 botoes (Cloud API hard limit) — {@code @Size(max=3)} forca early 400</li>
 * </ul>
 */
public record EnviarBotoesRequest(
        @NotBlank(message = "telefone obrigatorio")
        @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
        String telefone,

        @NotBlank(message = "texto obrigatorio")
        @Size(max = 1024, message = "texto max 1024 chars (Cloud API limit)")
        String texto,

        @NotEmpty(message = "botoes nao pode ser vazio")
        @Size(max = 3, message = "Maximo 3 botoes (Cloud API limit)")
        @Valid
        List<BotaoDto> botoes
) {
}
