package com.bancoxyz.bff.web.service;

import com.bancoxyz.bff.web.client.CoreApiClient;
import com.bancoxyz.bff.web.client.dto.CoreCuentaResponse;
import com.bancoxyz.bff.web.client.dto.CoreMovimientoResponse;
import com.bancoxyz.bff.web.client.dto.CoreTransaccionResponse;
import com.bancoxyz.bff.web.dto.WebCuentaResponse;
import com.bancoxyz.bff.web.dto.WebTransferenciaRequest;
import com.bancoxyz.bff.web.dto.WebDashboardResponse;
import com.bancoxyz.bff.web.dto.WebMovimientoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebCuentaService {

    private static final Logger log =
            LoggerFactory.getLogger(WebCuentaService.class);

    private final CoreApiClient coreApiClient;

    public WebCuentaService(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    public List<WebCuentaResponse> listarCuentas() {

        log.info("WEB-BFF preparando listado completo de cuentas");

        return coreApiClient.listarCuentas()
                .stream()
                .map(this::mapearCuenta)
                .toList();
    }

    public WebCuentaResponse obtenerCuenta(Long cuentaId) {

        log.info(
                "WEB-BFF preparando detalle de cuenta {}",
                cuentaId
        );

        return mapearCuenta(
                coreApiClient.obtenerCuenta(cuentaId)
        );
    }

    public List<WebMovimientoResponse> obtenerMovimientos(Long cuentaId) {

        log.info(
                "WEB-BFF preparando historial de cuenta {}",
                cuentaId
        );

        return coreApiClient.obtenerMovimientos(cuentaId)
                .stream()
                .map(this::mapearMovimiento)
                .toList();
    }

    public WebDashboardResponse obtenerDashboard(Long cuentaId) {

        log.info(
                "WEB-BFF construyendo dashboard para cuenta {}",
                cuentaId
        );

        WebCuentaResponse cuenta =
                obtenerCuenta(cuentaId);

        List<WebMovimientoResponse> movimientos =
                obtenerMovimientos(cuentaId);

        return new WebDashboardResponse(
                cuenta,
                movimientos,
                movimientos.size()
        );
    }

    public CoreTransaccionResponse transferir(WebTransferenciaRequest request) {

        log.info("WEB-BFF solicitando transferencia {} -> {}",
                request.cuentaOrigen(), request.cuentaDestino());

        return coreApiClient.transferir(request);
    }

    private WebCuentaResponse mapearCuenta(
            CoreCuentaResponse cuenta) {

        return new WebCuentaResponse(
                cuenta.cuentaId(),
                cuenta.nombre(),
                cuenta.edad(),
                cuenta.tipo(),
                cuenta.saldoActual(),
                cuenta.tasaInteres(),
                cuenta.interesCalculado()
        );
    }

    private WebMovimientoResponse mapearMovimiento(
            CoreMovimientoResponse movimiento) {

        return new WebMovimientoResponse(
                movimiento.id(),
                movimiento.fecha(),
                movimiento.tipoMovimiento(),
                movimiento.monto(),
                movimiento.descripcion()
        );
    }
}