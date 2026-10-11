package com.bancoxyz.bff.mobile.service;

import com.bancoxyz.bff.mobile.client.CoreApiClient;
import com.bancoxyz.bff.mobile.client.dto.CoreCuentaResponse;
import com.bancoxyz.bff.mobile.client.dto.CoreMovimientoResponse;
import com.bancoxyz.bff.mobile.dto.MobileCuentaResponse;
import com.bancoxyz.bff.mobile.dto.MobileMovimientoResponse;
import com.bancoxyz.bff.mobile.dto.MobileResumenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MobileCuentaService {

    private static final Logger log =
            LoggerFactory.getLogger(MobileCuentaService.class);

    private final CoreApiClient coreApiClient;

    public MobileCuentaService(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    public MobileCuentaResponse obtenerCuenta(Long cuentaId) {

        log.info(
                "MOBILE-BFF preparando resumen liviano de cuenta {}",
                cuentaId
        );

        return mapearCuenta(
                coreApiClient.obtenerCuenta(cuentaId)
        );
    }

    public List<MobileMovimientoResponse> obtenerUltimosMovimientos(
            Long cuentaId) {

        log.info(
                "MOBILE-BFF preparando ultimos movimientos de cuenta {}",
                cuentaId
        );

        return coreApiClient.obtenerMovimientos(cuentaId)
                .stream()
                .limit(3)
                .map(this::mapearMovimiento)
                .toList();
    }

    public MobileResumenResponse obtenerResumen(Long cuentaId) {

        log.info(
                "MOBILE-BFF construyendo resumen optimizado para cuenta {}",
                cuentaId
        );

        MobileCuentaResponse cuenta =
                obtenerCuenta(cuentaId);

        List<MobileMovimientoResponse> movimientos =
                obtenerUltimosMovimientos(cuentaId);

        return new MobileResumenResponse(
                cuenta,
                movimientos
        );
    }

    private MobileCuentaResponse mapearCuenta(
            CoreCuentaResponse cuenta) {

        return new MobileCuentaResponse(
                cuenta.cuentaId(),
                cuenta.nombre(),
                cuenta.tipo(),
                cuenta.saldoActual()
        );
    }

    private MobileMovimientoResponse mapearMovimiento(
            CoreMovimientoResponse movimiento) {

        return new MobileMovimientoResponse(
                movimiento.fecha(),
                movimiento.tipoMovimiento(),
                movimiento.monto()
        );
    }
}