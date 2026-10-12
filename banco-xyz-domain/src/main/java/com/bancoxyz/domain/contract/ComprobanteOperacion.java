package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Comprobante de un deposito, pago o transferencia.
 * {@code reintento=true} significa que la clave de idempotencia ya se habia aplicado.
 */
public record ComprobanteOperacion(
        String tipo,
        String codigoAutorizacion,
        Long cuentaId,
        Long cuentaDestinoId,
        BigDecimal monto,
        BigDecimal saldoResultante,
        Instant realizadoEn,
        boolean reintento) {
}
