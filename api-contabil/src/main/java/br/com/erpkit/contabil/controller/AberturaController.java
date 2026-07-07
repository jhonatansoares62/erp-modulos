package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.service.AberturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** Reaplica o aporte de capital que zera contas de liquidez negativas. Idempotente. */
    @PostMapping("/reaplicar")
    public ResponseEntity<Map<String, Integer>> reaplicar() {
        return ResponseEntity.ok(Map.of("aportes", aberturaService.reaplicar()));
    }
}
