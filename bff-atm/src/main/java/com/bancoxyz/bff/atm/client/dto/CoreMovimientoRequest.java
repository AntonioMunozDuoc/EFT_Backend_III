package com.bancoxyz.bff.atm.client.dto;

import java.math.BigDecimal;

/** Solicitud de retiro hacia pagos-service. */
public record CoreMovimientoRequest(
        Long cuentaId,
        BigDecimal monto,
        String canal,
        String referencia
) {
}
