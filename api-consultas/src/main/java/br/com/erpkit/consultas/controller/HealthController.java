package br.com.erpkit.consultas.controller;

import br.com.erpkit.shared.dto.HealthResponse;
import br.com.erpkit.shared.dto.InfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HealthController {

    @Value("${modulo.versao:1.0.0}")
    private String versao;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "consultas", versao));
    }

    @GetMapping("/api/info")
    public ResponseEntity<InfoResponse> info() {
        return ResponseEntity.ok(new InfoResponse(
                "consultas",
                versao,
                "Consultas externas (CEP via ViaCEP/BrasilAPI, CNPJ via BrasilAPI) com cache local",
                List.of("cep:lookup", "cnpj:lookup", "cache:caffeine")
        ));
    }
}
