package com.bancoxyz.bff.mobile.dto;

import com.bancoxyz.domain.contract.Movimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Movimiento reducido a lo que se ve en una fila de lista del telefono. De los
 * nueve campos que entrega el core se conservan cuatro: se descartan el id
 * interno, el estado, el motivo y el hilo que lo proceso, que son metadatos de
 * auditoria sin valor para el cliente en movilidad.
 */
public record MovimientoMobile(LocalDate fecha, String tipo, BigDecimal monto, String detalle) {

    public static MovimientoMobile desde(Movimiento movimiento, int largoDetalle) {
        return new MovimientoMobile(
                movimiento.fecha(),
                movimiento.tipoTransaccion(),
                redondear(movimiento.monto()),
                acortar(movimiento.descripcion(), largoDetalle));
    }

    /** Los pesos no tienen decimales: enviarlos solo agrega bytes. */
    private static BigDecimal redondear(BigDecimal monto) {
        return monto == null ? null : monto.setScale(0, RoundingMode.HALF_UP);
    }

    private static String acortar(String detalle, int largoMaximo) {
        if (detalle == null || detalle.length() <= largoMaximo) {
            return detalle;
        }
        return detalle.substring(0, largoMaximo - 3) + "...";
    }
}
