package com.bancoxyz.bff.web.client.dto;

import java.math.BigDecimal;

public record CoreCuentaResponse(
        Long cuentaId,
        String nombre,
        Integer edad,
        String tipo,
        BigDecimal saldoActual,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado
) {
}