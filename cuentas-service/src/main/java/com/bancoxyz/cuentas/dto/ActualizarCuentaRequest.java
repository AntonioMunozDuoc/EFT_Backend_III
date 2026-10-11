package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Mantenimiento de cuenta: solo tipo y tasa son modificables. */
public record ActualizarCuentaRequest(

        @Pattern(regexp = "(?i)^(ahorro|prestamo|hipoteca|corriente)$",
                message = "El tipo debe ser ahorro, prestamo, hipoteca o corriente")
        String tipo,

        @DecimalMin(value = "0.0", message = "La tasa no puede ser negativa")
        @Digits(integer = 6, fraction = 4, message = "Tasa invalida")
        BigDecimal tasaInteres
) {
}
