package com.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Tasas de interes mensual por tipo de cuenta y rango de edad aceptado,
 * usadas por el Job de Calculo de Intereses Mensuales.
 */
@ConfigurationProperties(prefix = "batch.intereses")
public record InteresProperties(
        BigDecimal tasaAhorro,
        BigDecimal tasaPrestamo,
        BigDecimal tasaHipoteca,
        int edadMinima,
        int edadMaxima) {
}
