package br.com.erpkit.whatsapp.dto;

/**
 * Resposta de {@code GET/PUT /api/whatsapp/assistente} — a persona efetiva do
 * assistente virtual.
 *
 * <p>Sem secrets: todos os campos voltam em claro (a persona e informacao publica de
 * identidade/canal). {@code atualizadoEm} = ISO-8601 da ultima gravacao (nulo se
 * ainda nunca salvo, quando a resposta reflete os defaults em memoria).
 */
public record AssistenteResponse(
        String nome,
        String emoji,
        String tom,
        String saudacao,
        String mensagemNaoEntendi,
        String mensagemForaHorario,
        String horarioInicio,
        String horarioFim,
        String diasAtendimento,
        String atualizadoEm
) {
}
