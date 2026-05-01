package br.com.erpkit.consultas.client.exception;

public class ConsultasException extends RuntimeException {

    private final int status;

    public ConsultasException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public ConsultasException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = 502;
    }

    public int getStatus() {
        return status;
    }
}
