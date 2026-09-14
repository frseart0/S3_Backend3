package com.bancoxyz.bff.atm.dto;

import com.bancoxyz.domain.contract.Movimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Linea de movimiento para el comprobante impreso: fecha, tipo y monto. */
public record MovimientoAtm(LocalDate fecha, String tipo, BigDecimal monto) {

    public static MovimientoAtm desde(Movimiento movimiento) {
        return new MovimientoAtm(
                movimiento.fecha(),
                movimiento.tipoTransaccion(),
                movimiento.monto() == null ? null : movimiento.monto().setScale(0, RoundingMode.HALF_UP));
    }
}
