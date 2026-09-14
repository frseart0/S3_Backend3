package com.bancoxyz.bff.atm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Restricciones fisicas y operativas del canal cajero. Son reglas del canal,
 * no del nucleo: el core-api valida saldo y limite diario, pero la
 * denominacion de los billetes solo tiene sentido aqui.
 */
@ConfigurationProperties(prefix = "bff.atm")
public record AtmProperties(
        BigDecimal denominacion,
        BigDecimal montoMaximo,
        int movimientosComprobante) {

    public AtmProperties {
        if (denominacion == null || denominacion.signum() <= 0) {
            throw new IllegalArgumentException("bff.atm.denominacion debe ser positiva");
        }
        if (montoMaximo == null || montoMaximo.signum() <= 0) {
            throw new IllegalArgumentException("bff.atm.monto-maximo debe ser positivo");
        }
        if (movimientosComprobante <= 0) {
            movimientosComprobante = 3;
        }
    }
}
