package br.com.erpkit.whatsapp.dto;

/**
 * Linha da trilha de auditoria de acesso (LGPD item 3) — projecao de leitura.
 * {@code atendenteEmail} null = acesso via X-API-Key (ERP/sistema).
 */
public record AuditoriaResponse(
        String atendenteEmail,
        String acao,
        String telefoneAlvo,
        String criadoEm) {
}
