package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.ContaArvoreNode;
import br.com.erpkit.contabil.dto.ContaCreateDTO;
import br.com.erpkit.contabil.dto.ContaResponse;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.service.ContaContabilService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/contas")
public class ContaContabilController {

    private final ContaContabilService service;

    public ContaContabilController(ContaContabilService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ContaResponse>> listar() {
        List<ContaResponse> contas = service.listar().stream().map(ContaResponse::de).toList();
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/arvore")
    public ResponseEntity<List<ContaArvoreNode>> arvore() {
        return ResponseEntity.ok(service.arvore());
    }

    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody ContaCreateDTO dto) {
        ContaContabil criada = service.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContaResponse.de(criada));
    }
}
