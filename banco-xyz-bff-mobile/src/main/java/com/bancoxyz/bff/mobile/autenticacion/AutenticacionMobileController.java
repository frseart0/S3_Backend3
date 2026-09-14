package com.bancoxyz.bff.mobile.autenticacion;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.TipoToken;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.common.seguridad.VerificacionIdentidad;
import com.bancoxyz.bff.mobile.dto.SesionMobile;
import com.bancoxyz.domain.Canal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesion del canal movil: access token de 15 minutos mas refresh token de 30
 * dias. El refresco existe porque una app se abre muchas veces al dia y pedir
 * la clave en cada apertura seria inaceptable, mientras que mantener vivo un
 * access token largo en un dispositivo que se puede perder no es aceptable
 * desde la seguridad.
 */
@RestController
@RequestMapping("/api/mobile/auth")
@Tag(name = "Autenticacion movil")
public class AutenticacionMobileController {

    private final CoreApiClient core;
    private final JwtService jwt;

    public AutenticacionMobileController(CoreApiClient core, JwtService jwt) {
        this.core = core;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Operation(summary = "Emite access y refresh token con audiencia 'mobile'")
    public SesionMobile login(@Valid @RequestBody CredencialesMobile credenciales) {
        var resultado = core.validarCredenciales(credenciales.usuario(), credenciales.password());
        UsuarioCanal usuario = VerificacionIdentidad.exigirAutenticado(resultado, Canal.MOBILE);
        return sesion(usuario, jwt.emitirRefresco(usuario));
    }

    /**
     * Renueva el access token sin volver a pedir credenciales. El refresh token
     * se reutiliza: rotarlo obligaria a guardar estado de sesion, que es
     * justamente lo que este BFF evita al ser stateless.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renueva el access token a partir de un refresh token vigente")
    public SesionMobile refrescar(@Valid @RequestBody SolicitudRefresco solicitud) {
        UsuarioCanal usuario = jwt.validar(solicitud.refreshToken(), TipoToken.REFRESCO);
        return sesion(usuario, solicitud.refreshToken());
    }

    private SesionMobile sesion(UsuarioCanal usuario, String refreshToken) {
        return new SesionMobile(
                jwt.emitirAcceso(usuario),
                refreshToken,
                jwt.duracionAcceso().toSeconds(),
                usuario.nombre());
    }
}
