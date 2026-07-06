package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.BalanceteResponse;
import br.com.erpkit.contabil.dto.DreResponse;
import br.com.erpkit.contabil.dto.RazaoResponse;
import br.com.erpkit.contabil.service.RelatorioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/relatorios")
public class RelatorioController {

    private static final LocalDate MIN = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/balancete")
    public ResponseEntity<BalanceteResponse> balancete(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(relatorioService.balancete(nvl(de, MIN), nvl(ate, MAX)));
    }

    @GetMapping("/dre")
    public ResponseEntity<DreResponse> dre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(relatorioService.dre(nvl(de, MIN), nvl(ate, MAX)));
    }

    @GetMapping("/razao")
    public ResponseEntity<RazaoResponse> razao(
            @RequestParam String conta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(relatorioService.razao(conta, nvl(de, MIN), nvl(ate, MAX)));
    }

    private static LocalDate nvl(LocalDate v, LocalDate fallback) {
        return v == null ? fallback : v;
    }
}
