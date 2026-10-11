package com.bancoxyz.bff.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WebTransferenciaRequest(

        @NotNull(message = "La cuenta de origen es obligatoria")
        @Positive(message = "La cuenta de origen debe ser valida")
        Long cuentaOrigen,

        @NotNull(message = "La cuenta de destino es obligatoria")
        @Positive(message = "La cuenta de destino debe ser valida")
        Long cuentaDestino,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 13 enteros y 2 decimales")
        BigDecimal monto,

        @Size(max = 200, message = "La descripcion no puede superar 200 caracteres")
        String descripcion,

        /** Opcional: reenviar la misma referencia hace la operacion idempotente. */
        @Size(max = 80, message = "La referencia no puede superar 80 caracteres")
        String referencia
) {
}
