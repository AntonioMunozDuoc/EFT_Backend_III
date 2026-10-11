package com.bancoxyz.common.error;

/**
 * Un microservicio del que depende la operacion no responde. Se traduce a
 * HTTP 503 con codigo SERVICIO_NO_DISPONIBLE (ver CommonExceptionHandler).
 */
public class ServicioNoDisponibleException extends CoreNoDisponibleException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
