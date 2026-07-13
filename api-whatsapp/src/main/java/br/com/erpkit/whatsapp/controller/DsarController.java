package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.ExportacaoTitularResponse;
import br.com.erpkit.whatsapp.dto.ResultadoEsquecimento;
import br.com.erpkit.whatsapp.service.DsarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DSAR — direitos do titular (LGPD item 4), sob {@code /api/whatsapp/titular} — guardado
 * pelo {@code WhatsappAuthFilter} (JWT do atendente ou X-API-Key) e auditado (interceptor).
 *
 * <ul>
 *   <li>{@code GET  /{telefone}/exportar} — dados do titular (mensagens decifradas).</li>
 *   <li>{@code POST /{telefone}/esquecer} — anonimiza + remove vínculos do titular.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/whatsapp/titular")
public class DsarController {

    private final DsarService service;

    public DsarController(DsarService service) {
        this.service = service;
    }

    @GetMapping("/{telefone}/exportar")
    public ResponseEntity<ExportacaoTitularResponse> exportar(@PathVariable String telefone) {
        return ResponseEntity.ok(service.exportar(telefone));
    }

    @PostMapping("/{telefone}/esquecer")
    public ResponseEntity<ResultadoEsquecimento> esquecer(@PathVariable String telefone) {
        return ResponseEntity.ok(service.esquecer(telefone));
    }
}
