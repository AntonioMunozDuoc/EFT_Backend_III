package com.bancoxyz.bff.atm.client;

import com.bancoxyz.bff.atm.client.dto.CoreCuentaResponse;
import com.bancoxyz.bff.atm.client.dto.CoreMovimientoRequest;
import com.bancoxyz.bff.atm.client.dto.CoreMovimientoResponse;
import com.bancoxyz.bff.atm.exception.AtmRecursoNoEncontradoException;
import com.bancoxyz.bff.atm.exception.AtmSaldoInsuficienteException;
import com.bancoxyz.common.error.CoreNoDisponibleException;
import com.bancoxyz.common.resilience.ResilientCall;
import com.bancoxyz.common.resilience.Causas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Cliente del microservicios protegido con el Circuit Breaker "coreApi".
 * Tanto la consulta de saldo como el retiro pasan por el breaker: con el
 * circuito abierto el cajero responde 503 de inmediato, sin esperar timeouts.
 */
@Component
public class CoreApiClient {

    private static final Logger log =
            LoggerFactory.getLogger(CoreApiClient.class);

    private final RestClient coreRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ResilientCall resilientCall;

    public CoreApiClient(
            RestClient coreRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            ResilientCall resilientCall) {

        this.coreRestClient = coreRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.resilientCall = resilientCall;
    }

    public CoreCuentaResponse obtenerCuenta(Long cuentaId) {

        log.info(
                "ATM-BFF consultando saldo de cuenta {} en microservicios",
                cuentaId
        );

        return ejecutar(() -> obtenerCuentaDesdeCore(cuentaId));
    }

    public CoreMovimientoResponse realizarRetiro(
            Long cuentaId,
            BigDecimal monto) {

        log.info(
                "ATM-BFF solicitando retiro para cuenta {}",
                cuentaId
        );

        CoreMovimientoRequest request =
                new CoreMovimientoRequest(
                        cuentaId,
                        monto,
                        "ATM",
                        java.util.UUID.randomUUID().toString()
                );

        // La referencia es unica por solicitud: pagos-service es idempotente por referencia,
        // por eso el retiro SI puede reintentarse sin duplicar el movimiento.
        return ejecutar(() -> retiroDesdeCore(cuentaId, request), true);
    }

    private CoreCuentaResponse obtenerCuentaDesdeCore(Long cuentaId) {

        try {
            return coreRestClient.get()
                    .uri("http://cuentas-service/api/cuentas/{cuentaId}", cuentaId)
                    .retrieve()
                    .body(CoreCuentaResponse.class);

        } catch (HttpClientErrorException.NotFound ex) {

            throw new AtmRecursoNoEncontradoException(
                    "Cuenta no encontrada: " + cuentaId
            );
        }
    }

    private CoreMovimientoResponse retiroDesdeCore(
            Long cuentaId,
            CoreMovimientoRequest request) {

        try {
            return coreRestClient.post()
                    .uri("http://pagos-service/api/pagos/retiros")
                    .body(request)
                    .retrieve()
                    .body(CoreMovimientoResponse.class);

        } catch (HttpClientErrorException.NotFound ex) {

            throw new AtmRecursoNoEncontradoException(
                    "Cuenta no encontrada: " + cuentaId
            );

        } catch (HttpClientErrorException.Conflict ex) {

            if (ex.getResponseBodyAsString().contains("SALDO_INSUFICIENTE")) {
                throw new AtmSaldoInsuficienteException(
                        "Saldo insuficiente para realizar el retiro"
                );
            }
            throw ex;
        }
    }

    /** Consultas (GET): idempotentes, se pueden reintentar. */
    private <T> T ejecutar(Supplier<T> operacion) {
        return ejecutar(operacion, true);
    }

    /**
     * CircuitBreaker( Retry( Bulkhead( llamada ) ) ).
     * reintentable=false para operaciones que modifican datos.
     */
    private <T> T ejecutar(Supplier<T> operacion, boolean reintentable) {
        return circuitBreakerFactory
                .create("coreApi")
                .run(
                        resilientCall.decorar(operacion, reintentable),
                        this::manejarFalloCore
                );
    }

    private <T> T manejarFalloCore(Throwable throwable) {

        AtmRecursoNoEncontradoException noEncontrado =
                Causas.buscar(throwable, AtmRecursoNoEncontradoException.class);

        if (noEncontrado != null) {
            throw noEncontrado;
        }

        AtmSaldoInsuficienteException saldoInsuficiente =
                Causas.buscar(throwable, AtmSaldoInsuficienteException.class);

        if (saldoInsuficiente != null) {
            throw saldoInsuficiente;
        }

        HttpClientErrorException rechazo =
                Causas.buscar(throwable, HttpClientErrorException.class);

        if (rechazo != null) {
            throw rechazo;
        }

        if (Causas.buscar(throwable, io.github.resilience4j.bulkhead.BulkheadFullException.class) != null) {
            log.warn("Bulkhead: demasiadas llamadas concurrentes al microservicios, solicitud rechazada");
        }

        log.error(
                "Circuit Breaker: microservicios no disponible. Tipo de error: {}",
                throwable.getClass().getName(),
                throwable
        );

        throw new CoreNoDisponibleException(
                "El servicio central del banco no se encuentra disponible temporalmente"
        );
    }
}
