package com.bancoxyz.cuentas.client;

import com.bancoxyz.common.error.ServicioNoDisponibleException;
import com.bancoxyz.common.resilience.Causas;
import com.bancoxyz.common.resilience.ResilientCall;
import com.bancoxyz.cuentas.exception.ClienteInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Llamada a clientes-service protegida con CircuitBreaker("clientesApi")
 * + Retry + Bulkhead. Los errores funcionales (404) NO abren el circuito.
 */
@Component
public class ClientesClient {

    private static final Logger log = LoggerFactory.getLogger(ClientesClient.class);

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ResilientCall resilientCall;

    public ClientesClient(
            RestClient clientesRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            ResilientCall clientesResilientCall) {

        this.restClient = clientesRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.resilientCall = clientesResilientCall;
    }

    /**
     * @throws ClienteInvalidoException        si el cliente no existe o esta inactivo
     * @throws ServicioNoDisponibleException   si clientes-service no responde
     */
    public ClienteDto obtener(Long clienteId) {

        return circuitBreakerFactory
                .create("clientesApi")
                .run(
                        resilientCall.decorar(() -> consultar(clienteId), true),
                        this::manejarFallo
                );
    }

    private ClienteDto consultar(Long clienteId) {
        try {
            return restClient.get()
                    .uri("/api/clientes/{id}", clienteId)
                    .retrieve()
                    .body(ClienteDto.class);

        } catch (HttpClientErrorException.NotFound ex) {
            throw new ClienteInvalidoException("Cliente no encontrado: " + clienteId);
        }
    }

    private ClienteDto manejarFallo(Throwable throwable) {

        ClienteInvalidoException invalido = Causas.buscar(throwable, ClienteInvalidoException.class);
        if (invalido != null) {
            throw invalido;
        }

        HttpClientErrorException rechazo = Causas.buscar(throwable, HttpClientErrorException.class);
        if (rechazo != null) {
            throw rechazo;
        }

        log.error("clientes-service no disponible ({})", throwable.getClass().getSimpleName());
        throw new ServicioNoDisponibleException(
                "El servicio de clientes no se encuentra disponible temporalmente");
    }
}
