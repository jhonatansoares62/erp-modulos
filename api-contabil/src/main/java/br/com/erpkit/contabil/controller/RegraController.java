package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.service.RegraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/regras")
public class RegraController {

    private final RegraService service;

    public RegraController(RegraService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RegraLancamento>> listar(@RequestParam String evento) {
        return ResponseEntity.ok(service.listarPorEvento(evento));
    }

    @PostMapping
    public ResponseEntity<RegraLancamento> criar(@Valid @RequestBody RegraCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }
}
