package com.bancoxyz.cuentas.client;

/** Vista minima del cliente que cuentas-service necesita (no comparte clases con clientes-service). */
public record ClienteDto(Long id, String nombre, Integer edad, String estado) {
}
