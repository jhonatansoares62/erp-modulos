package br.com.erpkit.contabil.client.exception;

/** Módulo contábil indisponível (desabilitado, offline ou circuit breaker aberto). */
public class ContabilIndisponivelException extends RuntimeException {

    public ContabilIndisponivelException(String message) {
        super(message);
    }

    public ContabilIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
