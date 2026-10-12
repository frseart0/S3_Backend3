package com.bancoxyz.cuentas.gestion;

import com.bancoxyz.domain.contract.CuentaOperativa;
import com.bancoxyz.domain.contract.SolicitudAperturaCuenta;
import com.bancoxyz.domain.contract.SolicitudMantenimientoCuenta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/cuentas")
@Tag(name = "Ciclo de vida", description = "Apertura, cierre y mantenimiento de cuentas")
public class CuentaGestionController {

    private final CuentaGestionService gestion;

    public CuentaGestionController(CuentaGestionService gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @Operation(summary = "Abre una cuenta y deja su saldo inicial")
    public CuentaOperativa abrir(@RequestBody SolicitudAperturaCuenta solicitud) {
        return gestion.abrir(solicitud);
    }

    @GetMapping("/{cuentaId}/ficha")
    @Operation(summary = "Ficha de ciclo de vida de la cuenta")
    public CuentaOperativa consultar(@PathVariable long cuentaId) {
        return gestion.consultar(cuentaId);
    }

    @PostMapping("/{cuentaId}/cierre")
    @Operation(summary = "Cierra la cuenta. Los pagos posteriores se rechazan")
    public CuentaOperativa cerrar(@PathVariable long cuentaId) {
        return gestion.cerrar(cuentaId);
    }

    @PatchMapping("/{cuentaId}")
    @Operation(summary = "Actualiza alias o tipo, sin tocar el saldo")
    public CuentaOperativa mantener(@PathVariable long cuentaId,
                                    @RequestBody SolicitudMantenimientoCuenta solicitud) {
        return gestion.mantener(cuentaId, solicitud);
    }
}
