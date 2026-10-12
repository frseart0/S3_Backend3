package com.bancoxyz.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Consultas de los reportes que dejaron los Jobs de Spring Batch.
 * Clientes, cuentas y pagos viven en sus propios servicios.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
