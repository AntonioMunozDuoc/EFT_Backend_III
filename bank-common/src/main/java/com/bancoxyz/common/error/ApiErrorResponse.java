package com.bancoxyz.common.error;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estructura unica de error HTTP para Core API y los tres BFF.
 */
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String path,
        List<CampoError> detalles
) {

    public record CampoError(
            String campo,
            String mensaje
    ) {
    }
}
