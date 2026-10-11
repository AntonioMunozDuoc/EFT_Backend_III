package com.bancoxyz.pagos.client;

import java.math.BigDecimal;

/** Respuesta de cuentas-service al aplicar un debito/credito (contrato HTTP, no clase compartida). */
public record OperacionDto(
        String operationId,
        Long cuentaId,
        Long movimientoId,
        BigDecimal monto,
        BigDecimal saldoResultante,
        boolean repetida
) {
}
