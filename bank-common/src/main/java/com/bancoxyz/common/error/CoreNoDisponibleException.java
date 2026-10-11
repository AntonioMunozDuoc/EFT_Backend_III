package com.bancoxyz.common.error;

/** Un servicio dependiente no responde (Circuit Breaker abierto, timeout o caida). */
public class CoreNoDisponibleException extends RuntimeException {

    public CoreNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
