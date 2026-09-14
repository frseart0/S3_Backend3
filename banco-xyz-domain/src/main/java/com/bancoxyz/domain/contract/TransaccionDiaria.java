package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Transaccion individual del reporte diario (Job 1). */
public record TransaccionDiaria(
        Long id,
        Long transaccionIdOrigen,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String estado,
        String motivo) {
}
