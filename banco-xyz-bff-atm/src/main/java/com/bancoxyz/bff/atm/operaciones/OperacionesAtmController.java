package com.bancoxyz.bff.atm.operaciones;

import com.bancoxyz.bff.atm.config.AtmProperties;
import com.bancoxyz.bff.atm.dto.ComprobanteAtm;
import com.bancoxyz.bff.atm.dto.MovimientoAtm;
import com.bancoxyz.bff.atm.dto.SaldoAtm;
import com.bancoxyz.bff.atm.resiliencia.NucleoAtm;
import com.bancoxyz.bff.common.error.OperacionInvalidaException;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operaciones del cajero. Todas exigen que el {@code X-Device-Id} de la
 * peticion coincida con el dispositivo grabado en el token: un token copiado
 * desde otro cajero no sirve.
 */
@RestController
@RequestMapping("/api/atm")
@Tag(name = "Operaciones de cajero")
public class OperacionesAtmController {

    private final NucleoAtm nucleo;
    private final RetiroAtmService retiros;
    private final AtmProperties propiedades;

    public OperacionesAtmController(NucleoAtm nucleo,
                                    RetiroAtmService retiros,
                                    AtmProperties propiedades) {
        this.nucleo = nucleo;
        this.retiros = retiros;
        this.propiedades = propiedades;
    }

    @GetMapping("/saldo")
    @Operation(summary = "Consulta de saldo disponible")
    public SaldoAtm saldo(@AuthenticationPrincipal UsuarioCanal usuario,
                          @RequestHeader("X-Device-Id") String dispositivoId) {
        exigirMismoDispositivo(usuario, dispositivoId);
        return SaldoAtm.desde(nucleo.saldo(usuario.cuentaId()));
    }

    /**
     * La cabecera {@code Idempotency-Key} es obligatoria y la genera el cajero
     * una vez por operacion: si la respuesta se pierde por un corte de red, el
     * reintento con la misma clave devuelve el comprobante original en vez de
     * entregar efectivo dos veces.
     */
    @PostMapping("/retiros")
    @Operation(summary = "Retiro de efectivo, idempotente respecto de la cabecera Idempotency-Key")
    public ComprobanteAtm retirar(@AuthenticationPrincipal UsuarioCanal usuario,
                                  @RequestHeader("X-Device-Id") String dispositivoId,
                                  @RequestHeader("Idempotency-Key") String claveIdempotencia,
                                  @Valid @RequestBody SolicitudRetiroAtm solicitud) {
        exigirMismoDispositivo(usuario, dispositivoId);
        if (claveIdempotencia.isBlank()) {
            throw new OperacionInvalidaException("CLAVE_IDEMPOTENCIA_REQUERIDA",
                    "La cabecera Idempotency-Key no puede venir vacia");
        }
        return retiros.retirar(usuario, solicitud.monto(), claveIdempotencia);
    }

    @GetMapping("/movimientos/ultimos")
    @Operation(summary = "Ultimos movimientos para el comprobante impreso")
    public List<MovimientoAtm> ultimosMovimientos(@AuthenticationPrincipal UsuarioCanal usuario,
                                                  @RequestHeader("X-Device-Id") String dispositivoId) {
        exigirMismoDispositivo(usuario, dispositivoId);
        var pagina = nucleo.movimientos(usuario.cuentaId(), null, null, 0,
                propiedades.movimientosComprobante());
        return pagina.contenido().stream().map(MovimientoAtm::desde).toList();
    }

    private void exigirMismoDispositivo(UsuarioCanal usuario, String dispositivoId) {
        if (usuario.dispositivoId() == null || !usuario.dispositivoId().equals(dispositivoId)) {
            throw new OperacionInvalidaException("DISPOSITIVO_NO_COINCIDE",
                    "El token no fue emitido para este cajero");
        }
    }
}
