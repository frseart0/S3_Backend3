package com.bancoxyz.auth.grant;

import com.bancoxyz.auth.identidad.CoreIdentidadClient;
import com.bancoxyz.auth.identidad.IdentidadAutenticada;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SolicitudPin;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifica al usuario contra el core-api y emite un access token JWT (y un
 * refresh token si el cliente del canal lo tiene habilitado).
 */
public class OAuth2CanalPasswordAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

    private final CoreIdentidadClient core;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final OAuth2AuthorizationService authorizationService;

    public OAuth2CanalPasswordAuthenticationProvider(CoreIdentidadClient core,
                                                     OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
                                                     OAuth2AuthorizationService authorizationService) {
        this.core = core;
        this.tokenGenerator = tokenGenerator;
        this.authorizationService = authorizationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var solicitud = (OAuth2CanalPasswordAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken cliente = clienteAutenticado(solicitud);
        RegisteredClient registeredClient = cliente.getRegisteredClient();
        if (registeredClient == null
                || !registeredClient.getAuthorizationGrantTypes().contains(CanalPasswordGrant.TIPO)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        IdentidadAutenticada identidad = identificar(solicitud);
        if (!registeredClient.getScopes().contains(identidad.canal())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
        Set<String> authorizedScopes = scopesAutorizados(solicitud.scopes(), registeredClient);

        var usuarioAuth = new UsernamePasswordAuthenticationToken(
                identidad, null, List.of(new SimpleGrantedAuthority("ROLE_" + identidad.canal().toUpperCase())));

        OAuth2Token generatedAccessToken = generar(registeredClient, usuarioAuth, authorizedScopes,
                solicitud, OAuth2TokenType.ACCESS_TOKEN);
        if (generatedAccessToken == null) {
            throw error(OAuth2ErrorCodes.SERVER_ERROR, "No se pudo emitir el access token");
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(),
                generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(),
                authorizedScopes);

        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2Token generatedRefresh = generar(registeredClient, usuarioAuth, authorizedScopes,
                    solicitud, OAuth2TokenType.REFRESH_TOKEN);
            if (generatedRefresh != null) {
                refreshToken = generatedRefresh instanceof OAuth2RefreshToken existente
                        ? existente
                        : new OAuth2RefreshToken(generatedRefresh.getTokenValue(),
                        generatedRefresh.getIssuedAt(), generatedRefresh.getExpiresAt());
            }
        }

        OAuth2Authorization.Builder authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(identidad.usuario())
                .authorizationGrantType(CanalPasswordGrant.TIPO)
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), usuarioAuth);
        if (generatedAccessToken instanceof Jwt jwt) {
            authorization.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, jwt.getClaims()));
        } else if (generatedAccessToken instanceof ClaimAccessor accessor) {
            authorization.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, accessor.getClaims()));
        } else {
            authorization.accessToken(accessToken);
        }
        if (refreshToken != null) {
            authorization.refreshToken(refreshToken);
        }
        authorizationService.save(authorization.build());

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, cliente, accessToken, refreshToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2CanalPasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2Token generar(RegisteredClient registeredClient,
                                Authentication usuario,
                                Set<String> scopes,
                                OAuth2CanalPasswordAuthenticationToken solicitud,
                                OAuth2TokenType tipo) {
        OAuth2TokenContext contexto = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(usuario)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(scopes)
                .tokenType(tipo)
                .authorizationGrantType(CanalPasswordGrant.TIPO)
                .authorizationGrant(solicitud)
                .build();
        return tokenGenerator.generate(contexto);
    }

    private IdentidadAutenticada identificar(OAuth2CanalPasswordAuthenticationToken solicitud) {
        if (solicitud.canal() == null || solicitud.canal().isBlank()) {
            throw error(OAuth2ErrorCodes.INVALID_REQUEST, "El canal es obligatorio");
        }
        ResultadoAutenticacion resultado;
        try {
            if ("atm".equals(solicitud.canal())) {
                resultado = core.pin(new SolicitudPin(
                        solicitud.tarjeta(), solicitud.pin(),
                        solicitud.dispositivoId(), solicitud.dispositivoClave()));
            } else {
                resultado = core.credenciales(solicitud.usuario(), solicitud.password(), solicitud.canal());
            }
        } catch (RuntimeException ex) {
            throw error(OAuth2ErrorCodes.SERVER_ERROR, "El nucleo no pudo verificar la identidad");
        }
        if (resultado == null || !resultado.autenticado()) {
            String motivo = resultado == null || resultado.motivo() == null || resultado.motivo().isBlank()
                    ? "Credenciales incorrectas"
                    : resultado.motivo();
            if (resultado != null && resultado.bloqueado()) {
                throw error("account_locked", motivo);
            }
            throw error(OAuth2ErrorCodes.INVALID_GRANT, motivo);
        }
        String dispositivo = "atm".equals(solicitud.canal()) ? solicitud.dispositivoId() : null;
        return new IdentidadAutenticada(
                resultado.usuario(), resultado.cuentaId(), resultado.nombre(), solicitud.canal(), dispositivo);
    }

    private static Set<String> scopesAutorizados(Set<String> pedidos, RegisteredClient cliente) {
        if (pedidos == null || pedidos.isEmpty()) {
            return new LinkedHashSet<>(cliente.getScopes());
        }
        Set<String> autorizados = new LinkedHashSet<>();
        for (String scope : pedidos) {
            if (!cliente.getScopes().contains(scope)) {
                throw error(OAuth2ErrorCodes.INVALID_SCOPE, "Scope no permitido: " + scope);
            }
            autorizados.add(scope);
        }
        return autorizados;
    }

    private static OAuth2ClientAuthenticationToken clienteAutenticado(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken cliente
                && cliente.isAuthenticated()) {
            return cliente;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    private static OAuth2AuthenticationException error(String codigo, String descripcion) {
        return new OAuth2AuthenticationException(new OAuth2Error(codigo, descripcion, null));
    }
}
