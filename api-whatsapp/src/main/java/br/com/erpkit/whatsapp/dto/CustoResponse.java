package br.com.erpkit.whatsapp.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Custo/volume do WhatsApp no periodo, direto do {@code pricing_analytics} da Meta
 * (§11) — autoritativo. Custo na moeda do WABA. Hoje tudo tende a
 * {@code FREE_CUSTOMER_SERVICE} com custo 0 (janela 24h); a partir de out/2026 as
 * mensagens de servico/utility viram {@code REGULAR} e o custo aparece nativamente.
 *
 * @param de                inicio (ISO-8601)
 * @param ate               fim (ISO-8601)
 * @param volumeTotal       total de mensagens contabilizadas
 * @param custoTotal        custo total (moeda do WABA)
 * @param volumeFaturavel   volume {@code REGULAR} (cobrado)
 * @param volumeGratis      volume {@code FREE_*} (gratuito)
 * @param volumePorCategoria volume por categoria faturavel (SERVICE/UTILITY/MARKETING/AUTHENTICATION)
 * @param custoPorCategoria  custo por categoria
 * @param volumePorTipo      volume por tipo (FREE_CUSTOMER_SERVICE/FREE_ENTRY_POINT/REGULAR)
 */
public record CustoResponse(
    String de,
    String ate,
    long volumeTotal,
    BigDecimal custoTotal,
    long volumeFaturavel,
    long volumeGratis,
    Map<String, Long> volumePorCategoria,
    Map<String, BigDecimal> custoPorCategoria,
    Map<String, Long> volumePorTipo
) { }
