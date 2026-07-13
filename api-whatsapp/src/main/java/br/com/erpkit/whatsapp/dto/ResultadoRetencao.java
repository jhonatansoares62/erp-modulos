package br.com.erpkit.whatsapp.dto;

/** Resultado de uma execução do motor de retenção (LGPD item 4). */
public record ResultadoRetencao(int mensagensAnonimizadas, int midiasPurgadas) {
}
