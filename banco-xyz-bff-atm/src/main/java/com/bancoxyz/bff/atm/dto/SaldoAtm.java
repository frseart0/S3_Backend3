package com.bancoxyz.bff.atm.dto;

import com.bancoxyz.domain.contract.SaldoCuenta;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Lo minimo que se imprime o se muestra en la pantalla de un cajero. */
public record SaldoAtm(BigDecimal saldoDisponible, String moneda) {

    public static SaldoAtm desde(SaldoCuenta saldo) {
        return new SaldoAtm(saldo.saldo().setScale(0, RoundingMode.DOWN), "CLP");
    }
}
