package com.bancoxyz.bff.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WebMovimientoResponse(
        Long id,
        LocalDate fecha,
        String tipo,
        BigDecimal monto,
        String descripcion
) {
}