package com.bancoxyz.batch.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Registros de entrada (texto crudo, tal como vienen del sistema legacy) y de salida. */
public final class Modelos {

    private Modelos() {
    }

    public record MovimientoCsv(String id, String fecha, String monto, String tipo) {
    }

    public record InteresCsv(String cuentaId, String nombre, String saldo, String edad, String tipo) {
    }

    public record EstadoCsv(String cuentaId, String fecha, String transaccion, String monto, String descripcion) {
    }

    public record MovimientoDiario(
            long id, LocalDate fecha, BigDecimal monto, String tipo, String anomalia, String dataset) {
    }

    public record InteresCalculado(
            long cuentaId, String nombre, String tipo, BigDecimal saldo, Integer edad,
            BigDecimal tasaAnual, BigDecimal interesMensual, String periodo, String dataset) {
    }

    public record EstadoMovimiento(
            String clave, long cuentaId, LocalDate fecha, String transaccion,
            BigDecimal monto, String descripcion) {
    }
}
