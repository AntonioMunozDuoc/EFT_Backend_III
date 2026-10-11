package com.bancoxyz.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de Procesamiento de Pagos: pagos, transferencias, depositos y
 * retiros. Orquesta a cuentas-service (saga con compensacion) y publica los
 * eventos de transacciones completadas y alertas de seguridad en Kafka.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bancoxyz.pagos",
        "com.bancoxyz.common"
})
public class PagosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosServiceApplication.class, args);
    }
}
