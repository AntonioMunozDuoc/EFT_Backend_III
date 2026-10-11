package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Operacion interna sobre una cuenta (la invoca pagos-service).
 * operationId identifica la operacion: reenviarla (por reintento) no la aplica dos veces.
 */
public record OperacionRequest(

        @NotBlank(message = "operationId es obligatorio")
        @Size(max = 100, message = "operationId no puede superar 100 caracteres")
        String operationId,

        @NotBlank(message = "El tipo es obligatorio")
        @Pattern(regexp = "^(debito|credito)$", message = "El tipo debe ser debito o credito")
        String tipo,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 13 enteros y 2 decimales")
        BigDecimal monto,

        @Size(max = 255, message = "La descripcion no puede superar 255 caracteres")
        String descripcion
) {
}
