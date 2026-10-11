package com.bancoxyz.cuentas.dto;

import java.math.BigDecimal;

public record CuentaResponse(
        Long cuentaId,
        Long clienteId,
        String nombre,
        Integer edad,
        String tipo,
        String estado,
        BigDecimal saldoActual,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado
) {
}
