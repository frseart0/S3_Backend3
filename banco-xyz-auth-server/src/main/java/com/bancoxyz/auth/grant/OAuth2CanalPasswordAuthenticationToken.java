package com.bancoxyz.auth.grant;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.Set;

/**
 * Solicitud de token todavia no autenticada. El principal es el cliente
 * OAuth (web-client, mobile-client o atm-client), ya autenticado por HTTP Basic.
 */
public class OAuth2CanalPasswordAuthenticationToken extends AbstractAuthenticationToken {

    private final String canal;
    private final String usuario;
    private final String password;
    private final String tarjeta;
    private final String pin;
    private final String dispositivoId;
    private final String dispositivoClave;
    private final Set<String> scopes;
    private final Authentication cliente;

    public OAuth2CanalPasswordAuthenticationToken(String canal,
                                                 String usuario,
                                                 String password,
                                                 String tarjeta,
                                                 String pin,
                                                 String dispositivoId,
                                                 String dispositivoClave,
                                                 Set<String> scopes,
                                                 Authentication cliente) {
        super(Collections.emptyList());
        this.canal = canal;
        this.usuario = usuario;
        this.password = password;
        this.tarjeta = tarjeta;
        this.pin = pin;
        this.dispositivoId = dispositivoId;
        this.dispositivoClave = dispositivoClave;
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        this.cliente = cliente;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return password != null ? password : pin;
    }

    @Override
    public Object getPrincipal() {
        return cliente;
    }

    public String canal() {
        return canal;
    }

    public String usuario() {
        return usuario;
    }

    public String password() {
        return password;
    }

    public String tarjeta() {
        return tarjeta;
    }

    public String pin() {
        return pin;
    }

    public String dispositivoId() {
        return dispositivoId;
    }

    public String dispositivoClave() {
        return dispositivoClave;
    }

    public Set<String> scopes() {
        return scopes;
    }
}
