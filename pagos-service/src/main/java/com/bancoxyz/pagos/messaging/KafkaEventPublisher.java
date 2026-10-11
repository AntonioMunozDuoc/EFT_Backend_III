package com.bancoxyz.pagos.messaging;

import com.bancoxyz.common.event.AlertaSeguridadEvento;
import com.bancoxyz.common.event.ContratoEventoException;
import com.bancoxyz.common.event.MovimientoEvento;
import com.bancoxyz.common.event.MovimientoEventoContrato;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Publica en Kafka SOLO despues del COMMIT de la transaccion local:
 *   - banco.movimientos : transacciones completadas (contrato JSON Schema v1)
 *   - banco.alertas     : alertas de seguridad
 *
 * Continuidad operativa: si Kafka cae, la transaccion bancaria ya esta
 * confirmada y NO debe fallar. El envio esta protegido por el Circuit Breaker
 * "kafkaPublisher" (config en el Config Server) y max.block.ms acotado.
 * (Mejora futura: patron Outbox para reenviar eventos perdidos.)
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final String topicMovimientos;
    private final String topicAlertas;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${banco.kafka.topic.movimientos}") String topicMovimientos,
            @Value("${banco.kafka.topic.alertas}") String topicAlertas) {

        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.topicMovimientos = topicMovimientos;
        this.topicAlertas = topicAlertas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicarMovimiento(MovimientoEvento evento) {

        String payload;
        try {
            payload = MovimientoEventoContrato.serializar(evento);
        } catch (ContratoEventoException ex) {
            log.error("Evento {} descartado: incumple el contrato v1 -> {}", evento.eventId(), ex.getErrores());
            return;
        }

        List<Header> headers = List.of(
                cabecera("eventId", evento.eventId()),
                cabecera("eventType", evento.tipoEvento()),
                cabecera("schemaVersion", evento.version()));

        enviar(new ProducerRecord<>(topicMovimientos, null, evento.cuentaId().toString(), payload, headers),
                evento.eventId(), evento.tipoEvento());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicarAlerta(AlertaSeguridadEvento alerta) {

        List<Header> headers = List.of(
                cabecera("eventId", alerta.eventId()),
                cabecera("eventType", alerta.tipoAlerta()),
                cabecera("schemaVersion", alerta.version()));

        String key = alerta.cuentaId() == null ? alerta.tipoAlerta() : alerta.cuentaId().toString();

        enviar(new ProducerRecord<>(topicAlertas, null, key, alerta.aJson(), headers),
                alerta.eventId(), alerta.tipoAlerta());
    }

    private void enviar(ProducerRecord<String, String> registro, String eventId, String tipo) {

        circuitBreakerFactory
                .create("kafkaPublisher")
                .run(
                        () -> {
                            kafkaTemplate.send(registro).whenComplete((resultado, error) -> {
                                if (error != null) {
                                    log.error("Error publicando evento {} ({}) en Kafka", eventId, tipo, error);
                                    return;
                                }
                                log.info("Evento Kafka publicado: eventId={}, tipo={}, topic={}, particion={}, offset={}",
                                        eventId, tipo, registro.topic(),
                                        resultado.getRecordMetadata().partition(),
                                        resultado.getRecordMetadata().offset());
                            });
                            return Boolean.TRUE;
                        },
                        error -> {
                            log.error("Kafka no disponible: evento {} ({}) no publicado", eventId, tipo, error);
                            return Boolean.FALSE;
                        });
    }

    private Header cabecera(String nombre, String valor) {
        return new RecordHeader(nombre, valor.getBytes(StandardCharsets.UTF_8));
    }
}
