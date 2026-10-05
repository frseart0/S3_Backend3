package com.bancoxyz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros del authorization server. El secreto HMAC es el mismo que usan
 * los BFF para validar el JWT, asi un token emitido aqui entra en el canal
 * correcto sin que cada BFF tenga su propia clave de firma.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String issuer,
        String secretoHmac,
        String clientSecret,
        String coreBaseUrl,
        String claveInterna,
        Duration webAccess,
        Duration mobileAccess,
        Duration mobileRefresh,
        Duration atmAccess) {

    public AuthProperties {
        if (secretoHmac == null || secretoHmac.getBytes().length < 32) {
            throw new IllegalArgumentException("auth.secreto-hmac debe tener al menos 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "http://banco-xyz-auth";
        }
        if (webAccess == null) {
            webAccess = Duration.ofMinutes(30);
        }
        if (mobileAccess == null) {
            mobileAccess = Duration.ofMinutes(15);
        }
        if (mobileRefresh == null) {
            mobileRefresh = Duration.ofDays(30);
        }
        if (atmAccess == null) {
            atmAccess = Duration.ofMinutes(3);
        }
    }
}
