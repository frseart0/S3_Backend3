package com.bancoxyz.auth.grant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lee el form del token endpoint solo cuando {@code grant_type=canal_password}.
 * Para el resto de los grants devuelve null y deja actuar a los converters
 * de serie (por ejemplo {@code refresh_token}).
 */
public class OAuth2CanalPasswordAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grant = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!CanalPasswordGrant.TIPO.getValue().equals(grant)) {
            return null;
        }
        return new OAuth2CanalPasswordAuthenticationToken(
                request.getParameter("canal"),
                request.getParameter("username"),
                request.getParameter("password"),
                request.getParameter("tarjeta"),
                request.getParameter("pin"),
                request.getParameter("dispositivo_id"),
                request.getParameter("dispositivo_clave"),
                scopes(request.getParameter(OAuth2ParameterNames.SCOPE)),
                SecurityContextHolder.getContext().getAuthentication());
    }

    private static Set<String> scopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(scope.split(" ")));
    }
}
