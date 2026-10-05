package com.bancoxyz.auth.config;

import com.bancoxyz.auth.grant.CanalPasswordGrant;
import com.bancoxyz.auth.grant.OAuth2CanalPasswordAuthenticationConverter;
import com.bancoxyz.auth.grant.OAuth2CanalPasswordAuthenticationProvider;
import com.bancoxyz.auth.identidad.CoreIdentidadClient;
import com.bancoxyz.auth.identidad.IdentidadAutenticada;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServer(HttpSecurity http,
                                                   CoreIdentidadClient core,
                                                   OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
                                                   OAuth2AuthorizationService authorizationService) throws Exception {
        var provider = new OAuth2CanalPasswordAuthenticationProvider(core, tokenGenerator, authorizationService);
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .csrf(AbstractHttpConfigurer::disable)
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer.tokenEndpoint(tokenEndpoint ->
                                tokenEndpoint
                                        .accessTokenRequestConverters(converters ->
                                                converters.add(new OAuth2CanalPasswordAuthenticationConverter()))
                                        .authenticationProviders(providers -> providers.add(provider))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain recursos(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(AuthProperties propiedades,
                                                                PasswordEncoder passwordEncoder) {
        return new InMemoryRegisteredClientRepository(
                cliente("web", "web-client", propiedades, passwordEncoder,
                        propiedades.webAccess(), null, "web"),
                cliente("mobile", "mobile-client", propiedades, passwordEncoder,
                        propiedades.mobileAccess(), propiedades.mobileRefresh(), "mobile"),
                cliente("atm", "atm-client", propiedades, passwordEncoder,
                        propiedades.atmAccess(), null, "atm"));
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(AuthProperties propiedades) {
        SecretKey key = new SecretKeySpec(
                propiedades.secretoHmac().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .keyID("banco-xyz-hmac")
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .build();
        JWKSet conjunto = new JWKSet(jwk);
        return (selector, contexto) -> selector.select(conjunto);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * El authorization server no publica este generador como bean. El grant
     * {@code canal_password} lo necesita para firmar el access token con HMAC.
     */
    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(
            JwtEncoder jwtEncoder,
            OAuth2TokenCustomizer<JwtEncodingContext> claimsDeCanal) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(claimsDeCanal);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(AuthProperties propiedades) {
        return AuthorizationServerSettings.builder()
                .issuer(propiedades.issuer())
                .build();
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    /**
     * Copia la identidad verificada a los claims que los BFF ya conocen:
     * audiencia del canal, cuenta, nombre y tipo de token.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> claimsDeCanal() {
        return contexto -> {
            contexto.getJwsHeader().algorithm(MacAlgorithm.HS256);
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(contexto.getTokenType())) {
                return;
            }
            Authentication principal = contexto.getPrincipal();
            if (principal != null && principal.getPrincipal() instanceof IdentidadAutenticada identidad) {
                aplicar(contexto, identidad);
            }
        };
    }

    private static void aplicar(JwtEncodingContext contexto, IdentidadAutenticada identidad) {
        contexto.getClaims()
                .subject(identidad.usuario())
                .audience(List.of(identidad.canal()))
                .claim("cuentaId", identidad.cuentaId())
                .claim("nombre", identidad.nombre())
                .claim("typ", "ACCESO")
                .claim("scope", identidad.canal());
        if (identidad.dispositivoId() != null) {
            contexto.getClaims().claim("dispositivoId", identidad.dispositivoId());
        }
    }

    private static RegisteredClient cliente(String id,
                                           String clientId,
                                           AuthProperties propiedades,
                                           PasswordEncoder passwordEncoder,
                                           Duration access,
                                           Duration refresh,
                                           String scope) {
        TokenSettings.Builder tokens = TokenSettings.builder()
                .accessTokenTimeToLive(access)
                .accessTokenFormat(org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat.SELF_CONTAINED);
        var builder = RegisteredClient.withId(id)
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(propiedades.clientSecret()))
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(CanalPasswordGrant.TIPO)
                .scope(scope);
        if (refresh != null) {
            builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            tokens.refreshTokenTimeToLive(refresh).reuseRefreshTokens(true);
        }
        return builder.tokenSettings(tokens.build()).build();
    }
}
