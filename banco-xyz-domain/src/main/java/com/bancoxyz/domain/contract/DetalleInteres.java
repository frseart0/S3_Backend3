package com.bancoxyz.domain.contract;

import com.bancoxyz.domain.TipoCuenta;

import java.math.BigDecimal;

/** Resultado del calculo de intereses mensuales de una cuenta. */
public record DetalleInteres(
        Long cuentaId,
        TipoCuenta tipo,
        BigDecimal saldoInicial,
        BigDecimal tasaAplicada,
        BigDecimal interesCalculado,
        BigDecimal saldoFinal,
        Integer edadUtilizada,
        String estado,
        String observacion) {
}
