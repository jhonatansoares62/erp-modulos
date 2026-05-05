package br.com.erpkit.whatsapp.util;

/**
 * String constants para o campo {@code tipo} de {@code mensagens_log}.
 *
 * <p>Constants em vez de enum — flexibilidade para tipos novos do Meta sem
 * precisar release. Tipos desconhecidos persistem com {@link #DESCONHECIDO}
 * (WEB-07 + D-05 do CONTEXT.md).
 *
 * <p>Constants UPPER_SNAKE_CASE (Java convention) com VALORES lowercase
 * (matching strings que vem do envelope do Meta). Phase 5 (parser) e Phase 6
 * (orquestrador) usam estes constants.
 */
public final class TipoMensagem {
    public static final String TEXT = "text";
    public static final String INTERACTIVE_BUTTON = "interactive_button";
    public static final String INTERACTIVE_LIST = "interactive_list";
    public static final String DOCUMENT = "document";
    public static final String IMAGE = "image";
    public static final String AUDIO = "audio";
    public static final String DESCONHECIDO = "desconhecido";

    private TipoMensagem() {}
}
