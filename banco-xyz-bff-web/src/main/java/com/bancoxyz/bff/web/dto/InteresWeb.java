package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.DetalleInteres;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Detalle del interes mensual. La tasa se entrega tambien en porcentaje ya
 * calculado para que el front no tenga que replicar la conversion.
 */
public record InteresWeb(
        String tipo,
        BigDecimal saldoInicial,
        BigDecimal tasaAplicada,
        BigDecimal tasaAplicadaPorcentaje,
        BigDecimal interesCalculado,
        BigDecimal saldoFinal,
        Integer edadUtilizada,
        String estadoRegistro,
        String observacion) {

    public static InteresWeb desde(DetalleInteres detalle) {
        BigDecimal tasa = detalle.tasaAplicada();
        return new InteresWeb(
                detalle.tipo() == null ? null : detalle.tipo().name(),
                detalle.saldoInicial(),
                tasa,
                tasa == null ? null : tasa.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                detalle.interesCalculado(),
                detalle.saldoFinal(),
                detalle.edadUtilizada(),
                detalle.estado(),
                detalle.observacion());
    }
}
