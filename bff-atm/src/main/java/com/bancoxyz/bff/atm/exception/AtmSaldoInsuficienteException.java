package com.bancoxyz.bff.atm.exception;

public class AtmSaldoInsuficienteException extends RuntimeException {

    public AtmSaldoInsuficienteException(String mensaje) {
        super(mensaje);
    }
}