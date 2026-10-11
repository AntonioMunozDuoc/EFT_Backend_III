package com.bancoxyz.common.event;

import java.util.List;

/** El evento no cumple el contrato JSON Schema (o no es JSON valido). */
public class ContratoEventoException extends RuntimeException {

    private final List<String> errores;

    public ContratoEventoException(String mensaje, List<String> errores) {
        super(mensaje + ": " + errores);
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
