package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.LancamentoResponse;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.service.LancamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/lancamentos")
public class LancamentoController {

    private final LancamentoService service;

    public LancamentoController(LancamentoService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LancamentoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PostMapping("/{id}/estorno")
    public ResponseEntity<LancamentoResponse> estornar(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        String motivo = body == null ? null : body.get("motivo");
        Lancamento estorno = service.estornar(id, motivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.toResponse(estorno));
    }
}
