package br.com.erpkit.whatsapp.exception;

import br.com.erpkit.shared.exception.CodigoCarrier;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;

/**
 * Falha na chamada Cloud API do Meta (D-02 CONTEXT.md + OUT-10 REQUIREMENTS.md).
 * Carrega {@link Tipo} categorizando a falha + opcionalmente {@code metaErrorCode}
 * (codigo numerico do Meta — ex: 131026 invalid phone, 131009 token expired)
 * extraido do response body.
 *
 * <p><b>Mapping (D-02):</b>
 * <table>
 *   <tr><th>Tipo</th><th>HTTP</th><th>codigo</th><th>Cenario</th></tr>
 *   <tr><td>CATEGORIA_4XX</td><td>422</td><td>META_ERROR</td><td>Meta 400/401/403</td></tr>
 *   <tr><td>INDISPONIVEL_5XX</td><td>502</td><td>META_INDISPONIVEL</td><td>Meta 5xx apos 3 retries</td></tr>
 *   <tr><td>TIMEOUT</td><td>504</td><td>META_TIMEOUT</td><td>Timeout apos 3 retries</td></tr>
 *   <tr><td>CIRCUIT_OPEN</td><td>503</td><td>CIRCUIT_OPEN</td><td>CallNotPermittedException</td></tr>
 * </table>
 *
 * <p>Implementa {@link CodigoCarrier} (lib-shared 04-01) — propagacao automatica
 * via GlobalExceptionHandler.
 */
public class MetaApiException extends ModuloException implements CodigoCarrier {

    public enum Tipo {
        CATEGORIA_4XX(HttpStatus.UNPROCESSABLE_ENTITY, "META_ERROR"),
        INDISPONIVEL_5XX(HttpStatus.BAD_GATEWAY, "META_INDISPONIVEL"),
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "META_TIMEOUT"),
        CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN");

        private final HttpStatus status;
        private final String codigo;

        Tipo(HttpStatus status, String codigo) {
            this.status = status;
            this.codigo = codigo;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getCodigo() {
            return codigo;
        }
    }

    private final Tipo tipo;
    private final Integer metaErrorCode;

    public MetaApiException(Tipo tipo, Integer metaErrorCode, String mensagem) {
        super(mensagem, tipo.getStatus());
        this.tipo = tipo;
        this.metaErrorCode = metaErrorCode;
    }

    public Tipo getTipo() {
        return tipo;
    }

    @Override
    public String getCodigo() {
        return tipo.getCodigo();
    }

    @Override
    public Integer getMetaErrorCode() {
        return metaErrorCode;
    }
}
