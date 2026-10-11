package com.bancoxyz.bff.web.client.dto;

import java.math.BigDecimal;

/** Transaccion devuelta por pagos-service. */
public record CoreTransaccionResponse(
        Long id,
        String tipo,
        Long cuentaOrigen,
        Long cuentaDestino,
        BigDecimal monto,
        String estado,
        String referencia,
        BigDecimal saldoResultante
) {
}
