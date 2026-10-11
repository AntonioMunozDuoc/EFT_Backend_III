package com.bancoxyz.clientes.dto;

public record ClienteResponse(
        Long id,
        String documento,
        String nombre,
        String email,
        String telefono,
        Integer edad,
        String perfil,
        String estado
) {
}
