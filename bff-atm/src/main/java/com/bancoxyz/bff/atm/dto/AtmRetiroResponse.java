package com.bancoxyz.bff.atm.dto;

import java.math.BigDecimal;

public record AtmRetiroResponse(
        Long cuentaId,
        BigDecimal montoRetirado,
        BigDecimal saldoDisponible,
        String estado
) {
}