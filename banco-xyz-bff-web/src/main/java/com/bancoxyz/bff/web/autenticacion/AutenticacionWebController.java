package com.bancoxyz.bff.web.autenticacion;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.common.seguridad.VerificacionIdentidad;
import com.bancoxyz.bff.web.dto.SesionWeb;
import com.bancoxyz.domain.Canal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sesion del canal web: usuario y contrasena, token de 30 minutos y sin
 * refresco. El navegador siempre puede volver a pedir credenciales, a
 * diferencia de la app movil que necesita mantener la sesion viva en segundo
 * plano.
 */
@RestController
@RequestMapping("/api/web/auth")
@Tag(name = "Autenticacion web")
public class AutenticacionWebController {

    private final CoreApiClient core;
    private final JwtService jwt;

    public AutenticacionWebController(CoreApiClient core, JwtService jwt) {
        this.core = core;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Operation(summary = "Emite un token con audiencia 'web' para la banca en linea")
    public SesionWeb login(@Valid @RequestBody CredencialesWeb credenciales) {
        var resultado = core.validarCredenciales(credenciales.usuario(), credenciales.password());
        UsuarioCanal usuario = VerificacionIdentidad.exigirAutenticado(resultado, Canal.WEB);

        return new SesionWeb(
                jwt.emitirAcceso(usuario),
                "Bearer",
                jwt.duracionAcceso().toSeconds(),
                usuario.usuario(),
                usuario.nombre(),
                usuario.cuentaId(),
                Canal.WEB.codigo(),
                List.of("CONSULTAR_SALDO", "CONSULTAR_MOVIMIENTOS", "VER_REPORTES"));
    }

    @GetMapping("/sesion")
    @Operation(summary = "Devuelve la identidad asociada al token en uso")
    public UsuarioCanal sesion(@AuthenticationPrincipal UsuarioCanal usuario) {
        return usuario;
    }
}
