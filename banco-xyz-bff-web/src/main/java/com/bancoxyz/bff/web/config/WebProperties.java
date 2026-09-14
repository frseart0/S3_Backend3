package com.bancoxyz.bff.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Ajustes propios del canal web.
 *
 * @param origenesPermitidos      origenes del SPA autorizados por CORS
 * @param movimientosDashboard    cuantos movimientos acompanan al dashboard
 */
@ConfigurationProperties(prefix = "bff.web")
public record WebProperties(List<String> origenesPermitidos, int movimientosDashboard) {

    public WebProperties {
        if (origenesPermitidos == null || origenesPermitidos.isEmpty()) {
            throw new IllegalArgumentException("bff.web.origenes-permitidos es obligatorio");
        }
        if (movimientosDashboard <= 0) {
            movimientosDashboard = 20;
        }
    }
}
