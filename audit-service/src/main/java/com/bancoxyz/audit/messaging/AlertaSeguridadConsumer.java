package com.bancoxyz.audit.messaging;

import com.bancoxyz.common.event.AlertaSeguridadEvento;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de alertas de seguridad (topico banco.alertas).
 * Valida el formato, omite duplicados por eventId y deja la traza para el
 * equipo de seguridad. Las alertas CRITICAS se registran como ERROR.
 */
@Component
public class AlertaSeguridadConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertaSeguridadConsumer.class);

    private final String instancia;
    private final ProcessedEventStore processedEvents;

    public AlertaSeguridadConsumer(
            @Value("${auditoria.instancia:auditoria-1}") String instancia,
            ProcessedEventStore processedEvents) {

        this.instancia = instancia;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(
            topics = "${banco.kafka.topic.alertas}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void procesar(ConsumerRecord<String, String> record) {

        AlertaSeguridadEvento alerta;

        try {
            alerta = AlertaSeguridadEvento.desdeJson(record.value());

        } catch (IllegalArgumentException ex) {
            log.error("ALERTAS [{}] mensaje RECHAZADO: key={}, particion={}, offset={}, motivo={}",
                    instancia, record.key(), record.partition(), record.offset(), ex.getMessage());
            return;
        }

        if (!processedEvents.registrarSiEsNuevo(alerta.eventId())) {
            log.warn("ALERTAS [{}] alerta DUPLICADA omitida: eventId={}", instancia, alerta.eventId());
            return;
        }

        try {
            String linea = String.format(
                    "ALERTAS [%s] %s severidad=%s origen=%s transaccion=%s cuenta=%s monto=%s detalle=%s (particion=%d, offset=%d)",
                    instancia, alerta.tipoAlerta(), alerta.severidad(), alerta.origen(),
                    alerta.transaccionId(), alerta.cuentaId(), alerta.monto(), alerta.detalle(),
                    record.partition(), record.offset());

            if ("CRITICA".equalsIgnoreCase(alerta.severidad())) {
                log.error(linea);
            } else {
                log.warn(linea);
            }

        } catch (RuntimeException ex) {
            processedEvents.olvidar(alerta.eventId());
            throw ex;
        }
    }
}
