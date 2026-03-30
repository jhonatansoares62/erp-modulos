package br.com.erpkit.shared.exception;

import org.springframework.http.HttpStatus;

public class ModuloException extends RuntimeException {

    private final HttpStatus status;

    public ModuloException(String mensagem) {
        super(mensagem);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public ModuloException(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
