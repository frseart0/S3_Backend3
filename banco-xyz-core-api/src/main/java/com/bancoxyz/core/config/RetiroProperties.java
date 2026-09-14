package com.bancoxyz.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Limites de retiro que el nucleo aplica sin importar el canal de origen. Las
 * restricciones propias de un canal (por ejemplo la denominacion de billetes
 * de un cajero) se validan en su BFF, no aqui.
 */
@ConfigurationProperties(prefix = "core.retiro")
public record RetiroProperties(
        BigDecimal montoMinimo,
        BigDecimal montoMaximoOperacion,
        BigDecimal limiteDiario) {
}
