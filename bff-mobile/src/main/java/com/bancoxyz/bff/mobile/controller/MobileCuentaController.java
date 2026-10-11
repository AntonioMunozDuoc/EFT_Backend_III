package com.bancoxyz.bff.mobile.controller;

import com.bancoxyz.bff.mobile.dto.MobileCuentaResponse;
import com.bancoxyz.bff.mobile.dto.MobileMovimientoResponse;
import com.bancoxyz.bff.mobile.dto.MobileResumenResponse;
import com.bancoxyz.bff.mobile.service.MobileCuentaService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mobile")
public class MobileCuentaController {

    private final MobileCuentaService mobileCuentaService;

    public MobileCuentaController(
            MobileCuentaService mobileCuentaService) {

        this.mobileCuentaService = mobileCuentaService;
    }

    @GetMapping("/cuentas/{cuentaId}")
    public ResponseEntity<MobileCuentaResponse> obtenerCuenta(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                mobileCuentaService.obtenerCuenta(cuentaId)
        );
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos-recientes")
    public ResponseEntity<List<MobileMovimientoResponse>> obtenerMovimientosRecientes(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                mobileCuentaService.obtenerUltimosMovimientos(cuentaId)
        );
    }

    @GetMapping("/resumen/{cuentaId}")
    public ResponseEntity<MobileResumenResponse> obtenerResumen(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                mobileCuentaService.obtenerResumen(cuentaId)
        );
    }
}