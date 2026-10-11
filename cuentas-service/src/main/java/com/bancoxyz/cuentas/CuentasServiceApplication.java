package com.bancoxyz.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de Gestion de Cuentas: apertura, cierre y mantenimiento de
 * cuentas, mas el libro de movimientos de cada cuenta. Es el UNICO dueno de
 * la base de datos banco_cuentas.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bancoxyz.cuentas",
        "com.bancoxyz.common"   // manejo global de errores compartido
})
public class CuentasServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuentasServiceApplication.class, args);
    }
}
