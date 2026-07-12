package br.com.erpkit.whatsapp.dto;

/**
 * Persona injetada no callback ao ERP ({@link ComandoCallbackDTO#assistente()}).
 *
 * <p>Bloco ADITIVO/opcional — um ERP antigo que nao conhece o campo simplesmente o
 * ignora (retrocompatibilidade: modulo e ERP atualizam em tempos diferentes no
 * on-premise). Enxuto de proposito: so as variaveis que o ERP usa para renderizar as
 * mensagens de dominio (nome/emoji/tom/saudacao). As mensagens 100% genericas
 * ("nao entendi" / "fora do horario") o modulo dispara sozinho, entao nao vao aqui.
 */
public record AssistentePersonaDTO(
    String nome,
    String emoji,
    String tom,
    String saudacao
) { }
