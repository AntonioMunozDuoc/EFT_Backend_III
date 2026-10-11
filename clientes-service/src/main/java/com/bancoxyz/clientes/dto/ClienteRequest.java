package com.bancoxyz.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(

        @NotBlank(message = "El documento es obligatorio")
        @Size(max = 20, message = "El documento no puede superar 20 caracteres")
        String documento,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es valido")
        @Size(max = 120, message = "El email no puede superar 120 caracteres")
        String email,

        @Pattern(regexp = "^[0-9+\\- ]{0,20}$", message = "El telefono solo admite digitos, +, - y espacios")
        String telefono,

        @Min(value = 18, message = "La edad minima es 18")
        @Max(value = 120, message = "La edad maxima es 120")
        Integer edad,

        @Pattern(regexp = "(?i)^(BASICO|PREMIUM|SENIOR)?$", message = "El perfil debe ser BASICO, PREMIUM o SENIOR")
        String perfil
) {
}
