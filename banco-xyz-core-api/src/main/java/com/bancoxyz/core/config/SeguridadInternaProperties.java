package com.bancoxyz.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seguridad del nucleo: la clave que los BFF deben presentar en
 * {@code X-Internal-Key} (el core-api no atiende clientes finales, solo a los
 * tres BFF) y el maximo de intentos fallidos antes de bloquear una identidad.
 */
@ConfigurationProperties(prefix = "core.seguridad")
public record SeguridadInternaProperties(String claveInterna, int maxIntentosFallidos) {

    public SeguridadInternaProperties {
        if (claveInterna == null || claveInterna.isBlank()) {
            throw new IllegalArgumentException("core.seguridad.clave-interna es obligatoria");
        }
        if (maxIntentosFallidos <= 0) {
            maxIntentosFallidos = 3;
        }
    }
}
