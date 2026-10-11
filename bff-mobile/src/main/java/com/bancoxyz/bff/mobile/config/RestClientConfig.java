package com.bancoxyz.bff.mobile.config;

import com.bancoxyz.common.http.ServiceIdLoadBalancerInterceptor;
import com.bancoxyz.common.resilience.ResilientCall;
import com.bancoxyz.common.security.ClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Identidad OAuth2 de este BFF frente al Core API (client_credentials).
     */
    @Bean
    public ClientCredentialsTokenProvider coreTokenProvider(
            @Value("${oauth2.client.token-uri}") String tokenUri,
            @Value("${oauth2.client.client-id}") String clientId,
            @Value("${oauth2.client.client-secret}") String clientSecret,
            @Value("${oauth2.client.scope}") String scope,
            @Value("${core.api.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${core.api.read-timeout-ms:3000}") long readTimeoutMs) {

        return new ClientCredentialsTokenProvider(
                tokenUri,
                clientId,
                clientSecret,
                scope,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs)
        );
    }

    /**
     * Timeouts explicitos (sin ellos una llamada colgada ocuparia el hilo
     * indefinidamente y el Circuit Breaker no contabilizaria la falla) y
     * Authorization: Bearer con el token del BFF en cada llamada al Core.
     */
    @Bean
    public RestClient coreRestClient(
            LoadBalancerClient loadBalancerClient,
            @Value("${core.api.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${core.api.read-timeout-ms:3000}") long readTimeoutMs,
            ClientCredentialsTokenProvider tokenProvider) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new ServiceIdLoadBalancerInterceptor(loadBalancerClient))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.getToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * Retry + Bulkhead de Resilience4j para las llamadas al Core
     * (el Circuit Breaker "coreApi" envuelve a ambos).
     */
    @Bean
    public ResilientCall coreResilientCall(
            @Value("${resilience.retry.max-attempts:3}") int maxIntentos,
            @Value("${resilience.retry.wait-ms:300}") long esperaMs,
            @Value("${resilience.bulkhead.max-concurrent-calls:10}") int maxConcurrentes,
            @Value("${resilience.bulkhead.max-wait-ms:100}") long esperaBulkheadMs) {

        return new ResilientCall(
                "coreApi",
                maxIntentos,
                Duration.ofMillis(esperaMs),
                maxConcurrentes,
                Duration.ofMillis(esperaBulkheadMs)
        );
    }
}
