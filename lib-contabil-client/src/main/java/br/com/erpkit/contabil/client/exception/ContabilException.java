package br.com.erpkit.contabil.client.exception;

/** Erro de negócio retornado pelo módulo contábil (status HTTP + corpo). */
public class ContabilException extends RuntimeException {

    private final int status;

    public ContabilException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
