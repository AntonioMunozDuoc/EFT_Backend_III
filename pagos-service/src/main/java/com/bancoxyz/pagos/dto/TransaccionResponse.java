package com.bancoxyz.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransaccionResponse(
        Long id,
        String tipo,
        Long cuentaOrigen,
        Long cuentaDestino,
        BigDecimal monto,
        String estado,
        String descripcion,
        String referencia,
        String detalleError,
        BigDecimal saldoResultante,
        LocalDateTime fecha
) {
}
