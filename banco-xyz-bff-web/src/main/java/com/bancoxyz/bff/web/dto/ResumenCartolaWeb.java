package com.bancoxyz.bff.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totales que el BFF calcula por el front: sumar ingresos y egresos en el
 * navegador obligaria a traerse todos los movimientos, mientras que aqui se
 * resuelve sobre los datos que ya se pidieron al core.
 */
public record ResumenCartolaWeb(
        int movimientosMostrados,
        long totalMovimientos,
        BigDecimal totalIngresos,
        BigDecimal totalEgresos,
        long movimientosConAnomalia) {

    public static ResumenCartolaWeb de(List<MovimientoWeb> movimientos, long totalMovimientos) {
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        long anomalias = 0;

        for (MovimientoWeb movimiento : movimientos) {
            if (movimiento.monto() != null) {
                if (movimiento.salidaDeDinero()) {
                    egresos = egresos.add(movimiento.monto().abs());
                } else {
                    ingresos = ingresos.add(movimiento.monto());
                }
            }
            if ("ANOMALIA".equals(movimiento.estadoRegistro())) {
                anomalias++;
            }
        }
        return new ResumenCartolaWeb(movimientos.size(), totalMovimientos, ingresos, egresos, anomalias);
    }
}
