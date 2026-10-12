package com.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tope de reejecuciones automaticas cuando un Job termina en FAILED.
 * Cada reejecucion usa el mismo JobInstance (Spring Batch retoma los steps
 * incompletos). Llegado el tope, el proceso queda en fallo.
 */
@ConfigurationProperties(prefix = "batch.restart")
public record RestartProperties(int maxIntentos) {

    public RestartProperties {
        if (maxIntentos < 0) {
            maxIntentos = 0;
        }
    }
}
