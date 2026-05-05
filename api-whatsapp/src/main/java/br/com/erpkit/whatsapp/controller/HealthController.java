package br.com.erpkit.whatsapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness probe stub. {@code /health} esta nos
 * {@link br.com.erpkit.shared.security.ApiKeyFilter} {@code DEFAULT_PUBLIC_PATHS}
 * — nao exige X-API-Key.
 *
 * <p>Phase 1 retorna estado static. Phase 4/6 (WHATS-17) podera incluir
 * validacao de WABA subscription via Graph API (PITFALLS C-12 — shadow delivery).
 *
 * <p>NAO usa Spring Boot Actuator — adiciona dependencia sem justificativa em
 * Phase 1. Controller manual e suficiente.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "modulo", "api-whatsapp"
        ));
    }
}
