package com.bancoxyz.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de Gestion de Clientes: informacion personal y perfil de los
 * clientes del banco. Dueno exclusivo de la base de datos banco_clientes.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bancoxyz.clientes",
        "com.bancoxyz.common"
})
public class ClientesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientesServiceApplication.class, args);
    }
}
