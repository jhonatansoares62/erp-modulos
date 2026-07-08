package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.DasResponse;
import br.com.erpkit.contabil.dto.PagamentoDasResponse;
import br.com.erpkit.contabil.dto.PagarDasRequest;
import br.com.erpkit.contabil.security.ContabilAuthFilter;
import br.com.erpkit.contabil.service.DasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DAS nativo do módulo. Aceita as duas credenciais do {@link ContabilAuthFilter}: JWT (contador,
 * origem "app") e X-API-Key (ERP delegando, origem "erp"). A origem é inferida pela presença do
 * contador autenticado.
 */
@RestController
@RequestMapping("/v1/das")
public class DasController {

    private final DasService dasService;

    public DasController(DasService dasService) {
        this.dasService = dasService;
    }

    @GetMapping
    public ResponseEntity<DasResponse> consultar(@RequestParam(required = false) String competencia) {
        return ResponseEntity.ok(dasService.consultar(competencia));
    }

    @PostMapping("/pagar")
    public ResponseEntity<PagamentoDasResponse> pagar(
            @Valid @RequestBody PagarDasRequest req,
            @RequestAttribute(name = ContabilAuthFilter.ATTR_EMAIL, required = false) String contadorEmail) {
        String origem = contadorEmail != null ? "app" : "erp";
        return ResponseEntity.status(HttpStatus.CREATED).body(dasService.pagar(req, origem));
    }
}
