package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.Instant;

/** Saldo vigente de una cuenta, unica fuente de verdad para los tres canales. */
public record SaldoCuenta(
        Long cuentaId,
        BigDecimal saldo,
        Instant actualizadoEn) {
}
