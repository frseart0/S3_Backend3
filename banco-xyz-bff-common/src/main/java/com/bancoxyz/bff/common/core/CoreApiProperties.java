package com.bancoxyz.bff.common.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Conexion de un BFF con el core-api. Los timeouts son parte del contrato del
 * canal: el cajero los define mas agresivos que la web porque un cliente
 * parado frente a la maquina no puede esperar.
 */
@ConfigurationProperties(prefix = "bff.core-api")
public record CoreApiProperties(
        String baseUrl,
        String claveInterna,
        Duration timeoutConexion,
        Duration timeoutLectura) {

    public CoreApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("bff.core-api.base-url es obligatorio");
        }
        if (claveInterna == null || claveInterna.isBlank()) {
            throw new IllegalArgumentException("bff.core-api.clave-interna es obligatoria");
        }
        if (timeoutConexion == null) {
            timeoutConexion = Duration.ofSeconds(2);
        }
        if (timeoutLectura == null) {
            timeoutLectura = Duration.ofSeconds(5);
        }
    }
}
