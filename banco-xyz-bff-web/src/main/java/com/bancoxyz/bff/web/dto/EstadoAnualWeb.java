package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.EstadoCuentaAnual;

import java.math.BigDecimal;

public record EstadoAnualWeb(
        Integer anio,
        BigDecimal totalDepositos,
        BigDecimal totalSalidas,
        BigDecimal saldoNetoAnual,
        Integer cantidadMovimientos,
        Integer cantidadAnomalias) {

    public static EstadoAnualWeb desde(EstadoCuentaAnual estado) {
        return new EstadoAnualWeb(
                estado.anio(),
                estado.totalDepositos(),
                estado.totalSalidas(),
                estado.saldoNetoAnual(),
                estado.cantidadMovimientos(),
                estado.cantidadAnomalias());
    }
}
