package com.bancoxyz.domain.contract;

import java.math.BigDecimal;

/**
 * Orden de retiro que un BFF envia al core-api. {@code claveIdempotencia} es
 * obligatoria: si el cajero reintenta por un timeout de red, el core devuelve
 * el comprobante original en vez de descontar el saldo dos veces.
 */
public record SolicitudRetiro(
        BigDecimal monto,
        String canal,
        String claveIdempotencia,
        String referenciaDispositivo) {
}
