package com.bancoxyz.core.eventos;

import java.math.BigDecimal;

/**
 * Notificacion de un retiro ya confirmado. El saldo no viaja como estado a
 * reconstruir: el core sigue siendo la fuente de verdad y este evento solo
 * avisa que la transaccion ocurrio.
 */
public record EventoRetiroRealizado(
        String codigoAutorizacion,
        long cuentaId,
        BigDecimal monto,
        String canal,
        String claveIdempotencia,
        BigDecimal saldoResultante,
        String referenciaDispositivo) {
}
