package com.bancoxyz.bff.mobile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites del canal movil. Existen para que el payload no crezca por accidente:
 * si el cliente pide 500 movimientos, el BFF le entrega el maximo que tiene
 * sentido en un telefono.
 */
@ConfigurationProperties(prefix = "bff.mobile")
public record MobileProperties(int movimientosInicio, int movimientosMaximo, int largoDetalle) {

    public MobileProperties {
        if (movimientosInicio <= 0) {
            movimientosInicio = 5;
        }
        if (movimientosMaximo <= 0) {
            movimientosMaximo = 25;
        }
        if (largoDetalle <= 0) {
            largoDetalle = 40;
        }
    }
}
