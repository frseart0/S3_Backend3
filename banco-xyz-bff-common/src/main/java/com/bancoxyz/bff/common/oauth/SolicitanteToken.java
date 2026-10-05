package com.bancoxyz.bff.common.oauth;

import com.bancoxyz.bff.common.error.AutenticacionFallidaException;
import com.bancoxyz.domain.Canal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Pide tokens al authorization server. El BFF no vuelve a firmar: reenvia las
 * credenciales del canal y devuelve el bearer que emitio el issuer OAuth2.
 */
public class SolicitanteToken {

    private final OAuthClienteProperties propiedades;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SolicitanteToken(OAuthClienteProperties propiedades, ObjectMapper objectMapper) {
        this.propiedades = propiedades;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public ResultadoToken canalPassword(Canal canal, String usuario, String password) {
        return pedir(canal, form -> {
            form.add("grant_type", "canal_password");
            form.add("canal", canal.codigo());
            form.add("username", usuario);
            form.add("password", password);
            form.add("scope", canal.codigo());
        });
    }

    public ResultadoToken canalPin(Canal canal,
                                  String tarjeta,
                                  String pin,
                                  String dispositivoId,
                                  String dispositivoClave) {
        return pedir(canal, form -> {
            form.add("grant_type", "canal_password");
            form.add("canal", canal.codigo());
            form.add("tarjeta", tarjeta);
            form.add("pin", pin);
            form.add("dispositivo_id", dispositivoId);
            form.add("dispositivo_clave", dispositivoClave);
            form.add("scope", canal.codigo());
        });
    }

    public ResultadoToken refrescar(Canal canal, String refreshToken) {
        return pedir(canal, form -> {
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", refreshToken);
        });
    }

    private ResultadoToken pedir(Canal canal, Consumer<LinkedMultiValueMap<String, String>> completar) {
        if (propiedades.authServerUrl() == null || propiedades.authServerUrl().isBlank()) {
            throw AutenticacionFallidaException.rechazo("No hay authorization server configurado");
        }
        var form = new LinkedMultiValueMap<String, String>();
        completar.accept(form);
        String base = propiedades.authServerUrl().endsWith("/")
                ? propiedades.authServerUrl().substring(0, propiedades.authServerUrl().length() - 1)
                : propiedades.authServerUrl();
        return restClient.post()
                .uri(base + "/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, basic(canal))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (peticion, respuesta) -> {
                    throw traducir(respuesta.getBody());
                })
                .body(ResultadoToken.class);
    }

    private String basic(Canal canal) {
        String raw = propiedades.clientId(canal) + ":" + propiedades.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private AutenticacionFallidaException traducir(java.io.InputStream cuerpo) {
        String error = "";
        String descripcion = "No se pudo emitir el token";
        try {
            JsonNode json = objectMapper.readTree(cuerpo);
            if (json.hasNonNull("error")) {
                error = json.get("error").asText();
            }
            if (json.hasNonNull("error_description")) {
                descripcion = json.get("error_description").asText();
            }
        } catch (IOException | RuntimeException ex) {
            // El cuerpo de error de OAuth no siempre es JSON.
        }
        if ("account_locked".equals(error)) {
            return AutenticacionFallidaException.bloqueo(descripcion);
        }
        return AutenticacionFallidaException.rechazo(descripcion);
    }
}
