package com.bancoxyz.bff.mobile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MobileMovimientoResponse(
        LocalDate fecha,
        String tipo,
        BigDecimal monto
) {
}