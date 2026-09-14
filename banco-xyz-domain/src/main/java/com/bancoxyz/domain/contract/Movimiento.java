package com.bancoxyz.domain.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento de una cuenta tal como lo dejo el Job de estados de cuenta
 * anuales. Los campos {@code estado}, {@code motivo} y
 * {@code procesadoPorHilo} son metadatos de la migracion batch: el BFF web los
 * expone para auditoria y el BFF movil los omite.
 */
public record Movimiento(
        Long id,
        Long cuentaId,
        LocalDate fecha,
        String tipoTransaccion,
        BigDecimal monto,
        String descripcion,
        String estado,
        String motivo,
        String procesadoPorHilo) {
}
