package com.bancoxyz.audit.messaging;

/**
 * Registro de eventos ya procesados. Permite que el consumidor sea idempotente
 * ante reentregas de Kafka (reintentos, rebalanceos, reinicios con offset
 * no confirmado, reenvio del productor).
 */
public interface ProcessedEventStore {

    /**
     * Marca el evento como en proceso/procesado.
     *
     * @return true si es la primera vez que se ve el eventId; false si es duplicado.
     */
    boolean registrarSiEsNuevo(String eventId);

    /** Revierte el registro cuando el procesamiento falla, para permitir el reintento. */
    void olvidar(String eventId);
}
