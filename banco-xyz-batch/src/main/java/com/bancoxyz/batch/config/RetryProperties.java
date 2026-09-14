package com.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Politica de reintento con backoff exponencial usada para errores
 * transitorios de escritura en base de datos (tolerancia a fallos, Semana 2).
 */
@ConfigurationProperties(prefix = "batch.retry")
public record RetryProperties(int limit, long initialIntervalMs, double multiplier, long maxIntervalMs) {
}
