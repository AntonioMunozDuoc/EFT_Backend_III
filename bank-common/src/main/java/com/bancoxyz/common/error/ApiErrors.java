package com.bancoxyz.common.error;

import com.bancoxyz.common.error.ApiErrorResponse.CampoError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

/** Fabrica de respuestas de error con el formato estandar. */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static ResponseEntity<ApiErrorResponse> respuesta(
            HttpStatusCode status,
            String codigo,
            String mensaje,
            HttpServletRequest request) {

        return respuesta(status, codigo, mensaje, request, List.of());
    }

    public static ResponseEntity<ApiErrorResponse> respuesta(
            HttpStatusCode status,
            String codigo,
            String mensaje,
            HttpServletRequest request,
            List<CampoError> detalles) {

        ApiErrorResponse cuerpo = new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                codigo,
                mensaje,
                request.getRequestURI(),
                detalles
        );

        return ResponseEntity.status(status).body(cuerpo);
    }
}
