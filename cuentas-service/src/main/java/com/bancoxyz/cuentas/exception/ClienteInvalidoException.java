package com.bancoxyz.cuentas.exception;

/** El cliente indicado no existe o esta inactivo en clientes-service. */
public class ClienteInvalidoException extends RuntimeException {

    public ClienteInvalidoException(String mensaje) {
        super(mensaje);
    }
}
