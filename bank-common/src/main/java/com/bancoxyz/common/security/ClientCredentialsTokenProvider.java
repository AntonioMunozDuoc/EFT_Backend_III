package com.bancoxyz.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Cliente OAuth 2.0 (grant client_credentials) usado por los BFF para llamar
 * al Core API con su propia identidad de servicio.
 *
 * Obtiene un access token en el Authorization Server y lo mantiene en cache
 * hasta poco antes de que expire, de modo que no se pide un token por cada
 * solicitud.
 */
public class ClientCredentialsTokenProvider {

    private static final Logger log =
            LoggerFactory.getLogger(ClientCredentialsTokenProvider.class);

    /** Margen para renovar el token antes de su expiracion real. */
    private static final Duration MARGEN = Duration.ofSeconds(30);

    private final RestClient authClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    private String accessToken;
    private Instant expiraEn = Instant.EPOCH;

    public ClientCredentialsTokenProvider(
            String tokenUri,
            String clientId,
            String clientSecret,
            String scope,
            Duration connectTimeout,
            Duration readTimeout) {

        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(readTimeout);

        this.authClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /** Devuelve un access token vigente (lo renueva si esta por expirar). */
    public synchronized String getToken() {

        if (accessToken == null || Instant.now().isAfter(expiraEn.minus(MARGEN))) {
            renovar();
        }

        return accessToken;
    }

    private void renovar() {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);

        Map<String, Object> respuesta;

        try {
            respuesta = authClient.post()
                    .uri(tokenUri)
                    .headers(h -> h.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

        } catch (RestClientException ex) {
            // Cualquier falla del Authorization Server es una falla de
            // infraestructura (el Circuit Breaker la cuenta y el BFF responde 503).
            throw new IllegalStateException(
                    "No fue posible obtener el token OAuth2 de '" + clientId + "'", ex);
        }

        if (respuesta == null || respuesta.get("access_token") == null) {
            throw new IllegalStateException(
                    "El Authorization Server no entrego un access_token");
        }

        long segundos = respuesta.get("expires_in") instanceof Number n
                ? n.longValue()
                : 300L;

        this.accessToken = respuesta.get("access_token").toString();
        this.expiraEn = Instant.now().plusSeconds(segundos);

        log.info(
                "Token OAuth2 obtenido para el cliente '{}' (scope={}, expira en {} s)",
                clientId,
                scope,
                segundos
        );
    }
}
