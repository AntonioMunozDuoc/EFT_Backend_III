package com.bancoxyz.bff.web.controller;

import com.bancoxyz.bff.web.client.dto.CoreTransaccionResponse;
import com.bancoxyz.bff.web.dto.WebCuentaResponse;
import com.bancoxyz.bff.web.dto.WebTransferenciaRequest;
import jakarta.validation.Valid;
import com.bancoxyz.bff.web.dto.WebDashboardResponse;
import com.bancoxyz.bff.web.dto.WebMovimientoResponse;
import com.bancoxyz.bff.web.service.WebCuentaService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web")
public class WebCuentaController {

    private final WebCuentaService webCuentaService;

    public WebCuentaController(WebCuentaService webCuentaService) {
        this.webCuentaService = webCuentaService;
    }

    @GetMapping("/cuentas")
    public ResponseEntity<List<WebCuentaResponse>> listarCuentas() {

        return ResponseEntity.ok(
                webCuentaService.listarCuentas()
        );
    }

    @GetMapping("/cuentas/{cuentaId}")
    public ResponseEntity<WebCuentaResponse> obtenerCuenta(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                webCuentaService.obtenerCuenta(cuentaId)
        );
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public ResponseEntity<List<WebMovimientoResponse>> obtenerMovimientos(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                webCuentaService.obtenerMovimientos(cuentaId)
        );
    }

    @GetMapping("/dashboard/{cuentaId}")
    public ResponseEntity<WebDashboardResponse> obtenerDashboard(
            @PathVariable
            @Positive(message = "La cuenta debe ser un identificador valido")
            Long cuentaId) {

        return ResponseEntity.ok(
                webCuentaService.obtenerDashboard(cuentaId)
        );
    }

    @PostMapping("/transferencias")
    public ResponseEntity<CoreTransaccionResponse> transferir(
            @Valid @RequestBody WebTransferenciaRequest request) {

        return ResponseEntity.ok(webCuentaService.transferir(request));
    }
}
