package com.bancoxyz.bff.mobile.exception;

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
 * Errores de dominio del BFF Mobile. Validaciones, JSON invalido, credenciales,
 * Core no disponible (503) y el fallback general los resuelve
 * CommonExceptionHandler (bank-common) con el formato de error unico.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MobileGlobalExceptionHandler {

    @ExceptionHandler(MobileRecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> manejarNoEncontrado(
            MobileRecursoNoEncontradoException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage(),
                request
        );
    }
}
