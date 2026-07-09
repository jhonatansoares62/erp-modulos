package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.dto.EncerramentoPreviewResponse;
import br.com.erpkit.contabil.dto.EncerramentoResponse;
import br.com.erpkit.contabil.dto.EncerrarExercicioDTO;
import br.com.erpkit.contabil.dto.FecharPeriodoDTO;
import br.com.erpkit.contabil.model.PeriodoFechado;
import br.com.erpkit.contabil.service.PeriodoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/periodos")
public class PeriodoController {

    private final PeriodoService service;

    public PeriodoController(PeriodoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PeriodoFechado>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping("/fechar")
    public ResponseEntity<PeriodoFechado> fechar(@Valid @RequestBody FecharPeriodoDTO dto) {
        PeriodoFechado p = service.fecharMensal(dto.getCompetencia(), dto.getFechadoPor());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @GetMapping("/encerramento/preview")
    public ResponseEntity<EncerramentoPreviewResponse> previewEncerramento(@RequestParam int ano) {
        return ResponseEntity.ok(service.previewEncerramento(ano));
    }

    @PostMapping("/encerrar-exercicio")
    public ResponseEntity<EncerramentoResponse> encerrarExercicio(@Valid @RequestBody EncerrarExercicioDTO dto) {
        return ResponseEntity.ok(service.encerrarExercicio(dto.getAno(), dto.getPor()));
    }
}
