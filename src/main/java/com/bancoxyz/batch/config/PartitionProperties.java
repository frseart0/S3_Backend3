package com.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de escalamiento via Partitioning (Semana 3): cuantas particiones
 * (gridSize) se generan por archivo y cuantos hilos del pool las ejecutan en
 * paralelo (poolSize).
 */
@ConfigurationProperties(prefix = "batch.partition")
public record PartitionProperties(int gridSize, int poolSize) {
}
