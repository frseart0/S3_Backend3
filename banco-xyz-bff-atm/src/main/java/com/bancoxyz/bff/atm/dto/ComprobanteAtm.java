package com.bancoxyz.bff.atm.dto;

import com.bancoxyz.domain.contract.ComprobanteRetiro;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Comprobante de retiro, el equivalente al voucher que imprime la maquina.
 * {@code reintento=true} avisa que la orden ya se habia aplicado y que este es
 * el comprobante original: el cajero no debe entregar efectivo dos veces.
 */
public record ComprobanteAtm(
        String codigoAutorizacion,
        BigDecimal montoEntregado,
        BigDecimal saldoResultante,
        Instant fechaHora,
        String dispositivoId,
        boolean reintento) {

    public static ComprobanteAtm desde(ComprobanteRetiro comprobante, String dispositivoId) {
        return new ComprobanteAtm(
                comprobante.codigoAutorizacion(),
                comprobante.montoRetirado(),
                comprobante.saldoResultante(),
                comprobante.realizadoEn(),
                dispositivoId,
                comprobante.reintento());
    }
}
