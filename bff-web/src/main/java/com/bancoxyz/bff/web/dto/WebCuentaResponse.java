package com.bancoxyz.bff.web.dto;

import java.math.BigDecimal;

public record WebCuentaResponse(
        Long cuentaId,
        String titular,
        Integer edad,
        String tipoCuenta,
        BigDecimal saldoActual,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado
) {
}