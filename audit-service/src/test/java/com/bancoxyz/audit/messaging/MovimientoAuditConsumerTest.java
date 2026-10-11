package com.bancoxyz.audit.messaging;

import com.bancoxyz.common.event.MovimientoEvento;
import com.bancoxyz.common.event.MovimientoEventoContrato;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovimientoAuditConsumerTest {

    /** Store que cuenta cuantos eventos distintos fueron aceptados. */
    private static class StoreEspia extends InMemoryProcessedEventStore {

        final List<String> aceptados = new ArrayList<>();

        StoreEspia() {
            super(24, 1000);
        }

        @Override
        public synchronized boolean registrarSiEsNuevo(String eventId) {
            boolean nuevo = super.registrarSiEsNuevo(eventId);
            if (nuevo) {
                aceptados.add(eventId);
            }
            return nuevo;
        }
    }

    private static String jsonValido() {
        return MovimientoEventoContrato.serializar(
                MovimientoEvento.nuevo(
                        "DEPOSITO_REALIZADO",
                        1L,
                        101L,
                        "deposito",
                        new BigDecimal("50.00"),
                        new BigDecimal("150.00"),
                        LocalDate.of(2026, 9, 30)
                )
        );
    }

    private static ConsumerRecord<String, String> registro(String valor) {
        return new ConsumerRecord<>("banco.movimientos", 0, 0L, "101", valor);
    }

    @Test
    void eventoRepetidoSeProcesaUnaSolaVez() {

        StoreEspia store = new StoreEspia();
        MovimientoAuditConsumer consumer =
                new MovimientoAuditConsumer("test", store);

        String json = jsonValido();

        consumer.procesar(registro(json));
        consumer.procesar(registro(json));
        consumer.procesar(registro(json));

        assertEquals(1, store.aceptados.size());
    }

    @Test
    void mensajeQueIncumpleContratoNoSeProcesaNiLanzaExcepcion() {

        StoreEspia store = new StoreEspia();
        MovimientoAuditConsumer consumer =
                new MovimientoAuditConsumer("test", store);

        consumer.procesar(registro("{\"hola\":\"mundo\"}"));
        consumer.procesar(registro("no es json"));

        assertTrue(store.aceptados.isEmpty());
    }

    @Test
    void storeOlvidaEventoParaPermitirReintento() {

        InMemoryProcessedEventStore store =
                new InMemoryProcessedEventStore(24, 1000);

        assertTrue(store.registrarSiEsNuevo("abc"));
        assertFalse(store.registrarSiEsNuevo("abc"));

        store.olvidar("abc");

        assertTrue(store.registrarSiEsNuevo("abc"));
    }
}
