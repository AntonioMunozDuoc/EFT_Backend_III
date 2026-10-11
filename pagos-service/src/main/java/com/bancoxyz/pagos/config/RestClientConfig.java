package com.bancoxyz.pagos.config;

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

/** Cliente HTTP hacia cuentas-service: Eureka + LoadBalancer + token OAuth2. */
@Configuration
public class RestClientConfig {

    @Bean
    public ClientCredentialsTokenProvider cuentasTokenProvider(
            @Value("${oauth2.client.token-uri}") String tokenUri,
            @Value("${oauth2.client.client-id}") String clientId,
            @Value("${oauth2.client.client-secret}") String clientSecret,
            @Value("${oauth2.client.scope}") String scope,
            @Value("${cuentas.api.connect-timeout-ms:2000}") long connectMs,
            @Value("${cuentas.api.read-timeout-ms:3000}") long readMs) {

        return new ClientCredentialsTokenProvider(
                tokenUri, clientId, clientSecret, scope,
                Duration.ofMillis(connectMs), Duration.ofMillis(readMs));
    }

    @Bean
    public RestClient cuentasRestClient(
            LoadBalancerClient loadBalancerClient,
            @Value("${cuentas.api.base-url:http://cuentas-service}") String baseUrl,
            @Value("${cuentas.api.connect-timeout-ms:2000}") long connectMs,
            @Value("${cuentas.api.read-timeout-ms:3000}") long readMs,
            ClientCredentialsTokenProvider cuentasTokenProvider) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new ServiceIdLoadBalancerInterceptor(loadBalancerClient))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(cuentasTokenProvider.getToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public ResilientCall cuentasResilientCall(
            @Value("${resilience.retry.max-attempts:3}") int maxIntentos,
            @Value("${resilience.retry.wait-ms:300}") long esperaMs,
            @Value("${resilience.bulkhead.max-concurrent-calls:10}") int maxConcurrentes,
            @Value("${resilience.bulkhead.max-wait-ms:100}") long esperaBulkheadMs) {

        return new ResilientCall("cuentasApi", maxIntentos, Duration.ofMillis(esperaMs),
                maxConcurrentes, Duration.ofMillis(esperaBulkheadMs));
    }
}
