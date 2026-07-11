package br.com.erpkit.whatsapp.client.dto;

import java.util.Map;

/**
 * Resumo de uso do WhatsApp (V7 §12) — resposta do
 * {@code GET /api/whatsapp/relatorios/resumo}. Contagens agregadas, sem PII.
 *
 * <p>{@code de}/{@code ate} chegam como ISO-8601 (String) — o api-whatsapp serializa
 * os {@code Instant} nesse formato; manter String aqui evita depender do módulo
 * jsr310 no {@code RestClient} do lib.
 */
public record ResumoUsoResponse(
    String de,
    String ate,
    long total,
    long entrada,
    long saida,
    long faturaveis,
    Map<String, Long> porTipo,
    Map<String, Long> statusSaida,
    Map<String, Long> categoriaSaida,
    Map<String, Long> porResultado
) { }
