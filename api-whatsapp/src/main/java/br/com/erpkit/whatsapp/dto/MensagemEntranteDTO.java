package br.com.erpkit.whatsapp.dto;

/**
 * Mensagem ja extraida e normalizada pelo parser — pronta para
 * {@link br.com.erpkit.whatsapp.service.MensagemService} processar (Plan 06).
 *
 * <p>Usar {@code record} aqui e seguro porque NAO ha (de)serializacao Jackson —
 * apenas instanciacao Java por
 * {@link br.com.erpkit.whatsapp.service.WebhookPayloadParser}.
 *
 * @param wamid        ID unico do Meta (UNIQUE em mensagens_log)
 * @param telefone     telefone NORMALIZADO via {@link br.com.erpkit.whatsapp.util.TelefoneBR#normalizar}
 *                     (uso interno: armazenamento em clientes_zap + lookup da janela 24h)
 * @param telefoneWaId {@code wa_id} EXATO como o Meta enviou (via {@link br.com.erpkit.whatsapp.util.TelefoneBR#sanitizar},
 *                     sem strip do 9o digito) — usado para RESPONDER ao cliente (outbound)
 * @param tipo         "text", "interactive_button", "interactive_list",
 *                     "document", "image", "audio", "desconhecido"
 * @param conteudo     conteudo extraido (texto, button_reply.id+title,
 *                     filename, etc.) ou {@code null} para tipos sem texto
 * @param mediaId      ID do media no Meta para document/image/audio, ou {@code null}
 */
public record MensagemEntranteDTO(
    String wamid,
    String telefone,
    String telefoneWaId,
    String tipo,
    String conteudo,
    String mediaId
) { }
