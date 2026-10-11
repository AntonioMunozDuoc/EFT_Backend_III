package com.bancoxyz.clientes.controller;

import com.bancoxyz.clientes.dto.ClienteRequest;
import com.bancoxyz.clientes.dto.ClienteResponse;
import com.bancoxyz.clientes.service.ClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(
            @PathVariable @Positive(message = "El cliente debe ser un identificador valido") Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(
            @PathVariable @Positive(message = "El cliente debe ser un identificador valido") Long id,
            @Valid @RequestBody ClienteRequest request) {
        return service.actualizar(id, request);
    }

    /** Baja logica (estado INACTIVO). */
    @DeleteMapping("/{id}")
    public ClienteResponse darDeBaja(
            @PathVariable @Positive(message = "El cliente debe ser un identificador valido") Long id) {
        return service.darDeBaja(id);
    }
}
