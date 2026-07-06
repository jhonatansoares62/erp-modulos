package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.ApurarInventarioRequest;
import br.com.erpkit.contabil.dto.InventarioApuracaoResponse;
import br.com.erpkit.contabil.dto.InventarioBaseResponse;
import br.com.erpkit.contabil.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    public ResponseEntity<List<InventarioApuracaoResponse>> listar() {
        return ResponseEntity.ok(inventarioService.listar());
    }

    @GetMapping("/base")
    public ResponseEntity<InventarioBaseResponse> base(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(inventarioService.base(de, ate));
    }

    @PostMapping("/apurar")
    public ResponseEntity<InventarioApuracaoResponse> apurar(@Valid @RequestBody ApurarInventarioRequest req) {
        return ResponseEntity.ok(inventarioService.apurar(req));
    }
}
