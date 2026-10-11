package com.bancoxyz.pagos.client;

import com.bancoxyz.common.error.ServicioNoDisponibleException;
import com.bancoxyz.common.resilience.Causas;
import com.bancoxyz.common.resilience.ResilientCall;
import com.bancoxyz.pagos.exception.PagoRechazadoException;
import com.bancoxyz.pagos.exception.RecursoNoEncontradoException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Cliente de cuentas-service protegido con CircuitBreaker("cuentasApi") +
 * Retry + Bulkhead. Como cada operacion lleva un operationId, TODAS (tambien
 * los POST) son seguras de reintentar: cuentas-service las aplica una sola vez.
 */
@Component
public class CuentasClient {

    private static final Logger log = LoggerFactory.getLogger(CuentasClient.class);

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ResilientCall resilientCall;

    public CuentasClient(
            RestClient cuentasRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            ResilientCall cuentasResilientCall) {

        this.restClient = cuentasRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.resilientCall = cuentasResilientCall;
    }

    public OperacionDto debitar(Long cuentaId, String operationId, BigDecimal monto, String descripcion) {
        return aplicar(cuentaId, new OperacionPayload(operationId, "debito", monto, descripcion));
    }

    public OperacionDto acreditar(Long cuentaId, String operationId, BigDecimal monto, String descripcion) {
        return aplicar(cuentaId, new OperacionPayload(operationId, "credito", monto, descripcion));
    }

    private OperacionDto aplicar(Long cuentaId, OperacionPayload payload) {

        log.info("Cuentas: {} de {} en cuenta {} (operationId={})",
                payload.tipo(), payload.monto(), cuentaId, payload.operationId());

        return circuitBreakerFactory
                .create("cuentasApi")
                .run(
                        resilientCall.decorar(() -> invocar(cuentaId, payload), true),
                        this::manejarFallo
                );
    }

    private OperacionDto invocar(Long cuentaId, OperacionPayload payload) {
        try {
            return restClient.post()
                    .uri("/api/cuentas/{cuentaId}/operaciones", cuentaId)
                    .body(payload)
                    .retrieve()
                    .body(OperacionDto.class);

        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Cuenta no encontrada: " + cuentaId);

        } catch (HttpClientErrorException.Conflict ex) {
            String cuerpo = ex.getResponseBodyAsString();
            if (cuerpo.contains("SALDO_INSUFICIENTE")) {
                throw new PagoRechazadoException("SALDO_INSUFICIENTE",
                        "Saldo insuficiente en la cuenta " + cuentaId);
            }
            throw new PagoRechazadoException("ESTADO_CUENTA_INVALIDO",
                    "La cuenta " + cuentaId + " no admite operaciones (cerrada)");
        }
    }

    /** Errores funcionales pasan tal cual; cualquier otra causa es indisponibilidad (503). */
    private OperacionDto manejarFallo(Throwable throwable) {

        RecursoNoEncontradoException noEncontrada =
                Causas.buscar(throwable, RecursoNoEncontradoException.class);
        if (noEncontrada != null) {
            throw noEncontrada;
        }

        PagoRechazadoException rechazo = Causas.buscar(throwable, PagoRechazadoException.class);
        if (rechazo != null) {
            throw rechazo;
        }

        HttpClientErrorException http = Causas.buscar(throwable, HttpClientErrorException.class);
        if (http != null) {
            throw http;
        }

        if (Causas.buscar(throwable, BulkheadFullException.class) != null) {
            log.warn("Bulkhead: demasiadas llamadas concurrentes a cuentas-service");
        }

        log.error("Circuit Breaker: cuentas-service no disponible ({})", throwable.getClass().getName());
        throw new ServicioNoDisponibleException(
                "El servicio de cuentas no se encuentra disponible temporalmente");
    }
}
