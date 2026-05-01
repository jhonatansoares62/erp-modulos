package br.com.erpkit.consultas.controller;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import br.com.erpkit.consultas.client.dto.FornecedorResponse;
import br.com.erpkit.consultas.service.CepService;
import br.com.erpkit.consultas.service.CnpjService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConsultasController {

    private final CepService cepService;
    private final CnpjService cnpjService;

    public ConsultasController(CepService cepService, CnpjService cnpjService) {
        this.cepService = cepService;
        this.cnpjService = cnpjService;
    }

    @GetMapping("/cep/{cep}")
    public ResponseEntity<EnderecoResponse> consultarCep(@PathVariable String cep) {
        return ResponseEntity.ok(cepService.consultar(cep));
    }

    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<FornecedorResponse> consultarCnpj(@PathVariable String cnpj) {
        return ResponseEntity.ok(cnpjService.consultar(cnpj));
    }
}
