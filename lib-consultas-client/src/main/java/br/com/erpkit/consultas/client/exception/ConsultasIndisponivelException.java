package br.com.erpkit.consultas.client.exception;

public class ConsultasIndisponivelException extends ConsultasException {

    public ConsultasIndisponivelException(String mensagem) {
        super(503, mensagem);
    }

    public ConsultasIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
