package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.CustoResponse;
import br.com.erpkit.whatsapp.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.service.MetaAnalyticsClient;
import br.com.erpkit.whatsapp.service.RelatorioUsoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Relatorios de uso do WhatsApp para o ERP (V7 §12) — rodam em PRODUCAO sob
 * {@code /api/whatsapp/*} (protegidos por {@code ApiKeyFilter}), ao contrario do
 * {@code /monitor} (dev/meta). Thin wrapper sobre {@link RelatorioUsoService}.
 *
 * <p>{@code de}/{@code ate} sao ISO-8601 opcionais (ex.: {@code 2026-07-01T00:00:00Z});
 * ausentes/invalidos caem no default: ultimos 30 dias ate agora.
 */
@RestController
@RequestMapping("/api/whatsapp/relatorios")
public class RelatorioController {

    private final RelatorioUsoService service;
    private final MetaAnalyticsClient metaAnalyticsClient;

    public RelatorioController(RelatorioUsoService service, MetaAnalyticsClient metaAnalyticsClient) {
        this.service = service;
        this.metaAnalyticsClient = metaAnalyticsClient;
    }

    @GetMapping("/resumo")
    public ResponseEntity<ResumoUsoResponse> resumo(
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate) {
        Instant ateI = parse(ate, Instant.now());
        Instant deI = parse(de, ateI.minus(30, ChronoUnit.DAYS));
        return ResponseEntity.ok(service.resumo(deI, ateI));
    }

    /**
     * Custo/volume autoritativo direto do {@code pricing_analytics} da Meta (§11).
     * {@code granularity}: DAILY (default) | HALF_HOUR | MONTHLY.
     */
    @GetMapping("/custo")
    public ResponseEntity<CustoResponse> custo(
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            @RequestParam(required = false, defaultValue = "DAILY") String granularity) {
        Instant ateI = parse(ate, Instant.now());
        Instant deI = parse(de, ateI.minus(30, ChronoUnit.DAYS));
        return ResponseEntity.ok(metaAnalyticsClient.custo(deI, ateI, granularity));
    }

    private static Instant parse(String iso, Instant fallback) {
        if (iso == null || iso.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
