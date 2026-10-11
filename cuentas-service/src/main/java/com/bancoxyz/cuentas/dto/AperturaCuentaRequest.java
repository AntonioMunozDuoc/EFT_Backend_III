package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Apertura de cuenta. El cliente se valida en clientes-service. Si ese
 * servicio no responde, la cuenta puede abrirse en estado PENDIENTE_VERIFICACION
 * siempre que el request incluya nombre y edad (comportamiento alternativo).
 */
public record AperturaCuentaRequest(

        @NotNull(message = "El cliente es obligatorio")
        @Positive(message = "El cliente debe ser un identificador valido")
        Long clienteId,

        @NotBlank(message = "El tipo de cuenta es obligatorio")
        @Pattern(regexp = "(?i)^(ahorro|prestamo|hipoteca|corriente)$",
                message = "El tipo debe ser ahorro, prestamo, hipoteca o corriente")
        String tipo,

        @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo")
        @Digits(integer = 13, fraction = 2, message = "El saldo admite hasta 13 enteros y 2 decimales")
        BigDecimal saldoInicial,

        @DecimalMin(value = "0.0", message = "La tasa no puede ser negativa")
        @Digits(integer = 6, fraction = 4, message = "Tasa invalida")
        BigDecimal tasaInteres,

        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @Min(value = 18, message = "La edad minima es 18")
        @Max(value = 120, message = "La edad maxima es 120")
        Integer edad
) {
}
