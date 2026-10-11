package com.bancoxyz.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de dominio publicado en el topico banco.movimientos.
 *
 * Contrato formal: contracts/movimiento-evento.v1.schema.json
 * (ver {@link MovimientoEventoContrato}).
 *
 * Las fechas viajan como texto ISO-8601 para que el formato de red no dependa
 * de la configuracion del serializador JSON de cada servicio.
 */
public record MovimientoEvento(
        String eventId,
        String version,
        String tipoEvento,
        Long movimientoId,
        Long cuentaId,
        String tipoMovimiento,
        BigDecimal monto,
        BigDecimal saldoResultante,
        String fechaMovimiento,
        String fechaEvento
) {

    /** Crea un evento nuevo con identificador unico y marca de tiempo UTC. */
    public static MovimientoEvento nuevo(
            String tipoEvento,
            Long movimientoId,
            Long cuentaId,
            String tipoMovimiento,
            BigDecimal monto,
            BigDecimal saldoResultante,
            LocalDate fechaMovimiento) {

        return new MovimientoEvento(
                UUID.randomUUID().toString(),
                MovimientoEventoContrato.VERSION,
                tipoEvento,
                movimientoId,
                cuentaId,
                tipoMovimiento,
                monto,
                saldoResultante,
                fechaMovimiento.toString(),
                Instant.now().toString()
        );
    }
}
