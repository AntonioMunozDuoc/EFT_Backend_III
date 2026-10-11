package com.bancoxyz.pagos.controller;

import com.bancoxyz.pagos.dto.DepositoRequest;
import com.bancoxyz.pagos.dto.PagoRequest;
import com.bancoxyz.pagos.dto.RetiroRequest;
import com.bancoxyz.pagos.dto.TransaccionResponse;
import com.bancoxyz.pagos.dto.TransferenciaRequest;
import com.bancoxyz.pagos.service.TransaccionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final TransaccionService service;

    public PagoController(TransaccionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransaccionResponse> pagar(@Valid @RequestBody PagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.pagar(request));
    }

    @PostMapping("/transferencias")
    public ResponseEntity<TransaccionResponse> transferir(@Valid @RequestBody TransferenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transferir(request));
    }

    @PostMapping("/depositos")
    public ResponseEntity<TransaccionResponse> depositar(@Valid @RequestBody DepositoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.depositar(request));
    }

    @PostMapping("/retiros")
    public ResponseEntity<TransaccionResponse> retirar(@Valid @RequestBody RetiroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.retirar(request));
    }

    @GetMapping("/{id}")
    public TransaccionResponse obtener(
            @PathVariable @Positive(message = "La transaccion debe ser un identificador valido") Long id) {
        return service.obtener(id);
    }

    @GetMapping("/cuenta/{cuentaId}")
    public List<TransaccionResponse> porCuenta(
            @PathVariable @Positive(message = "La cuenta debe ser un identificador valido") Long cuentaId) {
        return service.porCuenta(cuentaId);
    }
}
