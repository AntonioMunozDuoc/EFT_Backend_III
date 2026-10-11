package com.bancoxyz.common.resilience;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResilientCallTest {

    private ResilientCall nuevo(int maxConcurrentes) {
        return new ResilientCall(
                "test", 3, Duration.ofMillis(10), maxConcurrentes, Duration.ZERO);
    }

    @Test
    void reintentaFallosTransitoriosHastaTenerExito() {

        AtomicInteger intentos = new AtomicInteger();

        String resultado = nuevo(5).decorar(() -> {
            if (intentos.incrementAndGet() < 3) {
                throw new ResourceAccessException("timeout");
            }
            return "ok";
        }, true).get();

        assertEquals("ok", resultado);
        assertEquals(3, intentos.get());
    }

    @Test
    void reintentaErroresHttp5xx() {

        AtomicInteger intentos = new AtomicInteger();

        assertThrows(HttpServerErrorException.class, () ->
                nuevo(5).decorar(() -> {
                    intentos.incrementAndGet();
                    throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
                }, true).get());

        assertEquals(3, intentos.get());   // agota los 3 intentos
    }

    @Test
    void noReintentaErroresFuncionales() {

        AtomicInteger intentos = new AtomicInteger();

        assertThrows(HttpClientErrorException.class, () ->
                nuevo(5).decorar(() -> {
                    intentos.incrementAndGet();
                    throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
                }, true).get());

        assertEquals(1, intentos.get());
    }

    @Test
    void noReintentaOperacionesNoIdempotentes() {

        AtomicInteger intentos = new AtomicInteger();

        assertThrows(ResourceAccessException.class, () ->
                nuevo(5).decorar(() -> {
                    intentos.incrementAndGet();
                    throw new ResourceAccessException("timeout");
                }, false).get());

        assertEquals(1, intentos.get());
    }

    @Test
    void bulkheadRechazaLlamadasQueExcedenElLimite() throws Exception {

        ResilientCall call = nuevo(1);

        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);

        Thread ocupante = new Thread(() ->
                call.decorar(() -> {
                    dentro.countDown();
                    try {
                        liberar.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "lento";
                }, false).get());

        ocupante.start();
        dentro.await(5, TimeUnit.SECONDS);

        try {
            assertThrows(BulkheadFullException.class,
                    () -> call.decorar(() -> "rapido", false).get());
        } finally {
            liberar.countDown();
            ocupante.join();
        }
    }
}
