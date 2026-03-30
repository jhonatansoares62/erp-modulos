package br.com.erpkit.shared.exception;

import br.com.erpkit.shared.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Deve tratar ModuloException com status padrao (422)")
    void deveTratarModuloExceptionComStatusPadrao() {
        ModuloException ex = new ModuloException("Erro de negocio");

        ResponseEntity<ErrorResponse> response = handler.handleModuloException(ex);

        assertEquals(422, response.getStatusCode().value(), "Status deve ser 422");
        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertEquals(422, response.getBody().getStatus(), "Status do body deve ser 422");
        assertEquals("Erro de negocio", response.getBody().getMensagem(), "Mensagem deve corresponder");
    }

    @Test
    @DisplayName("Deve tratar ModuloException com status NOT_FOUND")
    void deveTratarModuloExceptionComStatusNotFound() {
        ModuloException ex = new ModuloException("Recurso nao encontrado", HttpStatus.NOT_FOUND);

        ResponseEntity<ErrorResponse> response = handler.handleModuloException(ex);

        assertEquals(404, response.getStatusCode().value(), "Status deve ser 404");
        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertEquals(404, response.getBody().getStatus(), "Status do body deve ser 404");
        assertEquals("Not Found", response.getBody().getErro(), "Erro deve ser Not Found");
        assertEquals("Recurso nao encontrado", response.getBody().getMensagem(), "Mensagem deve corresponder");
    }

    @Test
    @DisplayName("Deve tratar ModuloException com status BAD_REQUEST")
    void deveTratarModuloExceptionComStatusBadRequest() {
        ModuloException ex = new ModuloException("Dados invalidos", HttpStatus.BAD_REQUEST);

        ResponseEntity<ErrorResponse> response = handler.handleModuloException(ex);

        assertEquals(400, response.getStatusCode().value(), "Status deve ser 400");
        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertEquals("Dados invalidos", response.getBody().getMensagem(), "Mensagem deve corresponder");
    }

    @Test
    @DisplayName("Deve tratar MethodArgumentNotValidException com erros de campo")
    void deveTratarValidationException() throws NoSuchMethodException {
        // Criar binding result com erros de campo
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "nome", "Nome e obrigatorio"));
        bindingResult.addError(new FieldError("dto", "email", "Email invalido"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("deveTratarValidationException"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertEquals(400, response.getStatusCode().value(), "Status deve ser 400");
        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertEquals(400, response.getBody().getStatus(), "Status do body deve ser 400");
        assertTrue(response.getBody().getMensagem().contains("campos"), "Mensagem deve indicar validacao nos campos");
        assertNotNull(response.getBody().getCampos(), "Campos nao devem ser nulos");
        assertEquals(2, response.getBody().getCampos().size(), "Deve ter 2 campos com erro");
        assertEquals("Nome e obrigatorio", response.getBody().getCampos().get("nome"), "Erro do campo nome deve corresponder");
        assertEquals("Email invalido", response.getBody().getCampos().get("email"), "Erro do campo email deve corresponder");
    }

    @Test
    @DisplayName("Deve tratar Exception generica com status 500")
    void deveTratarExceptionGenerica() {
        Exception ex = new RuntimeException("Erro inesperado no sistema");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(500, response.getStatusCode().value(), "Status deve ser 500");
        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertEquals(500, response.getBody().getStatus(), "Status do body deve ser 500");
        assertEquals("Erro interno", response.getBody().getErro(), "Erro deve ser Erro interno");
        assertEquals("Erro inesperado no sistema", response.getBody().getMensagem(), "Mensagem deve corresponder");
    }

    @Test
    @DisplayName("Deve incluir timestamp na resposta de erro")
    void deveIncluirTimestampNaResposta() {
        ModuloException ex = new ModuloException("Teste timestamp");

        ResponseEntity<ErrorResponse> response = handler.handleModuloException(ex);

        assertNotNull(response.getBody(), "Body nao deve ser nulo");
        assertNotNull(response.getBody().getTimestamp(), "Timestamp nao deve ser nulo");
    }
}
