package com.bancoxyz.bff.atm.service;

import com.bancoxyz.bff.atm.client.CoreApiClient;
import com.bancoxyz.bff.atm.client.dto.CoreCuentaResponse;
import com.bancoxyz.bff.atm.dto.AtmRetiroRequest;
import com.bancoxyz.bff.atm.dto.AtmRetiroResponse;
import com.bancoxyz.bff.atm.dto.AtmSaldoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AtmService {

    private static final Logger log =
            LoggerFactory.getLogger(AtmService.class);

    private final CoreApiClient coreApiClient;

    public AtmService(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    public AtmSaldoResponse consultarSaldo(Long cuentaId) {

        log.info(
                "ATM-BFF preparando consulta de saldo para cuenta {}",
                cuentaId
        );

        CoreCuentaResponse cuenta =
                coreApiClient.obtenerCuenta(cuentaId);

        return new AtmSaldoResponse(
                cuenta.cuentaId(),
                cuenta.saldoActual()
        );
    }

    public AtmRetiroResponse retirar(AtmRetiroRequest request) {

        log.info(
                "ATM-BFF procesando retiro para cuenta {}",
                request.cuentaId()
        );

        coreApiClient.realizarRetiro(
                request.cuentaId(),
                request.monto()
        );

        CoreCuentaResponse cuentaActualizada =
                coreApiClient.obtenerCuenta(
                        request.cuentaId()
                );

        return new AtmRetiroResponse(
                request.cuentaId(),
                request.monto(),
                cuentaActualizada.saldoActual(),
                "APROBADO"
        );
    }
}