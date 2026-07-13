package br.com.erpkit.whatsapp.dto;

import java.util.List;

/** Export DSAR (LGPD item 4): todos os dados do titular (por telefone) no módulo. */
public record ExportacaoTitularResponse(
        String telefone,
        Long idClienteErp,
        String geradoEm,
        List<MensagemExportadaDTO> mensagens) {
}
