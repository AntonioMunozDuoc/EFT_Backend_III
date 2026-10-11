package com.bancoxyz.common.error;

/** Usuario o contrasena incorrectos al solicitar un token. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
