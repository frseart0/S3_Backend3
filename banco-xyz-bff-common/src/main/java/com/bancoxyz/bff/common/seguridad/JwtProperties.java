package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.domain.Canal;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros del emisor de tokens de un BFF. Cada canal define su propio
 * secreto, su propia audiencia y su propia duracion de sesion: eso es lo que
 * hace que un token emitido para la web sea invalido en el cajero.
 *
 * @param secreto      clave HMAC-SHA256; minimo 32 bytes
 * @param emisor       claim {@code iss} (ej. {@code banco-xyz-bff-web})
 * @param canal        claim {@code aud}; identifica el canal duenno del token
 * @param duracionAcceso  vigencia del access token
 * @param duracionRefresco vigencia del refresh token ({@code null} si el canal no usa refresco)
 */
@ConfigurationProperties(prefix = "bff.jwt")
public record JwtProperties(
        String secreto,
        String emisor,
        Canal canal,
        Duration duracionAcceso,
        Duration duracionRefresco) {

    public JwtProperties {
        if (secreto == null || secreto.getBytes().length < 32) {
            throw new IllegalArgumentException(
                    "bff.jwt.secreto debe tener al menos 32 bytes para firmar con HMAC-SHA256");
        }
        if (canal == null) {
            throw new IllegalArgumentException("bff.jwt.canal es obligatorio (web, mobile o atm)");
        }
        if (duracionAcceso == null || duracionAcceso.isZero() || duracionAcceso.isNegative()) {
            throw new IllegalArgumentException("bff.jwt.duracion-acceso debe ser positiva");
        }
    }

    public boolean soportaRefresco() {
        return duracionRefresco != null && !duracionRefresco.isZero() && !duracionRefresco.isNegative();
    }
}
