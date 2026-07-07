package br.com.erpkit.contabil.fiscal.controller;

import br.com.erpkit.contabil.fiscal.dto.ApuracaoFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.FiscalConfigDTO;
import br.com.erpkit.contabil.fiscal.model.FiscalConfig;
import br.com.erpkit.contabil.fiscal.service.FiscalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pacote fiscal (Simples Nacional) — config + apuração da alíquota efetiva. */
@RestController
@RequestMapping("/v1/fiscal")
public class FiscalController {

    private final FiscalService fiscalService;

    public FiscalController(FiscalService fiscalService) {
        this.fiscalService = fiscalService;
    }

    @GetMapping("/config")
    public ResponseEntity<FiscalConfig> getConfig() {
        return ResponseEntity.ok(fiscalService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<FiscalConfig> salvar(@RequestBody FiscalConfigDTO dto) {
        return ResponseEntity.ok(fiscalService.salvar(dto));
    }

    /** Apuração da alíquota (RBT12, Fator R, anexo efetivo, faixa, alíquota efetiva). */
    @GetMapping("/apuracao")
    public ResponseEntity<ApuracaoFiscalResponse> apuracao() {
        return ResponseEntity.ok(fiscalService.apurar());
    }
}
