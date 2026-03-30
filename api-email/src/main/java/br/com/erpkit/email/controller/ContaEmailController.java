package br.com.erpkit.email.controller;

import br.com.erpkit.email.dto.ContaEmailCreateDTO;
import br.com.erpkit.email.dto.ContaEmailResponse;
import br.com.erpkit.email.dto.PresetSmtpResponse;
import br.com.erpkit.email.service.ContaEmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contas")
public class ContaEmailController {

    private final ContaEmailService contaEmailService;

    public ContaEmailController(ContaEmailService contaEmailService) {
        this.contaEmailService = contaEmailService;
    }

    @PostMapping
    public ResponseEntity<ContaEmailResponse> criar(@Valid @RequestBody ContaEmailCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contaEmailService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaEmailResponse> atualizar(@PathVariable Long id, @Valid @RequestBody ContaEmailCreateDTO dto) {
        return ResponseEntity.ok(contaEmailService.atualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaEmailResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(contaEmailService.buscar(id));
    }

    @GetMapping
    public ResponseEntity<List<ContaEmailResponse>> listar() {
        return ResponseEntity.ok(contaEmailService.listar());
    }

    @PutMapping("/{id}/padrao")
    public ResponseEntity<ContaEmailResponse> definirPadrao(@PathVariable Long id) {
        return ResponseEntity.ok(contaEmailService.definirPadrao(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        contaEmailService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/presets")
    public ResponseEntity<List<PresetSmtpResponse>> listarPresets() {
        return ResponseEntity.ok(contaEmailService.listarPresets());
    }
}
