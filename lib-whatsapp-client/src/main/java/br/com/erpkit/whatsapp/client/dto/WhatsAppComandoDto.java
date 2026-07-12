package br.com.erpkit.whatsapp.client.dto;

/**
 * Comando entrante recebido do api-whatsapp via callback POST
 * {@code /api/modulos/whatsapp/comando}. Espelha exatamente o wire format de
 * {@code br.com.erpkit.whatsapp.dto.ComandoCallbackDTO} (Phase 3 D-06) para que o
 * ERP receba o mesmo shape que o api-whatsapp envia.
 *
 * <p>Record imutavel; Jackson desserializa nativamente no ERP consumidor.
 *
 * @param telefone      telefone normalizado (somente digitos)
 * @param comando       keyword extraida (ex: "orcamento", "aprovar_42", "document")
 * @param payload       conteudo cru da mensagem (texto, "id|title", filename) — pode ser {@code null}
 * @param idCliente     id do cliente resolvido no ERP — {@code null} se nao mapeado
 * @param mediaBase64   bytes da media em base64 — {@code null} se sem media
 * @param mediaMimeType MIME do arquivo — {@code null} se sem media
 * @param mediaFilename nome original do arquivo — {@code null} se sem media
 * @param assistente    persona do assistente (nome/emoji/tom/saudacao) — bloco opcional/aditivo,
 *                      {@code null} quando o modulo nao envia
 */
public record WhatsAppComandoDto(
        String telefone,
        String comando,
        String payload,
        Long idCliente,
        String mediaBase64,
        String mediaMimeType,
        String mediaFilename,
        AssistentePersonaDto assistente
) {
    /**
     * Construtor de compatibilidade (7 args, sem persona) — mantem os call sites e testes
     * anteriores ao bloco {@code assistente} compilando ({@code assistente = null}).
     */
    public WhatsAppComandoDto(String telefone, String comando, String payload, Long idCliente,
                              String mediaBase64, String mediaMimeType, String mediaFilename) {
        this(telefone, comando, payload, idCliente, mediaBase64, mediaMimeType, mediaFilename, null);
    }
}
