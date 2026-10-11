package com.bancoxyz.cuentas.dto;

import java.math.BigDecimal;

public record OperacionResponse(
        String operationId,
        Long cuentaId,
        Long movimientoId,
        BigDecimal monto,
        BigDecimal saldoResultante,
        boolean repetida
) {
}
