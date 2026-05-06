package br.com.erpkit.shared.exception;

import br.com.erpkit.shared.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests do {@link GlobalExceptionHandler} para a infraestrutura {@link CodigoCarrier}
 * (Phase 4 Plan 01 — habilita JanelaConversaFechadaException + MetaApiException no handler
 * sem acoplar lib-shared a pacotes de api-whatsapp).
 *
 * <p>Cobertura:
 * <ul>
 *   <li>Regressao: ModuloException SEM CodigoCarrier devolve ErrorResponse com codigo/metaErrorCode null
 *       (preserva backward-compat com api-email/api-storage/api-consultas).</li>
 *   <li>Propagacao: ModuloException com CodigoCarrier propaga apenas codigo (metaErrorCode null).</li>
 *   <li>Propagacao: ModuloException com CodigoCarrier propaga codigo + metaErrorCode (caso Meta API).</li>
 * </ul>
 */
class GlobalExceptionHandlerCodigoCarrierTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ModuloException sem CodigoCarrier devolve ErrorResponse compativel (codigo/meta null)")
    void modulo_exception_sem_codigo_carrier_devolve_response_compativel() {
        ResponseEntity<ErrorResponse> resp = handler.handleModuloException(
                new ModuloException("teste", HttpStatus.NOT_FOUND));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus()).isEqualTo(404);
        assertThat(resp.getBody().getMensagem()).isEqualTo("teste");
        assertThat(resp.getBody().getCodigo()).isNull();
        assertThat(resp.getBody().getMetaErrorCode()).isNull();
    }

    @Test
    @DisplayName("ModuloException com CodigoCarrier propaga codigo (metaErrorCode null)")
    void modulo_exception_com_codigo_carrier_propaga_codigo_e_meta_error_code() {
        class FakeException extends ModuloException implements CodigoCarrier {
            FakeException() {
                super("janela fechada", HttpStatus.CONFLICT);
            }

            @Override
            public String getCodigo() {
                return "JANELA_24H_FECHADA";
            }

            @Override
            public Integer getMetaErrorCode() {
                return null;
            }
        }

        ResponseEntity<ErrorResponse> resp = handler.handleModuloException(new FakeException());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCodigo()).isEqualTo("JANELA_24H_FECHADA");
        assertThat(resp.getBody().getMetaErrorCode()).isNull();
    }

    @Test
    @DisplayName("ModuloException com CodigoCarrier+metaErrorCode propaga ambos os campos")
    void modulo_exception_com_meta_error_code_propaga_ambos() {
        class MetaFake extends ModuloException implements CodigoCarrier {
            MetaFake() {
                super("meta 4xx", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            @Override
            public String getCodigo() {
                return "META_ERROR";
            }

            @Override
            public Integer getMetaErrorCode() {
                return 131026;
            }
        }

        ResponseEntity<ErrorResponse> resp = handler.handleModuloException(new MetaFake());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCodigo()).isEqualTo("META_ERROR");
        assertThat(resp.getBody().getMetaErrorCode()).isEqualTo(131026);
    }
}
