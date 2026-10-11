package com.bancoxyz.batch.processor;

/** Registro del legacy que no cumple las reglas de calidad: se OMITE (skip) y se audita. */
public class RegistroInvalidoException extends RuntimeException {

    public RegistroInvalidoException(String motivo) {
        super(motivo);
    }
}
