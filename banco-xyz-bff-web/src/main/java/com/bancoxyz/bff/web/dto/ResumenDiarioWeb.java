package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.ResumenDiario;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumen diario del Job 1, enriquecido con totales derivados para la
 * pantalla de reportes de la banca web.
 */
public record ResumenDiarioWeb(
        LocalDate fecha,
        Integer totalTransacciones,
        Integer totalValidas,
        Integer totalAnomalias,
        Integer totalDuplicadas,
        BigDecimal montoTotalCredito,
        BigDecimal montoTotalDebito,
        BigDecimal montoNeto) {

    public static ResumenDiarioWeb desde(ResumenDiario resumen) {
        BigDecimal credito = nvl(resumen.montoTotalCredito());
        BigDecimal debito = nvl(resumen.montoTotalDebito());
        return new ResumenDiarioWeb(
                resumen.fecha(),
                resumen.totalTransacciones(),
                resumen.totalValidas(),
                resumen.totalAnomalias(),
                resumen.totalDuplicadas(),
                credito,
                debito,
                credito.subtract(debito));
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
