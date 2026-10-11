package com.bancoxyz.bff.atm.client.dto;

import java.math.BigDecimal;

/** Transaccion devuelta por pagos-service. */
public record CoreMovimientoResponse(
        Long id,
        String tipo,
        Long cuentaOrigen,
        BigDecimal monto,
        String estado,
        String referencia,
        BigDecimal saldoResultante
) {
}
