package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Comprobante de un retiro aplicado. {@code reintento=true} indica que la
 * solicitud ya se habia procesado antes con la misma clave de idempotencia y
 * este comprobante es el original, no un segundo cargo.
 */
public record ComprobanteRetiro(
        String codigoAutorizacion,
        Long cuentaId,
        BigDecimal montoRetirado,
        BigDecimal saldoResultante,
        Instant realizadoEn,
        boolean reintento) {
}
