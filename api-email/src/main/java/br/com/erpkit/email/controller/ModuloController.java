package br.com.erpkit.email.controller;

import br.com.erpkit.shared.dto.HealthResponse;
import br.com.erpkit.shared.dto.InfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ModuloController {

    @Value("${modulo.versao:1.0.0}")
    private String versao;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "api-email", versao));
    }

    @GetMapping("/api/info")
    public ResponseEntity<InfoResponse> info() {
        return ResponseEntity.ok(new InfoResponse(
                "api-email",
                versao,
                "Módulo de envio de emails com SMTP, templates e fila",
                List.of("smtp", "templates", "fila", "agendamento", "reenvio")
        ));
    }
}
