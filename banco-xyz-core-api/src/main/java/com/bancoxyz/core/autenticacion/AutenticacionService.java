package com.bancoxyz.core.autenticacion;

import com.bancoxyz.core.config.SeguridadInternaProperties;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SolicitudCredenciales;
import com.bancoxyz.domain.contract.SolicitudPin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifica identidades. El core-api no emite tokens ni maneja sesiones: solo
 * responde quien es el usuario y en que canales puede operar, y cada BFF decide
 * como representar esa sesion. Asi la politica de canal (duracion, refresco,
 * PIN) vive en el BFF y la fuente de verdad de las credenciales en el nucleo.
 *
 * <p>Los mensajes de rechazo no distinguen entre usuario inexistente y
 * contrasena incorrecta, para no confirmar que una cuenta existe.
 */
@Service
public class AutenticacionService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionService.class);
    private static final String RECHAZO_GENERICO = "Credenciales incorrectas";

    private final UsuarioCanalRepository usuarios;
    private final DispositivoRepository dispositivos;
    private final PasswordEncoder encoder;
    private final SeguridadInternaProperties seguridad;

    public AutenticacionService(UsuarioCanalRepository usuarios,
                                DispositivoRepository dispositivos,
                                PasswordEncoder encoder,
                                SeguridadInternaProperties seguridad) {
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
        this.encoder = encoder;
        this.seguridad = seguridad;
    }

    public ResultadoAutenticacion validarCredenciales(SolicitudCredenciales solicitud) {
        if (solicitud.usuario() == null || solicitud.password() == null || solicitud.canal() == null) {
            return ResultadoAutenticacion.rechazo(RECHAZO_GENERICO);
        }

        Optional<RegistroUsuario> encontrado = usuarios.buscarPorUsuario(solicitud.usuario());
        if (encontrado.isEmpty()) {
            return ResultadoAutenticacion.rechazo(RECHAZO_GENERICO);
        }

        RegistroUsuario registro = encontrado.get();
        if (registro.bloqueado()) {
            return ResultadoAutenticacion.bloqueado("La identidad esta bloqueada por intentos fallidos");
        }
        if (!registro.habilitadoEn(solicitud.canal())) {
            log.info("Usuario {} intento entrar por el canal {} sin habilitacion",
                    registro.usuario(), solicitud.canal());
            return ResultadoAutenticacion.rechazo(
                    "El usuario no esta habilitado en el canal " + solicitud.canal());
        }
        if (!encoder.matches(solicitud.password(), registro.passwordHash())) {
            return fallo(registro, RECHAZO_GENERICO);
        }

        usuarios.limpiarIntentos(registro.id());
        return exito(registro);
    }

    /**
     * Login de cajero: primero se verifica el dispositivo y solo si es legitimo
     * se evalua el PIN de la tarjeta. Un cajero no registrado nunca llega a
     * probar PINes.
     */
    public ResultadoAutenticacion validarPin(SolicitudPin solicitud) {
        if (solicitud.tarjeta() == null || solicitud.pin() == null || solicitud.dispositivoId() == null) {
            return ResultadoAutenticacion.rechazo("Tarjeta, PIN y dispositivo son obligatorios");
        }

        Optional<DispositivoRepository.Dispositivo> dispositivo =
                dispositivos.buscarActivo(solicitud.dispositivoId());
        if (dispositivo.isEmpty()
                || !encoder.matches(nullPorVacio(solicitud.dispositivoClave()), dispositivo.get().claveHash())) {
            log.warn("Cajero {} rechazado: dispositivo no registrado o clave incorrecta",
                    solicitud.dispositivoId());
            return ResultadoAutenticacion.rechazo("Dispositivo no autorizado");
        }

        Optional<RegistroUsuario> encontrado = usuarios.buscarPorTarjeta(solicitud.tarjeta());
        if (encontrado.isEmpty()) {
            return ResultadoAutenticacion.rechazo("Tarjeta no reconocida");
        }

        RegistroUsuario registro = encontrado.get();
        if (registro.bloqueado()) {
            return ResultadoAutenticacion.bloqueado("La tarjeta esta bloqueada, acuda a una sucursal");
        }
        if (!registro.habilitadoEn("atm")) {
            return ResultadoAutenticacion.rechazo("La tarjeta no esta habilitada para cajeros");
        }
        if (registro.pinHash() == null || !encoder.matches(solicitud.pin(), registro.pinHash())) {
            return fallo(registro, "PIN incorrecto");
        }

        usuarios.limpiarIntentos(registro.id());
        log.info("Tarjeta {} autenticada en el cajero {} ({})",
                enmascarar(registro.tarjeta()), solicitud.dispositivoId(), dispositivo.get().ubicacion());
        return exito(registro);
    }

    private ResultadoAutenticacion fallo(RegistroUsuario registro, String motivo) {
        int maxIntentos = seguridad.maxIntentosFallidos();
        int intentos = usuarios.registrarIntentoFallido(registro.id(), maxIntentos);
        int restantes = Math.max(0, maxIntentos - intentos);
        if (restantes == 0) {
            return ResultadoAutenticacion.bloqueado(
                    "Identidad bloqueada tras " + maxIntentos + " intentos fallidos");
        }
        return new ResultadoAutenticacion(false, null, null, null, List.of(), false, restantes, motivo);
    }

    private ResultadoAutenticacion exito(RegistroUsuario registro) {
        return new ResultadoAutenticacion(
                true,
                registro.usuario(),
                registro.cuentaId(),
                registro.nombre(),
                new ArrayList<>(registro.canales()),
                false,
                null,
                null);
    }

    private String nullPorVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private String enmascarar(String tarjeta) {
        if (tarjeta == null || tarjeta.length() < 4) {
            return "****";
        }
        return "****" + tarjeta.substring(tarjeta.length() - 4);
    }
}
