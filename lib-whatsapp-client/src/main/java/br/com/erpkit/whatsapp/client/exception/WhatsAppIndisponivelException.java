package br.com.erpkit.whatsapp.client.exception;

/**
 * api-whatsapp indisponivel: circuit breaker aberto, connection refused, timeout
 * exausto ou modulo desabilitado. Espelha {@code ConsultasIndisponivelException}.
 */
public class WhatsAppIndisponivelException extends WhatsAppException {

    public WhatsAppIndisponivelException(String mensagem) {
        super(503, mensagem);
    }

    public WhatsAppIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
