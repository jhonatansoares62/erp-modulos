package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.ResultadoRetencao;
import br.com.erpkit.whatsapp.service.RetencaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execução sob demanda do motor de retenção (LGPD item 4), sob {@code /api/whatsapp/retencao}
 * — guardado pelo {@code WhatsappAuthFilter} (JWT do atendente ou X-API-Key). O job diário
 * roda sozinho; este endpoint serve ops/DPO (e o teste E2E).
 */
@RestController
@RequestMapping("/api/whatsapp/retencao")
public class RetencaoController {

    private final RetencaoService service;

    public RetencaoController(RetencaoService service) {
        this.service = service;
    }

    @PostMapping("/executar")
    public ResponseEntity<ResultadoRetencao> executar() {
        return ResponseEntity.ok(service.executar());
    }
}
