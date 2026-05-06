package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Request body para POST /api/whatsapp/enviar-lista (OUT-04 + OUT-11). Cloud
 * API limit hard: total de itens TODAS secoes &le; 10 (Meta hard-rejects > 10).
 * Validacao cross-secao via {@link #isTotalItensValido()} {@code @AssertTrue}.
 *
 * <p>{@code @JsonIgnore} no metodo para evitar Jackson incluir o boolean no JSON
 * de response (record toString OK; campo "totalItensValido" nao deve aparecer).
 */
public record EnviarListaRequest(
        @NotBlank(message = "telefone obrigatorio")
        @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
        String telefone,

        @NotBlank(message = "texto obrigatorio")
        @Size(max = 1024, message = "texto max 1024 chars (Cloud API limit)")
        String texto,

        @NotEmpty(message = "secoes nao pode ser vazio")
        @Size(max = 10, message = "Maximo 10 secoes")
        @Valid
        List<SecaoDto> secoes
) {

    /**
     * Soma cross-secao de itens deve ser &le; 10 (Cloud API hard limit). Falha
     * 400 via Bean Validation se total > 10.
     */
    @AssertTrue(message = "Total de itens em todas as secoes excede 10 (Cloud API limit)")
    @JsonIgnore
    public boolean isTotalItensValido() {
        if (secoes == null) return true;
        int total = secoes.stream()
                .filter(Objects::nonNull)
                .mapToInt(s -> s.itens() == null ? 0 : s.itens().size())
                .sum();
        return total <= 10;
    }
}
