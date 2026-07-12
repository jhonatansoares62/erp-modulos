package br.com.erpkit.whatsapp.client.dto;

/**
 * Persona do assistente virtual recebida no callback ({@link WhatsAppComandoDto#assistente()}).
 *
 * <p>Espelha o {@code br.com.erpkit.whatsapp.dto.AssistentePersonaDTO} do api-whatsapp. Bloco
 * opcional/aditivo — pode vir {@code null} (modulo antigo, ou persona indisponivel no envio).
 * O ERP usa nome/emoji/tom/saudacao para dar identidade as respostas do bot.
 *
 * @param nome     nome do assistente (ex.: "Sofia")
 * @param emoji    emoji/identidade do canal (ex.: "🦷")
 * @param tom      "formal" ou "informal"
 * @param saudacao template de saudacao configuravel
 */
public record AssistentePersonaDto(
        String nome,
        String emoji,
        String tom,
        String saudacao
) {
}
