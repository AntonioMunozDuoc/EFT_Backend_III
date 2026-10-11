package com.bancoxyz.audit.messaging;

import com.bancoxyz.common.event.ContratoEventoException;
import com.bancoxyz.common.event.MovimientoEvento;
import com.bancoxyz.common.event.MovimientoEventoContrato;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de auditoria del topico banco.movimientos.
 *
 * Por cada mensaje:
 *  1. Valida el contrato JSON Schema (mensajes invalidos se rechazan y se
 *     registran; no se reintentan porque reintentar no los corrige).
 *  2. Aplica idempotencia por eventId (los duplicados se omiten).
 *  3. Procesa el evento y deja la traza de auditoria.
 */
@Component
public class MovimientoAuditConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(MovimientoAuditConsumer.class);

    private final String instancia;
    private final ProcessedEventStore processedEvents;

    public MovimientoAuditConsumer(
            @Value("${auditoria.instancia:auditoria-1}") String instancia,
            ProcessedEventStore processedEvents) {

        this.instancia = instancia;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(
            topics = "${banco.kafka.topic.movimientos}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void procesar(ConsumerRecord<String, String> record) {

        MovimientoEvento evento;

        try {
            evento = MovimientoEventoContrato.deserializar(record.value());

        } catch (ContratoEventoException ex) {

            log.error(
                    "AUDITORIA [{}] mensaje RECHAZADO por contrato: key={}, particion={}, offset={}, errores={}",
                    instancia,
                    record.key(),
                    record.partition(),
                    record.offset(),
                    ex.getErrores()
            );
            return;
        }

        if (!processedEvents.registrarSiEsNuevo(evento.eventId())) {

            log.warn(
                    "AUDITORIA [{}] evento DUPLICADO omitido: eventId={}, cuenta={}, particion={}, offset={}",
                    instancia,
                    evento.eventId(),
                    evento.cuentaId(),
                    record.partition(),
                    record.offset()
            );
            return;
        }

        try {
            auditar(evento, record);

        } catch (RuntimeException ex) {
            // Permite que la reentrega del mensaje se procese nuevamente
            processedEvents.olvidar(evento.eventId());
            throw ex;
        }
    }

    private void auditar(
            MovimientoEvento evento,
            ConsumerRecord<String, String> record) {

        log.info(
                "AUDITORIA [{}] evento procesado: eventId={}, tipo={}, cuenta={}, monto={}, saldo={}, key={}, particion={}, offset={}",
                instancia,
                evento.eventId(),
                evento.tipoEvento(),
                evento.cuentaId(),
                evento.monto(),
                evento.saldoResultante(),
                record.key(),
                record.partition(),
                record.offset()
        );
    }
}
