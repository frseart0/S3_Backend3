package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.domain.Canal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Emite y valida los tokens de un canal. Cada BFF instancia su propio
 * {@code JwtService} con su {@link JwtProperties}, por lo que la validacion
 * exige que el token traiga la audiencia del canal: presentar un token de la
 * web en el BFF del cajero falla aqui, antes de llegar al controlador.
 */
public class JwtService {

    private static final String CLAIM_CUENTA = "cuentaId";
    private static final String CLAIM_NOMBRE = "nombre";
    private static final String CLAIM_TIPO = "typ";
    private static final String CLAIM_DISPOSITIVO = "dispositivoId";

    private final JwtProperties propiedades;
    private final SecretKey clave;

    public JwtService(JwtProperties propiedades) {
        this.propiedades = propiedades;
        this.clave = Keys.hmacShaKeyFor(propiedades.secreto().getBytes(StandardCharsets.UTF_8));
    }

    public Canal canal() {
        return propiedades.canal();
    }

    public Duration duracionAcceso() {
        return propiedades.duracionAcceso();
    }

    public Duration duracionRefresco() {
        return propiedades.duracionRefresco();
    }

    public String emitirAcceso(UsuarioCanal usuario) {
        return emitir(usuario, TipoToken.ACCESO, propiedades.duracionAcceso());
    }

    public String emitirRefresco(UsuarioCanal usuario) {
        if (!propiedades.soportaRefresco()) {
            throw new IllegalStateException(
                    "El canal " + propiedades.canal().codigo() + " no tiene refresh tokens habilitados");
        }
        return emitir(usuario, TipoToken.REFRESCO, propiedades.duracionRefresco());
    }

    private String emitir(UsuarioCanal usuario, TipoToken tipo, Duration vigencia) {
        Instant ahora = Instant.now();
        var constructor = Jwts.builder()
                .issuer(propiedades.emisor())
                .audience().add(propiedades.canal().codigo()).and()
                .subject(usuario.usuario())
                .claim(CLAIM_CUENTA, usuario.cuentaId())
                .claim(CLAIM_NOMBRE, usuario.nombre())
                .claim(CLAIM_TIPO, tipo.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(vigencia)));

        if (usuario.dispositivoId() != null) {
            constructor.claim(CLAIM_DISPOSITIVO, usuario.dispositivoId());
        }
        return constructor.signWith(clave).compact();
    }

    /**
     * Valida firma, vigencia, emisor, audiencia y tipo de token, y reconstruye
     * la identidad que viaja en los claims.
     */
    public UsuarioCanal validar(String token, TipoToken tipoEsperado) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(clave)
                    .requireIssuer(propiedades.emisor())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenInvalidoException("Token no valido: " + ex.getMessage(), ex);
        }

        Set<String> audiencia = claims.getAudience();
        String canalEsperado = propiedades.canal().codigo();
        if (audiencia == null || !audiencia.contains(canalEsperado)) {
            throw new TokenInvalidoException(
                    "El token no fue emitido para el canal " + canalEsperado);
        }

        String tipo = claims.get(CLAIM_TIPO, String.class);
        if (!tipoEsperado.name().equals(tipo)) {
            throw new TokenInvalidoException(
                    "Se esperaba un token de tipo " + tipoEsperado + " y se recibio " + tipo);
        }

        Number cuentaId = claims.get(CLAIM_CUENTA, Number.class);
        return new UsuarioCanal(
                claims.getSubject(),
                cuentaId == null ? null : cuentaId.longValue(),
                claims.get(CLAIM_NOMBRE, String.class),
                propiedades.canal(),
                claims.get(CLAIM_DISPOSITIVO, String.class));
    }
}
