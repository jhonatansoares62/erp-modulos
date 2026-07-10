package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.service.MetaConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Config Meta em runtime (WHATS-CONFIG). Dois endpoints sob {@code /api/whatsapp/config},
 * protegidos pelo {@code ApiKeyFilter} (path interno do ERP, nao publico).
 *
 * <ul>
 *   <li>{@code GET} — devolve a config Meta EFETIVA com secrets mascarados (booleans).</li>
 *   <li>{@code PUT} — grava as credenciais (atualizacao parcial) e reaplica no bean
 *       vivo; o modulo passa a enviar/responder sem restart.</li>
 * </ul>
 *
 * <p>O ERP proxeia estes endpoints em {@code /api/modulos/whatsapp/config}; a tela
 * Configuracoes -> Modulos -> WhatsApp e a UI dessas credenciais.
 */
@RestController
@RequestMapping("/api/whatsapp/config")
public class ConfigController {

    private final MetaConfigService configService;

    public ConfigController(MetaConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<MetaConfigResponse> obter() {
        return ResponseEntity.ok(configService.atual());
    }

    @PutMapping
    public ResponseEntity<MetaConfigResponse> salvar(@Valid @RequestBody MetaConfigRequest req) {
        return ResponseEntity.ok(configService.atualizar(req));
    }
}
