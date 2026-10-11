package com.bancoxyz.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servidor de autorizacion OAuth 2.0 del Banco XYZ.
 *
 * Toda la configuracion (clientes registrados, scopes, issuer) se declara en
 * application.properties: Spring Boot crea el RegisteredClientRepository, las
 * llaves RSA para firmar los JWT y los endpoints /oauth2/token y /oauth2/jwks.
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
