package com.bancoxyz.pagos.exception;

import com.bancoxyz.common.error.ApiErrorResponse;
import com.bancoxyz.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> noEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return ApiErrors.respuesta(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(PagoRechazadoException.class)
    public ResponseEntity<ApiErrorResponse> rechazado(
            PagoRechazadoException ex, HttpServletRequest request) {
        return ApiErrors.respuesta(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> argumentoInvalido(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ApiErrors.respuesta(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }
}
