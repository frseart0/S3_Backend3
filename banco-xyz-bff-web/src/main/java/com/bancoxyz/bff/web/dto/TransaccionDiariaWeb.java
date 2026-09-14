package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.TransaccionDiaria;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila del reporte de transacciones diarias, con los metadatos de auditoria
 * que el Job 1 dejo en cada registro (estado y motivo).
 */
public record TransaccionDiariaWeb(
        Long id,
        Long transaccionIdOrigen,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String estadoRegistro,
        String motivo,
        boolean anomalia) {

    public static TransaccionDiariaWeb desde(TransaccionDiaria transaccion) {
        String estado = transaccion.estado();
        return new TransaccionDiariaWeb(
                transaccion.id(),
                transaccion.transaccionIdOrigen(),
                transaccion.fecha(),
                transaccion.monto(),
                transaccion.tipo(),
                estado,
                transaccion.motivo(),
                estado != null && !"VALIDA".equals(estado) && !"VALIDA_CORREGIDA".equals(estado));
    }
}
