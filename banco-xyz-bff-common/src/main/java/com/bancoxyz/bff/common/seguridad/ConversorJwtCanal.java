package com.bancoxyz.bff.common.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Adapta el JWT del authorization server (o el emitido en local con la misma
 * clave) al principal {@link UsuarioCanal}. Rechaza audiencia o tipo ajenos
 * antes de llegar al controlador.
 */
public class ConversorJwtCanal implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtProperties propiedades;

    public ConversorJwtCanal(JwtProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String canal = propiedades.canal().codigo();
        List<String> audiencia = jwt.getAudience();
        if (audiencia == null || !audiencia.contains(canal)) {
            throw rechazo("El token no fue emitido para el canal " + canal);
        }
        if (!propiedades.emisor().equals(JwtService.emisor(jwt))) {
            throw rechazo("Token no valido: emisor distinto");
        }
        String tipo = jwt.getClaimAsString("typ");
        if (tipo != null && !TipoToken.ACCESO.name().equals(tipo)) {
            throw rechazo("Se esperaba un token de tipo ACCESO y se recibio " + tipo);
        }
        UsuarioCanal usuario = JwtService.identidad(jwt);
        return new UsernamePasswordAuthenticationToken(
                usuario, jwt, List.of(new SimpleGrantedAuthority(propiedades.canal().rol())));
    }

    private static OAuth2AuthenticationException rechazo(String mensaje) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", mensaje, null));
    }
}
