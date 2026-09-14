package com.bancoxyz.bff.mobile.cuenta;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.mobile.config.MobileProperties;
import com.bancoxyz.bff.mobile.dto.InicioMobile;
import com.bancoxyz.bff.mobile.dto.MovimientoMobile;
import com.bancoxyz.bff.mobile.dto.SaldoMobile;
import com.bancoxyz.domain.EstadoRegistro;
import com.bancoxyz.domain.contract.Movimiento;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Adapta los datos del core al canal movil: recorta campos, acota el largo de
 * las listas y deja fuera los movimientos que el batch marco como anomalia.
 *
 * <p>Ese filtro es una decision del canal: en la web una anomalia se muestra
 * con su motivo para que el cliente la entienda, pero en una lista de telefono
 * una fila sin explicacion solo genera dudas.
 */
@Service
public class CuentaMobileService {

    private final CoreApiClient core;
    private final MobileProperties propiedades;

    public CuentaMobileService(CoreApiClient core, MobileProperties propiedades) {
        this.core = core;
        this.propiedades = propiedades;
    }

    public InicioMobile inicio(UsuarioCanal usuario) {
        SaldoMobile saldo = SaldoMobile.desde(core.saldo(usuario.cuentaId()));
        List<MovimientoMobile> movimientos = movimientos(usuario, propiedades.movimientosInicio());
        return new InicioMobile(usuario.nombre(), saldo, movimientos);
    }

    public SaldoMobile saldo(UsuarioCanal usuario) {
        return SaldoMobile.desde(core.saldo(usuario.cuentaId()));
    }

    public List<MovimientoMobile> movimientos(UsuarioCanal usuario, int limite) {
        int solicitados = Math.min(Math.max(limite, 1), propiedades.movimientosMaximo());

        // Se piden algunos extra porque el filtro de anomalias puede descartar
        // filas y la lista debe llegar completa a la primera pantalla.
        var pagina = core.movimientos(usuario.cuentaId(), null, null, 0, solicitados * 2);

        return pagina.contenido().stream()
                .filter(this::esConfiable)
                .limit(solicitados)
                .map(movimiento -> MovimientoMobile.desde(movimiento, propiedades.largoDetalle()))
                .toList();
    }

    private boolean esConfiable(Movimiento movimiento) {
        return EstadoRegistro.desde(movimiento.estado()).esConfiable();
    }
}
