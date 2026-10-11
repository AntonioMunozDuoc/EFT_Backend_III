package com.bancoxyz.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de alerta de seguridad publicado en el topico banco.alertas.
 *
 * Se emite cuando un servicio detecta una situacion que debe ser revisada por
 * el area de seguridad: transferencias de monto inusual, compensaciones
 * (rollback distribuido) o transacciones que quedaron en estado incierto.
 */
public record AlertaSeguridadEvento(
        String eventId,
        String version,
        String tipoAlerta,
        String severidad,
        String origen,
        Long transaccionId,
        Long cuentaId,
        BigDecimal monto,
        String detalle,
        String fechaEvento
) {

    public static final String VERSION = "1.0";
    public static final String MONTO_INUSUAL = "MONTO_INUSUAL";
    public static final String TRANSACCION_COMPENSADA = "TRANSACCION_COMPENSADA";
    public static final String COMPENSACION_FALLIDA = "COMPENSACION_FALLIDA";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AlertaSeguridadEvento nueva(
            String tipoAlerta,
            String severidad,
            String origen,
            Long transaccionId,
            Long cuentaId,
            BigDecimal monto,
            String detalle) {

        return new AlertaSeguridadEvento(
                UUID.randomUUID().toString(),
                VERSION,
                tipoAlerta,
                severidad,
                origen,
                transaccionId,
                cuentaId,
                monto,
                detalle,
                Instant.now().toString()
        );
    }

    public String aJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la alerta " + eventId, ex);
        }
    }

    public static AlertaSeguridadEvento desdeJson(String json) {
        try {
            return MAPPER.readValue(json, AlertaSeguridadEvento.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Alerta de seguridad invalida: " + ex.getOriginalMessage(), ex);
        }
    }
}
