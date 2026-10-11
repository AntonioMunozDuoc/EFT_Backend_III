package com.bancoxyz.bff.web.dto;

import java.util.List;

public record WebDashboardResponse(
        WebCuentaResponse cuenta,
        List<WebMovimientoResponse> movimientos,
        int totalMovimientos
) {
}