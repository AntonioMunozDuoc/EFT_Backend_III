package com.bancoxyz.bff.atm.config;

import com.bancoxyz.common.error.ApiSecurityErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * BFF Atm: Resource Server OAuth 2.0.
 *
 * Ya no emite tokens propios: el access token lo entrega el auth-server
 * (grant client_credentials) y aqui solo se valida (firma RSA via JWKS,
 * expiracion, issuer) y se exige el scope atm.access.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        ApiSecurityErrorHandlers erroresSeguridad =
                new ApiSecurityErrorHandlers();

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()

                        .requestMatchers("/api/atm/**")
                        .hasAuthority("SCOPE_atm.access")

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(erroresSeguridad)
                        .accessDeniedHandler(erroresSeguridad)
                );

        return http.build();
    }
}
