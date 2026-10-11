package com.bancoxyz.common.http;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.io.IOException;
import java.net.URI;

/**
 * Balanceo de carga del lado del cliente para un RestClient concreto.
 *
 * Convierte http://<serviceId>/ruta en la direccion real de una instancia registrada
 * en Eureka (Spring Cloud LoadBalancer, round-robin entre las instancias vivas).
 *
 * Se usa este interceptor EXPLICITO en lugar de exponer un RestClient.Builder
 * anotado con @LoadBalanced: ese builder global tambien lo toma el propio cliente
 * de Eureka para registrarse, y entonces intentaria resolver "discovery-server"
 * a traves de Eureka (dependencia circular: "No instances available for discovery-server").
 */
public class ServiceIdLoadBalancerInterceptor implements ClientHttpRequestInterceptor {

    private final LoadBalancerClient loadBalancerClient;

    public ServiceIdLoadBalancerInterceptor(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        URI original = request.getURI();
        String serviceId = original.getHost();

        ServiceInstance instance = loadBalancerClient.choose(serviceId);

        if (instance == null) {
            throw new IOException("No instances available for " + serviceId);
        }

        URI destino = loadBalancerClient.reconstructURI(instance, original);

        HttpRequest reescrita = new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return destino;
            }
        };

        return execution.execute(reescrita, body);
    }
}
