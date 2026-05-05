package br.com.erpkit.whatsapp.dto;

import java.util.List;

/**
 * Output do {@link br.com.erpkit.whatsapp.service.WebhookPayloadParser} — separa mensagens
 * entrantes (a persistir) de statuses callbacks (Phase 2 nao persiste).
 *
 * @param mensagens lista de {@link MensagemEntranteDTO} extraidas, possivelmente vazia
 * @param statuses  lista de {@link StatusEntranteDTO} extraidos, possivelmente vazia
 */
public record ParsedWebhook(
    List<MensagemEntranteDTO> mensagens,
    List<StatusEntranteDTO> statuses
) { }
