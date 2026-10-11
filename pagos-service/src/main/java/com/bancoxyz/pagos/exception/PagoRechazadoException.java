package com.bancoxyz.pagos.exception;

/** La operacion fue rechazada por una regla de negocio (saldo, cuenta cerrada...). */
public class PagoRechazadoException extends RuntimeException {

    private final String codigo;

    public PagoRechazadoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
