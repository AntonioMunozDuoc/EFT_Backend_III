package com.bancoxyz.cuentas.exception;

/** La cuenta no admite la operacion por su estado (cerrada o con saldo pendiente). */
public class CuentaCerradaException extends RuntimeException {

    public CuentaCerradaException(String mensaje) {
        super(mensaje);
    }
}
