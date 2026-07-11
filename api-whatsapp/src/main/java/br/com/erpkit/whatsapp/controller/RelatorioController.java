package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.ResumoUsoResponse;
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

    public RelatorioController(RelatorioUsoService service) {
        this.service = service;
    }

    @GetMapping("/resumo")
    public ResponseEntity<ResumoUsoResponse> resumo(
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate) {
        Instant ateI = parse(ate, Instant.now());
        Instant deI = parse(de, ateI.minus(30, ChronoUnit.DAYS));
        return ResponseEntity.ok(service.resumo(deI, ateI));
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
