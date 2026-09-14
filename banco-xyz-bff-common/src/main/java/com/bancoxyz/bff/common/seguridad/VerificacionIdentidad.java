package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.bff.common.error.AutenticacionFallidaException;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;

/**
 * Convierte la respuesta de verificacion del core-api en la identidad del
 * canal. El core responde 200 con {@code autenticado=false} cuando rechaza, y
 * es cada BFF el que decide que significa eso para su cliente: aqui se unifica
 * esa traduccion para que los tres canales rechacen igual.
 */
public final class VerificacionIdentidad {

    private VerificacionIdentidad() {
    }

    public static UsuarioCanal exigirAutenticado(ResultadoAutenticacion resultado, Canal canal) {
        return exigirAutenticado(resultado, canal, null);
    }

    public static UsuarioCanal exigirAutenticado(ResultadoAutenticacion resultado,
                                                 Canal canal,
                                                 String dispositivoId) {
        if (resultado == null) {
            throw AutenticacionFallidaException.rechazo("No se pudo verificar la identidad");
        }
        if (resultado.bloqueado()) {
            throw AutenticacionFallidaException.bloqueo(
                    mensajeOPorDefecto(resultado.motivo(), "La identidad esta bloqueada"));
        }
        if (!resultado.autenticado()) {
            throw AutenticacionFallidaException.rechazo(conIntentosRestantes(resultado));
        }
        return new UsuarioCanal(
                resultado.usuario(),
                resultado.cuentaId(),
                resultado.nombre(),
                canal,
                dispositivoId);
    }

    private static String conIntentosRestantes(ResultadoAutenticacion resultado) {
        String mensaje = mensajeOPorDefecto(resultado.motivo(), "Credenciales incorrectas");
        if (resultado.intentosRestantes() != null) {
            return mensaje + " (intentos restantes: " + resultado.intentosRestantes() + ")";
        }
        return mensaje;
    }

    private static String mensajeOPorDefecto(String motivo, String porDefecto) {
        return motivo == null || motivo.isBlank() ? porDefecto : motivo;
    }
}
