package com.bancoxyz.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Procesos batch migrados desde COBOL/Shell a Spring Batch:
 *   1. reporteTransaccionesDiariasJob
 *   2. calculoInteresesMensualesJob
 *   3. estadosCuentaAnualesJob
 * Los jobs se disparan por API (/api/batch/...) o desde un scheduler externo.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bancoxyz.batch",
        "com.bancoxyz.common"
})
public class BatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
