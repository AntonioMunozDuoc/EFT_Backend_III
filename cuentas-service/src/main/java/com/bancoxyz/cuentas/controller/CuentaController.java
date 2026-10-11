package com.bancoxyz.cuentas.controller;

import com.bancoxyz.cuentas.dto.ActualizarCuentaRequest;
import com.bancoxyz.cuentas.dto.AperturaCuentaRequest;
import com.bancoxyz.cuentas.dto.CuentaResponse;
import com.bancoxyz.cuentas.dto.MovimientoResponse;
import com.bancoxyz.cuentas.dto.OperacionRequest;
import com.bancoxyz.cuentas.dto.OperacionResponse;
import com.bancoxyz.cuentas.service.CuentaService;
import com.bancoxyz.cuentas.service.OperacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;
    private final OperacionService operacionService;

    public CuentaController(CuentaService cuentaService, OperacionService operacionService) {
        this.cuentaService = cuentaService;
        this.operacionService = operacionService;
    }

    @GetMapping
    public List<CuentaResponse> listar(@RequestParam(required = false) Long clienteId) {
        return cuentaService.listar(clienteId);
    }

    @GetMapping("/{cuentaId}")
    public CuentaResponse obtener(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId) {
        return cuentaService.obtener(cuentaId);
    }

    @GetMapping("/{cuentaId}/movimientos")
    public List<MovimientoResponse> movimientos(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId) {
        return cuentaService.movimientos(cuentaId);
    }

    /** Apertura de cuenta. */
    @PostMapping
    public ResponseEntity<CuentaResponse> abrir(@Valid @RequestBody AperturaCuentaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaService.abrir(request));
    }

    /** Mantenimiento (tipo / tasa). */
    @PatchMapping("/{cuentaId}")
    public CuentaResponse actualizar(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId,
            @Valid @RequestBody ActualizarCuentaRequest request) {
        return cuentaService.actualizar(cuentaId, request);
    }

    /** Cierre de cuenta (requiere saldo 0). */
    @PostMapping("/{cuentaId}/cierre")
    public CuentaResponse cerrar(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId) {
        return cuentaService.cerrar(cuentaId);
    }

    /** Debito / credito interno e idempotente (lo usa pagos-service). */
    @PostMapping("/{cuentaId}/operaciones")
    public OperacionResponse operar(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId,
            @Valid @RequestBody OperacionRequest request) {
        return operacionService.aplicar(cuentaId, request);
    }
}
