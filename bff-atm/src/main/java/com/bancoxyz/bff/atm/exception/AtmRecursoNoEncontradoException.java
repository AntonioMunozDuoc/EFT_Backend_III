package com.bancoxyz.bff.atm.exception;

public class AtmRecursoNoEncontradoException extends RuntimeException {

    public AtmRecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}