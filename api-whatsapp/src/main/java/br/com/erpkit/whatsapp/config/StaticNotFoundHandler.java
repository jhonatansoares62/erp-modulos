package br.com.erpkit.whatsapp.config;

import br.com.erpkit.shared.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Recurso estatico inexistente (asset morto: chunk antigo, favicon, etc.) deve virar 404 — e
 * nao 500. Sem isto, o {@code @ExceptionHandler(Exception.class)} generico do lib-shared
 * captura o {@link NoResourceFoundException} e devolve 500. Ordenado antes do handler global.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StaticNotFoundHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNaoEncontrado(NoResourceFoundException ex) {
        ErrorResponse error = new ErrorResponse(404, "Nao encontrado", "Recurso nao encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
