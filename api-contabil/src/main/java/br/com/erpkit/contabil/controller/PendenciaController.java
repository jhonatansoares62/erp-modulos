package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.dto.PendenciaResponse;
import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.dto.ReprocessamentoResponse;
import br.com.erpkit.contabil.service.PendenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/pendencias")
public class PendenciaController {

    private final PendenciaService service;

    public PendenciaController(PendenciaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PendenciaResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /** Cria o roteiro e reprocessa o evento pendente (classifica uma vez, automatiza para sempre). */
    @PostMapping("/{eventoId}/salvar-regra")
    public ResponseEntity<EventoRecebidoResponse> salvarComoRegra(@PathVariable String eventoId,
                                                                  @Valid @RequestBody RegraCreateDTO dto) {
        return ResponseEntity.ok(service.salvarComoRegra(eventoId, dto));
    }

    /** Reprocessa em lote as pendências: os eventos que agora casam com alguma regra viram lançamento. */
    @PostMapping("/reprocessar")
    public ResponseEntity<ReprocessamentoResponse> reprocessar() {
        return ResponseEntity.ok(service.reprocessar());
    }
}
