package br.com.erpkit.whatsapp.client.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Custo/volume do WhatsApp (§11) — resposta do {@code GET /api/whatsapp/relatorios/custo},
 * autoritativo do {@code pricing_analytics} da Meta. Custo na moeda do WABA.
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
