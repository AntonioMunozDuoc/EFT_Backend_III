package com.bancoxyz.common.error;

import com.bancoxyz.common.error.ApiErrorResponse.CampoError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Manejo global de errores compartido por Core API y BFF.
 *
 * Se ejecuta con la prioridad mas baja: los handlers de dominio de cada
 * servicio (@Order HIGHEST_PRECEDENCE) se evaluan primero y este maneja
 * validaciones, errores de protocolo HTTP y el fallback general.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(CommonExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> manejarCuerpoInvalido(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<CampoError> detalles = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> new CampoError(e.getField(), e.getDefaultMessage()))
                .toList();

        return validacion(request, detalles);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> manejarParametrosInvalidos(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {

        List<CampoError> detalles = ex.getParameterValidationResults()
                .stream()
                .flatMap(resultado -> resultado.getResolvableErrors()
                        .stream()
                        .map(e -> new CampoError(
                                resultado.getMethodParameter().getParameterName(),
                                e.getDefaultMessage())))
                .toList();

        return validacion(request, detalles);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> manejarViolaciones(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<CampoError> detalles = ex.getConstraintViolations()
                .stream()
                .map(v -> new CampoError(
                        v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();

        return validacion(request, detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> manejarJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.BAD_REQUEST,
                "JSON_INVALIDO",
                "El cuerpo de la solicitud esta vacio o tiene un formato invalido",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> manejarTipoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.BAD_REQUEST,
                "PARAMETRO_INVALIDO",
                "El parametro '" + ex.getName() + "' tiene un valor invalido",
                request,
                List.of(new CampoError(ex.getName(), "Tipo de dato invalido"))
        );
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiErrorResponse> manejarCredenciales(
            CredencialesInvalidasException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.UNAUTHORIZED,
                "CREDENCIALES_INVALIDAS",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(CoreNoDisponibleException.class)
    public ResponseEntity<ApiErrorResponse> manejarCoreNoDisponible(
            CoreNoDisponibleException ex,
            HttpServletRequest request) {

        return ApiErrors.respuesta(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICIO_NO_DISPONIBLE",
                ex.getMessage(),
                request
        );
    }

    /**
     * Error 4xx devuelto por un microservicio que el BFF no tradujo a un error propio.
     * Se conservan 404/409/422 (reglas de negocio) con el mensaje del servicio de origen;
     * cualquier otro 4xx se informa como 400 generico.
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiErrorResponse> manejarRechazoDelCore(
            HttpClientErrorException ex,
            HttpServletRequest request) {

        int codigo = ex.getStatusCode().value();

        if (codigo == 404 || codigo == 409 || codigo == 422) {

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"mensaje\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(ex.getResponseBodyAsString());

            return ApiErrors.respuesta(
                    HttpStatus.valueOf(codigo),
                    "OPERACION_RECHAZADA",
                    m.find() ? m.group(1) : "El servicio rechazo la operacion",
                    request
            );
        }

        return ApiErrors.respuesta(
                HttpStatus.BAD_REQUEST,
                "SOLICITUD_RECHAZADA",
                "El servicio rechazo la solicitud",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> manejarGeneral(
            Exception ex,
            HttpServletRequest request) {

        // Errores de protocolo HTTP de Spring (405, 415, 404 de ruta, etc.)
        if (ex instanceof ErrorResponse errorHttp) {

            HttpStatus status = HttpStatus.resolve(
                    errorHttp.getStatusCode().value());

            String codigo = status == null
                    ? "ERROR_HTTP"
                    : status.name();

            String mensaje = status == null
                    ? "Error en la solicitud"
                    : status.getReasonPhrase();

            return ApiErrors.respuesta(
                    errorHttp.getStatusCode(), codigo, mensaje, request);
        }

        log.error("Error no controlado en {}", request.getRequestURI(), ex);

        return ApiErrors.respuesta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Ocurrio un error interno en el servidor",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> validacion(
            HttpServletRequest request,
            List<CampoError> detalles) {

        return ApiErrors.respuesta(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "La solicitud contiene datos invalidos",
                request,
                detalles
        );
    }
}
