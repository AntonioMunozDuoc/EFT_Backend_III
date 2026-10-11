package com.bancoxyz.bff.mobile.dto;

import java.util.List;

public record MobileResumenResponse(
        MobileCuentaResponse cuenta,
        List<MobileMovimientoResponse> ultimosMovimientos
) {
}