package br.com.erpkit.contabil.fiscal.controller;

import br.com.erpkit.contabil.fiscal.dto.ApuracaoFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.FiscalConfigDTO;
import br.com.erpkit.contabil.fiscal.dto.MemoriaFiscalResponse;
import br.com.erpkit.contabil.fiscal.dto.ReceitaHistoricaDTO;
import br.com.erpkit.contabil.fiscal.model.FiscalConfig;
import br.com.erpkit.contabil.fiscal.service.FiscalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** Pacote fiscal (Simples Nacional) — config, apuração, memória de cálculo e receita histórica. */
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

    /** Apuração da alíquota do mês corrente (RBT12, Fator R, anexo efetivo, faixa, alíquota efetiva). */
    @GetMapping("/apuracao")
    public ResponseEntity<ApuracaoFiscalResponse> apuracao() {
        return ResponseEntity.ok(fiscalService.apurar());
    }

    /** Memória de cálculo do Simples por competência (default: mês corrente). */
    @GetMapping("/memoria")
    public ResponseEntity<MemoriaFiscalResponse> memoria(@RequestParam(required = false) String competencia) {
        YearMonth ym = (competencia != null && !competencia.isBlank()) ? YearMonth.parse(competencia) : YearMonth.now();
        return ResponseEntity.ok(fiscalService.memoria(ym));
    }

    /** Receita bruta histórica (parâmetro fiscal de empresa migrando), ordenada por competência. */
    @GetMapping("/receita-historica")
    public ResponseEntity<List<ReceitaHistoricaDTO>> receitaHistorica() {
        return ResponseEntity.ok(fiscalService.listarReceitaHistorica());
    }

    /** REPLACE-ALL do histórico (array de 12 itens; upsert por competência). */
    @PutMapping("/receita-historica")
    public ResponseEntity<List<ReceitaHistoricaDTO>> salvarReceitaHistorica(@RequestBody List<ReceitaHistoricaDTO> itens) {
        return ResponseEntity.ok(fiscalService.salvarReceitaHistorica(itens));
    }
}
