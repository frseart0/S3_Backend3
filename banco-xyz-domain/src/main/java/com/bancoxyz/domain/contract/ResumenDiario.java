package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resumen diario agregado por el Tasklet del Job 1. */
public record ResumenDiario(
        LocalDate fecha,
        Integer totalTransacciones,
        Integer totalValidas,
        Integer totalAnomalias,
        Integer totalDuplicadas,
        BigDecimal montoTotalCredito,
        BigDecimal montoTotalDebito) {
}
