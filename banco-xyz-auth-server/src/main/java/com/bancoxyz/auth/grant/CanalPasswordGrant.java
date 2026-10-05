package com.bancoxyz.auth.grant;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Grant propio del banco. Spring Authorization Server no trae el grant
 * {@code password} (esta deprecado en OAuth 2.0); {@code canal_password}
 * cubre usuario/clave de web y movil, y tarjeta/PIN del cajero, y deja que
 * cada cliente OAuth solo emita tokens de su canal.
 */
public final class CanalPasswordGrant {

    public static final AuthorizationGrantType TIPO = new AuthorizationGrantType("canal_password");

    private CanalPasswordGrant() {
    }
}
