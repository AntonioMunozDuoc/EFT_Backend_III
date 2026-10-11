package com.bancoxyz.cuentas.service;

import com.bancoxyz.cuentas.dto.OperacionRequest;
import com.bancoxyz.cuentas.dto.OperacionResponse;
import com.bancoxyz.cuentas.entity.Cuenta;
import com.bancoxyz.cuentas.entity.Movimiento;
import com.bancoxyz.cuentas.exception.CuentaCerradaException;
import com.bancoxyz.cuentas.exception.RecursoNoEncontradoException;
import com.bancoxyz.cuentas.exception.SaldoInsuficienteException;
import com.bancoxyz.cuentas.repository.CuentaRepository;
import com.bancoxyz.cuentas.repository.MovimientoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aplica debitos y creditos sobre una cuenta.
 *
 * Consistencia en un entorno distribuido:
 *  - operationId unico (idempotencia): pagos-service puede reintentar sin riesgo de duplicar.
 *  - bloqueo pesimista de la cuenta: dos operaciones concurrentes se serializan.
 *  - saldo y movimiento se confirman en UNA transaccion local.
 */
@Service
public class OperacionService {

    private static final Logger log = LoggerFactory.getLogger(OperacionService.class);

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final TransactionTemplate transactionTemplate;

    public OperacionService(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            TransactionTemplate transactionTemplate) {

        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public OperacionResponse aplicar(Long cuentaId, OperacionRequest request) {
        try {
            // Transaccion explicita (TransactionTemplate): una auto-invocacion con
            // @Transactional no pasaria por el proxy de Spring.
            return transactionTemplate.execute(status -> aplicarTransaccional(cuentaId, request));

        } catch (DataIntegrityViolationException ex) {
            // Carrera entre dos reintentos con el mismo operationId: gana uno, el otro lo reconoce.
            return movimientoRepository.findByOperationId(request.operationId())
                    .map(m -> respuesta(m, true))
                    .orElseThrow(() -> ex);
        }
    }

    private OperacionResponse aplicarTransaccional(Long cuentaId, OperacionRequest request) {

        var previo = movimientoRepository.findByOperationId(request.operationId());
        if (previo.isPresent()) {
            log.info("Operacion {} ya aplicada: se devuelve el resultado original", request.operationId());
            return respuesta(previo.get(), true);
        }

        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + cuentaId));

        if (Cuenta.CERRADA.equals(cuenta.getEstado())) {
            throw new CuentaCerradaException("La cuenta " + cuentaId + " esta cerrada");
        }

        boolean debito = "debito".equals(request.tipo());
        BigDecimal monto = request.monto();

        if (debito) {
            if (cuenta.getSaldoActual().compareTo(monto) < 0) {
                throw new SaldoInsuficienteException("Saldo insuficiente para realizar la operacion");
            }
            cuenta.setSaldoActual(cuenta.getSaldoActual().subtract(monto));
        } else {
            cuenta.setSaldoActual(cuenta.getSaldoActual().add(monto));
        }

        cuentaRepository.save(cuenta);

        Movimiento movimiento = new Movimiento();
        movimiento.setCuentaId(cuentaId);
        movimiento.setFecha(LocalDate.now());
        movimiento.setTipoMovimiento(debito ? "debito" : "credito");
        movimiento.setMonto(debito ? monto.negate() : monto);
        movimiento.setSaldoResultante(cuenta.getSaldoActual());
        movimiento.setDescripcion(request.descripcion());
        movimiento.setOperationId(request.operationId());

        return respuesta(movimientoRepository.saveAndFlush(movimiento), false);
    }

    private OperacionResponse respuesta(Movimiento m, boolean repetida) {
        return new OperacionResponse(
                m.getOperationId(), m.getCuentaId(), m.getId(),
                m.getMonto(), m.getSaldoResultante(), repetida);
    }
}
