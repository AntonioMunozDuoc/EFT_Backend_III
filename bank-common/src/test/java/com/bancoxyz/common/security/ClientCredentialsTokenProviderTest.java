package com.bancoxyz.common.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el flujo client_credentials contra un servidor de tokens simulado.
 */
class ClientCredentialsTokenProviderTest {

    private HttpServer servidor;
    private String tokenUri;

    private final AtomicInteger llamadas = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();

    private volatile int status = 200;
    private volatile String respuesta =
            "{\"access_token\":\"jwt-de-prueba\",\"token_type\":\"Bearer\",\"expires_in\":300}";

    @BeforeEach
    void iniciarServidor() throws IOException {

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        servidor.createContext("/oauth2/token", exchange -> {

            llamadas.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        servidor.start();

        tokenUri = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/oauth2/token";
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private ClientCredentialsTokenProvider proveedor() {
        return new ClientCredentialsTokenProvider(
                tokenUri,
                "bff-atm",
                "secreto",
                "core.access",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );
    }

    @Test
    void pideElTokenConClientCredentialsYBasicAuth() {

        String token = proveedor().getToken();

        assertEquals("jwt-de-prueba", token);

        String esperado = "Basic " + Base64.getEncoder()
                .encodeToString("bff-atm:secreto".getBytes(StandardCharsets.UTF_8));

        assertEquals(esperado, authorization.get());
        assertTrue(cuerpo.get().contains("grant_type=client_credentials"));
        assertTrue(cuerpo.get().contains("scope=core.access"));
    }

    @Test
    void reutilizaElTokenMientrasNoExpira() {

        ClientCredentialsTokenProvider proveedor = proveedor();

        proveedor.getToken();
        proveedor.getToken();
        proveedor.getToken();

        assertEquals(1, llamadas.get());
    }

    @Test
    void renuevaElTokenCuandoEstaPorExpirar() {

        // expires_in menor que el margen de 30 s: siempre se considera vencido
        respuesta = "{\"access_token\":\"corto\",\"token_type\":\"Bearer\",\"expires_in\":10}";

        ClientCredentialsTokenProvider proveedor = proveedor();

        proveedor.getToken();
        proveedor.getToken();

        assertEquals(2, llamadas.get());
    }

    @Test
    void fallaDelAuthorizationServerSeConvierteEnIllegalState() {

        status = 401;
        respuesta = "{\"error\":\"invalid_client\"}";

        assertThrows(IllegalStateException.class, () -> proveedor().getToken());
    }
}
