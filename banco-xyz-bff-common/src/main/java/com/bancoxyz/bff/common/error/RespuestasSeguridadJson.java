package com.bancoxyz.bff.common.error;

import com.bancoxyz.bff.common.seguridad.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Convierte los rechazos de la cadena de seguridad en el mismo
 * {@link ErrorRespuesta} que usan los controladores. Sin esto, Spring Security
 * devolveria un 401/403 con cuerpo vacio o una pagina HTML de error.
 */
public class RespuestasSeguridadJson implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RespuestasSeguridadJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException excepcion) throws IOException {
        escribir(response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", mensaje(request, excepcion));
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException excepcion) throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, "SIN_PERMISO",
                "El token no autoriza esta operacion en este canal");
    }

    private static String mensaje(HttpServletRequest request, AuthenticationException excepcion) {
        Object motivo = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_MOTIVO);
        if (motivo != null) {
            return motivo.toString();
        }
        if (excepcion instanceof OAuth2AuthenticationException oauth) {
            OAuth2Error error = oauth.getError();
            if (error != null && error.getDescription() != null && !error.getDescription().isBlank()) {
                return error.getDescription();
            }
        }
        return "Se requiere un token valido para este canal";
    }

    private void escribir(HttpServletResponse response, HttpStatus estado, String codigo, String mensaje)
            throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorRespuesta.de(codigo, mensaje));
    }
}
