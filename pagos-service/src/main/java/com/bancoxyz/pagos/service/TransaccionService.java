package com.bancoxyz.pagos.service;

import com.bancoxyz.common.error.ServicioNoDisponibleException;
import com.bancoxyz.common.event.AlertaSeguridadEvento;
import com.bancoxyz.common.event.MovimientoEvento;
import com.bancoxyz.pagos.client.CuentasClient;
import com.bancoxyz.pagos.client.OperacionDto;
import com.bancoxyz.pagos.dto.DepositoRequest;
import com.bancoxyz.pagos.dto.PagoRequest;
import com.bancoxyz.pagos.dto.RetiroRequest;
import com.bancoxyz.pagos.dto.TransaccionResponse;
import com.bancoxyz.pagos.dto.TransferenciaRequest;
import com.bancoxyz.pagos.entity.Transaccion;
import com.bancoxyz.pagos.exception.PagoRechazadoException;
import com.bancoxyz.pagos.exception.RecursoNoEncontradoException;
import com.bancoxyz.pagos.repository.TransaccionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orquestador de pagos, transferencias, depositos y retiros.
 *
 * Consistencia de datos distribuida (patron SAGA orquestada, sin 2PC):
 *  1. Se registra la transaccion en estado PENDIENTE (base propia).
 *  2. Cada paso remoto en cuentas-service lleva un operationId derivado de la
 *     referencia ("ref:debito", "ref:credito", "ref:compensacion"): es idempotente,
 *     asi que reintentar nunca duplica un movimiento.
 *  3. Rechazo funcional en el 2do paso de una transferencia -> COMPENSACION
 *     (se devuelve el debito a la cuenta de origen) y alerta de seguridad.
 *  4. Falla de infraestructura -> la transaccion queda PENDIENTE y responde 503;
 *     reenviar la MISMA referencia reanuda la saga donde quedo.
 *  5. El estado final y los eventos Kafka se confirman en UNA transaccion local
 *     (los eventos se publican AFTER_COMMIT).
 *
 * Las llamadas remotas se hacen fuera de cualquier transaccion de BD.
 */
@Service
public class TransaccionService {

    private static final Logger log = LoggerFactory.getLogger(TransaccionService.class);

    private final TransaccionRepository repository;
    private final CuentasClient cuentasClient;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher eventos;
    private final BigDecimal umbralAlerta;

    public TransaccionService(
            TransaccionRepository repository,
            CuentasClient cuentasClient,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher eventos,
            @Value("${pagos.alerta.monto-umbral:1000000}") BigDecimal umbralAlerta) {

        this.repository = repository;
        this.cuentasClient = cuentasClient;
        this.tx = transactionTemplate;
        this.eventos = eventos;
        this.umbralAlerta = umbralAlerta;
    }

    // ---------------------------------------------------------------- consultas

    public TransaccionResponse obtener(Long id) {
        return repository.findById(id)
                .map(this::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Transaccion no encontrada: " + id));
    }

    public List<TransaccionResponse> porCuenta(Long cuentaId) {
        return repository.findByCuentaOrigenOrCuentaDestinoOrderByIdDesc(cuentaId, cuentaId)
                .stream().map(this::aRespuesta).toList();
    }

    // -------------------------------------------------------------- operaciones

    public TransaccionResponse pagar(PagoRequest r) {
        String desc = r.comercio() == null || r.comercio().isBlank() ? "Pago" : "Pago en " + r.comercio();
        return unaCuenta(Transaccion.PAGO, r.cuentaId(), r.monto(), desc, r.referencia());
    }

    public TransaccionResponse retirar(RetiroRequest r) {
        String desc = "Retiro" + (r.canal() == null || r.canal().isBlank() ? "" : " por " + r.canal());
        return unaCuenta(Transaccion.RETIRO, r.cuentaId(), r.monto(), desc, r.referencia());
    }

    public TransaccionResponse depositar(DepositoRequest r) {
        String desc = r.descripcion() == null || r.descripcion().isBlank() ? "Deposito" : r.descripcion();
        return unaCuenta(Transaccion.DEPOSITO, r.cuentaId(), r.monto(), desc, r.referencia());
    }

    /** Pago, retiro o deposito: un solo paso remoto sobre una cuenta. */
    private TransaccionResponse unaCuenta(
            String tipo, Long cuentaId, BigDecimal monto, String descripcion, String referencia) {

        boolean esDeposito = Transaccion.DEPOSITO.equals(tipo);
        String ref = normalizar(referencia);

        Transaccion t = obtenerOCrear(tipo,
                esDeposito ? null : cuentaId, esDeposito ? cuentaId : null,
                monto, descripcion, ref);

        if (!esPendiente(t)) {
            return aRespuesta(t);
        }

        OperacionDto op;
        try {
            op = esDeposito
                    ? cuentasClient.acreditar(cuentaId, ref + ":credito", monto, descripcion)
                    : cuentasClient.debitar(cuentaId, ref + ":debito", monto, descripcion);

        } catch (PagoRechazadoException | RecursoNoEncontradoException ex) {
            marcarFallida(t, ex.getMessage());
            throw ex;

        } catch (ServicioNoDisponibleException ex) {
            dejarPendiente(t, "cuentas-service no disponible; reintente con la misma referencia");
            throw ex;
        }

        List<Object> aPublicar = new ArrayList<>();
        aPublicar.add(movimiento(t, cuentaId, esDeposito, op));
        alertaMontoInusual(t, cuentaId).ifPresent(aPublicar::add);

        completar(t, op.saldoResultante(), Transaccion.COMPLETADA, null, aPublicar);
        return aRespuesta(t);
    }

    /** Transferencia entre dos cuentas: debito + credito con compensacion. */
    public TransaccionResponse transferir(TransferenciaRequest r) {

        if (r.cuentaOrigen().equals(r.cuentaDestino())) {
            throw new IllegalArgumentException("La cuenta de origen y destino deben ser distintas");
        }

        String ref = normalizar(r.referencia());
        String desc = r.descripcion() == null || r.descripcion().isBlank()
                ? "Transferencia" : r.descripcion();

        Transaccion t = obtenerOCrear(Transaccion.TRANSFERENCIA,
                r.cuentaOrigen(), r.cuentaDestino(), r.monto(), desc, ref);

        if (!esPendiente(t)) {
            return aRespuesta(t);
        }

        // Paso 1: debito en la cuenta de origen
        OperacionDto debito;
        try {
            debito = cuentasClient.debitar(r.cuentaOrigen(), ref + ":debito", r.monto(),
                    desc + " (a cuenta " + r.cuentaDestino() + ")");

        } catch (PagoRechazadoException | RecursoNoEncontradoException ex) {
            marcarFallida(t, ex.getMessage());
            throw ex;

        } catch (ServicioNoDisponibleException ex) {
            dejarPendiente(t, "cuentas-service no disponible antes del debito; reintente con la misma referencia");
            throw ex;
        }

        // Paso 2: credito en la cuenta de destino
        OperacionDto credito;
        try {
            credito = cuentasClient.acreditar(r.cuentaDestino(), ref + ":credito", r.monto(),
                    desc + " (desde cuenta " + r.cuentaOrigen() + ")");

        } catch (PagoRechazadoException | RecursoNoEncontradoException ex) {
            return compensar(t, r, ref, ex.getMessage());

        } catch (ServicioNoDisponibleException ex) {
            // El debito YA se aplico: no se compensa a ciegas (el credito podria haberse
            // aplicado y perderse solo la respuesta). Queda PENDIENTE y es reanudable.
            dejarPendiente(t, "debito aplicado; credito pendiente por indisponibilidad. Reintente con la misma referencia");
            throw ex;
        }

        List<Object> aPublicar = new ArrayList<>();
        aPublicar.add(movimiento(t, r.cuentaOrigen(), false, debito));
        aPublicar.add(movimiento(t, r.cuentaDestino(), true, credito));
        alertaMontoInusual(t, r.cuentaOrigen()).ifPresent(aPublicar::add);

        completar(t, debito.saldoResultante(), Transaccion.COMPLETADA, null, aPublicar);
        return aRespuesta(t);
    }

    // --------------------------------------------------------------- compensacion

    private TransaccionResponse compensar(
            Transaccion t, TransferenciaRequest r, String ref, String motivo) {

        log.warn("Transferencia {} rechazada en destino ({}): compensando cuenta {}",
                ref, motivo, r.cuentaOrigen());

        OperacionDto devolucion;

        try {
            devolucion = cuentasClient.acreditar(
                    r.cuentaOrigen(), ref + ":compensacion", r.monto(),
                    "Compensacion de transferencia " + ref);

        } catch (RuntimeException ex) {
            // No se pudo devolver el debito: caso critico -> revision manual + alerta
            List<Object> alerta = List.of(AlertaSeguridadEvento.nueva(
                    AlertaSeguridadEvento.COMPENSACION_FALLIDA, "CRITICA", "pagos-service",
                    t.getId(), r.cuentaOrigen(), r.monto(),
                    "No se pudo devolver el debito: requiere revision manual"));

            completar(t, null, Transaccion.REVISION_MANUAL,
                    "Compensacion fallida: " + ex.getMessage(), alerta);

            throw new ServicioNoDisponibleException(
                    "La transferencia quedo en revision manual (ref " + ref + ")");
        }

        List<Object> alerta = List.of(AlertaSeguridadEvento.nueva(
                AlertaSeguridadEvento.TRANSACCION_COMPENSADA, "MEDIA", "pagos-service",
                t.getId(), r.cuentaOrigen(), r.monto(),
                "Transferencia revertida: " + motivo));

        completar(t, devolucion.saldoResultante(), Transaccion.COMPENSADA, motivo, alerta);

        throw new PagoRechazadoException("TRANSFERENCIA_COMPENSADA",
                "Transferencia revertida: " + motivo + ". El monto fue devuelto a la cuenta de origen");
    }

    // ------------------------------------------------------------------ helpers

    private String normalizar(String referencia) {
        return referencia == null || referencia.isBlank() ? UUID.randomUUID().toString() : referencia.trim();
    }

    private boolean esPendiente(Transaccion t) {
        return Transaccion.PENDIENTE.equals(t.getEstado());
    }

    /** Crea la transaccion PENDIENTE o devuelve la existente (idempotencia por referencia). */
    private Transaccion obtenerOCrear(
            String tipo, Long origen, Long destino, BigDecimal monto, String descripcion, String ref) {

        var existente = repository.findByReferencia(ref);
        if (existente.isPresent()) {
            return validarMismaSolicitud(existente.get(), tipo, origen, destino, monto);
        }

        Transaccion t = new Transaccion();
        t.setTipo(tipo);
        t.setCuentaOrigen(origen);
        t.setCuentaDestino(destino);
        t.setMonto(monto);
        t.setDescripcion(descripcion);
        t.setReferencia(ref);

        try {
            return repository.save(t);

        } catch (DataIntegrityViolationException ex) {
            return validarMismaSolicitud(
                    repository.findByReferencia(ref).orElseThrow(() -> ex), tipo, origen, destino, monto);
        }
    }

    private Transaccion validarMismaSolicitud(
            Transaccion t, String tipo, Long origen, Long destino, BigDecimal monto) {

        boolean igual = t.getTipo().equals(tipo)
                && java.util.Objects.equals(t.getCuentaOrigen(), origen)
                && java.util.Objects.equals(t.getCuentaDestino(), destino)
                && t.getMonto().compareTo(monto) == 0;

        if (!igual) {
            throw new PagoRechazadoException("REFERENCIA_DUPLICADA",
                    "La referencia " + t.getReferencia() + " ya fue usada con otros datos");
        }
        return t;
    }

    private void marcarFallida(Transaccion t, String detalle) {
        completar(t, null, Transaccion.FALLIDA, detalle, List.of());
    }

    private void dejarPendiente(Transaccion t, String detalle) {
        log.warn("Transaccion {} ({}) queda PENDIENTE: {}", t.getId(), t.getReferencia(), detalle);
        t.setDetalleError(detalle);
        repository.save(t);
    }

    /** Estado final + eventos en una sola transaccion local (los eventos salen AFTER_COMMIT). */
    private void completar(
            Transaccion t, BigDecimal saldo, String estado, String detalle, List<Object> aPublicar) {

        t.setEstado(estado);
        t.setDetalleError(detalle);
        t.setSaldoResultante(saldo);

        tx.executeWithoutResult(status -> {
            repository.save(t);
            aPublicar.forEach(eventos::publishEvent);
        });
    }

    private MovimientoEvento movimiento(Transaccion t, Long cuentaId, boolean esCredito, OperacionDto op) {

        String tipoEvento;
        String tipoMovimiento;

        if (esCredito) {
            tipoEvento = "DEPOSITO_REALIZADO";
            tipoMovimiento = "deposito";
        } else if (Transaccion.PAGO.equals(t.getTipo())) {
            tipoEvento = "COMPRA_REALIZADA";
            tipoMovimiento = "compra";
        } else {
            tipoEvento = "RETIRO_REALIZADO";
            tipoMovimiento = "retiro";
        }

        BigDecimal monto = esCredito ? t.getMonto() : t.getMonto().negate();

        return MovimientoEvento.nuevo(tipoEvento, t.getId(), cuentaId, tipoMovimiento,
                monto, op.saldoResultante(), LocalDate.now());
    }

    private java.util.Optional<AlertaSeguridadEvento> alertaMontoInusual(Transaccion t, Long cuentaId) {

        if (t.getMonto().compareTo(umbralAlerta) < 0) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(AlertaSeguridadEvento.nueva(
                AlertaSeguridadEvento.MONTO_INUSUAL, "ALTA", "pagos-service",
                t.getId(), cuentaId, t.getMonto(),
                t.getTipo() + " por un monto igual o superior al umbral (" + umbralAlerta + ")"));
    }

    private TransaccionResponse aRespuesta(Transaccion t) {
        return new TransaccionResponse(
                t.getId(), t.getTipo(), t.getCuentaOrigen(), t.getCuentaDestino(), t.getMonto(),
                t.getEstado(), t.getDescripcion(), t.getReferencia(), t.getDetalleError(),
                t.getSaldoResultante(), t.getFecha());
    }
}
