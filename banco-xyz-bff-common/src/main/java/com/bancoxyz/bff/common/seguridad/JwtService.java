package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.domain.Canal;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Emite y valida los JWT de un canal con la misma clave HMAC que usa el
 * authorization server. La audiencia tiene que coincidir con el canal: un
 * token de la web no abre el cajero aunque la firma sea valida.
 */
public class JwtService {

    static final String KID = "banco-xyz-hmac";
    private static final String CLAIM_CUENTA = "cuentaId";
    private static final String CLAIM_NOMBRE = "nombre";
    private static final String CLAIM_TIPO = "typ";
    private static final String CLAIM_DISPOSITIVO = "dispositivoId";
    private static final String CLAIM_SCOPE = "scope";

    private final JwtProperties propiedades;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(JwtProperties propiedades) {
        this.propiedades = propiedades;
        SecretKey clave = new SecretKeySpec(
                propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(jwks(clave));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        nimbus.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        this.decoder = nimbus;
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

    public JwtDecoder decodificador() {
        return decoder;
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

    /**
     * Valida firma, vigencia, emisor, audiencia y tipo de token, y reconstruye
     * la identidad que viaja en los claims.
     */
    public UsuarioCanal validar(String token, TipoToken tipoEsperado) {
        Jwt jwt = decodificar(token);
        if (!propiedades.emisor().equals(emisor(jwt))) {
            throw new TokenInvalidoException("Token no valido: emisor distinto");
        }
        List<String> audiencia = jwt.getAudience();
        String canalEsperado = propiedades.canal().codigo();
        if (audiencia == null || !audiencia.contains(canalEsperado)) {
            throw new TokenInvalidoException("El token no fue emitido para el canal " + canalEsperado);
        }
        String tipo = jwt.getClaimAsString(CLAIM_TIPO);
        if (!tipoEsperado.name().equals(tipo)) {
            throw new TokenInvalidoException(
                    "Se esperaba un token de tipo " + tipoEsperado + " y se recibio " + tipo);
        }
        return identidad(jwt);
    }

    private Jwt decodificar(String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenInvalidoException("Token no valido: " + ex.getMessage(), ex);
        }
    }

    private String emitir(UsuarioCanal usuario, TipoToken tipo, Duration vigencia) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .audience(List.of(propiedades.canal().codigo()))
                .subject(usuario.usuario())
                .claim(CLAIM_CUENTA, usuario.cuentaId())
                .claim(CLAIM_NOMBRE, usuario.nombre())
                .claim(CLAIM_TIPO, tipo.name())
                .claim(CLAIM_SCOPE, propiedades.canal().codigo())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia));
        if (usuario.dispositivoId() != null) {
            claims.claim(CLAIM_DISPOSITIVO, usuario.dispositivoId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(KID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    static String emisor(Jwt jwt) {
        Object emisor = jwt.getClaim("iss");
        return emisor == null ? null : emisor.toString();
    }

    static UsuarioCanal identidad(Jwt jwt) {
        Number cuenta = jwt.getClaim(CLAIM_CUENTA);
        String canalClaim = jwt.getAudience() == null || jwt.getAudience().isEmpty()
                ? jwt.getClaimAsString(CLAIM_SCOPE)
                : jwt.getAudience().getFirst();
        return new UsuarioCanal(
                jwt.getSubject(),
                cuenta == null ? null : cuenta.longValue(),
                jwt.getClaimAsString(CLAIM_NOMBRE),
                Canal.desde(canalClaim),
                jwt.getClaimAsString(CLAIM_DISPOSITIVO));
    }

    private static JWKSource<SecurityContext> jwks(SecretKey clave) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(clave)
                .keyID(KID)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .build();
        JWKSet conjunto = new JWKSet(jwk);
        return (selector, contexto) -> selector.select(conjunto);
    }
}
