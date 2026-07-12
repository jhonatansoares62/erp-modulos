package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.AssistenteRequest;
import br.com.erpkit.whatsapp.dto.AssistenteResponse;
import br.com.erpkit.whatsapp.service.AssistenteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persona do assistente virtual (WHATS-ASSISTENTE). Dois endpoints sob
 * {@code /api/whatsapp/assistente}, protegidos pelo {@code ApiKeyFilter} (path
 * interno, nao publico — mesmo prefixo do {@code /api/whatsapp/config}).
 *
 * <ul>
 *   <li>{@code GET} — devolve a persona efetiva (nunca nula; defaults se nunca salva).</li>
 *   <li>{@code PUT} — grava a persona (substituicao total) e devolve a efetiva.</li>
 * </ul>
 *
 * <p>Config GENERICA, reusavel entre ERPs; a tela "Assistente" do whatsapp-app e a UI.
 * O ERP consome a persona pelo bloco "assistente" injetado no callback.
 */
@RestController
@RequestMapping("/api/whatsapp/assistente")
public class AssistenteController {

    private final AssistenteService assistenteService;

    public AssistenteController(AssistenteService assistenteService) {
        this.assistenteService = assistenteService;
    }

    @GetMapping
    public ResponseEntity<AssistenteResponse> obter() {
        return ResponseEntity.ok(assistenteService.atual());
    }

    @PutMapping
    public ResponseEntity<AssistenteResponse> salvar(@Valid @RequestBody AssistenteRequest req) {
        return ResponseEntity.ok(assistenteService.atualizar(req));
    }
}
