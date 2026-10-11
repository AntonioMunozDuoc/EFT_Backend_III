package com.bancoxyz.cuentas.exception;

import com.bancoxyz.common.error.ApiErrorResponse;
import com.bancoxyz.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de dominio de cuentas-service. Validaciones, JSON invalido y el fallback
 * general los resuelve CommonExceptionHandler (modulo bank-common), de modo que
 * todos los servicios respondan con el mismo formato.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> manejarNoEncontrado(
            RecursoNoEncontradoException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ApiErrorResponse> manejarSaldoInsuficiente(
            SaldoInsuficienteException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.CONFLICT,
                "SALDO_INSUFICIENTE",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(CuentaCerradaException.class)
    public ResponseEntity<ApiErrorResponse> manejarCuentaCerrada(
            CuentaCerradaException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.CONFLICT,
                "ESTADO_CUENTA_INVALIDO",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(ClienteInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> manejarClienteInvalido(
            ClienteInvalidoException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CLIENTE_INVALIDO",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> manejarArgumentoInvalido(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage(),
                request
        );
    }
}
