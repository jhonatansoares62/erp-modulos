package br.com.erpkit.whatsapp.dto;

/** Resultado de um "esquecer" DSAR (LGPD item 4). */
public record ResultadoEsquecimento(
        int mensagensAnonimizadas,
        boolean clienteRemovido,
        boolean estadoRemovido) {
}
