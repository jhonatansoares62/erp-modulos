package br.com.erpkit.whatsapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Secao em mensagem interactive list (OUT-04). Cada secao agrupa itens.
 * Cloud API: max 24 chars no titulo da secao; soma TOTAL de itens em todas as
 * secoes &le; 10 — validacao cross-secao em {@code EnviarListaRequest.@AssertTrue}
 * (04-05).
 */
public record SecaoDto(
        @NotBlank(message = "titulo da secao obrigatorio")
        @Size(max = 24, message = "titulo da secao max 24 chars (Cloud API limit)")
        String titulo,

        @NotEmpty(message = "secao precisa ter ao menos 1 item")
        @Valid
        List<ItemDto> itens
) {
}
