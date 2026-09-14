package com.bancoxyz.core.autenticacion;

import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SolicitudCredenciales;
import com.bancoxyz.domain.contract.SolicitudPin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificacion de identidad para los BFF. Responde 200 incluso cuando la
 * verificacion falla: el resultado del chequeo es informacion valida para el
 * canal, que necesita saber si quedan intentos o si la identidad quedo
 * bloqueada para decidir que mostrarle al cliente.
 */
@RestController
@RequestMapping("/internal/auth")
@Tag(name = "Autenticacion", description = "Verificacion de credenciales y de tarjeta + PIN")
public class AutenticacionController {

    private final AutenticacionService autenticacion;

    public AutenticacionController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/credenciales")
    @Operation(summary = "Verifica usuario y contrasena para un canal (web, movil)")
    public ResultadoAutenticacion credenciales(@RequestBody SolicitudCredenciales solicitud) {
        return autenticacion.validarCredenciales(solicitud);
    }

    @PostMapping("/pin")
    @Operation(summary = "Verifica dispositivo, tarjeta y PIN para el canal cajero")
    public ResultadoAutenticacion pin(@RequestBody SolicitudPin solicitud) {
        return autenticacion.validarPin(solicitud);
    }
}
