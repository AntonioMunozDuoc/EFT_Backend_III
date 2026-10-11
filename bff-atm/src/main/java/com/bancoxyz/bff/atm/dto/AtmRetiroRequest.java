package com.bancoxyz.bff.atm.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AtmRetiroRequest(

        @NotNull(message = "La cuenta es obligatoria")
        @Positive(message = "La cuenta debe ser valida")
        Long cuentaId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 13 enteros y 2 decimales")
        BigDecimal monto
) {
}