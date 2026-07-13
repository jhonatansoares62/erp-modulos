package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.AuditoriaResponse;
import br.com.erpkit.whatsapp.service.AuditoriaAcessoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura da trilha de auditoria de acesso (LGPD item 3), sob {@code /api/whatsapp/auditoria}
 * — protegido pelo {@code WhatsappAuthFilter} (Bearer JWT do atendente ou X-API-Key).
 * Somente leitura, paginado, mais recente primeiro.
 */
@RestController
@RequestMapping("/api/whatsapp/auditoria")
public class AuditoriaController {

    private static final int SIZE_DEFAULT = 50;
    private static final int SIZE_MAX = 200;

    private final AuditoriaAcessoService service;

    public AuditoriaController(AuditoriaAcessoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<AuditoriaResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditoriaResponse> resultado = service
                .listar(PageRequest.of(Math.max(page, 0), sanitizarSize(size)))
                .map(a -> new AuditoriaResponse(
                        a.getAtendenteEmail(),
                        a.getAcao(),
                        a.getTelefoneAlvo(),
                        a.getCriadoEm() == null ? null : a.getCriadoEm().toString()));
        return ResponseEntity.ok(resultado);
    }

    private int sanitizarSize(int size) {
        if (size < 1) {
            return SIZE_DEFAULT;
        }
        return Math.min(size, SIZE_MAX);
    }
}
