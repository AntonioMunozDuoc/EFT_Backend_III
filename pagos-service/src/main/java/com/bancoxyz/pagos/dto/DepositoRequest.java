package com.bancoxyz.pagos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositoRequest(

        @NotNull(message = "La cuenta es obligatoria")
        @Positive(message = "La cuenta debe ser un identificador valido")
        Long cuentaId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 13 enteros y 2 decimales")
        BigDecimal monto,

        @Size(max = 200, message = "La descripcion no puede superar 200 caracteres")
        String descripcion,

        @Size(max = 80, message = "La referencia no puede superar 80 caracteres")
        String referencia
) {
}
