package com.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limite maximo de items "salteados" (skip) permitidos por step/partition
 * antes de que el {@link com.bancoxyz.batch.common.policy.BankSkipPolicy}
 * aborte el Job (tolerancia a fallos personalizada, Semana 2).
 */
@ConfigurationProperties(prefix = "batch.skip")
public record SkipProperties(int limit) {
}
