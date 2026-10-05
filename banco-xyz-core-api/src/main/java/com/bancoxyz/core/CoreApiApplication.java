package com.bancoxyz.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API de dominio del Banco XYZ. Expone los datos que dejaron los Jobs de
 * Spring Batch (semanas 1-3) y las operaciones con dinero, y es el unico
 * componente con acceso a PostgreSQL: los tres BFF lo consumen por HTTP.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
