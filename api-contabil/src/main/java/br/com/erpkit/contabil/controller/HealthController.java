package br.com.erpkit.contabil.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness probe. {@code /health} está nos DEFAULT_PUBLIC_PATHS do ApiKeyFilter
 * (não exige X-API-Key). Controller manual — sem Actuator na Fase 0.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "modulo", "api-contabil"
        ));
    }
}
