package com.bancoxyz.domain.contract;

import java.math.BigDecimal;

/** Estado de cuenta anual agregado por el Tasklet del Job 3. */
public record EstadoCuentaAnual(
        Long cuentaId,
        Integer anio,
        BigDecimal totalDepositos,
        BigDecimal totalSalidas,
        BigDecimal saldoNetoAnual,
        Integer cantidadMovimientos,
        Integer cantidadAnomalias) {
}
