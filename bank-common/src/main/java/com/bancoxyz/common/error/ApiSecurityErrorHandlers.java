package com.bancoxyz.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Respuestas 401/403 con el mismo formato JSON que el resto de los errores.
 * Se instancia desde la SecurityConfig de cada BFF (no es un bean por escaneo).
 */
public class ApiSecurityErrorHandlers
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException ex) throws IOException {

        escribir(
                response,
                HttpStatus.UNAUTHORIZED,
                "NO_AUTENTICADO",
                "Token ausente, invalido o expirado",
                request.getRequestURI()
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException ex) throws IOException {

        escribir(
                response,
                HttpStatus.FORBIDDEN,
                "ACCESO_DENEGADO",
                "El token no tiene permisos para este recurso",
                request.getRequestURI()
        );
    }

    private void escribir(
            HttpServletResponse response,
            HttpStatus status,
            String codigo,
            String mensaje,
            String path) throws IOException {

        String json = "{\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + codigo + "\","
                + "\"mensaje\":\"" + escapar(mensaje) + "\","
                + "\"path\":\"" + escapar(path) + "\","
                + "\"detalles\":[]}";

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }

    private String escapar(String valor) {
        return valor == null
                ? ""
                : valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
