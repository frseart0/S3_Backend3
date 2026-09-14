package com.bancoxyz.bff.mobile.dto;

import com.bancoxyz.domain.contract.SaldoCuenta;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SaldoMobile(BigDecimal saldo, String moneda) {

    public static SaldoMobile desde(SaldoCuenta saldo) {
        return new SaldoMobile(saldo.saldo().setScale(0, RoundingMode.HALF_UP), "CLP");
    }
}
