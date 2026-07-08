package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class PagarDasRequest {

    @NotBlank(message = "Competência é obrigatória")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Competência deve estar no formato AAAA-MM")
    private String competencia;

    @Positive(message = "Valor do DAS deve ser maior que zero")
    private long valorCentavos;

    @NotBlank(message = "Conta de liquidação é obrigatória")
    private String contaLiquidacao;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataPagamento;

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }
    public String getContaLiquidacao() { return contaLiquidacao; }
    public void setContaLiquidacao(String contaLiquidacao) { this.contaLiquidacao = contaLiquidacao; }
    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }
}
