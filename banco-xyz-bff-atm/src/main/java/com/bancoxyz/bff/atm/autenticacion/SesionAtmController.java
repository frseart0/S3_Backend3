package com.bancoxyz.bff.atm.autenticacion;

import com.bancoxyz.bff.atm.dto.SesionAtm;
import com.bancoxyz.bff.atm.seguridad.SesionesCerradas;
import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.common.seguridad.VerificacionIdentidad;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.SolicitudPin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Apertura y cierre de sesion en un cajero. La identidad tiene dos capas: el
 * dispositivo (cabeceras {@code X-Device-Id} y {@code X-Device-Key}) y el
 * cliente (tarjeta + PIN). Si el cajero no esta registrado, el nucleo no
 * evalua el PIN.
 */
@RestController
@RequestMapping("/api/atm")
@Tag(name = "Sesion de cajero")
public class SesionAtmController {

    private static final Logger log = LoggerFactory.getLogger(SesionAtmController.class);

    private final CoreApiClient core;
    private final JwtService jwt;
    private final SesionesCerradas sesionesCerradas;

    public SesionAtmController(CoreApiClient core, JwtService jwt, SesionesCerradas sesionesCerradas) {
        this.core = core;
        this.jwt = jwt;
        this.sesionesCerradas = sesionesCerradas;
    }

    @PostMapping("/auth/sesion")
    @Operation(summary = "Abre una sesion de cajero con dispositivo registrado, tarjeta y PIN")
    public SesionAtm abrir(@RequestHeader("X-Device-Id") String dispositivoId,
                           @RequestHeader("X-Device-Key") String dispositivoClave,
                           @Valid @RequestBody SolicitudSesionAtm solicitud) {

        var resultado = core.validarPin(new SolicitudPin(
                solicitud.tarjeta(), solicitud.pin(), dispositivoId, dispositivoClave));
        UsuarioCanal usuario = VerificacionIdentidad.exigirAutenticado(resultado, Canal.ATM, dispositivoId);

        log.info("Sesion abierta en el cajero {} para la cuenta {}", dispositivoId, usuario.cuentaId());
        return new SesionAtm(
                jwt.emitirAcceso(usuario),
                jwt.duracionAcceso().toSeconds(),
                primerNombre(usuario.nombre()),
                dispositivoId);
    }

    /**
     * Invalida el token en curso. El cajero lo llama al expulsar la tarjeta,
     * para que la sesion no sobreviva al cliente que se va.
     */
    @PostMapping("/sesion/cerrar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesion e invalida el token antes de su expiracion")
    public void cerrar(@RequestHeader("Authorization") String autorizacion) {
        String token = autorizacion.substring("Bearer ".length()).trim();
        sesionesCerradas.cerrar(token, Instant.now().plus(jwt.duracionAcceso()));
        log.info("Sesion de cajero cerrada por solicitud del dispositivo");
    }

    private String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Cliente";
        }
        return nombre.trim().split("\\s+")[0];
    }
}
