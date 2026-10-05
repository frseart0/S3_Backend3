package com.bancoxyz.bff.common.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/** Respuesta estandar del token endpoint OAuth2. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultadoToken(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("token_type") String tokenType) {

    public long expiraEnSegundos(Duration respaldo) {
        return expiresIn == null ? respaldo.toSeconds() : expiresIn;
    }
}
