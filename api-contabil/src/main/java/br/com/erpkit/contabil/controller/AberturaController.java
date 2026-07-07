package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.AberturaInformadaRequest;
import br.com.erpkit.contabil.dto.AberturaResponse;
import br.com.erpkit.contabil.service.AberturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/abertura")
public class AberturaController {

    private final AberturaService aberturaService;

    public AberturaController(AberturaService aberturaService) {
        this.aberturaService = aberturaService;
    }

    /** Reaplica a abertura (informada se houver; senão o auto-aporte). Idempotente. */
    @PostMapping("/reaplicar")
    public ResponseEntity<Map<String, Integer>> reaplicar() {
        return ResponseEntity.ok(Map.of("aportes", aberturaService.reaplicar()));
    }

    /** Estado dos saldos de abertura informados (para a tela). */
    @GetMapping
    public ResponseEntity<AberturaResponse> consultar() {
        return ResponseEntity.ok(aberturaService.consultar());
    }

    /** Informa (substitui) os saldos de abertura reais e posta a abertura. */
    @PostMapping("/informar")
    public ResponseEntity<AberturaResponse> informar(@RequestBody AberturaInformadaRequest req) {
        return ResponseEntity.ok(aberturaService.informar(req));
    }
}
