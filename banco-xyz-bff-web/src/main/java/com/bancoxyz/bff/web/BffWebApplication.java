package com.bancoxyz.bff.web;

import com.bancoxyz.bff.common.config.BffCommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * BFF del canal web. Optimizado para navegadores: compone en una sola llamada
 * todo lo que necesita una pantalla de banca en linea y devuelve el detalle
 * completo, incluidos los metadatos de auditoria de la migracion batch.
 */
@SpringBootApplication
@Import(BffCommonConfig.class)
public class BffWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffWebApplication.class, args);
    }
}
