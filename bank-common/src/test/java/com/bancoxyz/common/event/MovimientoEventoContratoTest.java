package com.bancoxyz.common.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovimientoEventoContratoTest {

    private static MovimientoEvento retiroValido() {
        return MovimientoEvento.nuevo(
                "RETIRO_REALIZADO",
                13L,
                101L,
                "retiro",
                new BigDecimal("-100.00"),
                new BigDecimal("4850.00"),
                LocalDate.of(2026, 9, 30)
        );
    }

    @Test
    void eventoValidoSeSerializaYSeDeserializa() {

        MovimientoEvento original = retiroValido();

        String json = MovimientoEventoContrato.serializar(original);
        MovimientoEvento recibido = MovimientoEventoContrato.deserializar(json);

        assertEquals(original.eventId(), recibido.eventId());
        assertEquals("RETIRO_REALIZADO", recibido.tipoEvento());
        assertEquals(101L, recibido.cuentaId());
        assertEquals(0, new BigDecimal("-100.00").compareTo(recibido.monto()));
    }

    @Test
    void retiroConMontoPositivoViolaElContrato() {

        MovimientoEvento invalido = new MovimientoEvento(
                "3f2b8c1e-5d4a-4c3b-9a1e-0d2f6b7a8c90",
                "1.0",
                "RETIRO_REALIZADO",
                13L,
                101L,
                "retiro",
                new BigDecimal("100.00"),
                new BigDecimal("4850.00"),
                "2026-09-30",
                "2026-09-30T12:00:00Z"
        );

        assertThrows(
                ContratoEventoException.class,
                () -> MovimientoEventoContrato.serializar(invalido)
        );
    }

    @Test
    void mensajeSinEventIdEsRechazado() {

        String json = """
                {"version":"1.0","tipoEvento":"DEPOSITO_REALIZADO",
                 "movimientoId":1,"cuentaId":101,"tipoMovimiento":"deposito",
                 "monto":50.00,"saldoResultante":150.00,
                 "fechaMovimiento":"2026-09-30",
                 "fechaEvento":"2026-09-30T12:00:00Z"}
                """;

        List<String> errores = MovimientoEventoContrato.validar(json);

        assertFalse(errores.isEmpty());
    }

    @Test
    void jsonMalformadoEsRechazado() {

        assertFalse(MovimientoEventoContrato.validar("{no es json").isEmpty());
        assertFalse(MovimientoEventoContrato.validar("").isEmpty());
    }

    @Test
    void campoNuevoOpcionalMantieneCompatibilidadV1() {

        String json = MovimientoEventoContrato.serializar(retiroValido());

        String conCampoNuevo =
                json.substring(0, json.length() - 1) + ",\"canal\":\"ATM\"}";

        assertTrue(MovimientoEventoContrato.validar(conCampoNuevo).isEmpty());
    }
}
