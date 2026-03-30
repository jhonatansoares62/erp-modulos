package br.com.erpkit.email.controller;

import br.com.erpkit.email.dto.EmailCreateDTO;
import br.com.erpkit.email.dto.EmailResponse;
import br.com.erpkit.email.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<EmailResponse> criar(@Valid @RequestBody EmailCreateDTO dto) {
        EmailResponse response = emailService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(emailService.buscar(id));
    }

    @GetMapping
    public ResponseEntity<Page<EmailResponse>> listar(
            @RequestParam(required = false) String status,
            Pageable pageable) {

        Page<EmailResponse> page;
        if (status != null && !status.isBlank()) {
            page = emailService.listarPorStatus(status, pageable);
        } else {
            page = emailService.listar(pageable);
        }
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}/reenviar")
    public ResponseEntity<EmailResponse> reenviar(@PathVariable Long id) {
        return ResponseEntity.ok(emailService.reenviar(id));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<EmailResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(emailService.cancelar(id));
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<Map<String, Long>> estatisticas() {
        return ResponseEntity.ok(emailService.estatisticas());
    }
}
