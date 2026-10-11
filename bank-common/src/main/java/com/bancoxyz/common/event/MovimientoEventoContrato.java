package com.bancoxyz.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Serializa, valida y deserializa MovimientoEvento contra el contrato
 * JSON Schema versionado. Lo usan tanto el productor (bank-core-api) como
 * el consumidor (audit-service), de modo que ambos extremos comparten
 * exactamente la misma definicion.
 *
 * Reglas de evolucion (version 1.x):
 *  - Agregar campos opcionales: compatible (additionalProperties = true).
 *  - Quitar, renombrar o cambiar el tipo de un campo requerido: ruptura,
 *    exige nuevo schema (v2) y nuevo campo "version" 2.x.
 */
public final class MovimientoEventoContrato {

    public static final String VERSION = "1.0";
    public static final String RECURSO_SCHEMA =
            "/contracts/movimiento-evento.v1.schema.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final JsonSchema SCHEMA = cargarSchema();

    private MovimientoEventoContrato() {
    }

    private static JsonSchema cargarSchema() {
        try (InputStream in =
                     MovimientoEventoContrato.class.getResourceAsStream(RECURSO_SCHEMA)) {

            if (in == null) {
                throw new IllegalStateException(
                        "No se encontro el schema " + RECURSO_SCHEMA);
            }

            return JsonSchemaFactory
                    .getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(in);

        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo leer el schema " + RECURSO_SCHEMA, ex);
        }
    }

    /** Devuelve la lista de incumplimientos; vacia si el JSON es valido. */
    public static List<String> validar(String json) {

        if (json == null || json.isBlank()) {
            return List.of("El mensaje esta vacio");
        }

        JsonNode nodo;
        try {
            nodo = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            return List.of("JSON malformado: " + ex.getOriginalMessage());
        }

        return SCHEMA.validate(nodo)
                .stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
    }

    /** Serializa el evento y verifica el contrato antes de enviarlo a Kafka. */
    public static String serializar(MovimientoEvento evento) {

        String json;
        try {
            json = MAPPER.writeValueAsString(evento);
        } catch (JsonProcessingException ex) {
            throw new ContratoEventoException(
                    "No se pudo serializar el evento",
                    List.of(ex.getOriginalMessage()));
        }

        List<String> errores = validar(json);

        if (!errores.isEmpty()) {
            throw new ContratoEventoException(
                    "El evento no cumple el contrato v1", errores);
        }

        return json;
    }

    /** Valida el contrato y convierte el mensaje recibido en evento tipado. */
    public static MovimientoEvento deserializar(String json) {

        List<String> errores = validar(json);

        if (!errores.isEmpty()) {
            throw new ContratoEventoException(
                    "El mensaje recibido no cumple el contrato v1", errores);
        }

        try {
            return MAPPER.readValue(json, MovimientoEvento.class);
        } catch (JsonProcessingException ex) {
            throw new ContratoEventoException(
                    "No se pudo deserializar el evento",
                    List.of(ex.getOriginalMessage()));
        }
    }
}
