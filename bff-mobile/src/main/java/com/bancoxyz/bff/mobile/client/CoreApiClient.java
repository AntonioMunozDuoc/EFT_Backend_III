package com.bancoxyz.bff.mobile.client;

import com.bancoxyz.bff.mobile.client.dto.CoreCuentaResponse;
import com.bancoxyz.bff.mobile.client.dto.CoreMovimientoResponse;
import com.bancoxyz.bff.mobile.exception.MobileRecursoNoEncontradoException;
import com.bancoxyz.common.error.CoreNoDisponibleException;
import com.bancoxyz.common.resilience.ResilientCall;
import com.bancoxyz.common.resilience.Causas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

/**
 * Cliente del microservicios protegido con el Circuit Breaker "coreApi" (Resilience4j).
 * Los errores funcionales (404, 4xx) no cuentan como falla de infraestructura.
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
                "MOBILE-BFF solicitando cuenta {} al microservicios",
                cuentaId
        );

        return ejecutar(() -> {
            try {
                return coreRestClient.get()
                        .uri("http://cuentas-service/api/cuentas/{cuentaId}", cuentaId)
                        .retrieve()
                        .body(CoreCuentaResponse.class);

            } catch (HttpClientErrorException.NotFound ex) {
                throw new MobileRecursoNoEncontradoException(
                        "Cuenta no encontrada: " + cuentaId
                );
            }
        });
    }

    public List<CoreMovimientoResponse> obtenerMovimientos(Long cuentaId) {

        log.info(
                "MOBILE-BFF solicitando movimientos de cuenta {} al microservicios",
                cuentaId
        );

        return ejecutar(() -> {
            try {
                return coreRestClient.get()
                        .uri(
                                "http://cuentas-service/api/cuentas/{cuentaId}/movimientos",
                                cuentaId
                        )
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        List<CoreMovimientoResponse>>() {}
                        );

            } catch (HttpClientErrorException.NotFound ex) {
                throw new MobileRecursoNoEncontradoException(
                        "Cuenta no encontrada: " + cuentaId
                );
            }
        });
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

    /**
     * Se invoca ante cualquier excepcion. Los errores funcionales se re-lanzan
     * tal cual (el breaker los ignora); el resto se traduce a 503 controlado.
     */
    private <T> T manejarFalloCore(Throwable throwable) {

        MobileRecursoNoEncontradoException noEncontrado =
                Causas.buscar(throwable, MobileRecursoNoEncontradoException.class);

        if (noEncontrado != null) {
            throw noEncontrado;
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
