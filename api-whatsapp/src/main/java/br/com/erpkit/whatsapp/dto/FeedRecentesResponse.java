package br.com.erpkit.whatsapp.dto;

import java.util.List;

/**
 * Feed das ultimas mensagens (entrada + saida) para a tela de Testes — resposta de
 * {@code GET /api/whatsapp/mensagens/recentes} (producao, autenticado) e do
 * {@code GET /monitor/feed} (dev/meta). Mesmo shape nos dois para a tela nao precisar
 * ramificar por ambiente.
 *
 * @param phoneNumberId       numero configurado (sanity check no topo da tela)
 * @param circuitBreakerState estado do circuit breaker {@code whatsapp-cloud}
 *                            ({@code CLOSED} | {@code OPEN} | {@code HALF_OPEN} | {@code UNKNOWN})
 * @param total               total de mensagens no log (contador do rodape)
 * @param mensagens           ultimas N mensagens, mais recentes primeiro
 */
public record FeedRecentesResponse(
        String phoneNumberId,
        String circuitBreakerState,
        long total,
        List<MensagemRecenteResponse> mensagens
) {
}
