package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestão de eventos de negócio (contrato /v1). Ver CONTRATO-EVENTOS.md.
 * Idempotente por eventoId. Casa o roteiro e posta o lançamento, ou pendencia o evento.
 */
@RestController
@RequestMapping("/v1/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @PostMapping
    public ResponseEntity<EventoRecebidoResponse> receber(@Valid @RequestBody EventoContabilRequest evento) {
        return ResponseEntity.ok(eventoService.receber(evento));
    }

    /** Reprocessa (estorna-e-refaz) reaplicando o roteiro atual; no-op se nada mudou. */
    @PostMapping("/reprocessar")
    public ResponseEntity<EventoRecebidoResponse> reprocessar(@Valid @RequestBody EventoContabilRequest evento) {
        return ResponseEntity.ok(eventoService.reprocessar(evento));
    }
}
