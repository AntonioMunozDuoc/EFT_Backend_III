package com.bancoxyz.bff.mobile.dto;

import java.math.BigDecimal;

public record MobileCuentaResponse(
        Long cuentaId,
        String titular,
        String tipoCuenta,
        BigDecimal saldoDisponible
) {
}