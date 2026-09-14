package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.SaldoCuenta;

import java.math.BigDecimal;
import java.time.Instant;

public record SaldoWeb(Long cuentaId, BigDecimal saldo, String moneda, Instant actualizadoEn) {

    public static SaldoWeb desde(SaldoCuenta saldo) {
        return new SaldoWeb(saldo.cuentaId(), saldo.saldo(), "CLP", saldo.actualizadoEn());
    }
}
