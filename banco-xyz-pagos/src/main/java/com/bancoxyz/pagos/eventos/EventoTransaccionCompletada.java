package com.bancoxyz.pagos.eventos;

import java.math.BigDecimal;

/** Aviso de un deposito, pago o transferencia ya confirmados en el saldo. */
public record EventoTransaccionCompletada(
        String tipo,
        String codigoAutorizacion,
        long cuentaId,
        Long cuentaDestinoId,
        BigDecimal monto,
        String canal,
        String claveIdempotencia,
        BigDecimal saldoResultante) {
}
