package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestão de eventos de negócio (contrato /v1). Ver CONTRATO-EVENTOS.md.
 *
 * <p><b>Fase 0 (scaffold):</b> stub — valida o envelope e responde 202 aceitando o
 * evento, mas ainda NÃO deduplica, casa roteiro nem gera lançamento. Isso entra na
 * Fase 1 (motor de roteiros + ledger).
 */
@RestController
@RequestMapping("/v1/eventos")
public class EventoController {

    private static final Logger log = LoggerFactory.getLogger(EventoController.class);

    @PostMapping
    public ResponseEntity<EventoRecebidoResponse> receber(@Valid @RequestBody EventoContabilRequest evento) {
        log.info("[Fase 0 stub] Evento recebido: id={}, tipo={}, origem={}, valor={} — ainda não processado",
                evento.getEventoId(), evento.getTipo(), evento.getOrigem(), evento.getValorCentavos());
        EventoRecebidoResponse resposta = new EventoRecebidoResponse(evento.getEventoId(), "pendente", null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta);
    }
}
