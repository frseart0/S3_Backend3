package com.bancoxyz.bff.common.oauth;

import com.bancoxyz.domain.Canal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Como obtiene el BFF el access token. {@code local} lo firma el propio canal
 * (pruebas y arranque sin authorization server). {@code authorization-server}
 * se lo pide al auth server con el grant {@code canal_password}.
 */
@ConfigurationProperties(prefix = "bff.oauth2")
public record OAuthClienteProperties(
        String modo,
        String issuer,
        String authServerUrl,
        String clientSecret) {

    public OAuthClienteProperties {
        if (modo == null || modo.isBlank()) {
            modo = "local";
        }
    }

    public boolean remoto() {
        return "authorization-server".equalsIgnoreCase(modo);
    }

    public String clientId(Canal canal) {
        return switch (canal) {
            case WEB -> "web-client";
            case MOBILE -> "mobile-client";
            case ATM -> "atm-client";
        };
    }
}
