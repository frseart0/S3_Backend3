package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.Movimiento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento con todo el detalle disponible, incluidos los metadatos de la
 * migracion batch ({@code estadoRegistro}, {@code motivo},
 * {@code procesadoPorHilo}) que permiten auditar de donde viene cada fila.
 */
public record MovimientoWeb(
        Long id,
        LocalDate fecha,
        String tipoTransaccion,
        BigDecimal monto,
        String descripcion,
        boolean salidaDeDinero,
        String estadoRegistro,
        String motivo,
        String procesadoPorHilo) {

    public static MovimientoWeb desde(Movimiento movimiento) {
        BigDecimal monto = movimiento.monto();
        return new MovimientoWeb(
                movimiento.id(),
                movimiento.fecha(),
                movimiento.tipoTransaccion(),
                monto,
                movimiento.descripcion(),
                monto != null && monto.signum() < 0,
                movimiento.estado(),
                movimiento.motivo(),
                movimiento.procesadoPorHilo());
    }
}
