package com.bancoxyz.bff.mobile.exception;

public class MobileRecursoNoEncontradoException extends RuntimeException {

    public MobileRecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}