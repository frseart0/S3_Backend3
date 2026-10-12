package com.bancoxyz.domain.contract;

import java.math.BigDecimal;

public record SolicitudTransferencia(
        Long cuentaDestinoId,
        BigDecimal monto,
        String canal,
        String claveIdempotencia) {
}
