package com.bancoxyz.clientes.config;

import com.bancoxyz.common.error.ApiSecurityErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * clientes-service es un Resource Server OAuth 2.0: exige un JWT valido
 * (firma via JWKS, issuer, expiracion) con el scope clientes.access.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        ApiSecurityErrorHandlers erroresSeguridad = new ApiSecurityErrorHandlers();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/clientes/**").hasAuthority("SCOPE_clientes.access")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(erroresSeguridad)
                        .accessDeniedHandler(erroresSeguridad)
                );

        return http.build();
    }
}
