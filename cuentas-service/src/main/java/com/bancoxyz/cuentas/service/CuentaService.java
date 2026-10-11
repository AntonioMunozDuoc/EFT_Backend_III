package com.bancoxyz.cuentas.service;

import com.bancoxyz.common.error.ServicioNoDisponibleException;
import com.bancoxyz.cuentas.client.ClienteDto;
import com.bancoxyz.cuentas.client.ClientesClient;
import com.bancoxyz.cuentas.dto.ActualizarCuentaRequest;
import com.bancoxyz.cuentas.dto.AperturaCuentaRequest;
import com.bancoxyz.cuentas.dto.CuentaResponse;
import com.bancoxyz.cuentas.dto.MovimientoResponse;
import com.bancoxyz.cuentas.entity.Cuenta;
import com.bancoxyz.cuentas.exception.ClienteInvalidoException;
import com.bancoxyz.cuentas.exception.CuentaCerradaException;
import com.bancoxyz.cuentas.exception.RecursoNoEncontradoException;
import com.bancoxyz.cuentas.mapper.CuentaMapper;
import com.bancoxyz.cuentas.repository.CuentaRepository;
import com.bancoxyz.cuentas.repository.MovimientoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Apertura, cierre y mantenimiento de cuentas. Expone DTOs: la entidad no sale de esta capa. */
@Service
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final CuentaMapper mapper;
    private final ClientesClient clientesClient;

    public CuentaService(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            CuentaMapper mapper,
            ClientesClient clientesClient) {

        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.mapper = mapper;
        this.clientesClient = clientesClient;
    }

    @Transactional(readOnly = true)
    public List<CuentaResponse> listar(Long clienteId) {
        List<Cuenta> cuentas = clienteId == null
                ? cuentaRepository.findAll()
                : cuentaRepository.findByClienteId(clienteId);

        return cuentas.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtener(Long cuentaId) {
        return mapper.toResponse(buscar(cuentaId));
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> movimientos(Long cuentaId) {
        buscar(cuentaId);
        return movimientoRepository
                .findByCuentaIdOrderByFechaDescIdDesc(cuentaId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Apertura. La llamada remota a clientes-service se hace FUERA de la
     * transaccion de base de datos (no se mantiene una conexion abierta
     * mientras se espera a otro servicio).
     *
     * Comportamiento alternativo: si clientes-service no responde y el request
     * trae nombre y edad, la cuenta se crea en PENDIENTE_VERIFICACION.
     */
    public CuentaResponse abrir(AperturaCuentaRequest request) {

        String nombre;
        Integer edad;
        String estado = Cuenta.ACTIVA;

        try {
            ClienteDto cliente = clientesClient.obtener(request.clienteId());

            if (!"ACTIVO".equalsIgnoreCase(cliente.estado())) {
                throw new ClienteInvalidoException(
                        "El cliente " + request.clienteId() + " esta inactivo");
            }

            nombre = cliente.nombre();
            edad = cliente.edad();

        } catch (ServicioNoDisponibleException ex) {

            if (request.nombre() == null || request.nombre().isBlank() || request.edad() == null) {
                throw ex;
            }

            log.warn("clientes-service no disponible: cuenta del cliente {} queda PENDIENTE_VERIFICACION",
                    request.clienteId());

            nombre = request.nombre();
            edad = request.edad();
            estado = Cuenta.PENDIENTE_VERIFICACION;
        }

        return guardarNueva(request, nombre, edad, estado);
    }

    private CuentaResponse guardarNueva(
            AperturaCuentaRequest request, String nombre, Integer edad, String estado) {

        Cuenta cuenta = new Cuenta();
        cuenta.setClienteId(request.clienteId());
        cuenta.setNombre(nombre);
        cuenta.setEdad(edad);
        cuenta.setTipo(request.tipo().toLowerCase());
        cuenta.setEstado(estado);
        cuenta.setSaldoActual(request.saldoInicial() == null ? BigDecimal.ZERO : request.saldoInicial());
        cuenta.setTasaInteres(request.tasaInteres() == null ? tasaPorDefecto(request.tipo()) : request.tasaInteres());
        cuenta.setInteresCalculado(BigDecimal.ZERO);

        return mapper.toResponse(cuentaRepository.save(cuenta));
    }

    @Transactional
    public CuentaResponse actualizar(Long cuentaId, ActualizarCuentaRequest request) {

        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + cuentaId));

        exigirNoCerrada(cuenta);

        if (request.tipo() != null) {
            cuenta.setTipo(request.tipo().toLowerCase());
        }
        if (request.tasaInteres() != null) {
            cuenta.setTasaInteres(request.tasaInteres());
        }

        return mapper.toResponse(cuentaRepository.save(cuenta));
    }

    /** Cierre: solo si el saldo es cero (regla de negocio). Es idempotente. */
    @Transactional
    public CuentaResponse cerrar(Long cuentaId) {

        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + cuentaId));

        if (Cuenta.CERRADA.equals(cuenta.getEstado())) {
            return mapper.toResponse(cuenta);
        }

        if (cuenta.getSaldoActual().compareTo(BigDecimal.ZERO) != 0) {
            throw new CuentaCerradaException(
                    "No se puede cerrar la cuenta " + cuentaId + ": el saldo debe ser 0");
        }

        cuenta.setEstado(Cuenta.CERRADA);
        return mapper.toResponse(cuentaRepository.save(cuenta));
    }

    private Cuenta buscar(Long cuentaId) {
        return cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada: " + cuentaId));
    }

    private void exigirNoCerrada(Cuenta cuenta) {
        if (Cuenta.CERRADA.equals(cuenta.getEstado())) {
            throw new CuentaCerradaException("La cuenta " + cuenta.getCuentaId() + " esta cerrada");
        }
    }

    private BigDecimal tasaPorDefecto(String tipo) {
        return switch (tipo.toLowerCase()) {
            case "ahorro" -> new BigDecimal("0.0150");
            case "hipoteca" -> new BigDecimal("0.0400");
            case "prestamo" -> new BigDecimal("0.0500");
            default -> BigDecimal.ZERO;
        };
    }
}
