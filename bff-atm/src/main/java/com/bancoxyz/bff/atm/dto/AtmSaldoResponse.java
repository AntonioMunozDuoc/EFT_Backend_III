package com.bancoxyz.bff.atm.dto;

import java.math.BigDecimal;

public record AtmSaldoResponse(
        Long cuentaId,
        BigDecimal saldoDisponible
) {
}