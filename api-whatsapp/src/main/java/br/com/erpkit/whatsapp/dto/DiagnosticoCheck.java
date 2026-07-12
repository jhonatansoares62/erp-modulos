package br.com.erpkit.whatsapp.dto;

/**
 * Resultado de uma checagem individual do diagnostico (Meta ou ERP) —
 * usado em {@link DiagnosticoResponse}.
 *
 * @param ok      {@code true} se a checagem passou
 * @param detalhe mensagem legivel em PT-BR (motivo do sucesso ou da falha)
 */
public record DiagnosticoCheck(
        boolean ok,
        String detalhe
) {
}
