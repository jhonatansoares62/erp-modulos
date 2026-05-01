package br.com.erpkit.consultas.service.provider;

public class ProviderIndisponivelException extends RuntimeException {

    private final String provedor;

    public ProviderIndisponivelException(String provedor, Throwable causa) {
        super("Provedor " + provedor + " indisponível: " + causa.getMessage(), causa);
        this.provedor = provedor;
    }

    public String getProvedor() {
        return provedor;
    }
}
