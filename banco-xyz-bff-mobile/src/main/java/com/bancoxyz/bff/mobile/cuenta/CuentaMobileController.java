package com.bancoxyz.bff.mobile.cuenta;

import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.mobile.dto.InicioMobile;
import com.bancoxyz.bff.mobile.dto.MovimientoMobile;
import com.bancoxyz.bff.mobile.dto.SaldoMobile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mobile")
@Tag(name = "Cuenta movil")
public class CuentaMobileController {

    private final CuentaMobileService cuentas;

    public CuentaMobileController(CuentaMobileService cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/inicio")
    @Operation(summary = "Saldo y ultimos movimientos para la primera pantalla de la app")
    public InicioMobile inicio(@AuthenticationPrincipal UsuarioCanal usuario) {
        return cuentas.inicio(usuario);
    }

    @GetMapping("/saldo")
    @Operation(summary = "Solo el saldo, la consulta mas frecuente del canal")
    public SaldoMobile saldo(@AuthenticationPrincipal UsuarioCanal usuario) {
        return cuentas.saldo(usuario);
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Ultimos movimientos confiables, acotados al maximo del canal")
    public List<MovimientoMobile> movimientos(@AuthenticationPrincipal UsuarioCanal usuario,
                                              @RequestParam(defaultValue = "10") int limite) {
        return cuentas.movimientos(usuario, limite);
    }
}
