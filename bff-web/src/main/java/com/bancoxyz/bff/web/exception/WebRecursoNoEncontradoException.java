package com.bancoxyz.bff.web.exception;

public class WebRecursoNoEncontradoException extends RuntimeException {

    public WebRecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}