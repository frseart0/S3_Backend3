package com.bancoxyz.bff.web.dashboard;

import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.web.dto.DashboardWeb;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta nunca llega por parametro: se toma del token, de modo que un
 * usuario no puede pedir el dashboard de otro cambiando la URL.
 */
@RestController
@RequestMapping("/api/web")
@Tag(name = "Dashboard web")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Perfil, saldo, cartola reciente, intereses y estados anuales en una llamada")
    public DashboardWeb dashboard(@AuthenticationPrincipal UsuarioCanal usuario) {
        return dashboard.componer(usuario.cuentaId());
    }
}
