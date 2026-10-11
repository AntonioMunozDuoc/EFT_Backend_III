package com.bancoxyz.bff.web.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CoreMovimientoResponse(
        Long id,
        Long cuentaId,
        LocalDate fecha,
        String tipoMovimiento,
        BigDecimal monto,
        String descripcion
) {
}