package br.com.erpkit.whatsapp.dto;

/** Uma mensagem no export DSAR (LGPD item 4) — conteúdo já decifrado. */
public record MensagemExportadaDTO(
        String direcao,
        String tipo,
        String conteudo,
        String timestamp,
        String status) {
}
