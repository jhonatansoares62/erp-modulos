package br.com.erpkit.whatsapp.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Resumo de uso do WhatsApp num periodo [de, ate] — agregacoes de {@code mensagens_log}
 * (V7 §12). Consumido pelo ERP via {@code lib-whatsapp-client} para o painel de Modulos.
 *
 * <p>Sem PII: apenas contagens. {@code statusSaida}/{@code categoriaSaida} sao so das
 * saidas (in nao tem status de entrega nem categoria de cobranca). Chaves nulas viram
 * rotulos: status {@code null} -&gt; {@code "pendente"}, categoria {@code null} -&gt;
 * {@code "sem_categoria"}, tipo {@code null} -&gt; {@code "desconhecido"}.
 *
 * @param de             inicio do periodo (inclusive)
 * @param ate            fim do periodo (inclusive)
 * @param total          total de mensagens (in + out)
 * @param entrada        mensagens recebidas (direcao=in)
 * @param saida          mensagens enviadas (direcao=out)
 * @param faturaveis     saidas com {@code billable=true} (base de custo pos out/2026)
 * @param porTipo        contagem por tipo Meta (text, interactive_list, ...)
 * @param statusSaida    contagem de saidas por status de entrega (sent/delivered/read/failed/pendente)
 * @param categoriaSaida contagem de saidas por categoria faturavel (service/utility/.../sem_categoria)
 */
public record ResumoUsoResponse(
    Instant de,
    Instant ate,
    long total,
    long entrada,
    long saida,
    long faturaveis,
    Map<String, Long> porTipo,
    Map<String, Long> statusSaida,
    Map<String, Long> categoriaSaida
) { }
