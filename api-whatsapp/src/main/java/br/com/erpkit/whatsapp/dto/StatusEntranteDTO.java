package br.com.erpkit.whatsapp.dto;

/**
 * Status callback do Meta (sent/delivered/read/failed) — Phase 2 parseia mas
 * NAO persiste (D-05 + D-06 do CONTEXT.md). Phase 4+ pode adicionar.
 *
 * @param wamid    ID unico do Meta da mensagem original (correlaciona com mensagens_log)
 * @param status   "sent" | "delivered" | "read" | "failed"
 * @param telefone recipient_id JA NORMALIZADO via TelefoneBR
 */
public record StatusEntranteDTO(
    String wamid,
    String status,
    String telefone
) { }
