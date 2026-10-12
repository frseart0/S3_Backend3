package com.bancoxyz.domain.contract;

import java.math.BigDecimal;

/** Deposito o pago. La clave de idempotencia evita aplicar dos veces la misma orden. */
public record SolicitudMovimiento(
        BigDecimal monto,
        String canal,
        String claveIdempotencia,
        String referencia) {
}
