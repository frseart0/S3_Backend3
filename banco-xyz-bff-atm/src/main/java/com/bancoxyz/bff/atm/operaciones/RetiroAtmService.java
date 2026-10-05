package com.bancoxyz.bff.atm.operaciones;

import com.bancoxyz.bff.atm.config.AtmProperties;
import com.bancoxyz.bff.atm.dto.ComprobanteAtm;
import com.bancoxyz.bff.atm.resiliencia.NucleoAtm;
import com.bancoxyz.bff.common.error.OperacionInvalidaException;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Retiro desde un cajero. Valida primero lo que solo el canal sabe (que el
 * monto se pueda armar con los billetes de la maquina y el tope del canal) y
 * recien entonces le pide al nucleo aplicar la operacion, que es quien valida
 * saldo, limite diario e idempotencia.
 *
 * <p>Cada operacion queda en el log con el dispositivo, la cuenta y el codigo
 * de autorizacion, que es el rastro que exige un canal que entrega efectivo.
 */
@Service
public class RetiroAtmService {

    private static final Logger log = LoggerFactory.getLogger(RetiroAtmService.class);

    private final NucleoAtm nucleo;
    private final AtmProperties propiedades;

    public RetiroAtmService(NucleoAtm nucleo, AtmProperties propiedades) {
        this.nucleo = nucleo;
        this.propiedades = propiedades;
    }

    public ComprobanteAtm retirar(UsuarioCanal usuario, BigDecimal monto, String claveIdempotencia) {
        validarDenominacion(monto);
        validarTope(monto);

        var solicitud = new SolicitudRetiro(
                monto, Canal.ATM.codigo(), claveIdempotencia, usuario.dispositivoId());
        ComprobanteRetiro comprobante = nucleo.retirar(usuario.cuentaId(), solicitud);

        log.info("Retiro {} por {} en cajero {} sobre la cuenta {} (reintento={})",
                comprobante.codigoAutorizacion(), comprobante.montoRetirado(),
                usuario.dispositivoId(), usuario.cuentaId(), comprobante.reintento());

        return ComprobanteAtm.desde(comprobante, usuario.dispositivoId());
    }

    private void validarDenominacion(BigDecimal monto) {
        if (monto.remainder(propiedades.denominacion()).signum() != 0) {
            throw new OperacionInvalidaException("DENOMINACION_INVALIDA",
                    "El cajero solo entrega multiplos de " + propiedades.denominacion().toPlainString());
        }
    }

    private void validarTope(BigDecimal monto) {
        if (monto.compareTo(propiedades.montoMaximo()) > 0) {
            throw new OperacionInvalidaException("MONTO_SOBRE_TOPE_CANAL",
                    "El maximo por operacion en cajero es " + propiedades.montoMaximo().toPlainString());
        }
    }
}
