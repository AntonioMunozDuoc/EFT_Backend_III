package com.bancoxyz.audit.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementacion en memoria con ventana de tiempo (TTL) y tamano maximo.
 *
 * Limitacion conocida: el estado se pierde al reiniciar el servicio. En
 * produccion se reemplaza por una tabla con restriccion UNIQUE(event_id)
 * (o Redis con SETNX + TTL) implementando esta misma interfaz.
 */
@Component
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Duration ttl;
    private final int maxEventos;

    // Orden de insercion: el mas antiguo es el primero a expirar
    private final Map<String, Instant> eventos = new LinkedHashMap<>();

    public InMemoryProcessedEventStore(
            @Value("${auditoria.idempotencia.ttl-horas:24}") long ttlHoras,
            @Value("${auditoria.idempotencia.max-eventos:100000}") int maxEventos) {

        this.ttl = Duration.ofHours(ttlHoras);
        this.maxEventos = maxEventos;
    }

    @Override
    public synchronized boolean registrarSiEsNuevo(String eventId) {

        Instant ahora = Instant.now();

        depurar(ahora);

        if (eventos.containsKey(eventId)) {
            return false;
        }

        eventos.put(eventId, ahora);

        return true;
    }

    @Override
    public synchronized void olvidar(String eventId) {
        eventos.remove(eventId);
    }

    private void depurar(Instant ahora) {

        Iterator<Map.Entry<String, Instant>> it = eventos.entrySet().iterator();

        while (it.hasNext()) {

            Map.Entry<String, Instant> entrada = it.next();

            boolean expirado =
                    entrada.getValue().plus(ttl).isBefore(ahora);

            boolean excedeCapacidad = eventos.size() >= maxEventos;

            if (expirado || excedeCapacidad) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
