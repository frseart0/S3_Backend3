package com.bancoxyz.domain.contract;

import java.math.BigDecimal;

/**
 * Apertura de cuenta. Si {@code cuentaId} viene nulo, el servicio asigna el
 * siguiente identificador libre.
 */
public record SolicitudAperturaCuenta(
        Long cuentaId,
        String tipo,
        String alias,
        BigDecimal saldoInicial,
        String clienteUsuario) {
}
