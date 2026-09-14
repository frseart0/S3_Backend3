package com.bancoxyz.bff.web.movimientos;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.web.dto.MovimientoWeb;
import com.bancoxyz.bff.web.dto.PaginaWeb;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Cartola completa con filtros por rango de fechas y paginacion, el caso de uso
 * tipico de una tabla en pantalla grande.
 */
@RestController
@RequestMapping("/api/web")
@Tag(name = "Movimientos web")
public class MovimientosWebController {

    private final CoreApiClient core;

    public MovimientosWebController(CoreApiClient core) {
        this.core = core;
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Movimientos paginados de la cuenta del token, con filtro por fechas")
    public PaginaWeb<MovimientoWeb> movimientos(
            @AuthenticationPrincipal UsuarioCanal usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        PaginaMovimientos resultado = core.movimientos(usuario.cuentaId(), desde, hasta, pagina, tamano);
        List<MovimientoWeb> contenido = resultado.contenido().stream().map(MovimientoWeb::desde).toList();

        return new PaginaWeb<>(
                contenido,
                resultado.pagina(),
                resultado.tamano(),
                resultado.totalElementos(),
                resultado.totalPaginas());
    }
}
