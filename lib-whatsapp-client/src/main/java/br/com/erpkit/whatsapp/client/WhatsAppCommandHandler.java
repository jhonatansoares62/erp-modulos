package br.com.erpkit.whatsapp.client;

import br.com.erpkit.whatsapp.client.dto.WhatsAppComandoDto;
import br.com.erpkit.whatsapp.client.dto.WhatsAppRespostaDto;

/**
 * SPI implementada pelo ERP: 1 bean Spring por keyword de comando ({@code orcamento},
 * {@code boleto}, {@code aprovar}, ...). O {@link WhatsAppCommandRegistry} coleta todos
 * os handlers do contexto e roteia o comando entrante para o primeiro que casar.
 *
 * <p>Handler simples (1 keyword): implementa apenas {@link #getComando()} e
 * {@link #processar(WhatsAppComandoDto)}; o {@link #matches(String)} default cobre
 * match por igualdade case-insensitive.
 *
 * <p>Handler com prefixo (ex: {@code "aprovar 1234"}): sobrescreve {@link #matches(String)}:
 * <pre>{@code
 *   @Override
 *   public boolean matches(String comando) {
 *       return comando != null && comando.toLowerCase().startsWith("aprovar");
 *   }
 * }</pre>
 *
 * <p>Use {@code @Order} no bean para controlar precedencia em caso de colisao.
 */
public interface WhatsAppCommandHandler {

    /** Keyword principal do comando (ex: {@code "orcamento"}). */
    String getComando();

    /**
     * Decide se este handler trata o comando recebido. Default: igualdade
     * case-insensitive com {@link #getComando()}. Sobrescreva para prefix matching.
     */
    default boolean matches(String comando) {
        return comando != null && comando.equalsIgnoreCase(getComando());
    }

    /**
     * Processa o comando entrante e retorna a resposta a enviar. Retorne {@code null}
     * para nao responder (apenas registrar).
     */
    WhatsAppRespostaDto processar(WhatsAppComandoDto comando);
}
