package com.bancoxyz.common.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Patrones Resilience4j que complementan al Circuit Breaker en los BFF:
 *
 *  - Bulkhead : limita las llamadas concurrentes al Core. Si el Core se pone
 *               lento, el BFF no agota todos sus hilos y rechaza el exceso de
 *               inmediato (BulkheadFullException).
 *  - Retry    : reintenta SOLO fallos transitorios de infraestructura
 *               (timeout / conexion / HTTP 5xx / falla al obtener token).
 *               Nunca reintenta errores funcionales (404, 409, 400).
 *
 * Orden de composicion (de afuera hacia adentro):
 *     CircuitBreaker -> Retry -> Bulkhead -> llamada HTTP
 * El Circuit Breaker contabiliza el resultado FINAL (tras los reintentos).
 *
 * Las operaciones que modifican datos (retiro) se ejecutan con
 * reintentable=false: reintentar un POST no idempotente podria duplicar el
 * movimiento. Siguen protegidas por Bulkhead y Circuit Breaker.
 */
public class ResilientCall {

    private static final Logger log = LoggerFactory.getLogger(ResilientCall.class);

    private final Retry retry;
    private final Bulkhead bulkhead;

    public ResilientCall(
            String nombre,
            int maxIntentos,
            Duration esperaEntreIntentos,
            int maxLlamadasConcurrentes,
            Duration esperaMaximaBulkhead) {

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxIntentos)
                .waitDuration(esperaEntreIntentos)
                .retryOnException(esFalloTransitorio())
                .build();

        this.retry = Retry.of(nombre, retryConfig);

        this.retry.getEventPublisher().onRetry(evento ->
                log.warn(
                        "Retry [{}]: intento {} fallido, reintentando. Causa: {}",
                        nombre,
                        evento.getNumberOfRetryAttempts(),
                        evento.getLastThrowable() == null
                                ? "desconocida"
                                : evento.getLastThrowable().toString()
                ));

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(maxLlamadasConcurrentes)
                .maxWaitDuration(esperaMaximaBulkhead)
                .build();

        this.bulkhead = Bulkhead.of(nombre, bulkheadConfig);
    }

    /** Falla de infraestructura que vale la pena reintentar. */
    static Predicate<Throwable> esFalloTransitorio() {
        return ex ->
                ex instanceof ResourceAccessException
                        || ex instanceof HttpServerErrorException
                        || (ex instanceof IllegalStateException
                        && ex.getCause() instanceof org.springframework.web.client.RestClientException);
    }

    /**
     * Envuelve la operacion con Bulkhead y, si es reintentable, con Retry.
     */
    public <T> Supplier<T> decorar(Supplier<T> operacion, boolean reintentable) {

        Supplier<T> conBulkhead = Bulkhead.decorateSupplier(bulkhead, operacion);

        return reintentable
                ? Retry.decorateSupplier(retry, conBulkhead)
                : conBulkhead;
    }

    public Bulkhead getBulkhead() {
        return bulkhead;
    }

    public Retry getRetry() {
        return retry;
    }
}
