package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body para {@code PUT /api/whatsapp/assistente} — grava a persona do
 * assistente virtual.
 *
 * <p><b>Substituicao total:</b> a tela "Assistente" envia todos os campos (nao ha
 * secret pra preservar, diferente do config_meta). {@code nome} e {@code tom} sao
 * obrigatorios; os demais opcionais (em branco = limpa o campo).
 */
public record AssistenteRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 60, message = "Nome excede 60 caracteres")
        String nome,

        @Size(max = 16, message = "Emoji excede 16 caracteres")
        String emoji,

        @NotBlank(message = "Tom é obrigatório")
        @Size(max = 16, message = "Tom excede 16 caracteres")
        String tom,

        @Size(max = 1000, message = "Saudação excede 1000 caracteres")
        String saudacao,

        @Size(max = 1000, message = "Mensagem 'não entendi' excede 1000 caracteres")
        String mensagemNaoEntendi,

        @Size(max = 1000, message = "Mensagem 'fora do horário' excede 1000 caracteres")
        String mensagemForaHorario,

        @Size(max = 5, message = "Horário de início excede 5 caracteres")
        String horarioInicio,

        @Size(max = 5, message = "Horário de fim excede 5 caracteres")
        String horarioFim,

        @Size(max = 32, message = "Dias de atendimento excede 32 caracteres")
        String diasAtendimento
) {
}
