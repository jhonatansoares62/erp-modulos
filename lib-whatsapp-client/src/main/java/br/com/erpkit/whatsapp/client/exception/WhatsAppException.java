package br.com.erpkit.whatsapp.client.exception;

/**
 * Erro categorizado (4xx/5xx) retornado pelo api-whatsapp. Carrega o status HTTP
 * e o corpo da resposta para diagnostico. Espelha {@code ConsultasException} do
 * lib-consultas-client.
 */
public class WhatsAppException extends RuntimeException {

    private final int status;

    public WhatsAppException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public WhatsAppException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = 502;
    }

    public int getStatus() {
        return status;
    }
}
