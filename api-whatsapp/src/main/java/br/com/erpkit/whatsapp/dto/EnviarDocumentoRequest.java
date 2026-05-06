package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body para POST /api/whatsapp/enviar-documento (OUT-02 + OUT-11). Per
 * D-01 CONTEXT.md: ERP envia bytes via {@code mediaBase64} String dentro de JSON
 * regular — NAO multipart. Controller decodifica via {@code Base64.getDecoder().decode}
 * antes de delegar a {@code WhatsAppCloudClient.enviarDocumento(byte[])}.
 *
 * <p><b>Limites:</b>
 * <ul>
 *   <li>{@code mediaBase64}: max 18MB (PDF ~13MB binario × 1.33 inflation)</li>
 *   <li>{@code mimeType}: pattern {@code type/subtype} basico (ex: application/pdf)</li>
 *   <li>{@code filename}: max 255 chars (filesystem-safe)</li>
 *   <li>{@code caption}: opcional, max 1024 chars (Cloud API limit)</li>
 * </ul>
 */
public record EnviarDocumentoRequest(
        @NotBlank(message = "telefone obrigatorio")
        @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
        String telefone,

        @NotBlank(message = "mediaBase64 obrigatorio")
        @Size(max = 18_000_000, message = "mediaBase64 excede limite (~13MB binario apos decode)")
        String mediaBase64,

        @NotBlank(message = "mimeType obrigatorio")
        @Pattern(regexp = "^[a-zA-Z]+/[a-zA-Z0-9.+\\-]+$", message = "mimeType invalido (ex: application/pdf)")
        String mimeType,

        @NotBlank(message = "filename obrigatorio")
        @Size(max = 255, message = "filename max 255 chars")
        String filename,

        @Size(max = 1024, message = "caption max 1024 chars (Cloud API limit)")
        String caption
) {
}
